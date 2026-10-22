package com.osrslive;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.Locale;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@PluginDescriptor(
	name = "Live Positie",
	description = "Geeft je positie door aan de kaart van OSRS Companion, alleen op deze computer",
	tags = {"map", "position", "kaart", "locatie"}
)
public class LivePositiePlugin extends Plugin
{
	private static final int PORT = 37780;

	@Inject
	private Client client;

	private PositionServer server;
	private String lastKey = "";
	private long lastSent;

	@Override
	protected void startUp()
	{
		server = new PositionServer(PORT);
		try
		{
			server.start();
		}
		catch (IOException e)
		{
			// Poort bezet (bijvoorbeeld door een andere plugin die dezelfde poort gebruikt)
			server = null;
		}
	}

	@Override
	protected void shutDown()
	{
		if (server != null)
		{
			server.stop();
			server = null;
		}
		lastKey = "";
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (server == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		Player p = client.getLocalPlayer();
		if (p == null)
		{
			return;
		}
		WorldPoint wp = p.getWorldLocation();
		if (wp == null)
		{
			return;
		}
		boolean instanced = client.isInInstancedRegion();
		String key = wp.getX() + "," + wp.getY() + "," + wp.getPlane() + "," + instanced;
		long now = System.currentTimeMillis();
		if (key.equals(lastKey) && now - lastSent < 5000)
		{
			return;
		}
		lastKey = key;
		lastSent = now;
		String json = String.format(Locale.ROOT,
			"{\"type\":\"position\",\"x\":%d,\"y\":%d,\"plane\":%d,\"regionId\":%d,\"world\":%d,\"instanced\":%b,\"timestamp\":%d}",
			wp.getX(), wp.getY(), wp.getPlane(), wp.getRegionID(), client.getWorld(), instanced, now);
		server.broadcast(json, true);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		if (server == null)
		{
			return;
		}
		GameState s = e.getGameState();
		if (s == GameState.LOGIN_SCREEN || s == GameState.HOPPING)
		{
			server.forget();
			server.broadcast("{\"type\":\"logout\"}", false);
			lastKey = "";
		}
	}
}
