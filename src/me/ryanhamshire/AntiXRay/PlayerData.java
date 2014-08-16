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

import org.bukkit.Location;

// holds all of AntiXray's player-tied data
public class PlayerData {

	// a player's "ore score", which determines whether or not he can break a specific block type
	public int points = AntiXRay.instance.config_startingPoints;
	public int reachedLimitCount = 0;

	// where this player was the last time we checked on him for earning points
	public Location lastAfkCheckLocation = null;

	// whether or not this player has reached his mining limit this play session
	public boolean reachedLimitThisSession = false;

	// the location of the last block placed, to allow for breaking a block just now placed without spending ore score
	public Location lastPlacedBlockLocation = null;
}