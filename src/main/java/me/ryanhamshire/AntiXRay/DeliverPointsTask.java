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

import java.text.DecimalFormat;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

// FEATURE: give players points for playing, as long as they're not away from their computer

// runs every minute in the main thread, grants points per hour / 60 to each online player who appears to be actively playing
class DeliverPointsTask implements Runnable {

	private static final DecimalFormat decimalFormat = new DecimalFormat("0.0#");

	DeliverPointsTask() {
	}

	@Override
	public void run() {
		double pointsEarnedPrecise = AntiXRay.instance.config_pointsPerHour / 60.0D;

		// for each online player
		for (Player player : Bukkit.getOnlinePlayers()) {
			DataStore dataStore = AntiXRay.instance.dataStore;
			PlayerData playerData = dataStore.getOrCreatePlayerData(player);

			Location lastLocation = playerData.lastAfkCheckLocation;

			// remember current location for next time
			Location currentLocation = player.getLocation();
			playerData.lastAfkCheckLocation = currentLocation;

			// check if the player might be afk
			boolean afk = false;
			if (lastLocation != null) {
				if (lastLocation.getWorld().equals(currentLocation.getWorld())) {
					// player did move less then three blocks since the last check,
					// or might try to circumvent afk detection by letting a vehicle move himself around:
					if (player.isInsideVehicle() || lastLocation.distanceSquared(currentLocation) <= 9.0D) {
						// considering player to be afk since the last check:
						afk = true;
					}
				}
			}

			if (afk) {
				playerData.afkMinutes++;
				AntiXRay.debug("Player '" + player.getName() + "' seems to be AFK since " + playerData.afkMinutes + " minutes.");
				// don't punish players for being afk for only a short time,
				// or for being unlucky to be inside a vehicle when we check:
				if (playerData.afkMinutes >= 5) {
					// this player is already afk for quite some time now,
					// we can be sure now that the player is really afk
					// skip this player from getting points from now on, while being afk
					continue;
				}
			} else {
				// player is not afk, reset afkMinutes:
				playerData.afkMinutes = 0;
			}

			// deliver points:

			double newPointsPrecise = playerData.points + playerData.remainingPoints + pointsEarnedPrecise;
			playerData.points = (int) newPointsPrecise;
			playerData.remainingPoints = newPointsPrecise - (double) playerData.points;

			// respect limits
			if (playerData.points > AntiXRay.instance.config_maxPoints) {
				playerData.points = AntiXRay.instance.config_maxPoints;
				playerData.remainingPoints = 0.0D;
			}

			AntiXRay.debug("Player '" + player.getName() + "' received " + decimalFormat.format(pointsEarnedPrecise) + " points and has now "
					+ playerData.points + " (+" + decimalFormat.format(playerData.remainingPoints) + ") points (max: " + AntiXRay.instance.config_maxPoints + ").");

			// intentionally NOT saving changes. accrued score will be saved on logout, when successfully breaking a
			// block, or during server shutdown
			// dataStore.savePlayerData(player.getName(), playerData);
		}
	}
}