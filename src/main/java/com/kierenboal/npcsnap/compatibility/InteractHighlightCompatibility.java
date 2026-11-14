package com.kierenboal.npcsnap.compatibility;

import java.util.Arrays;
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
	private boolean npcOutlinesReplaced;
	private boolean playerOutlinesReplaced;
	private boolean itemOutlinesReplaced;
	private boolean objectOutlinesReplaced;
	private boolean itemHoverEnabled = true;
	private boolean itemInteractEnabled = true;
	private boolean objectHoverEnabled = true;
	private boolean objectInteractEnabled = true;
	private boolean npcHoverEnabled = true;
	private boolean npcInteractEnabled = true;
	private boolean playerHoverEnabled = true;
	private boolean playerInteractEnabled = true;

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
			clearCachedReplacementState();
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

	public boolean replacesNpcOutlines()
	{
		return npcOutlinesReplaced;
	}

	public boolean replacesPlayerOutlines()
	{
		return playerOutlinesReplaced;
	}

	public boolean npcShowHover()
	{
		return npcHoverEnabled;
	}

	public boolean npcShowInteract()
	{
		return npcInteractEnabled;
	}

	public boolean playerShowHover()
	{
		return playerHoverEnabled;
	}

	public boolean playerShowInteract()
	{
		return playerInteractEnabled;
	}

	public boolean replacesItemOutlines() { return itemOutlinesReplaced; }
	public boolean replacesObjectOutlines() { return objectOutlinesReplaced; }
	public boolean itemShowHover() { return itemHoverEnabled; }
	public boolean itemShowInteract() { return itemInteractEnabled; }
	public boolean objectShowHover() { return objectHoverEnabled; }
	public boolean objectShowInteract() { return objectInteractEnabled; }

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
			clearCachedReplacementState();
		});
	}

	public void restore()
	{
		onClientThread(this::restoreOnClient);
	}

	private void synchronizeOnClient(boolean requested, boolean replaceNpcs, boolean replacePlayers, boolean replaceItems, boolean replaceObjects)
	{
		boolean takeoverActive = requested && isInteractHighlightActive();
		boolean replaceNpcOutlines = takeoverActive && replaceNpcs;
		boolean replacePlayerOutlines = takeoverActive && replacePlayers;
		boolean replaceItemOutlines = takeoverActive && replaceItems;
		boolean replaceObjectOutlines = takeoverActive && replaceObjects;
		boolean anyReplacement = replaceNpcOutlines || replacePlayerOutlines || replaceItemOutlines || replaceObjectOutlines;
		suppressing = anyReplacement;

		for (String key : KEYS)
		{
			boolean replaceKey = key.startsWith("npc") ? replaceNpcOutlines
				: key.startsWith("player") ? replacePlayerOutlines
				: key.startsWith("item") ? replaceItemOutlines : replaceObjectOutlines;
			if (isStashed(key) && !replaceKey)
			{
				unstash(key);
			}
		}
		for (String key : KEYS)
		{
			boolean replaceKey = key.startsWith("npc") ? replaceNpcOutlines
				: key.startsWith("player") ? replacePlayerOutlines
				: key.startsWith("item") ? replaceItemOutlines : replaceObjectOutlines;
			if (replaceKey)
			{
				if (!isStashed(key))
				{
					stash(key);
				}
				write(key, "false");
			}
		}
		npcOutlinesReplaced = replaceNpcOutlines;
		playerOutlinesReplaced = replacePlayerOutlines;
		itemOutlinesReplaced = replaceItemOutlines;
		objectOutlinesReplaced = replaceObjectOutlines;
		itemHoverEnabled = !replaceItemOutlines || wanted(ITEM_HOVER);
		itemInteractEnabled = !replaceItemOutlines || wanted(ITEM_INTERACT);
		objectHoverEnabled = !replaceObjectOutlines || wanted(OBJECT_HOVER);
		objectInteractEnabled = !replaceObjectOutlines || wanted(OBJECT_INTERACT);
		npcHoverEnabled = !replaceNpcOutlines || wanted(NPC_HOVER);
		npcInteractEnabled = !replaceNpcOutlines || wanted(NPC_INTERACT);
		playerHoverEnabled = !replacePlayerOutlines || wanted(PLAYER_HOVER);
		playerInteractEnabled = !replacePlayerOutlines || wanted(PLAYER_INTERACT);
	}

	private void restoreOnClient()
	{
		if (!suppressing)
		{
			return;
		}

		suppressing = false;
		clearCachedReplacementState();
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

	private void clearCachedReplacementState()
	{
		npcOutlinesReplaced = false;
		playerOutlinesReplaced = false;
		itemOutlinesReplaced = false;
		objectOutlinesReplaced = false;
		itemHoverEnabled = true;
		itemInteractEnabled = true;
		objectHoverEnabled = true;
		objectInteractEnabled = true;
		npcHoverEnabled = true;
		npcInteractEnabled = true;
		playerHoverEnabled = true;
		playerInteractEnabled = true;
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
