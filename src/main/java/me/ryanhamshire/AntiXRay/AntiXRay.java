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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package me.ryanhamshire.AntiXRay;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.WorldType;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import me.ryanhamshire.AntiXRay.thirdparty.mcstats.Metrics;

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
	public boolean debug;
	public boolean config_metrics;
	public int config_pointsPerHour; // how quickly players earn "points" which allow them to mine valuables
	public int config_maxPoints; // the upper limit on points
	public int config_startingPoints; // initial points for players who are new to the server
	public boolean config_ignoreMaxPointsForBlockRatio; // whether it shall ignore if a player has more than maxPoints
														 // points when receiving them via breaking blocks (block ratio)
	public boolean config_exemptCreativeModePlayers; // whether creative mode players should be exempt from the rules
	public boolean config_notifyOnLimitReached; // whether to notify online moderators when a player reaches his limit

	public ProtectedBlocks protections;

	// initializes well... everything
	public void onEnable() {
		instance = this;
		logger = this.getLogger();

		protections = new ProtectedBlocks();

		// load configuration
		this.loadConfig();

		dataStore = new FlatFileDataStore();

		// start the task to regularly give players the points they've earned for play time 20L ~ 1 second
		Bukkit.getScheduler().runTaskTimer(this, new DeliverPointsTask(), 20L * 60, 20L * 60);

		// register event handlers:

		// player events
		Bukkit.getPluginManager().registerEvents(new PlayerEventHandler(dataStore), this);

		// block events
		Bukkit.getPluginManager().registerEvents(new BlockEventHandler(dataStore), this);

		// entity events
		Bukkit.getPluginManager().registerEvents(new ExplosionsListener(), this);

		// command handler
		this.getCommand("antixray").setExecutor(new CommandHandler());

		// let's see how many people use this plugin:
		if (config_metrics) {
			try {
				Metrics metrics = new Metrics(this);
				metrics.start();
			} catch (IOException e) {
				// Failed to submit the stats :-(
			}
		}
	}

	// on disable, close any open files and/or database connections
	public void onDisable() {
		// ensure all online players get their data saved
		for (Player player : Bukkit.getOnlinePlayers()) {
			UUID uuid = player.getUniqueId();
			dataStore.savePlayerData(uuid, dataStore.getOrCreatePlayerData(player));
		}

		dataStore.close();
	}

	void loadConfig() {
		// initialize fresh config with custom path separator, as some users might have world names with dots in it:
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
		debug = baseSection.getBoolean("DebugMode", false);
		config_metrics = baseSection.getBoolean("EnableMetricsTracking", true);
		config_startingPoints = baseSection.getInt("NewPlayerStartingPoints", -400);
		config_pointsPerHour = baseSection.getInt("PointsEarnedPerHourPlayed", 800);
		config_maxPoints = baseSection.getInt("MaximumPoints", 1600);

		config_ignoreMaxPointsForBlockRatio = baseSection.getBoolean("IgnoreMaxPointsForBlockRatio", true);
		config_exemptCreativeModePlayers = baseSection.getBoolean("ExemptCreativeModePlayers", true);
		config_notifyOnLimitReached = baseSection.getBoolean("NotifyOnMiningLimitReached", false);

		// default max height: only checks for blocks broken below this height
		int defaultHeight = baseSection.getInt("DefaultMaxHeight", 63);

		// write all those loaded values from above back to file (for defaults):
		baseSection.set("DebugMode", debug);
		baseSection.set("NewPlayerStartingPoints", config_startingPoints);
		baseSection.set("PointsEarnedPerHourPlayed", config_pointsPerHour);
		baseSection.set("MaximumPoints", config_maxPoints);

		baseSection.set("IgnoreMaxPointsForBlockRatio", config_ignoreMaxPointsForBlockRatio);
		baseSection.set("ExemptCreativeModePlayers", config_exemptCreativeModePlayers);
		baseSection.set("NotifyOnMiningLimitReached", config_notifyOnLimitReached);

		baseSection.set("DefaultMaxHeight", defaultHeight);

		// load the list of default protected blocks:
		ConfigurationSection defaultProtectionsSection = baseSection.getConfigurationSection("ProtectedBlockValues");
		List<ProtectedBlock> defaultProtections = this.loadBlockData(defaultProtectionsSection, null, defaultHeight, true);

		// no valid values found? -> add default values for the default protected blocks list
		if (defaultProtections.isEmpty()) {
			defaultProtections.add(new ProtectedBlock(Material.DIAMOND_ORE, 100, 20));
			defaultProtections.add(new ProtectedBlock(Material.EMERALD_ORE, 50, 35));
		}

		// write values back to config:
		// remove section first to remove invalid entries:
		baseSection.set("ProtectedBlockValues", null);
		defaultProtectionsSection = baseSection.createSection("ProtectedBlockValues");
		for (ProtectedBlock protectedBlock : defaultProtections) {
			ConfigurationSection blockSection = defaultProtectionsSection.createSection(this.toConfigKey(protectedBlock));
			blockSection.set("Value", protectedBlock.getValue());
			if (protectedBlock.getMaxHeight() != defaultHeight) blockSection.set("MaxHeight", protectedBlock.getMaxHeight());
		}

		// read world (specific) data:
		Map<String, List<ProtectedBlock>> allWorldsProtections = new HashMap<>();

		ConfigurationSection worldsSection = baseSection.getConfigurationSection("Worlds");
		if (worldsSection == null) {
			// init with default world data:
			worldsSection = baseSection.createSection("Worlds");
			// add defaults for all loaded worlds:
			for (World world : Bukkit.getWorlds()) {
				String worldName = world.getName();
				logger.info("Adding default world entry for world: " + worldName);

				WorldType worldType = world.getWorldType();
				Environment environment = world.getEnvironment();

				ConfigurationSection worldSection = worldsSection.createSection(worldName);

				// write default world data to config:
				if (environment == Environment.NORMAL && worldType == WorldType.NORMAL) {
					ConfigurationSection protectedBlocksSection = worldSection.createSection("ProtectedBlocks");
					String diamondKey = Material.DIAMOND_ORE.name();
					protectedBlocksSection.set(diamondKey + DOT + "Value", 100);
					protectedBlocksSection.set(diamondKey + DOT + "MaxHeight", 20);
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
			List<ProtectedBlock> worldProtections = new ArrayList<ProtectedBlock>();
			allWorldsProtections.put(worldName, worldProtections);
			// add this world:
			protections.addWorld(worldName);
			// validate world:
			World world = getServer().getWorld(worldName);
			if (world == null) {
				logger.warning("Configuration: There's no world named \"" + worldName + "\".  Make sure that the worldnames in your config.yml are correct!");
			}

			// world specific informations:
			ConfigurationSection worldSection = worldsSection.getConfigurationSection(worldName);

			// remove old world section from config, but keep world name / keep world enabled, it gets rewritten with
			// valid data afterwards:
			worldsSection.set(worldName, "");

			if (worldSection != null) {
				boolean worldHeightSet = worldSection.isSet("DefaultMaxHeight");
				int worldHeight = worldSection.getInt("DefaultMaxHeight", defaultHeight);

				// add default protected blocks with worldHeight:
				for (ProtectedBlock defaultProtectedBlock : defaultProtections) {
					// using worldHeight for the block data here, so that the world specific height value can overwrite
					// the height of the default ores
					// so users don't have to overwrite the height for each specific default protected block in each
					// world
					worldProtections.add(new ProtectedBlock(defaultProtectedBlock.getType(), defaultProtectedBlock.getValue(), worldHeight));
				}

				// load world specific protected blocks data:
				ConfigurationSection blocksSection = worldSection.getConfigurationSection("ProtectedBlocks");
				List<ProtectedBlock> worldSpecificProtections = this.loadBlockData(blocksSection, defaultProtections, worldHeight, worldHeightSet);
				// overwrite default protected blocks data for this world:
				worldProtections.addAll(worldSpecificProtections);

				// write all world specific data back to config:
				if (worldHeightSet) {
					// we also have to store world specific max height data if it equals the general default max height
					// value because otherwise the world specific block specific height values wouldn't use it as
					// default value
					worldsSection.set(worldName + DOT + "DefaultMaxHeight", worldHeight);
				}
				if (worldSpecificProtections.size() > 0) {
					for (ProtectedBlock protectedBlock : worldSpecificProtections) {
						Material blockType = protectedBlock.getType();
						int value = protectedBlock.getValue();
						int maxHeight = protectedBlock.getMaxHeight();

						ProtectedBlock defaultProtection = this.findFirst(defaultProtections, blockType);
						int defaultBlockValue = defaultProtection != null ? defaultProtection.getValue() : 0;
						int defaultMaxBlockHeight = (defaultProtection != null) && !worldHeightSet ? defaultProtection.getMaxHeight() : worldHeight;

						String blockNode = worldName + DOT + "ProtectedBlocks" + DOT + this.toConfigKey(protectedBlock);
						if (value != defaultBlockValue) worldsSection.set(blockNode + DOT + "Value", value);
						if (maxHeight != defaultMaxBlockHeight) worldsSection.set(blockNode + DOT + "MaxHeight", maxHeight);
					}
				}
			} else {
				// add only the default ores (with default height):
				worldProtections.addAll(defaultProtections);
			}
		}

		// clear old loaded world protections:
		protections.clear();

		// set the loaded protections for all worlds:
		for (Entry<String, List<ProtectedBlock>> worldData : allWorldsProtections.entrySet()) {
			String worldName = worldData.getKey();
			for (ProtectedBlock protectedBlock : worldData.getValue()) {
				protections.addProtection(worldName, protectedBlock);
			}
		}

		// save config (writes default values):
		try {
			config.save(DataStore.configFilePath);
		} catch (IOException exception) {
			logger.severe("Unable to write to the configuration file at \"" + DataStore.configFilePath + "\"");
		}
	}

	private ProtectedBlock findFirst(List<ProtectedBlock> protectedBlocks, Material blockType) {
		for (ProtectedBlock protectedBlock : protectedBlocks) {
			if (protectedBlock.getType() == blockType) {
				return protectedBlock;
			}
		}
		return null;
	}

	private String toConfigKey(ProtectedBlock protectedBlock) {
		return this.toConfigKey(protectedBlock.getType());
	}

	private String toConfigKey(Material blockType) {
		return blockType.name();
	}

	// utility method for loading block data from the given config section
	private List<ProtectedBlock> loadBlockData(ConfigurationSection section, List<ProtectedBlock> defaultProtectedBlocks, int defaultHeight, boolean defaultHeightExplicitlySet) {
		List<ProtectedBlock> protectedBlocks = new ArrayList<>();
		if (section == null) return protectedBlocks;

		for (String blockConfigKey : section.getKeys(false)) {
			ConfigurationSection blockSection = section.getConfigurationSection(blockConfigKey);
			if (blockSection == null) continue; // no valid section

			// determine block type by material name:
			Material blockType = Material.matchMaterial(blockConfigKey);
			if (blockType == null) {
				logger.warning("Material not found: " + blockConfigKey);
				continue;
			}

			// if we have default block data for this specific block, use that for default values:
			ProtectedBlock defaultData = (defaultProtectedBlocks != null ? this.findFirst(defaultProtectedBlocks, blockType) : null);

			// data for this type of block:
			int defaultValue = (defaultData != null ? defaultData.getValue() : 0);
			int value = blockSection.getInt("Value", defaultValue);

			// only uses the default block specific max height value if it is available and no (world specific) max
			// height value was set:
			int effectiveDefaultHeight = (defaultData != null) && !defaultHeightExplicitlySet ? defaultData.getMaxHeight() : defaultHeight;
			int height = blockSection.getInt("MaxHeight", effectiveDefaultHeight);

			// initialize the ProtectedBlock with the configured block type and height
			protectedBlocks.add(new ProtectedBlock(blockType, value, height));
			// TODO warning/error on duplicate block types? Or allow different values for different heights by sorting
			// protections by their heights?
		}
		return protectedBlocks;
	}

	static ProtectedBlocks getProtections() {
		return AntiXRay.instance.protections;
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
		// split on 'real' new lines and on escaped new line character sequence:
		String[] messages = message.split("\n|\\\\n");
		for (String msg : messages) {
			if (receiver == null) {
				logger.info(msg);
			} else {
				receiver.sendMessage(msg);
			}
		}
	}

	// creates an easy-to-read location description
	public static String getfriendlyLocationString(Location location) {
		return location.getWorld().getName() + "(" + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ() + ")";
	}

	public static void debug(String message) {
		if (AntiXRay.instance.debug) {
			logger.info("[Debug] " + message);
		}
	}

	// this will run async and call the provided callback when done
	static void lookupPlayerUUIDForName(final String playerName, final Callback<UUID> runWhenDone) {
		assert playerName != null;
		assert runWhenDone != null;
		assert instance != null;

		// fast check for online players:
		Player player = Bukkit.getPlayerExact(playerName);
		if (player != null) {
			runWhenDone.setResult(player.getUniqueId()).run();
			return;
		}

		Bukkit.getScheduler().runTaskAsynchronously(instance, new Runnable() {

			@Override
			public void run() {
				final OfflinePlayer offlinePlayer = Bukkit.getServer().getOfflinePlayer(playerName);
				UUID uuid = null;
				try {
					// get uuid (casting to Player if the player is online might fix certain issues on older bukkit
					// versions for at least online players)
					uuid = offlinePlayer instanceof Player ? ((Player) offlinePlayer).getUniqueId() : offlinePlayer.getUniqueId();
				} catch (Throwable e) {
					// well.. seems like the bukkit version we are running on does not support getting the uuid of
					// offline players
					uuid = null;
				}

				// Note: for now let's not use the uuid we got to import old playerdata here, because of potential
				// issues regarding this being the wrong uuid in certain circumstances

				Bukkit.getScheduler().runTask(AntiXRay.instance, runWhenDone.setResult(uuid));
			}
		});
	}
}
