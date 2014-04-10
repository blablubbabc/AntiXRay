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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.WorldType;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.mcstats.Metrics;

public class AntiXRay extends JavaPlugin {
	// for convenience, a reference to the instance of this plugin
	public static AntiXRay instance;

	// for logging to the console and log file
	public static Logger logger;

	// this handles data storage, like player data
	public DataStore dataStore;

	// custom configuration path separator, as some users use dots in their world names:
	public static final char DOT = '\uF8FF';
	// configuration variables, loaded/saved from a config.yml
	public boolean config_metrics;
	public int config_pointsPerHour; // how quickly players earn "points" which allow them to mine valuables
	public int config_maxPoints; // the upper limit on points
	public int config_startingPoints; // initial points for players who are new to the server
	public boolean config_ignoreMaxPointsForBlockRatio; // whether it shall ignore if a player has more than maxPoints points when receiving them via breaking blocks (block ratio)
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

		// let's see how many people use this plugin:
		if (config_metrics) {
			try {
			    Metrics metrics = new Metrics(this);
			    metrics.start();
			} catch (IOException e) {
			    // Failed to submit the stats :-(
			}
		}
		
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
		// init fresh config with custom path separator, as some users might have world names with dots in it:
		FileConfiguration config = new YamlConfiguration();
		config.options().pathSeparator(DOT);
		try {
			config.load(new File(DataStore.configFilePath));
		} catch (FileNotFoundException e) {
			// do nothing here: config is initialized empty, so default values will be used
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InvalidConfigurationException e) {
			e.printStackTrace();
		}

		// read configuration settings and set default values if necessary:
		ConfigurationSection baseSection = config.getConfigurationSection("AntiXRay");
		if (baseSection == null) baseSection = config.createSection("AntiXRay");
		this.config_metrics = baseSection.getBoolean("EnableMetricsTracking", true);
		this.config_startingPoints = baseSection.getInt("NewPlayerStartingPoints", -400);
		this.config_pointsPerHour = baseSection.getInt("PointsEarnedPerHourPlayed", 800);
		this.config_maxPoints = baseSection.getInt("MaximumPoints", 1600);

		this.config_ignoreMaxPointsForBlockRatio = baseSection.getBoolean("IgnoreMaxPointsForBlockRatio", true);
		this.config_exemptCreativeModePlayers = baseSection.getBoolean("ExemptCreativeModePlayers", true);
		this.config_notifyOnLimitReached = baseSection.getBoolean("NotifyOnMiningLimitReached", false);

		// default max height: only checks for blocks broken below this height
		int defaultHeight = baseSection.getInt("DefaultMaxHeight", 63);

		// write all those loaded values from above back to file (for defaults):
		baseSection.set("NewPlayerStartingPoints", this.config_startingPoints);
		baseSection.set("PointsEarnedPerHourPlayed", this.config_pointsPerHour);
		baseSection.set("MaximumPoints", this.config_maxPoints);

		baseSection.set("IgnoreMaxPointsForBlockRatio", this.config_ignoreMaxPointsForBlockRatio);
		baseSection.set("ExemptCreativeModePlayers", this.config_exemptCreativeModePlayers);
		baseSection.set("NotifyOnMiningLimitReached", this.config_notifyOnLimitReached);

		baseSection.set("DefaultMaxHeight", defaultHeight);

		// load custom block definitions:
		Map<String, BlockData> customBlocks = new HashMap<String, BlockData>();
		ConfigurationSection customBlocksSection = baseSection.getConfigurationSection("CustomBlockDefinitions");
		if (customBlocksSection == null) customBlocksSection = baseSection.createSection("CustomBlockDefinitions");
		else {
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
					logger.warning("CustomBlock 'ID' is not a number: " + customBlockName + ".");
					continue;
				}
				if (!customBlockSection.contains("Sub ID")) {
					logger.warning("CustomBlock 'Sub ID' not found: " + customBlockName + ".");
					continue;
				}
				if (!customBlockSection.isInt("Sub ID")) {
					logger.warning("CustomBlock 'Sub ID' is not a number: " + customBlockName + ".");
					continue;
				}
				int id = customBlockSection.getInt("ID");
				int subidInt = customBlockSection.getInt("Sub ID", 0);
				// range check
				if (subidInt < -1 || subidInt > Byte.MAX_VALUE) {
					logger.warning("CustomBlock 'Sub ID' is not a valid sub id: " + customBlockName + ".");
					continue;
				}
				byte subid = (byte) subidInt;

