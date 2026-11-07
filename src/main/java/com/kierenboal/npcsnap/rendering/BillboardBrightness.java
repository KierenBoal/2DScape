package com.kierenboal.npcsnap.rendering;

import com.kierenboal.npcsnap.NpcSnapConfig;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.TextureProvider;

public final class BillboardBrightness
{
	private static final double DARKEST = 0.9d;
	private static final double BRIGHTEST = 0.6d;

	private BillboardBrightness()
	{
	}

	public static int effectiveLightBoostPercent(Client client, int configuredPercent)
	{
		TextureProvider provider = client == null ? null : client.getTextureProvider();
		if (provider == null)
		{
			return configuredPercent;
		}
		double brightness = provider.getBrightness();
		if (!Double.isFinite(brightness))
		{
			return configuredPercent;
		}
		double position = Math.max(0.0d, Math.min(1.0d, (DARKEST - brightness) / (DARKEST - BRIGHTEST)));
		return (int) Math.round(configuredPercent * (1.0d + position));
	}

	/** The configured light boost that applies to this model's source. */
	public static int configuredLightBoostPercent(NpcSnapConfig config, Model model)
	{
		return model instanceof OwnedModel
			? config.retroNpcSwapperBrightness()
			: config.billboardLightBoostPercent();
	}

	/** Applies the selected setting and the current game-brightness adjustment to this model. */
	public static int effectiveLightBoostPercent(Client client, NpcSnapConfig config, Model model)
	{
		return effectiveLightBoostPercent(client, configuredLightBoostPercent(config, model));
	}
}
