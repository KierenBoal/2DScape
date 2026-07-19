package com.kierenboal.npcsnap.rendering;

import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Texture;
import net.runelite.api.TextureProvider;

@Slf4j
public final class NpcSnapTextureBandingManager
{
	private final Client client;
	private final Map<Integer, int[]> originalTexturePixels = new HashMap<>();
	private boolean applied;
	private boolean pending = true;
	private int appliedTextureBands = -1;
	private double appliedTextureQuality = -1.0d;
	private TextureProvider appliedTextureProvider;

	public NpcSnapTextureBandingManager(Client client)
	{
		this.client = client;
	}

	public void markDirty()
	{
		pending = true;
	}

	public boolean sync(boolean enabled, int bands, double quality)
	{
		TextureProvider currentProvider = client.getTextureProvider();
		if (appliedTextureProvider != null && currentProvider != appliedTextureProvider)
		{
			pending = true;
		}

		if (!pending && applied == enabled && (!enabled
			|| (appliedTextureBands == bands && Double.compare(appliedTextureQuality, quality) == 0)))
		{
			return false;
		}

		boolean changed = restore();
		pending = false;

		if (enabled)
		{
			changed |= apply(bands, quality);
		}

		return changed;
	}

	public boolean restore()
	{
		if (!applied && originalTexturePixels.isEmpty())
		{
			return false;
		}

		TextureProvider textureProvider = appliedTextureProvider;
		if (textureProvider != null)
		{
			Texture[] textures = textureProvider.getTextures();
			for (Map.Entry<Integer, int[]> entry : originalTexturePixels.entrySet())
			{
				restoreTexturePixels(textureProvider, textures, entry.getKey(), entry.getValue());
			}
		}

		boolean changed = applied || !originalTexturePixels.isEmpty();
		originalTexturePixels.clear();
		applied = false;
		appliedTextureBands = -1;
		appliedTextureQuality = -1.0d;
		appliedTextureProvider = null;
		if (textureProvider != null)
		{
			resetTextureProviderCache(textureProvider);
		}
		return changed;
	}

	private boolean apply(int bands, double quality)
	{
		log.debug("applyGlobalTextureQuality");

		TextureProvider textureProvider = client.getTextureProvider();
		if (textureProvider == null)
		{
			pending = true;
			return false;
		}

		Texture[] textures = textureProvider.getTextures();
		if (textures == null)
		{
			pending = true;
			return false;
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

			int[] original = pixels.clone();
			int[] transformed = resampleTexturePixels(original, quality);
			NpcSnapColorBanding.applyBandsInPlace(transformed, bands);
			originalTexturePixels.put(textureId, original);
			System.arraycopy(transformed, 0, pixels, 0, pixels.length);

			changed++;
		}

		applied = true;
		appliedTextureBands = bands;
		appliedTextureQuality = quality;
		appliedTextureProvider = textureProvider;
		log.debug("Applied global texture reduction to {} textures with {} bands at {}% quality", changed, bands, quality);
		return changed > 0;
	}

	static int[] resampleTexturePixels(int[] pixels, double quality)
	{
		if (pixels == null || pixels.length == 0)
		{
			return new int[0];
		}

		int size = (int) Math.round(Math.sqrt(pixels.length));
		if (size * size != pixels.length)
		{
			return pixels.clone();
		}

		double clampedQuality = Math.max(1.0d, Math.min(100.0d, quality));
		if (clampedQuality >= 99.999d)
		{
			return pixels.clone();
		}

		int reducedSize = Math.max(1, Math.min(size, (int) Math.round(size * clampedQuality / 100.0d)));
		int[] reduced = new int[reducedSize * reducedSize];
		for (int y = 0; y < reducedSize; y++)
		{
			int sourceY = Math.min(size - 1, (int) Math.floor(y * (double) size / reducedSize));
			for (int x = 0; x < reducedSize; x++)
			{
				int sourceX = Math.min(size - 1, (int) Math.floor(x * (double) size / reducedSize));
				reduced[(y * reducedSize) + x] = pixels[(sourceY * size) + sourceX];
			}
		}

		int[] resampled = new int[pixels.length];
		for (int y = 0; y < size; y++)
		{
			int reducedY = Math.min(reducedSize - 1, (int) Math.floor(y * (double) reducedSize / size));
			for (int x = 0; x < size; x++)
			{
				int reducedX = Math.min(reducedSize - 1, (int) Math.floor(x * (double) reducedSize / size));
				resampled[(y * size) + x] = reduced[(reducedY * reducedSize) + reducedX];
			}
		}

		return resampled;
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
