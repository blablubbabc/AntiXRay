/*
    AntiXRay Server Plugin for Minecraft
    Copyright (C) 2012 Ryan Hamshire

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.ryanhamshire.AntiXRay;

import java.util.AbstractMap.SimpleEntry;

import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

//event handlers related to blocks
public class BlockEventHandler implements Listener 
{
	//convenience reference to singleton datastore
	private DataStore dataStore;
	
	//boring typical constructor
	public BlockEventHandler(DataStore dataStore)
	{
		this.dataStore = dataStore;
	}
	
	//when a player breaks a block...
	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
	public void onBlockBreak(BlockBreakEvent breakEvent)
	{
		Player player = breakEvent.getPlayer();
		
		//ignore players with the bypass permission
		if(player.hasPermission("antixray.bypass")) return;
		
		//ignore players in creative mode
		if(AntiXRay.instance.config_exemptCreativeModePlayers && player.getGameMode() == GameMode.CREATIVE) return;
		
		Block block = breakEvent.getBlock();
		
		//if the block's world isn't in the list of controlled worlds, ignore the event
		if(!AntiXRay.instance.config_enabledWorlds.contains(block.getWorld())) return;
		
		//allows a player to break a block he just placed (he must have been charged points already to collect it in the first place) without cost
		PlayerData playerData = this.dataStore.getPlayerData(player);
		if(playerData.lastPlacedBlockLocation != null && block.getLocation().equals(playerData.lastPlacedBlockLocation))
		{
			playerData.lastPlacedBlockLocation = null;
			return;
		}
		
		//look for the block's type in the list of protected blocks
		for(int i = 0; i < AntiXRay.instance.config_protectedBlocks.size(); i++)
		{
			//if it's in the list, consider whether this player should be permitted to break the block
			SimpleEntry<BlockData, Integer> entry = AntiXRay.instance.config_protectedBlocks.get(i);
			if(entry.getKey().isEqual(block))
			{
				//if he doesn't have enough points
				if(entry.getValue() > 0 && playerData.points < entry.getValue())
				{
					//estimate how long it will be before he can break this block
					int minutesUntilBreak = (int)((entry.getValue() - playerData.points) / (float)(AntiXRay.instance.config_pointsPerHour) * 60);
					if(minutesUntilBreak == 0) minutesUntilBreak = 1;
					
					//inform him
					AntiXRay.sendMessage(player, TextMode.Instr, Messages.CantBreakYet, String.valueOf(minutesUntilBreak));
					
					//cancel the breakage
					breakEvent.setCancelled(true);
					
					//if configured to do so, make an entry in the log and notify any online moderators
					if(AntiXRay.instance.config_notifyOnLimitReached && !playerData.reachedLimitThisSession)
					{
						//avoid doing this twice in one play session for this player
						playerData.reachedLimitThisSession = true;
						
						//make log entry
						AntiXRay.logger.info(player.getName() + " reached the mining speed limit at " + AntiXRay.getfriendlyLocationString(player.getLocation()));
						
						//notify online moderators
						Player [] players = AntiXRay.instance.getServer().getOnlinePlayers();
						for(int j = 0; j < players.length; j++)
						{
							Player moderator = players[j];
							if(moderator.hasPermission("antixray.monitorxrayers"))
							{
								AntiXRay.sendMessage(moderator, TextMode.Instr, Messages.AdminNotification, player.getName());
							}
						}
					}
				}
				
				//otherwise, subtract the value of the block from his points
				else
				{
					playerData.points -= entry.getValue();					
				}
				
				//once a match is found, no need to look farther
				return;
			}
		}
	}
	
	//when a player places a block...
	@EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
	public void onBlockPlace(BlockPlaceEvent placeEvent)
	{
		Player player = placeEvent.getPlayer();
		
		//ignore players with the bypass permission
		if(player.hasPermission("antixray.bypass")) return;
		
		//ignore players in creative mode
		if(AntiXRay.instance.config_exemptCreativeModePlayers && player.getGameMode() == GameMode.CREATIVE) return;
		
		Block block = placeEvent.getBlockPlaced();
		
		//if the block's world isn't in the list of controlled worlds, ignore the event
		if(!AntiXRay.instance.config_enabledWorlds.contains(block.getWorld())) return;
		
		//allows a player to break a block he just placed (he must have been charged points already to collect it in the first place) without cost
		PlayerData playerData = this.dataStore.getPlayerData(player);
		playerData.lastPlacedBlockLocation = block.getLocation();
	}
}
