package com.kierenboal.npcsnap;

import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Texture;
import net.runelite.api.TextureProvider;

@Slf4j
final class NpcSnapTextureBandingManager
{
	private final Client client;
	private final Map<Integer, int[]> originalTexturePixels = new HashMap<>();
	private boolean applied;
	private boolean pending = true;
	private int appliedTextureBands = -1;

	NpcSnapTextureBandingManager(Client client)
	{
		this.client = client;
	}

	void markDirty()
	{
		pending = true;
	}

	void sync(boolean enabled, int bands)
	{
		if (!pending && applied == enabled && (!enabled || appliedTextureBands == bands))
		{
			return;
		}

		restore();
		pending = false;

		if (enabled)
		{
			apply(bands);
		}
	}

	void restore()
	{
		if (!applied && originalTexturePixels.isEmpty())
		{
			return;
		}

		TextureProvider textureProvider = client.getTextureProvider();
		Texture[] textures = textureProvider != null ? textureProvider.getTextures() : null;
		for (Map.Entry<Integer, int[]> entry : originalTexturePixels.entrySet())
		{
			restoreTexturePixels(textureProvider, textures, entry.getKey(), entry.getValue());
		}

		originalTexturePixels.clear();
		applied = false;
		appliedTextureBands = -1;
		if (textureProvider != null)
		{
			resetTextureProviderCache(textureProvider);
		}
	}

	private void apply(int bands)
	{
		log.debug("applyGlobalTextureQuality");

		TextureProvider textureProvider = client.getTextureProvider();
		if (textureProvider == null)
		{
			pending = true;
			return;
		}

		Texture[] textures = textureProvider.getTextures();
		if (textures == null)
		{
			pending = true;
			return;
		}

		resetTextureProviderCache(textureProvider);
		int changed = 0;
		for (int textureId = 0; textureId < textures.length; textureId++)
		{
			int[] pixels = texturePixels(textureProvider, textures, textureId);
			if (pixels == null || pixels.length == 0)
			{
				log.debug("pixels null/empty for {}", textureId);
				continue;
			}

			originalTexturePixels.put(textureId, pixels.clone());
			NpcSnapColorBanding.applyBandsInPlace(pixels, bands);

			changed++;
		}

		applied = true;
		appliedTextureBands = bands;
		log.debug("Applied global texture banding to {} textures with {} bands", changed, bands);
	}

	private static int[] texturePixels(TextureProvider textureProvider, Texture[] textures, int textureId)
	{
		int[] pixels = textureProvider.load(textureId);
		Texture texture = textures[textureId];
		if (texture != null && texture.getPixels() != null)
		{
			pixels = texture.getPixels();
		}

		return pixels;
	}

	private static void restoreTexturePixels(
		TextureProvider textureProvider,
		Texture[] textures,
		int textureId,
		int[] originalPixels
	)
	{
		int[] pixels = null;
		if (textures != null && textureId >= 0 && textureId < textures.length)
		{
			Texture texture = textures[textureId];
			if (texture != null)
			{
				pixels = texture.getPixels();
			}
		}

		if (pixels == null && textureProvider != null)
		{
			pixels = textureProvider.load(textureId);
		}

		if (pixels != null && pixels.length == originalPixels.length)
		{
			System.arraycopy(originalPixels, 0, pixels, 0, originalPixels.length);
		}
	}

	private static void resetTextureProviderCache(TextureProvider textureProvider)
	{
		textureProvider.setBrightness(textureProvider.getBrightness());
	}
}
