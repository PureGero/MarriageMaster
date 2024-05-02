/*
 *   Copyright (C) 2024 GeorgH93
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package at.pcgamingfreaks.MarriageMaster.Bukkit.Listener;

import at.pcgamingfreaks.MarriageMaster.Bukkit.API.DelayableTeleportAction;
import at.pcgamingfreaks.MarriageMaster.Bukkit.API.Marriage;
import at.pcgamingfreaks.MarriageMaster.Bukkit.API.MarriagePlayer;
import at.pcgamingfreaks.MarriageMaster.Bukkit.Commands.HomeCommand;
import at.pcgamingfreaks.MarriageMaster.Bukkit.Commands.TpCommand;
import at.pcgamingfreaks.MarriageMaster.Bukkit.MarriageMaster;
import at.pcgamingfreaks.MarriageMaster.Database.PluginChannelCommunicatorBase;
import com.github.puregero.multilib.MultiLib;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;

public class MultiLibCommunicator extends PluginChannelCommunicatorBase
{

	@Getter @Setter(AccessLevel.PRIVATE) private static String serverName = null;

	private final MarriageMaster plugin;
	private final long delayTime;
	@Setter private TpCommand tpCommand = null;
	@Setter private HomeCommand homeCommand = null;

	public MultiLibCommunicator(MarriageMaster plugin)
	{
		super(plugin.getLogger(), plugin.getDatabase());
		this.plugin = plugin;
		delayTime = plugin.getConfiguration().getTPDelayTime() * 20L;

		MultiLib.on(plugin, CHANNEL_MARRIAGE_MASTER, data -> receive(CHANNEL_MARRIAGE_MASTER, data));

		setServerName(MultiLib.getLocalServerName());

		logger.info("MultiLib data sync handler initialized.");
	}

	@Override
	protected void receiveUnknownChannel(@NotNull String channel, byte[] bytes)
	{
		// Do nothing
	}

	@Override
	protected boolean receiveMarriageMaster(@NotNull String cmd, @NotNull DataInputStream inputStream) throws IOException
	{
		switch(cmd)
		{
			case "update": plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), "marry update"); break;
			case "reload": plugin.reload(); break;
			case "home":
				{
					MarriagePlayer toTP = plugin.getPlayerData(UUID.fromString(inputStream.readUTF()));
					Marriage marriage = toTP.getMarriageData(plugin.getPlayerData(UUID.fromString(inputStream.readUTF())));
					if(marriage == null || !toTP.isOnline()) return true;
					homeCommand.doTheTP(toTP, marriage);
				}
				break;
			case "delayHome":
				{
					MarriagePlayer player = plugin.getPlayerData(UUID.fromString(inputStream.readUTF()));
					if(player.isOnline()) plugin.doDelayableTeleportAction(new DelayedAction("home", player, inputStream.readUTF()));
				}
				break;
			case "tp":
				{
					Player player = plugin.getServer().getPlayer(UUID.fromString(inputStream.readUTF()));
					Player target = plugin.getServer().getPlayer(UUID.fromString(inputStream.readUTF()));
					if(player != null && target != null && tpCommand != null) tpCommand.doTheTP(player, target);
				}
				break;
			case "delayTP":
				{
					MarriagePlayer player = plugin.getPlayerData(UUID.fromString(inputStream.readUTF()));
					if(player.isOnline()) plugin.doDelayableTeleportAction(new DelayedAction("tp", player, inputStream.readUTF()));
				}
				break;
			//region Sync settings
			case "UseUUIDs":
				{
					String useUUIDsString = inputStream.readUTF();
					boolean useUUIdDs = Boolean.parseBoolean(useUUIDsString);
					if(!useUUIdDs)
					{
						logger.warning("Your BungeeCord version of Marriage Master is outdated! And you have disabled the UseUUIDs setting on BungeeCord! Changing config ...");
					}
				}
				break;
			case "UseUUIDSeparators":
				{
					boolean useUUIDSeparators = Boolean.parseBoolean(inputStream.readUTF());
					if(useUUIDSeparators != plugin.getConfiguration().useUUIDSeparators())
					{
						logger.warning("UseUUIDSeparators setting does not match value on BungeeCord! Changing config ...");
						plugin.getConfiguration().setUseUUIDSeparators(useUUIDSeparators);
						logger.log(Level.INFO, "UseUUIDSeparators setting has been set to {0} to match BungeeCord setting. Please restart the server or reload the plugin.", useUUIDSeparators);
					}
				}
				break;
			case "UUID_Type":
				{
					String type = inputStream.readUTF();
					if((type.equals("online") && !plugin.getConfiguration().useOnlineUUIDs()) || (type.equals("offline") && plugin.getConfiguration().useOnlineUUIDs()))
					{
						logger.warning("UUID_Type setting does not match value on BungeeCord! Changing config ...");
						plugin.getConfiguration().setUUIDType(type);
						logger.log(Level.INFO, "UUID_Type setting has been set to {0} to match BungeeCord setting. Please restart the server or reload the plugin.", type);
					}
				}
				break;
			//endregion
			default: return false;
		}
		return true;
	}

	//region send methods
	@Override
	public void sendMessage(final byte[] data)
	{
		MultiLib.notify(CHANNEL_MARRIAGE_MASTER, data);
	}
	//endregion

	//region helper classes
	private class DelayedAction implements DelayableTeleportAction
	{
		@Getter private final MarriagePlayer player;
		private final String command, partnerUUID;

		public DelayedAction(String command, MarriagePlayer player, String partnerUUID)
		{
			this.command = command;
			this.player = player;
			this.partnerUUID = partnerUUID;
		}

		@Override
		public void run()
		{
			sendMessage(command, player.getUUID().toString(), partnerUUID);
		}

		@Override
		public long getDelay()
		{
			return delayTime;
		}
	}
	//endregion
}