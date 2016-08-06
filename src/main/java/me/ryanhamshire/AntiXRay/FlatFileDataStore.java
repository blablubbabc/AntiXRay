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

import java.io.*;
import java.util.UUID;

import org.bukkit.entity.Player;

// singleton class which manages all AntiXRay data (except for config options)
class FlatFileDataStore extends DataStore {

	final static String playerDataFolderPath = dataLayerFolderPath + File.separator + "PlayerData";
	final static String convertedPlayerDataFolderPath = dataLayerFolderPath + File.separator + "PlayerDataConverted";

	FlatFileDataStore() {
		this.initialize();
	}

	@Override
	void initialize() {
		super.initialize();

		// ensure data folders exist
		new File(playerDataFolderPath).mkdirs();
		new File(convertedPlayerDataFolderPath).mkdirs(); // to backup old player files (from pre MC 1.8)
	}

	private File getPlayerFile(UUID uuid) {
		return new File(playerDataFolderPath + File.separator + uuid.toString());
	}

	@Override
	boolean isPlayerDataExisting(UUID uuid) {
		File playerFile = this.getPlayerFile(uuid);
		// whether or not the file exists
		return playerFile.exists();
	}

	@Override
	PlayerData loadOrCreatePlayerDataFromStorage(Player player) {
		UUID uuid = player.getUniqueId();
		PlayerData playerData = this.loadPlayerDataFromStorageIfExist(uuid);

		// if it doesn't exist, set default points
		if (playerData == null) {
			playerData = this.getDefaultPlayerData(player);

			// check if we have some old player data for this player (from pre MC 1.8):
			String playerName = player.getName();
			this.loadOldPlayerData(playerName, playerData);
		}

		return playerData;
	}

	// returns null, if there is no PlayerData saved for this uuid
	@Override
	PlayerData loadPlayerDataFromStorageIfExist(UUID uuid) {
		File playerFile = this.getPlayerFile(uuid);
		return this.loadPlayerDataFromFile(playerFile);
	}

	@Override
	void savePlayerData(UUID uuid, PlayerData playerData) {
		this.savePlayerData(playerData, this.getPlayerFile(uuid));
	}

	// methods to handle old playerdata (from pre MC 1.8):

	private File getOldPlayerFile(String playerName) {
		return new File(playerDataFolderPath + File.separator + playerName);
	}

	@Override
	boolean isOldPlayerDataExisting(String playerName) {
		File oldPlayerFile = this.getOldPlayerFile(playerName);
		return oldPlayerFile.isFile(); // this also checks if the file exists
	}

	@Override
	PlayerData getOldPlayerDataIfExists(String playerName) {
		return this.loadOldPlayerData(playerName, null);
	}

	@Override
	void saveOldPlayerData(String playerName, PlayerData playerData) {
		this.savePlayerData(playerData, this.getOldPlayerFile(playerName));
	}

	// 'importInto' is an optional argument: if it is null the old playerdata will only be loaded
	// Otherwise it's data gets moved into the provided importInfo-PlayerData and the old player file gets moved into a separate backup folder
	private PlayerData loadOldPlayerData(String playerName, PlayerData importInto) {
		// load old playerdata:
		File oldPlayerFile = this.getOldPlayerFile(playerName);
		PlayerData oldPlayerData = this.loadPlayerDataFromFile(oldPlayerFile);

		// if we have a new PlayerData object given: do conversion related stuff and remove old player data:
		if (importInto != null && oldPlayerData != null) {
			// use that data for our new player data:
			importInto.points = oldPlayerData.points;
			importInto.reachedLimitCount = oldPlayerData.reachedLimitCount;

			// move the old player file into separate folder so we know it has been converted:
			File convertedFile = new File(convertedPlayerDataFolderPath + File.separator + playerName);
			if (!oldPlayerFile.renameTo(convertedFile)) {
				// moving failed for some reason.. let's print a warning and then remove the file:
				AntiXRay.logger.warning("Failed to move old player data file (" + playerName + "|" + oldPlayerData.points + "|" + oldPlayerData.reachedLimitCount + ") to \"" + convertedFile.getPath() + "\"!");
				if (!convertedFile.delete()) {
					// well.. shit.
					AntiXRay.logger.warning("Removing that old player data file failed as well..");
				}
			}
		}
		// else: only return the old player data but still keep it untouched
		return oldPlayerData;
	}

	// shared methods to load and save playerdata from and to file:

	private PlayerData loadPlayerDataFromFile(File playerFile) {
		// if it doesn't exist as a file
		if (!playerFile.exists()) {
			return null;
		}

		// otherwise, read the file
		PlayerData playerData = new PlayerData();
		BufferedReader inStream = null;
		try {
			inStream = new BufferedReader(new FileReader(playerFile.getAbsolutePath()));

			// first line is points
			String pointsString = inStream.readLine();
			// second line is, how often the player has already reached his limit: can be null, if the files are not containing this information yet
			String reachedLimitCountString = inStream.readLine();

			// convert that to numbers and store it
			playerData.points = Integer.parseInt(pointsString);
			// if the file is in the old format and doesn't contain the information, the playerData will automatically initialized with 0
			if (reachedLimitCountString != null) playerData.reachedLimitCount = Integer.parseInt(reachedLimitCountString);

			inStream.close();
		} catch (Exception e) {
			// if there's any problem with the file's content, log an error message
			AntiXRay.logger.severe("Unable to load player data from \"" + playerFile.getPath() + "\": " + e.getMessage());
		}

		try {
			if (inStream != null) inStream.close();
		} catch (IOException exception) {
		}

		return playerData;
	}

	private void savePlayerData(PlayerData playerData, File playerFile) {
		BufferedWriter outStream = null;
		try {
			// open the player's file
			playerFile.createNewFile();
			outStream = new BufferedWriter(new FileWriter(playerFile));

			// first line is available points
			outStream.write(String.valueOf(playerData.points));
			outStream.newLine();

			// second line is, how often the player has already reached his limit
			outStream.write(String.valueOf(playerData.reachedLimitCount));
			outStream.newLine();
		} catch (Exception e) {
			// if any problem, log it
			AntiXRay.logger.severe("Unexpected exception saving player data to \"" + playerFile.getPath() + "\": " + e.getMessage());
		}

		try {
			// close the file
			if (outStream != null) {
				outStream.close();
			}
		} catch (IOException exception) {
		}
	}

	@Override
	void close() {
		// nothing to do here because files are not left open after reading or writing
	}
}
