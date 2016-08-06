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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Stores information about all block protections per world.
class ProtectedBlocks {

	// stores protections by world <worldName -> protection>
	private final Map<String, List<BlockData>> worlds = new HashMap<String, List<BlockData>>();

	ProtectedBlocks() {
	}

	void addWorld(String worldName) {
		worlds.put(worldName, new ArrayList<BlockData>());
	}

	void clear() {
		worlds.clear();
	}

	void addProtection(String worldName, BlockData blockData) {
		List<BlockData> worldBlockData = worlds.get(worldName);
		if (worldBlockData == null) {
			worldBlockData = new ArrayList<BlockData>();
			worlds.put(worldName, worldBlockData);
		}
		// check if protection for the same type of block already exists and remove it:
		int foundIndex = -1;
		for (int i = 0; i < worldBlockData.size(); i++) {
			if (worldBlockData.get(i).isOfSameType(blockData)) {
				foundIndex = i;
				// we can assume that there is max. one BlockData of the same type..
				break;
			}
		}
		if (foundIndex != -1) worldBlockData.remove(foundIndex);
		worldBlockData.add(blockData);
	}

	boolean isWorldProtected(String worldName) {
		return worlds.containsKey(worldName);
	}

	List<BlockData> getProtections(String worldName) {
		return worlds.get(worldName);
	}
}