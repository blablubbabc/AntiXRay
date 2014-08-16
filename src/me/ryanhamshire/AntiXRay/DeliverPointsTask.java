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

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

// FEATURE: give players points for playing, as long as they're not away from their computer

// runs every 5 minutes in the main thread, grants points per hour / 12 to each online player who appears to be actively playing
class DeliverPointsTask implements Runnable {

	@Override
	public void run() {
		// ensure players get at least 1 point
		int pointsEarned = AntiXRay.instance.config_pointsPerHour / 12;
		if (pointsEarned == 0) pointsEarned = 1;

		// for each online player
		for (Player player : Bukkit.getOnlinePlayers()) {
			DataStore dataStore = AntiXRay.instance.dataStore;
			PlayerData playerData = dataStore.getOrCreatePlayerData(player);

			Location lastLocation = playerData.lastAfkCheckLocation;
			// distance squared will throw an exception if the player has changed worlds
			try {
				// if he's not in a vehicle and has moved at least three blocks since the last check
				if (!player.isInsideVehicle() && (lastLocation == null || lastLocation.distanceSquared(player.getLocation()) >= 9)) {
					playerData.points += pointsEarned;

					// respect limits
					if (playerData.points > AntiXRay.instance.config_maxPoints) {
						playerData.points = AntiXRay.instance.config_maxPoints;
					}

					// intentionally NOT saving changes. accrued score will be saved on logout, when successfully breaking a block, or during server shutdown
					// dataStore.savePlayerData(player.getName(), playerData);
				}
			} catch (Exception e) {
			}

			// remember current location for next time
			playerData.lastAfkCheckLocation = player.getLocation();
		}
	}
}