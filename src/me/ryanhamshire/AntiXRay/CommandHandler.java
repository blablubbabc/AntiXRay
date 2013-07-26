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

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

// handles slash commands
class CommandHandler implements CommandExecutor {

	private static final ChatColor CMD = ChatColor.YELLOW;
	private static final ChatColor DESC = ChatColor.BLUE;
	
	
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (args.length == 0 || args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("?")) {
			if (sender.hasPermission("antixray.help")) sendHelp(sender);
			else AntiXRay.sendMessage(sender, Messages.NoPermission);
			return true;
		} else {
			if (args[0].equalsIgnoreCase("reload")) {
				if (sender.hasPermission("antixray.reload")) {
					// reload configuration 
					AntiXRay.instance.loadConfig();
					// reload messages
					AntiXRay.instance.dataStore.initialize();
					
					AntiXRay.sendMessage(sender, Messages.ReloadDone);
				} else AntiXRay.sendMessage(sender, Messages.NoPermission);
				
				return true;
			} else if (args[0].equalsIgnoreCase("check") || args[0].equalsIgnoreCase("points")) {
				if (!(sender instanceof Player)) {
					AntiXRay.sendMessage(sender, Messages.OnlyAsPlayer);
				} else {
					Player player = (Player) sender;
					if (args.length == 1) {
						if (sender.hasPermission("antixray.check.self")) {
							int points = AntiXRay.instance.dataStore.getPlayerData(player).points;
							AntiXRay.sendMessage(player, Messages.CurrentPoints, player.getName(), String.valueOf(points));
							
						} else AntiXRay.sendMessage(sender, Messages.NoPermission);
					} else {
						if (sender.hasPermission("antixray.check.others")) {
							String targetName = args[1];
							PlayerData playerData = AntiXRay.instance.dataStore.getPlayerDataIfExist(targetName);
							if (playerData == null) {
								AntiXRay.sendMessage(player, Messages.NoPlayerDataFound, targetName);
							} else {
								AntiXRay.sendMessage(player, Messages.CurrentPoints, targetName, String.valueOf(playerData.points));
							}
						} else AntiXRay.sendMessage(sender, Messages.NoPermission);
					}	
				}
				
				return true;
			}
		}
		return false;
	}
	
	private void sendHelp(CommandSender sender) {
		sender.sendMessage(ChatColor.DARK_GREEN + "--- " + ChatColor.DARK_RED + "AntiXRay" + ChatColor.DARK_GREEN + " ---");
		sender.sendMessage(CMD + "/antixray reload");
		sender.sendMessage(DESC + "     - " + AntiXRay.getMessage(Messages.CommandReload));
		sender.sendMessage(CMD + "/antixray check [player]");
		sender.sendMessage(DESC + "     - " + AntiXRay.getMessage(Messages.CommandPoints));
	}

}
