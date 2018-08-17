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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package me.ryanhamshire.AntiXRay;

import org.bukkit.Material;
import org.bukkit.block.Block;

// Holds data about protected blocks
class ProtectedBlock {

	private final Material type;
	private final int value;
	private final int maxHeight;

	public ProtectedBlock(Material type, int value, int maxHeight) {
		this.type = type;
		this.value = value;
		this.maxHeight = maxHeight;
	}

	public Material getType() {
		return type;
	}

	public int getValue() {
		return value;
	}

	public int getMaxHeight() {
		return maxHeight;
	}

	// check if this ProtectedBlock's type equals a given type of block 
	public boolean isOfSameType(Block block) {
		return (block != null && type == block.getType());
	}

	// check if this ProtectedBlock's type equals another ProtectedBlock's type
	public boolean isOfSameType(ProtectedBlock protectedBlock) {
		return (protectedBlock != null && type == protectedBlock.getType());
	}
}
