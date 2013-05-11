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

// Stores data about blocked blocks. Supports custom blocks.
public class BlockData {
	private final String name;
	private final int id;
	private final byte subid;
	
	public BlockData(String name, int id, byte subid) {
		this.name = name;
		this.id = id;
		this.subid = subid;
	}
	
	public String getName() {
		return name;
	}
	
	public byte getSubid() {
		return subid;
	}

	public int getId() {
		return id;
	}
	
	// check if a given block equals this type of blocked block (a subid of -1 ignores the subid)
	public boolean isEqual(Block block) {
		return (block != null && id == block.getTypeId() && (subid == -1 || subid == block.getData()));
	}
}
