/*
 *   Copyright (C) 2022 GeorgH93
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

package at.pcgamingfreaks.MarriageMaster.Bukkit.Management.Requests;

import at.pcgamingfreaks.MarriageMaster.Bukkit.API.AcceptPendingRequest;
import at.pcgamingfreaks.MarriageMaster.Bukkit.API.MarriagePlayer;
import at.pcgamingfreaks.MarriageMaster.Bukkit.MarriageMaster;
import com.github.puregero.multilib.MultiLib;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ExternalAcceptRequest extends AcceptPendingRequest
{

	private enum Action
	{
		ACCEPT, DENY, CANCEL, DISCONNECT
	}

	private static final Map<UUID, WeakReference<AcceptPendingRequest>> requests = new ConcurrentHashMap<>();

	public static void init(MarriageMaster plugin) {
		MultiLib.onString(plugin, "marriagemaster:setrequest", string -> {
			String[] parts = string.split("\t");
			UUID requestUuid = UUID.fromString(parts[0]);
			UUID uuid = UUID.fromString(parts[1]);
			String destinationServer = parts[2];
			UUID[] canCancel = Arrays.stream(parts).skip(3).map(UUID::fromString).toArray(UUID[]::new);

			MarriagePlayer marriagePlayer = plugin.getPlayerData(uuid);
			MarriagePlayer[] marriagePlayerCanCancel = Arrays.stream(canCancel).map(plugin::getPlayerData).toArray(MarriagePlayer[]::new);

			plugin.getCommandManager().registerAcceptPendingRequest(new ExternalAcceptRequest(requestUuid, marriagePlayer, destinationServer, marriagePlayerCanCancel));
		});

		MultiLib.onString(plugin, "marriagemaster:answerrequest", string -> {
			String[] parts = string.split("\t");
			UUID requestUUID = UUID.fromString(parts[0]);
			String destinationServer = parts[1];
			Action action = Action.valueOf(parts[2]);
			UUID extraPlayerUuid = UUID.fromString(parts[3]);
			AcceptPendingRequest request = requests.get(requestUUID).get();

			if (!MultiLib.getLocalServerName().equals(destinationServer)) return;

			if (request == null) {
				return;
			}

			MarriagePlayer player = request.getPlayerThatHasToAccept();
			switch (action) {
				case ACCEPT:
					request.accept(player);
					break;
				case DENY:
					request.deny(player);
					break;
				case CANCEL:
					request.cancel(plugin.getPlayerData(extraPlayerUuid));
					break;
				case DISCONNECT:
					request.disconnect(plugin.getPlayerData(extraPlayerUuid));
					break;
			}
		});
	}

	private static void answerRequest(ExternalAcceptRequest request, Action action, MarriagePlayer player) {
		List<String> args = new ArrayList<>();

		args.add(request.uuid.toString());
		args.add(request.server);
		args.add(action.name());
		args.add(player.getUUID().toString());

		MultiLib.notify("marriagemaster:answerrequest", String.join("\t", args));
	}

	public static void addRequest(AcceptPendingRequest request) {
		UUID requestUuid = request instanceof ExternalAcceptRequest ? ((ExternalAcceptRequest) request).uuid : UUID.randomUUID();
		requests.put(requestUuid, new WeakReference<>(request));

		if (request instanceof ExternalAcceptRequest) return;

		List<String> args = new ArrayList<>();

		args.add(requestUuid.toString());
		args.add(request.getPlayerThatHasToAccept().getUUID().toString());
		args.add(MultiLib.getLocalServerName());
		args.addAll(Arrays.stream(request.getPlayersThatCanCancel()).map(MarriagePlayer::getUUID).map(UUID::toString).collect(Collectors.toList()));

		MultiLib.notify("marriagemaster:setrequest", String.join("\t", args));
	}

	public static void closeRequest(AcceptPendingRequest request) {
		requests.entrySet().stream().filter(entry -> entry.getValue().get() == request).findFirst().ifPresent(entry -> {
			requests.remove(entry.getKey());

			if (!(request instanceof ExternalAcceptRequest)) MultiLib.notify("marriagemaster:closerequest", entry.getKey().toString());
		});
	}

	private final UUID uuid;
	private final String server;

	public ExternalAcceptRequest(@NotNull UUID uuid, @NotNull MarriagePlayer hasToAccept, @NotNull String server, @NotNull MarriagePlayer... canCancel)
	{
		super(hasToAccept, canCancel);
		this.uuid = uuid;
		this.server = server;
	}

	@Override
	public void onAccept()
	{
		answerRequest(this, Action.ACCEPT, this.getPlayerThatHasToAccept());
	}

	@Override
	public void onDeny()
	{
		answerRequest(this, Action.DENY, this.getPlayerThatHasToAccept());
	}

	@Override
	public void onCancel(@NotNull MarriagePlayer player)
	{
		answerRequest(this, Action.CANCEL, player);
	}

	@Override
	protected void onDisconnect(@NotNull MarriagePlayer player)
	{
		answerRequest(this, Action.DISCONNECT, player);
	}
}