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

import java.util.List;

import org.bukkit.Location;
import org.bukkit.block.Block;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;

// handles events related to entities
class EntityEventHandler implements Listener {

	// when there's an explosion...
	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
	public void onEntityExplode(EntityExplodeEvent explodeEvent) {
		List<Block> blocks = explodeEvent.blockList();
		Location location = explodeEvent.getLocation();

		// get block protections for this world
		List<BlockData> protections = AntiXRay.getProtections().getProtections(location.getWorld().getName());

		// don't do anything when the explosion world isn't one of the controlled worlds
		if (protections == null || protections.isEmpty()) return;

		// FEATURE: don't allow players to circumvent the anti xray limitation by using explosives to anonymously break a block

		// for each block that will be broken by the explosion
		for (int i = 0; i < blocks.size(); i++) {
			Block block = blocks.get(i);
			int height = block.getLocation().getBlockY();
			// look for that block's type in the list of protected blocks
			for (BlockData blockData : protections) {
				// if it's type is in our protected blocks list, remove the block from the explosion list (so it doesn't break)
				if (blockData.isOfSameType(block) && height <= blockData.getHeight() && blockData.getValue() > 0) {
					blocks.remove(i--);
				}
			}
		}
	}
}