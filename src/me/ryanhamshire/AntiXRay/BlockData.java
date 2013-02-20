package me.ryanhamshire.AntiXRay;

import org.bukkit.block.Block;

//Stores data about blocked blocks. Supports custom blocks.
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
	
	//check if a given block equals this type of blocked block (a subid of -1 ignores the subid)
	public boolean isEqual(Block block) {
		return (block != null && id == block.getTypeId() && (subid == -1 || subid == block.getData()));
	}
}
