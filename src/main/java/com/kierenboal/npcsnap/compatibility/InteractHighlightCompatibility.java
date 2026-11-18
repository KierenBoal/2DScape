package com.kierenboal.npcsnap.compatibility;

import java.awt.Color;
import java.util.Arrays;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.TileItem;
import net.runelite.client.plugins.interacthighlight.InteractHighlightConfig;
import net.runelite.client.util.ColorUtil;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.interacthighlight.InteractHighlightPlugin;

/** Temporarily hands enabled target outlines from Interaction Highlight to 2DScape. */
@Singleton
@Slf4j
public class InteractHighlightCompatibility
{
	private static final String GROUP = "interacthighlight";
	private static final String NPC_HOVER = "npcShowHover";
	private static final String NPC_INTERACT = "npcShowInteract";
	private static final String PLAYER_HOVER = "playerShowHover";
	private static final String PLAYER_INTERACT = "playerShowInteract";
	private static final String ITEM_HOVER = "itemShowHover";
	private static final String ITEM_INTERACT = "itemShowInteract";
	private static final String OBJECT_HOVER = "objectShowHover";
	private static final String OBJECT_INTERACT = "objectShowInteract";
	private static final String[] KEYS = {NPC_HOVER, NPC_INTERACT, PLAYER_HOVER, PLAYER_INTERACT,
		ITEM_HOVER, ITEM_INTERACT, OBJECT_HOVER, OBJECT_INTERACT};
	private static final String STASH_PREFIX = "interactHighlightOverrideStash_";
	private static final String STASH_UNSET = "unset";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ConfigManager configManager;

	@Inject
	private PluginManager pluginManager;

	private boolean suppressing;
	private boolean selfWrite;
	private final boolean[] enabled = new boolean[KEYS.length];
	private final Color[] colors = new Color[KEYS.length];
	private Color attackHover, attackInteract;
	private int width, feather;

	public int width() { return width; }
	public int feather() { return feather; }

	public void settingsChanged(ConfigChanged event)
	{
		if (!selfWrite && GROUP.equals(event.getGroup())) onClientThread(this::refresh);
	}

	InteractHighlightConfig settings() { return configManager.getConfig(InteractHighlightConfig.class); }

	private void refresh()
	{
		InteractHighlightConfig config = settings();
		for (int i = 0; i < KEYS.length; i++) enabled[i] = wanted(KEYS[i]);
		colors[0] = config.npcHoverHighlightColor();
		colors[1] = config.npcInteractHighlightColor();
		colors[2] = config.playerHoverHighlightColor();
		colors[3] = config.playerInteractHighlightColor();
		colors[4] = config.itemHoverHighlightColor();
		colors[5] = config.itemInteractHighlightColor();
		colors[6] = config.objectHoverHighlightColor();
		colors[7] = config.objectInteractHighlightColor();
		attackHover = config.npcAttackHoverHighlightColor();
		attackInteract = config.npcAttackHighlightColor();
		width = Math.max(0, Math.min(50, config.borderWidth()));
		feather = Math.max(0, Math.min(4, config.outlineFeather()));
	}

	public Color color(Object target, boolean interacting, boolean attack, int age)
	{
		if (target == null) return null;
		int index = target instanceof NPC ? 0 : target instanceof Player ? 2 : target instanceof TileItem ? 4 : 6;
		if (!enabled[interacting ? index + 1 : index]) return null;
		Color start = index == 0 && attack ? attackHover : colors[index];
		if (!interacting) return start;
		Color end = index == 0 && attack ? attackInteract : colors[index + 1];
		if (age < 0 || age >= 10) return end;
		return age < 5 ? ColorUtil.colorLerp(start, Color.WHITE, age / 5f)
			: ColorUtil.colorLerp(Color.WHITE, end, (age - 5) / 5f);
	}

	public boolean isInteractHighlightPlugin(Plugin plugin)
	{
		return plugin instanceof InteractHighlightPlugin;
	}

	public void synchronize(boolean requested, boolean replaceNpcs, boolean replacePlayers, boolean replaceItems, boolean replaceObjects)
	{
		onClientThread(() -> synchronizeOnClient(requested, replaceNpcs, replacePlayers, replaceItems, replaceObjects));
	}

	/** Restore leftovers from a client that stopped before it could shut down cleanly. */
	public void restoreStaleStash()
	{
		onClientThread(this::restoreStaleStashOnClient);
	}

