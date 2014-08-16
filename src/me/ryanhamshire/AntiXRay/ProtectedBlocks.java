/*
    AntiXRay Server Plugin for Minecraft
    Copyright (C) 2013 Ryan Hamshire

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
	private static Map<String, List<BlockData>> worlds = new HashMap<String, List<BlockData>>();

	public static void addWorld(String worldName) {
		worlds.put(worldName, new ArrayList<BlockData>());
	}

	public static void clear() {
		worlds.clear();
	}

	public static void addProtection(String worldName, BlockData blockData) {
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

	public static boolean isWorldProtected(String worldName) {
		return worlds.containsKey(worldName);
	}

	public static List<BlockData> getProtections(String worldName) {
		return worlds.get(worldName);
	}

	/*
	 * public static BlockData getBlockData(String worldName, Block block) {
	 * // search through all protections for this world:
	 * List<BlockData> worldBlockData = worlds.get(worldName);
	 * if (worldBlockData != null) {
	 * for (BlockData blockData : worldBlockData) {
	 * if (blockData.isOfSameType(block)) return blockData;
	 * }
	 * }
	 * // return null if no protection was found:
	 * return null;
	 * }
	 */
}