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

import org.bukkit.block.Block;

// Stores data about protected blocks. Supports custom blocks.
class BlockData {
	private final int id;
	private final byte subid;
	private final int value;
	private final int height;

	public BlockData(int id, byte subid, int value, int height) {
		this.id = id;
		this.subid = subid;
		this.value = value;
		this.height = height;
	}

	public byte getSubid() {
		return subid;
	}

	public int getId() {
		return id;
	}

	public int getHeight() {
		return height;
	}

	public int getValue() {
		return value;
	}

	// check if this BlockDatas type equals a given type of block (a subid of -1 ignores the subid)
	public boolean isOfSameType(Block block) {
		return (block != null && id == block.getTypeId() && (subid == -1 || subid == block.getData()));
	}

	// check if this BlockDatas type equals another BlockDatas type (a subid of -1 ignores the subid)
	public boolean isOfSameType(BlockData blockData) {
		return (blockData != null && id == blockData.getId() && (subid == -1 || subid == blockData.getSubid()));
	}
}