				// note: value and height are not important for the custom block definitions:
				customBlocks.put(customBlockName, new BlockData(id, subid, 0, 0));
			}
		}

		// default for custom blocks definitions (value and height will be ignored)
		if (customBlocks.size() == 0) {
			customBlocks.put("SomeCustomOre", new BlockData(123, (byte) 0, 0, 0));
		}

		// write values back to config:
		for (Entry<String, BlockData> entry : customBlocks.entrySet()) {
			baseSection.set("CustomBlockDefinitions" + DOT + entry.getKey() + DOT + "ID", entry.getValue().getId());
			baseSection.set("CustomBlockDefinitions" + DOT + entry.getKey() + DOT + "Sub ID", entry.getValue().getSubid());
		}

		// load the list of default valuable ores:
		Map<String, BlockData> defaultProtections = new HashMap<String, BlockData>();
		ConfigurationSection defaultBlocksSection = baseSection.getConfigurationSection("ProtectedBlockValues");
		if (defaultBlocksSection == null) defaultBlocksSection = baseSection.createSection("ProtectedBlockValues");
		else {
			for (String oreName : defaultBlocksSection.getKeys(false)) {
				ConfigurationSection blockSection = defaultBlocksSection.getConfigurationSection(oreName);
				// remove section first (gets rewritten with valid data afterwards):
				defaultBlocksSection.set(oreName, null);
				if (blockSection == null) continue; // no valid section
				// data for this type of block
				int value = blockSection.getInt("Value", 0);
				int height = blockSection.getInt("MaxHeight", defaultHeight);

				// check for custom block:
				if (customBlocks.containsKey(oreName)) {
					BlockData customData = customBlocks.get(oreName);
					// initialize BlockData with information from the custom block definition and the default height
					defaultProtections.put(oreName, new BlockData(customData.getId(), customData.getSubid(), value, height));

				} else {
					// check material name:
					Material material = Material.getMaterial(oreName);
					if (material == null) {
						logger.warning("Material not found: " + oreName);
						continue;
					} else {
						// initialize BlockData with id from the found material and subid of 0 and the default height
						defaultProtections.put(oreName, new BlockData(material.getId(), (byte) 0, value, height));
					}
				}

			}
		}

		// no valid values found? -> add default values for the default protected blocks list
		if (defaultProtections.size() == 0) {
			defaultProtections.put(Material.DIAMOND_ORE.toString(), new BlockData(Material.DIAMOND_ORE.getId(), (byte) 0, 100, 20));
			defaultProtections.put(Material.EMERALD_ORE.toString(), new BlockData(Material.EMERALD_ORE.getId(), (byte) 0, 50, 35));
		}

		// write values back to config:
		for (Entry<String, BlockData> entry : defaultProtections.entrySet()) {
			ConfigurationSection blockSection = defaultBlocksSection.createSection(entry.getKey());
			BlockData data = entry.getValue();
			blockSection.set("Value", data.getValue());
			if (data.getHeight() != defaultHeight) blockSection.set("MaxHeight", data.getHeight());
		}

		// read world (specific) data:
		Map<String, Map<String, BlockData>> worldBlockData = new HashMap<String, Map<String, BlockData>>();

		ConfigurationSection worldsSection = baseSection.getConfigurationSection("Worlds");
		if (worldsSection == null) {
			// init with default world data:
			worldsSection = baseSection.createSection("Worlds");
			// add defaults for all loaded worlds:
			for (World world : this.getServer().getWorlds()) {
				String worldName = world.getName();
				logger.info("Adding default world entry for world: " + worldName);

				WorldType worldType = world.getWorldType();
				Environment environment = world.getEnvironment();

				ConfigurationSection worldSection = worldsSection.createSection(worldName);

				// write default world data to config:
				if (environment == Environment.NORMAL && worldType == WorldType.NORMAL) {
					ConfigurationSection protectedBlocksSection = worldSection.createSection("ProtectedBlocks");
					protectedBlocksSection.set(Material.DIAMOND_ORE.name() + DOT + "Value", 100);
					protectedBlocksSection.set(Material.DIAMOND_ORE.name() + DOT + "MaxHeight", 20);
				} else if (environment == Environment.NETHER || environment == Environment.THE_END) {
					worldSection.set("DefaultMaxHeight", 256);
				} else {
					worldsSection.set(worldName, "");
				}
			}
		}

		// should have been initialized with default data:
		assert worldsSection != null;

		// read world data now:
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

			// remove old world section from config, but keep world name / keep world enabled, it gets rewritten with valid data afterwards:
			worldsSection.set(worldName, "");

			if (worldSection != null) {
				int worldHeight = worldSection.getInt("DefaultMaxHeight", defaultHeight);

				// add default ores with worldHeight:
				for (Entry<String, BlockData> defaultOre : defaultProtections.entrySet()) {
					BlockData defaultData = defaultOre.getValue();
					// using worldHeight for the block data here, so that the world specific height value can overwrite the height of the default ores
					// so users don't have to overwrite the height for each specific default ore in each world
					worldOres.put(defaultOre.getKey(), new BlockData(defaultData.getId(), defaultData.getSubid(), defaultData.getValue(), worldHeight));
				}

				// world specific ore data: this will later be used to overwrite the default ore data for this world
				ConfigurationSection oresSection = worldSection.getConfigurationSection("ProtectedBlocks");
				if (oresSection != null) {
					for (String oreName : oresSection.getKeys(false)) {
						ConfigurationSection oreSection = oresSection.getConfigurationSection(oreName);
						if (oreSection != null) {
							// if we have a default block data for this specific ore, use that for default values:
							BlockData defaultData = defaultProtections.get(oreName);

							// get value:
							int defaultOreValue = defaultData != null ? defaultData.getValue() : 0;
							int value = oreSection.getInt("Value", defaultOreValue);

							// get max height:
							int defaultMaxOreHeight = defaultData != null ? defaultData.getHeight() : worldHeight;
							int height = oreSection.getInt("MaxHeight", defaultMaxOreHeight);
							
							// heights get overwritten in this hierarchy:
							// defaultMaxHeight < default protected ore specific MaxHeight < world specific MaxHeight < world specific ore specific MaxHeight
							// however, the world specific ore specific MaxHeight value prefers the default ore MaxHeight value as default
							// so if we add DIAMOND_ORE to the world specific ores, without specifying a max height for it there,
							// it will use the max height value of the same ore from the default protected ores section,
							// which uses the general DefaultMaxHeight value as default..

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
									worldSpecificOres.put(oreName, new BlockData(material.getId(), (byte) 0, value, height));
								}
							}

						}
					}

					// overwrite default ore data for this world:
					worldOres.putAll(worldSpecificOres);
				}

				// write all world specific data back to config:
				if (worldHeight != defaultHeight) {
					worldsSection.set(worldName + DOT + "DefaultMaxHeight", worldHeight);
				}
				if (worldSpecificOres.size() > 0) {
					for (Entry<String, BlockData> entry : worldSpecificOres.entrySet()) {
						String oreName = entry.getKey();
						BlockData blockData = entry.getValue();
						int value = blockData.getValue();
						int maxHeight = blockData.getHeight();

						BlockData defaultData = defaultProtections.get(oreName);
						int defaultOreValue = defaultData != null ? defaultData.getValue() : 0;
						int defaultMaxOreHeight = defaultData != null ? defaultData.getHeight() : worldHeight;

						String oreNode = worldName + DOT + "ProtectedBlocks" + oreName;
						if (value != defaultOreValue) worldsSection.set(oreNode + DOT + "Value", value);
						if (maxHeight != defaultMaxOreHeight) worldsSection.set(oreNode + DOT + "MaxHeight", value);
					}
				}

			} else {
				// add only the default ores (with default height):
				worldOres.putAll(defaultProtections);
			}
		}

		// write all world data back to config:

		// clear old loaded world protections:
		ProtectedBlocks.clear();

		// set the loaded protections for all worlds:
		for (Entry<String, Map<String, BlockData>> worldData : worldBlockData.entrySet()) {
			String worldName = worldData.getKey();
			for (BlockData blockData : worldData.getValue().values()) {
				ProtectedBlocks.addProtection(worldName, blockData);
			}
		}

		// save config (writes default values):
		try {
			config.save(DataStore.configFilePath);
		} catch (IOException exception) {
			logger.severe("Unable to write to the configuration file at \"" + DataStore.configFilePath + "\"");
		}

	}

	// sends a message to a player
	static void sendMessage(CommandSender receiver, Messages messageID, String... args) {
		String message = getMessage(messageID, args);
		sendMessage(receiver, message);
	}

	// gets a message from the datastore by the given id
	static String getMessage(Messages messageID, String... args) {
		return AntiXRay.instance.dataStore.getMessage(messageID, args);
	}

	// sends a message to a player
	static void sendMessage(CommandSender receiver, String message) {
		if (receiver == null) {
			logger.info(message);
		} else {
			receiver.sendMessage(message);
		}
	}

	// creates an easy-to-read location description
	public static String getfriendlyLocationString(Location location) {
		return location.getWorld().getName() + "(" + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ() + ")";
	}
}