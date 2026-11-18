package com.kierenboal.npcsnap.rendering;

import com.kierenboal.npcsnap.BillboardConstants;
import com.kierenboal.npcsnap.state.TextureCacheEntry;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.Texture;
import net.runelite.api.TextureProvider;

public final class BillboardTextureResolver
{
	public static final int SCENE_TEXTURES = -2;
	private static final TextureUvs DEFAULT_UVS = new TextureUvs(0f, 0f, 1f, 0f, 0f, 1f);
	private final Client client;
	private final Map<Integer, TextureCacheEntry> textureCache = new HashMap<>();
	private final float[] uvScratch = new float[6];

	public BillboardTextureResolver(Client client)
	{
		this.client = client;
	}

	public void clear()
	{
		textureCache.clear();
	}

	public void expire(long nowMillis)
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

	public int animatedTextureOffsetStateHash(Model model, int animatedTextureId, long nowMillis)
	{
		short[] faces = animatedTextureId == SCENE_TEXTURES && model != null ? model.getFaceTextures() : null;
		if (faces != null)
		{
			TextureProvider provider = client.getTextureProvider();
			Texture[] textures = provider == null ? null : provider.getTextures();
			if (textures != null)
			{
				for (short faceTexture : faces)
				{
					int id = Short.toUnsignedInt(faceTexture);
					if (id < textures.length && scrolls(textures[id])) return 1 + (client.getGameCycle() & 127);
				}
			}
		}
		return BillboardAnimatedTextures.offsetStateHash(animatedTextureId, nowMillis);
	}

	private static boolean scrolls(Texture texture)
	{
		return texture != null && texture.getAnimationDirection() > 0 && texture.getAnimationSpeed() > 0;
	}

	public TextureSample resolveTextureSample(int textureId, long nowMillis, int animatedTextureId)
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
		if (animatedTextureId == SCENE_TEXTURES)
		{
			// The GPU scrolls UVs in the shader. The software provider updates its pixels.
			if (scrolls(texture) && client.isGpu())
			{
				float shift = (client.getGameCycle() & 127) * texture.getAnimationSpeed() / 128f;
				int direction = texture.getAnimationDirection();
				uOffset = direction == 2 ? -shift : direction == 4 ? shift : 0f;
				vOffset = direction == 1 ? -shift : direction == 3 ? shift : 0f;
			}
		}
		else if (BillboardAnimatedTextures.isAnimatedTextureId(animatedTextureId))
		{
			uOffset = 0f;
			vOffset = BillboardAnimatedTextures.vOffset(nowMillis, texture);
		}
		int stateHash = 31 * textureId + Float.floatToIntBits(uOffset);
		stateHash = 31 * stateHash + Float.floatToIntBits(vOffset);
		return new TextureSample(entry, uOffset, vOffset, stateHash);
	}

	public TextureUvs computeTextureUvs(Model model, int face)
	{
		return computeTextureUvs(face, model.getVerticesX(), model.getVerticesY(), model.getVerticesZ(),
			model.getFaceIndices1(), model.getFaceIndices2(), model.getFaceIndices3(), model.getTextureFaces(),
			model.getTexIndices1(), model.getTexIndices2(), model.getTexIndices3());
	}

	public TextureUvs computeTextureUvs(int face, float[] x, float[] y, float[] z,
		int[] indices1, int[] indices2, int[] indices3, byte[] textureFaces,
		int[] texIndices1, int[] texIndices2, int[] texIndices3)
	{
		if (textureFaces == null || face >= textureFaces.length || textureFaces[face] == -1
			|| texIndices1 == null || texIndices2 == null || texIndices3 == null)
		{
			return DEFAULT_UVS;
		}

		int textureFace = textureFaces[face] & 0xFF;
		if (textureFace >= texIndices1.length || textureFace >= texIndices2.length || textureFace >= texIndices3.length)
		{
			return null;
		}

		int a = texIndices1[textureFace], b = texIndices2[textureFace], c = texIndices3[textureFace];
		int limit = Math.min(x.length, Math.min(y.length, z.length));
		if (Math.min(a, Math.min(b, c)) < 0 || Math.max(a, Math.max(b, c)) >= limit) return null;
		double bx = x[b] - x[a], by = y[b] - y[a], bz = z[b] - z[a];
		double cx = x[c] - x[a], cy = y[c] - y[a], cz = z[c] - z[a];
		double bb = bx * bx + by * by + bz * bz;
		double cc = cx * cx + cy * cy + cz * cz;
		double bc = bx * cx + by * cy + bz * cz;
		double determinant = bb * cc - bc * bc;
		if (!(determinant >= 1.0e-6)) return null;
		for (int i = 0; i < 3; i++)
		{
			int vertex = i == 0 ? indices1[face] : i == 1 ? indices2[face] : indices3[face];
			if (vertex < 0 || vertex >= limit) return null;
			double dx = x[vertex] - x[a], dy = y[vertex] - y[a], dz = z[vertex] - z[a];
			double alongB = dx * bx + dy * by + dz * bz;
			double alongC = dx * cx + dy * cy + dz * cz;
			uvScratch[2 * i] = (float) ((cc * alongB - bc * alongC) / determinant);
			uvScratch[2 * i + 1] = (float) ((bb * alongC - bc * alongB) / determinant);
		}
		return new TextureUvs(uvScratch[0], uvScratch[1], uvScratch[2], uvScratch[3], uvScratch[4], uvScratch[5]);
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
