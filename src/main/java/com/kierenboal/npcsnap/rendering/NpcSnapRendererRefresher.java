package com.kierenboal.npcsnap.rendering;

import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.hooks.DrawCallbacks;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginInstantiationException;
import net.runelite.client.plugins.PluginManager;

@Slf4j
public final class NpcSnapRendererRefresher
{
	private final Client client;
	private final PluginManager pluginManager;
	private final AtomicBoolean refreshPending = new AtomicBoolean();

	public NpcSnapRendererRefresher(Client client, PluginManager pluginManager)
	{
		this.client = client;
		this.pluginManager = pluginManager;
	}

	public void requestRefresh()
	{
		if (!client.isGpu() || !refreshPending.compareAndSet(false, true))
		{
			return;
		}

		SwingUtilities.invokeLater(this::refreshActiveRenderer);
	}

	private void refreshActiveRenderer()
	{
		try
		{
			DrawCallbacks drawCallbacks = client.getDrawCallbacks();
			if (!(drawCallbacks instanceof Plugin))
			{
				log.debug("Active GPU draw callbacks are not directly refreshable; world textures will update on the next renderer reload");
				return;
			}

			Plugin renderer = (Plugin) drawCallbacks;
			if (!pluginManager.isPluginActive(renderer))
			{
				return;
			}

			if (pluginManager.stopPlugin(renderer))
			{
				pluginManager.startPlugin(renderer);
				log.debug("Refreshed active renderer after world texture changes");
			}
		}
		catch (PluginInstantiationException ex)
		{
			log.debug("Unable to refresh active renderer after world texture changes", ex);
		}
		finally
		{
			refreshPending.set(false);
		}
	}
}
