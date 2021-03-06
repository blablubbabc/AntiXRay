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

import java.util.UUID;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

// handles slash commands
class CommandHandler implements CommandExecutor {

	@Override
	public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
		if (args.length == 0 || args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("?")) {
			if (sender.hasPermission("antixray.help")) sendHelp(sender);
			else AntiXRay.sendMessage(sender, Messages.NoPermission);
			return true;
		} else {
			// reloads the configuration and messages:
			if (args[0].equalsIgnoreCase("reload")) {
				if (sender.hasPermission("antixray.reload")) {
					// reload configuration
					AntiXRay.instance.loadConfig();
					// reload messages
					AntiXRay.instance.dataStore.initialize();

					AntiXRay.sendMessage(sender, Messages.ReloadDone);
				} else AntiXRay.sendMessage(sender, Messages.NoPermission);

				return true;

				// prints information about a player (for example his current points):
			} else if (args[0].equalsIgnoreCase("check") || args[0].equalsIgnoreCase("points")) {
				// checking for himself:
				if (args.length == 1) {
					// only possible for players..
					if (!(sender instanceof Player)) {
						AntiXRay.sendMessage(sender, Messages.OnlyAsPlayer);
					} else {
						Player player = (Player) sender;

						if (player.hasPermission("antixray.check.self")) {
							String targetName = player.getName();
							PlayerData playerData = AntiXRay.instance.dataStore.getOrCreatePlayerData(player);

							// send player information
							sendPlayerCheckInformation(player, targetName, playerData);
						} else {
							AntiXRay.sendMessage(player, Messages.NoPermission);
						}
					}
				} else {
					// checking for someone else:
					if (sender.hasPermission("antixray.check.others")) {
						final String targetName = args[1];

						// continue after looking up the uuid for that playername:
						AntiXRay.lookupPlayerUUIDForName(targetName, new Callback<UUID>() {

							@Override
							protected void onComplete(UUID uuid) {
								PlayerData playerData = null;
								if (uuid != null) {
									playerData = AntiXRay.instance.dataStore.getPlayerDataIfExist(uuid);
								}

								// there might be old playerdata for this name, which hasn't be converted yet:
								if (playerData == null) {
									playerData = AntiXRay.instance.dataStore.getOldPlayerDataIfExists(targetName);
								}

								// send player information
								sendPlayerCheckInformation(sender, targetName, playerData);
							}
						});
					} else {
						AntiXRay.sendMessage(sender, Messages.NoPermission);
					}
				}

				return true;

				// sets a players value (for example his points) to a specified value:
			} else if (args[0].equalsIgnoreCase("set")) {
				if (sender.hasPermission("antixray.set")) {
					if (args.length == 4) {
						final String targetName = args[1];

						// continue after looking up the uuid for that playername:
						AntiXRay.lookupPlayerUUIDForName(targetName, new Callback<UUID>() {

							@Override
							protected void onComplete(UUID uuid) {
								PlayerData playerData = null;
								boolean isOldPlayerData = false;
								if (uuid != null) {
									playerData = AntiXRay.instance.dataStore.getPlayerDataIfExist(uuid);
								}

								// this also tries to load the playerdata from an old playerdata file, if we don't have a converted playerdata file for this player uuid yet:
								if (playerData == null) {
									// Note: if the player joined in the meantime and triggered an import of his old playerdata, we simple won't find the player's data in this specific request.
									// But nothing serious should break, like: no old playerdata file will be saved/recreated in this situation.
									playerData = AntiXRay.instance.dataStore.getOldPlayerDataIfExists(targetName);
									isOldPlayerData = true;
								}

								if (playerData != null) {
									boolean playerDataDirty = false;

									if (args[2].equalsIgnoreCase("points")) {
										// set the points:
										Integer newPoints = getNumber(args[3]);
										if (newPoints != null) {
											int oldPoints = playerData.points;

											// only change, if necessary:
											if (oldPoints != newPoints) {
												playerData.points = newPoints;
												playerDataDirty = true;
											}

											// inform player that we are done:
											AntiXRay.sendMessage(sender, Messages.ChangesAreDone);
										} else {
											AntiXRay.sendMessage(sender, Messages.InvalidNumber, args[3]);
										}

									} else if (args[2].equalsIgnoreCase("counter")) {
										// set the reached-limit-counter:
										Integer newCounter = getNumber(args[3]);
										if (newCounter != null && newCounter >= 0) {
											int oldCounter = playerData.reachedLimitCount;

											// only change, if necessary:
											if (oldCounter != newCounter) {
												playerData.reachedLimitCount = newCounter;
												playerDataDirty = true;
											}

											// inform player that we are done:
											AntiXRay.sendMessage(sender, Messages.ChangesAreDone);
										} else {
											AntiXRay.sendMessage(sender, Messages.InvalidNumber, args[3]);
										}

									} else {
										sender.sendMessage(AntiXRay.getMessage(Messages.CommandSetCmd));
									}

									// save changes
									if (playerDataDirty) {
										if (isOldPlayerData) {
											AntiXRay.instance.dataStore.saveOldPlayerData(targetName, playerData);
										} else {
											AntiXRay.instance.dataStore.savePlayerData(uuid, playerData);
										}
									}
								} else {
									AntiXRay.sendMessage(sender, Messages.NoPlayerDataFound, targetName);
								}
							}
						});
					} else {
						sender.sendMessage(AntiXRay.getMessage(Messages.CommandSetCmd));
					}
				} else {
					AntiXRay.sendMessage(sender, Messages.NoPermission);
				}

				return true;
			}
		}
		return false;
	}

	// sends information about a players data to a CommandSender
	private void sendPlayerCheckInformation(CommandSender sender, String targetName, PlayerData playerData) {
		if (playerData == null) {
			AntiXRay.sendMessage(sender, Messages.NoPlayerDataFound, targetName);
		} else {
			AntiXRay.sendMessage(sender, Messages.CurrentPoints, targetName, String.valueOf(playerData.points));
			AntiXRay.sendMessage(sender, Messages.ReachedLimitCount, targetName, String.valueOf(playerData.reachedLimitCount));
		}
	}

	// sends the help page to a CommandSender
	private void sendHelp(CommandSender sender) {
		sender.sendMessage(AntiXRay.getMessage(Messages.CommandHelpHeader));
		sender.sendMessage(AntiXRay.getMessage(Messages.CommandReloadCmd));
		sender.sendMessage(AntiXRay.getMessage(Messages.CommandReloadDesc));
		sender.sendMessage(AntiXRay.getMessage(Messages.CommandCheckCmd));
		sender.sendMessage(AntiXRay.getMessage(Messages.CommandCheckDesc));
		sender.sendMessage(AntiXRay.getMessage(Messages.CommandSetCmd));
		sender.sendMessage(AntiXRay.getMessage(Messages.CommandSetDesc));
	}

	// Tries to parse an Integer from a String. Returns null, if the string is not a number.
	private Integer getNumber(String string) {
		try {
			return Integer.parseInt(string);
		} catch (Exception e) {
			return null;
		}
	}
}
