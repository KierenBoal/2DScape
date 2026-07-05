package com.kierenboal.npcsnap;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.Texture;
import net.runelite.api.TextureProvider;

final class BillboardTextureResolver
{
	private final Client client;
	private final NpcSnapConfig config;
	private final Map<Integer, TextureCacheEntry> textureCache = new HashMap<>();

	BillboardTextureResolver(Client client, NpcSnapConfig config)
	{
		this.client = client;
		this.config = config;
	}

	void clear()
	{
		textureCache.clear();
	}

	void expire(long nowMillis)
	{
		Iterator<Map.Entry<Integer, TextureCacheEntry>> iterator = textureCache.entrySet().iterator();
		while (iterator.hasNext())
		{
			TextureCacheEntry entry = iterator.next().getValue();
			if (nowMillis - entry.lastUsedMillis() > BillboardConstants.CACHE_TTL_MILLIS)
			{
				iterator.remove();
			}
		}
	}

	int animatedTextureOffsetStateHash(int animatedTextureId, long nowMillis)
	{
		return BillboardAnimatedTextures.offsetStateHash(
			animatedTextureId,
			nowMillis,
			config.enableAnimationFrameSnapping(),
			config.animationFrameCount()
		);
	}

	TextureSample resolveTextureSample(int textureId, long nowMillis, int animatedTextureId)
	{
		TextureProvider textureProvider = client.getTextureProvider();
		if (textureProvider == null)
		{
			return null;
		}

		Texture[] textures = textureProvider.getTextures();
		if (textures == null || textureId < 0 || textureId >= textures.length)
		{
			return null;
		}

		Texture texture = textures[textureId];
		int[] pixels = texture != null && texture.getPixels() != null ? texture.getPixels() : textureProvider.load(textureId);
		if (pixels == null || pixels.length == 0)
		{
			return null;
		}

		int dimension = (int) Math.round(Math.sqrt(pixels.length));
		if (dimension <= 0 || dimension * dimension != pixels.length)
		{
			return null;
		}

		TextureCacheEntry entry = textureCache.get(textureId);
		if (entry == null || entry.pixels != pixels || entry.width != dimension || entry.height != dimension)
		{
			entry = new TextureCacheEntry(pixels, dimension, dimension, nowMillis);
			textureCache.put(textureId, entry);
		}
		else
		{
			entry.touch(nowMillis);
		}

		float uOffset = texture != null ? normalizeTextureOffset(texture.getU(), entry.width) : 0f;
		float vOffset = texture != null ? normalizeTextureOffset(texture.getV(), entry.height) : 0f;
		if (BillboardAnimatedTextures.isAnimatedTextureId(animatedTextureId))
		{
			uOffset = 0f;
			vOffset = BillboardAnimatedTextures.vOffset(nowMillis, texture, config.enableAnimationFrameSnapping(), config.animationFrameCount());
		}
		int stateHash = 31 * textureId + Float.floatToIntBits(uOffset);
		stateHash = 31 * stateHash + Float.floatToIntBits(vOffset);
		return new TextureSample(entry, uOffset, vOffset, stateHash);
	}

	TextureUvs computeTextureUvs(Model model, int face)
	{
		float[] vertexX = model.getVerticesX();
		float[] vertexY = model.getVerticesY();
		float[] vertexZ = model.getVerticesZ();
		int[] indices1 = model.getFaceIndices1();
		int[] indices2 = model.getFaceIndices2();
		int[] indices3 = model.getFaceIndices3();
		byte[] textureFaces = model.getTextureFaces();
		int[] texIndices1 = model.getTexIndices1();
		int[] texIndices2 = model.getTexIndices2();
		int[] texIndices3 = model.getTexIndices3();

		if (textureFaces == null || face >= textureFaces.length || textureFaces[face] == -1
			|| texIndices1 == null || texIndices2 == null || texIndices3 == null)
		{
			return new TextureUvs(0f, 0f, 1f, 0f, 0f, 1f);
		}

		int triangleA = indices1[face];
		int triangleB = indices2[face];
		int triangleC = indices3[face];
		int textureFace = textureFaces[face] & 0xFF;
		if (textureFace >= texIndices1.length || textureFace >= texIndices2.length || textureFace >= texIndices3.length)
		{
			return null;
		}

		int texA = texIndices1[textureFace];
		int texB = texIndices2[textureFace];
		int texC = texIndices3[textureFace];

		float v1x = vertexX[texA];
		float v1y = vertexY[texA];
		float v1z = vertexZ[texA];
		float v2x = vertexX[texB] - v1x;
		float v2y = vertexY[texB] - v1y;
		float v2z = vertexZ[texB] - v1z;
		float v3x = vertexX[texC] - v1x;
		float v3y = vertexY[texC] - v1y;
		float v3z = vertexZ[texC] - v1z;

		float v4x = vertexX[triangleA] - v1x;
		float v4y = vertexY[triangleA] - v1y;
		float v4z = vertexZ[triangleA] - v1z;
		float v5x = vertexX[triangleB] - v1x;
		float v5y = vertexY[triangleB] - v1y;
		float v5z = vertexZ[triangleB] - v1z;
		float v6x = vertexX[triangleC] - v1x;
		float v6y = vertexY[triangleC] - v1y;
		float v6z = vertexZ[triangleC] - v1z;

		float v7x = v2y * v3z - v2z * v3y;
		float v7y = v2z * v3x - v2x * v3z;
		float v7z = v2x * v3y - v2y * v3x;

		float v8x = v3y * v7z - v3z * v7y;
		float v8y = v3z * v7x - v3x * v7z;
		float v8z = v3x * v7y - v3y * v7x;
		float denominator = v8x * v2x + v8y * v2y + v8z * v2z;
		if (Math.abs(denominator) < 1.0e-6f)
		{
			return null;
		}

		float factor = 1.0f / denominator;
		float u0 = (v8x * v4x + v8y * v4y + v8z * v4z) * factor;
		float u1 = (v8x * v5x + v8y * v5y + v8z * v5z) * factor;
		float u2 = (v8x * v6x + v8y * v6y + v8z * v6z) * factor;

		v8x = v2y * v7z - v2z * v7y;
		v8y = v2z * v7x - v2x * v7z;
		v8z = v2x * v7y - v2y * v7x;
		denominator = v8x * v3x + v8y * v3y + v8z * v3z;
		if (Math.abs(denominator) < 1.0e-6f)
		{
			return null;
		}

		factor = 1.0f / denominator;
		float v0 = (v8x * v4x + v8y * v4y + v8z * v4z) * factor;
		float v1 = (v8x * v5x + v8y * v5y + v8z * v5z) * factor;
		float v2 = (v8x * v6x + v8y * v6y + v8z * v6z) * factor;
		return new TextureUvs(u0, v0, u1, v1, u2, v2);
	}

	private static float normalizeTextureOffset(float offset, int dimension)
	{
		if (!Float.isFinite(offset))
		{
			return 0f;
		}

		return Math.abs(offset) > 1.0f && dimension > 0 ? offset / dimension : offset;
	}
}
