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

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;

class PlayerEventHandler implements Listener {
	private DataStore dataStore;

	PlayerEventHandler(DataStore dataStore) {
		this.dataStore = dataStore;
	}

	// when a player successfully joins the server...
	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
	void onPlayerJoin(PlayerJoinEvent event) {
		// get his player data, forcing it to initialize if we've never seen him before
		@SuppressWarnings("unused")
		PlayerData playerData = this.dataStore.getPlayerData(event.getPlayer());
	}

	// when a player quits...
	@EventHandler(priority = EventPriority.HIGHEST)
	void onPlayerQuit(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		String playerName = player.getName();

		// save player data, just in case he accrued some block points which haven't been saved yet
		this.dataStore.savePlayerData(playerName, this.dataStore.getPlayerData(player));

		// drop player data from memory
		this.dataStore.clearCachedPlayerData(playerName);
	}
}