	/** Switch config profiles without copying the previous profile's saved values. */
	public void onProfileChanged(boolean requested, boolean replaceNpcs, boolean replacePlayers, boolean replaceItems, boolean replaceObjects)
	{
		onClientThread(() ->
		{
			suppressing = false;
			restoreStaleStashOnClient();
			synchronizeOnClient(requested, replaceNpcs, replacePlayers, replaceItems, replaceObjects);
		});
	}

	public boolean isUserOverride(ConfigChanged event)
	{
		return !selfWrite
			&& suppressing
			&& GROUP.equals(event.getGroup())
			&& Arrays.asList(KEYS).contains(event.getKey())
			&& isStashed(event.getKey())
			&& "true".equalsIgnoreCase(event.getNewValue());
	}

	/** Keep the setting the user just enabled and restore the other suppressed settings. */
	public void optOut(String changedKey)
	{
		onClientThread(() ->
		{
			suppressing = false;
			discard(changedKey);
			for (String key : KEYS)
			{
				if (!key.equals(changedKey))
				{
					unstash(key);
				}
			}
		});
	}

	public void restore()
	{
		onClientThread(this::restoreOnClient);
	}

	private void synchronizeOnClient(boolean requested, boolean replaceNpcs, boolean replacePlayers, boolean replaceItems, boolean replaceObjects)
	{
		boolean takeoverActive = requested && isInteractHighlightActive();
		boolean[] replace = {replaceNpcs, replacePlayers, replaceItems, replaceObjects};
		suppressing = false;
		for (int i = 0; i < KEYS.length; i++)
		{
			String key = KEYS[i];
			if (takeoverActive && replace[i / 2])
			{
				suppressing = true;
				if (!isStashed(key)) stash(key);
				write(key, "false");
			}
			else unstash(key);
		}
		refresh();
	}

	private void restoreOnClient()
	{
		if (!suppressing)
		{
			return;
		}

		suppressing = false;
		for (String key : KEYS)
		{
			unstash(key);
		}
		log.debug("Restored Interaction Highlight outline settings");
	}

	private void restoreStaleStashOnClient()
	{
		boolean stale = false;
		for (String key : KEYS)
		{
			stale |= isStashed(key);
		}
		if (!stale)
		{
			return;
		}

		log.debug("Restoring Interaction Highlight settings left by a previous session");
		suppressing = true;
		restoreOnClient();
	}

	boolean isInteractHighlightActive()
	{
		for (Plugin plugin : pluginManager.getPlugins())
		{
			if (plugin instanceof InteractHighlightPlugin)
			{
				return pluginManager.isPluginActive(plugin);
			}
		}
		return false;
	}

	private boolean isStashed(String key)
	{
		return getConfiguration("npc-snap", STASH_PREFIX + key) != null;
	}

	private boolean wanted(String key)
	{
		String stashed = getConfiguration("npc-snap", STASH_PREFIX + key);
		if (stashed != null)
		{
			return STASH_UNSET.equals(stashed) || Boolean.parseBoolean(stashed);
		}

		String live = getConfiguration(GROUP, key);
		return live == null || Boolean.parseBoolean(live);
	}

	private void stash(String key)
	{
		String current = getConfiguration(GROUP, key);
		setConfiguration("npc-snap", STASH_PREFIX + key,
			current == null ? STASH_UNSET : current);
	}

	private void unstash(String key)
	{
		String stashed = getConfiguration("npc-snap", STASH_PREFIX + key);
		if (stashed == null)
		{
			return;
		}

		selfWrite = true;
		try
		{
			if (STASH_UNSET.equals(stashed))
			{
				unsetConfiguration(GROUP, key);
			}
			else
			{
				setConfiguration(GROUP, key, stashed);
			}
		}
		finally
		{
			selfWrite = false;
		}
		discard(key);
	}

	private void write(String key, String value)
	{
		selfWrite = true;
		try
		{
			setConfiguration(GROUP, key, value);
		}
		finally
		{
			selfWrite = false;
		}
	}

	private void discard(String key)
	{
		unsetConfiguration("npc-snap", STASH_PREFIX + key);
	}

	String getConfiguration(String group, String key)
	{
		return configManager.getConfiguration(group, key);
	}

	void setConfiguration(String group, String key, String value)
	{
		configManager.setConfiguration(group, key, value);
	}

	void unsetConfiguration(String group, String key)
	{
		configManager.unsetConfiguration(group, key);
	}

	void onClientThread(Runnable action)
	{
		if (client.isClientThread())
		{
			action.run();
		}
		else
		{
			clientThread.invoke(action);
		}
	}
}
