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
import java.util.HashMap;
import java.util.Iterator;
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
	public int config_pointsPerHour; // how quickly players earn "points" which allow them to mine valuables
	public int config_maxPoints; // the upper limit on points
	public int config_startingPoints; // initial points for players who are new to the server
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

		// read configuration settings and set default values if necessary:
		this.config_startingPoints = config.getInt("AntiXRay.NewPlayerStartingPoints", -400);
		this.config_pointsPerHour = config.getInt("AntiXRay.PointsEarnedPerHourPlayed", 800);
		this.config_maxPoints = config.getInt("AntiXRay.MaximumPoints", 1600);

		this.config_exemptCreativeModePlayers = config.getBoolean("AntiXRay.ExemptCreativeModePlayers", true);

		this.config_notifyOnLimitReached = config.getBoolean("AntiXRay.NotifyOnMiningLimitReached", false);
		
		// write these values back to config(for the defaults):
		config.set("AntiXRay.NewPlayerStartingPoints", this.config_startingPoints);
		config.set("AntiXRay.PointsEarnedPerHourPlayed", this.config_pointsPerHour);
		config.set("AntiXRay.MaximumPoints", this.config_maxPoints);

		config.set("AntiXRay.ExemptCreativeModePlayers", this.config_exemptCreativeModePlayers);

		config.set("AntiXRay.NotifyOnMiningLimitReached", this.config_notifyOnLimitReached);
		
		// default ore values:
		
		// // default height: only blocks broken below this height will get checked
		int defaultHeight = config.getInt("DefaultHeight", 63);
		// write value back to config (for defaults):
		config.set("AntiXRay.DefaultHeight", defaultHeight);
		
		// // load custom block definitions:
		Map<String,  BlockData> customBlocks = new HashMap<String, BlockData>();
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
				
				// note: value and height are not important for the custom block definitions:
				customBlocks.put(customBlockName, new BlockData(id, subid, 0, 0));
			}
		}
		// // default for custom blocks definitions (value and height will be ignored)
		if (customBlocks.size() == 0) {
			customBlocks.put("SomeCustomOre", new BlockData(123, (byte) 0, 0, 0));
		}
		// write defaults for the custom ore definitions to config:
		for (Entry<String, BlockData> entry : customBlocks.entrySet()) {
			config.set("AntiXRay.CustomBlockDefinitions." + entry.getKey() + ".ID", entry.getValue().getId());
			config.set("AntiXRay.CustomBlockDefinitions." + entry.getKey() + ".Sub ID", entry.getValue().getSubid());
		}
		
		// // load the list of default valuable ores:
		Map<String, BlockData> defaultProtections = new HashMap<String, BlockData>();
		ConfigurationSection defaultBlocksSection = config.getConfigurationSection("AntiXRay.ProtectedBlockValues");
		if (defaultBlocksSection != null) {
			for (String oreName : defaultBlocksSection.getKeys(false)) {
				// value for this type of block
				int value = defaultBlocksSection.getInt(oreName, 0);
				
				// check for custom block:
				if (customBlocks.containsKey(oreName)) {
					BlockData customData = customBlocks.get(oreName);
					// initialize BlockData with information from the custom block definition and the default height
					defaultProtections.put(oreName, new BlockData(customData.getId(), customData.getSubid(), value, defaultHeight));
					
				} else {
					// check material name:
					Material material = Material.getMaterial(oreName);
					if (material == null) {
						logger.warning("Material not found: " + oreName);
						continue;
					} else {
						// initialize BlockData with id from the found material and subid of 0 and the default height
						defaultProtections.put(oreName, new BlockData(material.getId(), (byte) 0, value, defaultHeight));
					}
				}
				
			}
		}

		// // default values for the default protected blocks list
		if (defaultProtections.size() == 0) {
			defaultProtections.put(Material.DIAMOND_ORE.toString(), new BlockData(Material.DIAMOND_ORE.getId(), (byte) 0, 100, defaultHeight));
			defaultProtections.put(Material.EMERALD_ORE.toString(), new BlockData(Material.EMERALD_ORE.getId(), (byte) 0, 50, defaultHeight));
		}
		// // write default values to config:
		for (Entry<String, BlockData> entry : defaultProtections.entrySet()) {
			config.set("AntiXRay.ProtectedBlockValues." + entry.getKey(), entry.getValue().getValue());
		}
		
		// read world (specific) data:
		Map<String, Map<String, BlockData>> worldBlockData = new HashMap<String, Map<String,BlockData>>();
		
		ConfigurationSection worldsSection = config.getConfigurationSection("AntiXRay.Worlds");
		if (worldsSection != null) {
			for (String worldName : worldsSection.getKeys(false)) {
				Map<String, BlockData> worldOres = new HashMap<String, BlockData>();
				Map<String, BlockData> worldSpecificOres = new HashMap<String, BlockData>();
				worldBlockData.put(worldName, worldOres);
				// add this world:
				ProtectedBlocks.addWorld(worldName);
				// validate world:
				World world = getServer().getWorld(worldName);
				if (world == null) {
					logger.warning("Configuration: There's no world named \"" + worldName + "\".  Make sure that the worldnames in your config.yml are correct!");
				}
				
				// world specific informations:
				ConfigurationSection worldSection = worldsSection.getConfigurationSection(worldName);
				if (worldSection != null) {
					int worldHeight = worldSection.getInt("DefaultHeight", defaultHeight);
					
					// add default ores with worldHeight:
					for (Entry<String, BlockData> defaultOre : defaultProtections.entrySet()) {
						BlockData defaultData = defaultOre.getValue();
						worldOres.put(defaultOre.getKey(), new BlockData(defaultData.getId(), defaultData.getSubid(), defaultData.getValue(), worldHeight));
					}
					
					// world specific ore data: this will later be used to overwrite the default ore data for this world
					ConfigurationSection oresSection = worldSection.getConfigurationSection("ProtectedBlocks");
					if (oresSection != null) {
						for (String oreName : oresSection.getKeys(false)) {
							ConfigurationSection oreSection = oresSection.getConfigurationSection(oreName);
							if (oreSection != null) {
								int value = oreSection.getInt("Value", 0);
								int height = oreSection.getInt("Height", worldHeight);
								
								// check for custom block:
								if (customBlocks.containsKey(oreName)) {
									BlockData customData = customBlocks.get(oreName);
									// initialize BlockData with information from the custom block definition and add it to the world ores:
									worldSpecificOres.put(oreName, new BlockData(customData.getId(), customData.getSubid(), value, height));
									
								} else {
									// check material name:
									Material material = Material.getMaterial(oreName);
									if (material == null) {
										logger.warning("Material not found: " + oreName);
										continue;
									} else {
										// initialize BlockData with id from the found material and subid of 0 and add it to the world ores:
										worldSpecificOres.put(oreName, new BlockData(material.getId(), (byte) 0, value, worldHeight));
									}
								}
								
							}
						}
						
						// overwrite default ore data for this world:
						worldOres.putAll(worldSpecificOres);
					}
					
					// write world specific data back to config:
					String worldNode = "AntiXRay.Worlds." + worldName;
					if (worldHeight != defaultHeight) config.set(worldNode + ".DefaultHeight", worldHeight);
					String worldOresNode = worldNode + ".ProtectedBlocks";
					for (Entry<String, BlockData> entry : worldSpecificOres.entrySet()) {
						BlockData blockData = entry.getValue();
						config.set(worldOresNode + "." + entry.getKey() + ".Value", blockData.getValue());
						config.set(worldOresNode + "." + entry.getKey() + ".Height", blockData.getHeight());
					}
					
					
				} else {
					// add only the default ores (with default height):
					worldOres.putAll(defaultProtections);
				}
			}
		} else {
			// default worlds:
			for (World world : getServer().getWorlds()) {
				Map<String, BlockData> worldOres = new HashMap<String, BlockData>();
				String worldName  = world.getName();
				worldBlockData.put(worldName, worldOres);
				// add the default ores:
				worldOres.putAll(defaultProtections);
				
				// write default world data to config:
				String worldNode = "AntiXRay.Worlds." + worldName;
				config.set(worldNode, "");
			}
		}
		
		// set the protections for all worlds:
		for (Entry<String, Map<String, BlockData>> worldData : worldBlockData.entrySet()) {
			String worldName = worldData.getKey();
			for (BlockData blockData : worldData.getValue().values()) {
				ProtectedBlocks.addProtection(worldName, blockData);
			}
		}

		// write all those configuration values from above back to file
		// (this writes the defaults to the config file when nothing is specified)

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