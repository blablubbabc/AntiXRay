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

import java.io.*;
import java.util.*;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

// abstract class for data storage.  implementing classes fill the implementation gaps for flat file storage and database storage, respectively
abstract class DataStore {
	// in-memory cache for player data
	private HashMap<String, PlayerData> playerUUIDToPlayerDataMap = new HashMap<String, PlayerData>();

	// in-memory cache for messages
	private String[] messages;

	// path information, for where stuff stored on disk is well... stored
	final static String dataLayerFolderPath = "plugins" + File.separator + "AntiXRayData";
	final static String configFilePath = dataLayerFolderPath + File.separator + "config.yml";
	final static String messagesFilePath = dataLayerFolderPath + File.separator + "messages.yml";

	// initialization varies depending on flat file or database storage
	void initialize() {
		// load up all the messages from messages.yml
		this.loadMessages();
	}

	// removes cached player data from memory
	void clearCachedPlayerData(UUID uuid) {
		this.playerUUIDToPlayerDataMap.remove(uuid.toString());
	}

	// retrieves player data from memory or file, as necessary
	// if the player has never been on the server before, this will return a fresh player data with default values
	public PlayerData getOrCreatePlayerData(Player player) {
		String uuidS = player.getUniqueId().toString();

		// first, look in memory
		PlayerData playerData = this.playerUUIDToPlayerDataMap.get(uuidS);

		// if not there, look on disk
		if (playerData == null) {
			playerData = this.loadOrCreatePlayerDataFromStorage(player);

			// shove that new player data into the hash map cache
			this.playerUUIDToPlayerDataMap.put(uuidS, playerData);
		}

		// try the hash map again. if it's STILL not there, we have a bug to fix
		return this.playerUUIDToPlayerDataMap.get(uuidS);
	}

	// returns PlayerData for a player with the given uuid and RETURNS NULL if no PlayerData was found for this uuid.
	// The loaded playerData will not be saved in memory and is meant for one-time lookup purposes.
	public PlayerData getPlayerDataIfExist(UUID uuid) {
		// first, look in memory
		PlayerData playerData = this.playerUUIDToPlayerDataMap.get(uuid.toString());

		// if not there, look on disk
		if (playerData == null) {
			playerData = this.loadPlayerDataFromStorageIfExist(uuid);
		}

		return playerData;
	}

	// default points = max when the player has played on the server before
	// otherwise, use starting points from config file
	int getDefaultPoints(Player player) {
		return getDefaultPoints(player.hasPlayedBefore());
	}

	int getDefaultPoints(boolean hasPlayedBefore) {
		if (!hasPlayedBefore) {
			return AntiXRay.instance.config_startingPoints;
		} else {
			return AntiXRay.instance.config_maxPoints;
		}
	}

	// returns a default PlayerData object for the given player
	PlayerData getDefaultPlayerData(Player player) {
		PlayerData playerData = new PlayerData();

		// default points
		playerData.points = getDefaultPoints(player);

		return playerData;
	}

	// whether or not data was stored for a player with this uuid
	// implementation varies based on flat file or database storage
	abstract boolean isPlayerDataExisting(UUID uuid);

	// implementation varies depending on flat file or database storage
	abstract PlayerData loadOrCreatePlayerDataFromStorage(Player player);

	// loading PlayerData by a given uuid and returns null, if there is no data stored for a player with this uuid
	// implementation varies depending on flat file or database storage
	abstract PlayerData loadPlayerDataFromStorageIfExist(UUID uuid);

	// saves changes to player data. MUST be called after you're done making changes, otherwise a reload will lose them
	// implementation varies based on flat file or database storage
	abstract void savePlayerData(UUID uuid, PlayerData playerData);

	// methods to handle old playerdata (from pre MC 1.8):

	// checks if there is old player data existing for this player name
	abstract boolean isOldPlayerDataExisting(String playerName);

	// gets the old playerdata, if it exists/hasn't yet been converted (good to still lookup playerdata of non-converted players and for better compatibility with slightly older versions (1.7.x))
	abstract PlayerData getOldPlayerDataIfExists(String playerName);

	// saves the given playerdata into an 'old' player file
	abstract void saveOldPlayerData(String playerName, PlayerData playerData);

