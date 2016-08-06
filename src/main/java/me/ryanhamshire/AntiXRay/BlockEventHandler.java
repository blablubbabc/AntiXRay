/**
 * AntiXRay Server Plugin for Minecraft
 * Copyright (C) 2012 Ryan Hamshire
 * Copyright (C) blablubbabc <http://www.blablubbabc.de>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package me.ryanhamshire.AntiXRay;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

// event handlers related to blocks
class BlockEventHandler implements Listener {

	// convenience reference to singleton datastore
	private final DataStore dataStore;

	// boring typical constructor
	BlockEventHandler(DataStore dataStore) {
		this.dataStore = dataStore;
	}

	// when a player breaks a block... priority high, so other plugins can first cancel block breaking
	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
	void onBlockBreak(BlockBreakEvent breakEvent) {
		Player player = breakEvent.getPlayer();

		// ignore players with the bypass permission
		if (player.hasPermission("antixray.bypass")) return;

		// ignore players in creative mode
		if (AntiXRay.instance.config_exemptCreativeModePlayers && player.getGameMode() == GameMode.CREATIVE) return;

		Block block = breakEvent.getBlock();

		// get block protections for this world
		List<BlockData> protections = AntiXRay.getProtections().getProtections(block.getWorld().getName());

		// if there are no protections for this world, ignore the event
		if (protections == null || protections.isEmpty()) return;

		// allows a player to break a block he just placed (he must have been charged points already to collect it in the first place) without cost
		PlayerData playerData = dataStore.getOrCreatePlayerData(player);
		if (playerData.lastPlacedBlockLocation != null && block.getLocation().equals(playerData.lastPlacedBlockLocation)) {
			playerData.lastPlacedBlockLocation = null;
			return;
		}

		int height = block.getLocation().getBlockY();

		// look for the block's type in the list of protected blocks
		for (BlockData blockData : protections) {
			// if it's in the list, consider whether this player should be permitted to break the block
			if (blockData.isOfSameType(block) && height <= blockData.getHeight()) {
				// if he doesn't have enough points
				if (blockData.getValue() > 0 && playerData.points < blockData.getValue()) {
					String reachedLimitCounterString = String.valueOf(playerData.reachedLimitCount);

					if (!playerData.reachedLimitThisSession) {
						// avoid doing this twice in one play session for this player
						playerData.reachedLimitThisSession = true;

						// increment reached-limit-counter
						playerData.reachedLimitCount += 1;
						// update reachedLimitCounter string
						reachedLimitCounterString = String.valueOf(playerData.reachedLimitCount);

						// if configured to do so, make an entry in the log and notify any online moderators
						if (AntiXRay.instance.config_notifyOnLimitReached) {
							// make log entry
							AntiXRay.logger.info(player.getName() + " reached the mining speed limit at " + AntiXRay.getfriendlyLocationString(player.getLocation())
									+ ". He already reached it for about " + reachedLimitCounterString + " times.");

							// notify online moderators
							for (Player moderator : Bukkit.getOnlinePlayers()) {
								if (moderator.hasPermission("antixray.monitorxrayers")) {
									AntiXRay.sendMessage(moderator, Messages.AdminNotification, player.getName(), reachedLimitCounterString);
								}
							}
						}
					}

					// estimate how long it will be before he can break this block
					int minutesUntilBreak = (int) ((blockData.getValue() - playerData.points) / (float) (AntiXRay.instance.config_pointsPerHour) * 60);
					if (minutesUntilBreak == 0) minutesUntilBreak = 1;

					// inform him
					AntiXRay.sendMessage(player, Messages.CantBreakYet, String.valueOf(minutesUntilBreak), reachedLimitCounterString);

					// cancel the breakage
					breakEvent.setCancelled(true);

				} else {
					// otherwise, subtract the value of the block from his points
					playerData.points -= blockData.getValue();
					// make sure that the players point are lower than the maxPoints limit:
					if (!AntiXRay.instance.config_ignoreMaxPointsForBlockRatio && playerData.points > AntiXRay.instance.config_maxPoints) {
						playerData.points = AntiXRay.instance.config_maxPoints;
					}
				}

				// once a match is found, no need to look farther
				return;
			}
		}
	}

	// when a player places a block...
	@EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
	void onBlockPlace(BlockPlaceEvent placeEvent) {
		Player player = placeEvent.getPlayer();

		// ignore players with the bypass permission
		if (player.hasPermission("antixray.bypass")) return;

		// ignore players in creative mode
		if (AntiXRay.instance.config_exemptCreativeModePlayers && player.getGameMode() == GameMode.CREATIVE) return;

		Block block = placeEvent.getBlockPlaced();

		// if the block's world isn't in the list of controlled worlds, ignore the event
		if (!AntiXRay.getProtections().isWorldProtected(block.getWorld().getName())) return;

		// allows a player to break a block he just placed (he must have been charged points already to collect it in the first place) without cost
		PlayerData playerData = dataStore.getOrCreatePlayerData(player);
		playerData.lastPlacedBlockLocation = block.getLocation();
	}
}