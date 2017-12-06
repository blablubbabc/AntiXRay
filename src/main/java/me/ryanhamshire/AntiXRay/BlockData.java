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

import org.bukkit.Material;
import org.bukkit.block.Block;

// Stores data about protected blocks. Supports custom blocks.
class BlockData {

	private final Material type;
	private final byte data; // -1 to ignore
	private final int value;
	private final int maxHeight;

	public BlockData(Material type, byte data, int value, int maxHeight) {
		this.type = type;
		this.data = data;
		this.value = value;
		this.maxHeight = maxHeight;
	}

	public Material getType() {
		return type;
	}

	public byte getData() {
		return data;
	}

	public int getValue() {
		return value;
	}

	public int getMaxHeight() {
		return maxHeight;
	}

	// check if this BlockData's type equals a given type of block (a data value of -1 ignores the data value)
	public boolean isOfSameType(Block block) {
		return (block != null && type == block.getType() && (data == -1 || data == block.getData()));
	}

	// check if this BlockData's type equals another BlockData's type (a data value of -1 ignores the data value)
	public boolean isOfSameType(BlockData blockData) {
		return (blockData != null && type == blockData.getType() && (data == -1 || data == blockData.getData()));
	}
}