	// loads user-facing messages from the messages.yml configuration file into memory
	private void loadMessages() {
		Messages[] messageIDs = Messages.values();
		this.messages = new String[Messages.values().length];

		HashMap<String, CustomizableMessage> defaults = new HashMap<String, CustomizableMessage>();

		// initialize defaults
		this.addDefault(defaults, Messages.CantBreakYet,
				"&eWow, you're good at mining!  You have to wait about {0} minutes to break this block.  If you wait longer, you can mine even more of this.  Consider taking a break from mining to do something else, like building or exploring.  This mining speed limit keeps our ores safe from cheaters.  :)",
				"0: minutes until the block can be broken  1: how often the player has already reached his limit (unused by default)");
		this.addDefault(defaults, Messages.AdminNotification, "&e{0} reached the mining speed limit. He already reached it about {1} times.",
				"0: player name  1: how often the player has already reached his limit");
		this.addDefault(defaults, Messages.NoPermission, "&cYou have no permission for that.", null);
		this.addDefault(defaults, Messages.OnlyAsPlayer, "&cThis command can only be executed as a player.", null);
		this.addDefault(defaults, Messages.CommandHelpHeader, "&2--- &4AntiXRay &2---", null);
		this.addDefault(defaults, Messages.CommandReloadCmd, "&e/antixray reload", null);
		this.addDefault(defaults, Messages.CommandReloadDesc, "&9     - Reloads the configuration and messages.", null);
		this.addDefault(defaults, Messages.ReloadDone, "&aAntiXRay was reloaded. Check the log if there were any errors.", null);
		this.addDefault(defaults, Messages.CommandCheckCmd, "&e/antixray check [player]", null);
		this.addDefault(defaults, Messages.CommandCheckDesc, "&9     - Shows you your or another players current points.", null);
		this.addDefault(defaults, Messages.CurrentPoints, "&e{0} currently has {1} points.", "0: player name  1: the players points");
		this.addDefault(defaults, Messages.ReachedLimitCount, "&e{0} has reached the limit {1} times.", "0: player name  1: how often the player has already reached the limit");
		this.addDefault(defaults, Messages.NoPlayerDataFound, "&eNo PlayerData was found for '{0}'.", "0: player name");
		this.addDefault(defaults, Messages.CommandSetCmd, "&e/antixray set <player> <points|counter> <value>", null);
		this.addDefault(defaults, Messages.CommandSetDesc, "&9     - Sets the players points or counter value.", null);
		this.addDefault(defaults, Messages.InvalidNumber, "&c'{0}' is not a valid number.", "0: the invalid argument");
		this.addDefault(defaults, Messages.ChangesAreDone, "&aChanges were successfully made.", null);

		// load the config file
		FileConfiguration config = YamlConfiguration.loadConfiguration(new File(messagesFilePath));

		// for each message ID
		for (int i = 0; i < messageIDs.length; i++) {
			// get default for this message
			Messages messageID = messageIDs[i];
			CustomizableMessage messageData = defaults.get(messageID.name());

			// if default is missing, log an error and use some fake data for now so that the plugin can run
			if (messageData == null) {
				AntiXRay.logger.severe("Missing message for " + messageID.name() + ".  Please contact the developer.");
				messageData = new CustomizableMessage(messageID, "Missing message!  ID: " + messageID.name() + ".  Please contact a server admin.", null);
			}

			// read the message from the file, use default if necessary
			String message = config.getString("Messages." + messageID.name() + ".Text", messageData.text);
			config.set("Messages." + messageID.name() + ".Text", message);

			// colorize and store message
			this.messages[messageID.ordinal()] = ChatColor.translateAlternateColorCodes('&', message);

			if (messageData.notes != null) {
				messageData.notes = config.getString("Messages." + messageID.name() + ".Notes", messageData.notes);
				config.set("Messages." + messageID.name() + ".Notes", messageData.notes);
			}
		}

		// save any changes
		try {
			config.save(DataStore.messagesFilePath);
		} catch (IOException exception) {
			AntiXRay.logger.severe("Unable to write to the configuration file at \"" + DataStore.messagesFilePath + "\"");
		}

		defaults.clear();
		System.gc();
	}

	// helper for above, adds a default message and notes to go with a message ID
	private void addDefault(HashMap<String, CustomizableMessage> defaults, Messages id, String text, String notes) {
		CustomizableMessage message = new CustomizableMessage(id, text, notes);
		defaults.put(id.name(), message);
	}

	// gets a message from memory
	public String getMessage(Messages messageID, String... args) {
		String message = messages[messageID.ordinal()];

		for (int i = 0; i < args.length; i++) {
			String param = args[i];
			message = message.replace("{" + i + "}", param);
		}

		return message;

	}

	// closes any open connections. implementation varies depending on flat file or database storage.
	abstract void close();
}