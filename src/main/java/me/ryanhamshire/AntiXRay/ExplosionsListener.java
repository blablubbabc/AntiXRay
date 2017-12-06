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

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/**
 * Protects protected blocks from being destroyed/broken by explosions.
 * Prevents players from circumventing the anti-xray limitations by using explosives to anonymously break blocks.
 */
class ExplosionsListener implements Listener {

	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
	void onEntityExplode(EntityExplodeEvent event) {
		Location location = event.getLocation();
		List<Block> blocks = event.blockList();
		this.handleExplosion(location, blocks);
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
	void onBlockExplode(BlockExplodeEvent event) {
		Location location = event.getBlock().getLocation();
		List<Block> blocks = event.blockList();
		this.handleExplosion(location, blocks);
	}

	void handleExplosion(Location location, List<Block> blocks) {
		assert location != null && blocks != null;

		// get block protections for this world
		List<BlockData> protections = AntiXRay.getProtections().getProtections(location.getWorld().getName());

		// don't do anything when the explosion world isn't one of the controlled worlds
		if (protections == null || protections.isEmpty()) return;

		// for each block that will be broken by the explosion
		for (int i = 0; i < blocks.size(); i++) {
			Block block = blocks.get(i);
			int height = block.getLocation().getBlockY();
			// look for that block's type in the list of protected blocks
			for (BlockData blockData : protections) {
				// if it's type is in our protected blocks list, remove the block from the explosion list (so it doesn't break)
				if (blockData.isOfSameType(block) && height <= blockData.getMaxHeight() && blockData.getValue() > 0) {
					blocks.remove(i--);
				}
			}
		}
	}
}
