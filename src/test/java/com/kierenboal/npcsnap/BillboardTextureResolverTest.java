package com.kierenboal.npcsnap;

import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.Texture;
import net.runelite.api.TextureProvider;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class BillboardTextureResolverTest
{
	@Test
	public void rejectsMissingInvalidAndNonSquareTextures()
	{
		assertNull(resolver(null).resolveTextureSample(0, 1L, -1));
		assertNull(resolver(provider(new Texture[0])).resolveTextureSample(0, 1L, -1));
		assertNull(resolver(provider(new Texture[] { texture(new int[] {1, 2, 3}, 0f, 0f) }))
			.resolveTextureSample(0, 1L, -1));
	}

	@Test
	public void loadsPixelsAndNormalizesPixelSizedOffsets()
	{
		int[] pixels = {1, 2, 3, 4};
		Texture texture = texture(pixels, 1f, 2f);
		TextureSample sample = resolver(provider(new Texture[] {texture})).resolveTextureSample(0, 10L, -1);

		assertNotNull(sample);
		assertSame(pixels, sample.entry.pixels);
		assertEquals(2, sample.entry.width);
		assertEquals(1f, sample.uOffset, 0f);
		assertEquals(1f, sample.vOffset, 0f);
	}

	@Test
	public void reusesCachedEntryForTheSamePixelArray()
	{
		int[] pixels = {1, 2, 3, 4};
		BillboardTextureResolver resolver = resolver(provider(new Texture[] {texture(pixels, 0f, 0f)}));

		TextureSample first = resolver.resolveTextureSample(0, 10L, -1);
		TextureSample second = resolver.resolveTextureSample(0, 20L, -1);

		assertSame(first.entry, second.entry);
		assertEquals(20L, second.entry.lastUsedMillis());
	}

	@Test
	public void computesDefaultAndMappedUvs()
	{
		BillboardTextureResolver resolver = resolver(null);
		Model defaultModel = TestProxies.proxy(Model.class,
			TestProxies.method("getTextureFaces", null));
		TextureUvs defaults = resolver.computeTextureUvs(defaultModel, 0);
		assertEquals(0f, defaults.u0, 0f);
		assertEquals(1f, defaults.u1, 0f);
		assertEquals(1f, defaults.v2, 0f);

		Model model = model(
			new float[] {0, 1, 0}, new float[] {0, 0, 1}, new float[] {0, 0, 0},
			new int[] {0}, new int[] {1}, new int[] {2},
			new byte[] {0}, new int[] {0}, new int[] {1}, new int[] {2});
		TextureUvs mapped = resolver.computeTextureUvs(model, 0);
		assertNotNull(mapped);
		assertEquals(0f, mapped.u0, 0.0001f);
		assertEquals(1f, mapped.u1, 0.0001f);
		assertEquals(1f, mapped.v2, 0.0001f);
	}

	private static BillboardTextureResolver resolver(TextureProvider provider)
	{
		Client client = TestProxies.proxy(Client.class, TestProxies.method("getTextureProvider", provider));
		NpcSnapConfig config = TestProxies.proxy(NpcSnapConfig.class);
		return new BillboardTextureResolver(client, config);
	}

	private static TextureProvider provider(Texture[] textures)
	{
		return TestProxies.proxy(TextureProvider.class, TestProxies.method("getTextures", textures));
	}

	private static Texture texture(int[] pixels, float u, float v)
	{
		return TestProxies.proxy(Texture.class,
			TestProxies.method("getPixels", pixels),
			TestProxies.method("getU", u),
			TestProxies.method("getV", v));
	}

	private static Model model(
		float[] x, float[] y, float[] z,
		int[] face1, int[] face2, int[] face3,
		byte[] textureFaces, int[] texture1, int[] texture2, int[] texture3)
	{
		return TestProxies.proxy(Model.class,
			TestProxies.method("getVerticesX", x), TestProxies.method("getVerticesY", y), TestProxies.method("getVerticesZ", z),
			TestProxies.method("getFaceIndices1", face1), TestProxies.method("getFaceIndices2", face2), TestProxies.method("getFaceIndices3", face3),
			TestProxies.method("getTextureFaces", textureFaces),
			TestProxies.method("getTexIndices1", texture1), TestProxies.method("getTexIndices2", texture2), TestProxies.method("getTexIndices3", texture3));
	}
}
