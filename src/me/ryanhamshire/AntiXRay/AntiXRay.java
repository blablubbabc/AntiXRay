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

import java.io.File;
import java.io.IOException;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class AntiXRay extends JavaPlugin {
	// for convenience, a reference to the instance of this plugin
	public static AntiXRay instance;

	// for logging to the console and log file
	public static Logger logger;

	// this handles data storage, like player data
	public DataStore dataStore;

	// configuration variables, loaded/saved from a config.yml
	public ArrayList<World> config_enabledWorlds; // list of worlds where players are limited in how much quickly they can mine valuable ores
	public int config_pointsPerHour; // how quickly players earn "points" which allow them to mine valuables
	public int config_maxPoints; // the upper limit on points
	public int config_startingPoints; // initial points for players who are new to the server
	public ArrayList<SimpleEntry<BlockData, Integer>> config_protectedBlocks; // points required to break various block types
	public boolean config_exemptCreativeModePlayers; // whether creative mode players should be exempt from the rules
	public boolean config_notifyOnLimitReached; // whether to notify online moderators when a player reaches his limit

	// initializes well... everything
	public void onEnable() {
		instance = this;
		logger = getLogger();

		// load configuration
		loadConfig();

		this.dataStore = new FlatFileDataStore();

		// start the task to regularly give players the points they've earned for play time 20L ~ 1 second
		DeliverPointsTask task = new DeliverPointsTask();
		this.getServer().getScheduler().scheduleSyncRepeatingTask(this, task, 20L * 60 * 5, 20L * 60 * 5);

		// register for events
		PluginManager pluginManager = this.getServer().getPluginManager();

		// player events
		PlayerEventHandler playerEventHandler = new PlayerEventHandler(this.dataStore);
		pluginManager.registerEvents(playerEventHandler, this);

		// block events
		BlockEventHandler blockEventHandler = new BlockEventHandler(this.dataStore);
		pluginManager.registerEvents(blockEventHandler, this);

		// entity events
		EntityEventHandler entityEventHandler = new EntityEventHandler();
		pluginManager.registerEvents(entityEventHandler, this);
		
		// command handler
		getCommand("antixray").setExecutor(new CommandHandler());
		
		logger.info("AntiXRay enabled.");
	}

	// on disable, close any open files and/or database connections
	public void onDisable() {
		// ensure all online players get their data saved
		Player[] players = this.getServer().getOnlinePlayers();
		for (int i = 0; i < players.length; i++) {
			Player player = players[i];
			String playerName = player.getName();
			this.dataStore.savePlayerData(playerName, this.dataStore.getPlayerData(player));
		}

		this.dataStore.close();
	}
	
	void loadConfig() {
		// load the config if it exists
		FileConfiguration config = YamlConfiguration.loadConfiguration(new File(DataStore.configFilePath));

		// read configuration settings (note defaults)

		// default for worlds list (all worlds)
		ArrayList<String> defaultWorldNames = new ArrayList<String>();
		List<World> worlds = this.getServer().getWorlds();
		for (int i = 0; i < worlds.size(); i++) {
			defaultWorldNames.add(worlds.get(i).getName());
		}

		// get claims world names from the config file
		List<String> enabledWorldNames = config.getStringList("AntiXRay.Worlds");
		if (enabledWorldNames == null || enabledWorldNames.size() == 0) {
			enabledWorldNames = defaultWorldNames;
		}

		// validate that list
		this.config_enabledWorlds = new ArrayList<World>();
		for (int i = 0; i < enabledWorldNames.size(); i++) {
			String worldName = enabledWorldNames.get(i);
			World world = this.getServer().getWorld(worldName);
			if (world == null) {
				logger.warning("Configuration: There's no world named \"" + worldName + "\".  Please update your config.yml.");
			} else {
				this.config_enabledWorlds.add(world);
			}
		}

		this.config_startingPoints = config.getInt("AntiXRay.NewPlayerStartingPoints", -400);
		this.config_pointsPerHour = config.getInt("AntiXRay.PointsEarnedPerHourPlayed", 800);
		this.config_maxPoints = config.getInt("AntiXRay.MaximumPoints", 1600);

		this.config_exemptCreativeModePlayers = config.getBoolean("AntiXRay.ExemptCreativeModePlayers", true);

		this.config_notifyOnLimitReached = config.getBoolean("AntiXRay.NotifyOnMiningLimitReached", false);

		// try to load the list of custom blocks definitions:
		Map<String, BlockData> customBlocks = new HashMap<String, BlockData>();
		ConfigurationSection customBlocksSection = config.getConfigurationSection("AntiXRay.CustomBlockDefinitions");
		if (customBlocksSection != null) {
			Set<String> names = customBlocksSection.getKeys(false);
			Iterator<String> iterator = names.iterator();
			while (iterator.hasNext()) {
				String customBlockName = iterator.next();
				ConfigurationSection customBlockSection = customBlocksSection.getConfigurationSection(customBlockName);
				if (customBlockSection == null) {
					logger.warning("CustomBlock information not found: " + customBlockName + ".");
					continue;
				}
				if (!customBlockSection.contains("ID")) {
					logger.warning("CustomBlock 'ID' not found: " + customBlockName + ".");
					continue;
				}
				if (!customBlockSection.isInt("ID")) {
					logger.warning("CustomBlock 'ID' is no number: " + customBlockName + ".");
					continue;
				}
				if (!customBlockSection.contains("Sub ID")) {
					logger.warning("CustomBlock 'Sub ID' not found: " + customBlockName + ".");
					continue;
				}
				if (!customBlockSection.isInt("Sub ID")) {
					logger.warning("CustomBlock 'Sub ID' is no number: " + customBlockName + ".");
					continue;
				}
				int id = customBlockSection.getInt("ID");
				int subidInt = customBlockSection.getInt("Sub ID", 0);
				// range check
				if (subidInt < -1 || subidInt > Byte.MAX_VALUE) {
					logger.warning("CustomBlock 'Sub ID' is no valid sub id: " + customBlockName + ".");
					continue;
				}
				byte subid = (byte) subidInt;

				customBlocks.put(customBlockName, new BlockData(customBlockName, id, subid));
			}
		}
		// default for custom blocks definitions
		if (customBlocks.size() == 0) {
			customBlocks.put("SomeCustomOre", new BlockData("SomeCustomOre", 123, (byte) 0));
		}

		// try to load the list of valuable ores from the config file
		ArrayList<String> protectedBlockNames = new ArrayList<String>();
		ConfigurationSection protectedBlocksSection = config.getConfigurationSection("AntiXRay.ProtectedBlockValues");
		if (protectedBlocksSection != null) {
			Set<String> names = protectedBlocksSection.getKeys(false);
			Iterator<String> iterator = names.iterator();
			while (iterator.hasNext()) {
				protectedBlockNames.add(iterator.next());
			}
		}

		// validate the list
		this.config_protectedBlocks = new ArrayList<SimpleEntry<BlockData, Integer>>();
		for (int i = 0; i < protectedBlockNames.size(); i++) {
			String blockName = protectedBlockNames.get(i);
			BlockData blockData;
			// validate the material name
			// check for custom block:
			if (customBlocks.containsKey(blockName)) {
				blockData = customBlocks.get(blockName);
			} else {
				// check material name:
				Material material = Material.getMaterial(blockName);
				if (material == null) {
					logger.warning("Material not found: " + blockName + ".");
					continue;
				} else {
					blockData = new BlockData(blockName, material.getId(), (byte) 0);
				}
			}
			// read the material value
			int materialValue = config.getInt("AntiXRay.ProtectedBlockValues." + blockName, 0);
			// add to protected blocks list
			this.config_protectedBlocks.add(new SimpleEntry<BlockData, Integer>(blockData, materialValue));
		}

		// default for the protected blocks list
		if (this.config_protectedBlocks.size() == 0) {
			this.config_protectedBlocks.add(new SimpleEntry<BlockData, Integer>(new BlockData(Material.DIAMOND_ORE.toString(), Material.DIAMOND_ORE
					.getId(), (byte) 0), 100));
			this.config_protectedBlocks.add(new SimpleEntry<BlockData, Integer>(new BlockData(Material.EMERALD_ORE.toString(), Material.EMERALD_ORE
					.getId(), (byte) 0), 50));
		}

		// write all those configuration values back to file
		// (this writes the defaults to the config file when nothing is
		// specified)
		config.set("AntiXRay.Worlds", enabledWorldNames);
		config.set("AntiXRay.NewPlayerStartingPoints", this.config_startingPoints);
		config.set("AntiXRay.PointsEarnedPerHourPlayed", this.config_pointsPerHour);
		config.set("AntiXRay.MaximumPoints", this.config_maxPoints);

		config.set("AntiXRay.ExemptCreativeModePlayers", this.config_exemptCreativeModePlayers);

		config.set("AntiXRay.NotifyOnMiningLimitReached", this.config_notifyOnLimitReached);

		for (Entry<String, BlockData> entry : customBlocks.entrySet()) {
			config.set("AntiXRay.CustomBlockDefinitions." + entry.getKey() + ".ID", entry.getValue().getId());
			config.set("AntiXRay.CustomBlockDefinitions." + entry.getKey() + ".Sub ID", entry.getValue().getSubid());
		}

		for (int i = 0; i < this.config_protectedBlocks.size(); i++) {
			SimpleEntry<BlockData, Integer> entry = this.config_protectedBlocks.get(i);
			config.set("AntiXRay.ProtectedBlockValues." + entry.getKey().getName(), entry.getValue().intValue());
		}

		try {
			config.save(DataStore.configFilePath);
		} catch (IOException exception) {
			logger.severe("Unable to write to the configuration file at \"" + DataStore.configFilePath + "\"");
		}
		
	}

	// sends a color-coded message to a player
	static void sendMessage(CommandSender receiver, ChatColor color, Messages messageID, String... args) {
		String message = getMessage(messageID, args);
		sendMessage(receiver, color, message);
	}
	
	// gets a message from the datastore by the given id
	static String getMessage(Messages messageID, String... args) {
		return AntiXRay.instance.dataStore.getMessage(messageID, args);
	}

	// sends a color-coded message to a player
	static void sendMessage(CommandSender receiver, ChatColor color, String message) {
		if (receiver == null) {
			logger.info(color + message);
		} else {
			receiver.sendMessage(color + message);
		}
	}

	// creates an easy-to-read location description
	public static String getfriendlyLocationString(Location location) {
		return location.getWorld().getName() + "(" + location.getBlockX() + "," + location.getBlockY() + ","
				+ location.getBlockZ() + ")";
	}
}