package com.kierenboal.npcsnap.rendering;

import com.kierenboal.npcsnap.TestProxies;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import net.runelite.api.Client;
import net.runelite.api.Texture;
import net.runelite.api.TextureProvider;
import org.junit.Assert;
import org.junit.Test;

import static com.kierenboal.npcsnap.TestProxies.method;
import static com.kierenboal.npcsnap.TestProxies.proxy;

public class NpcSnapTextureBandingManagerTest
{
	@Test
	public void restorePutsOriginalPixelsBackAfterBanding()
	{
		int[] pixels = new int[] { 0xFF123456, 0xFFABCDEF };
		TextureProvider textureProvider = proxy(
			TextureProvider.class,
			method("getTextures", new Texture[1]),
			method("load", pixels),
			method("getBrightness", 0.8d)
		);
		Client client = proxy(Client.class, method("getTextureProvider", textureProvider));
		NpcSnapTextureBandingManager manager = new NpcSnapTextureBandingManager(client);

		manager.sync(true, 2, 100.0d);
		Assert.assertNotEquals(0xFF123456, pixels[0]);

		manager.restore();
		Assert.assertArrayEquals(new int[] { 0xFF123456, 0xFFABCDEF }, pixels);
	}

	@Test
	public void pendingApplyRetriesWhenTextureProviderBecomesAvailable()
	{
		int[] pixels = new int[] { 0xFF123456, 0xFFABCDEF };
		TextureProvider textureProvider = proxy(
			TextureProvider.class,
			method("getTextures", new Texture[1]),
			method("load", pixels),
			method("getBrightness", 0.8d)
		);
		MutableTextureClient client = new MutableTextureClient();
		NpcSnapTextureBandingManager manager = new NpcSnapTextureBandingManager(client.proxy());

		manager.sync(true, 2, 100.0d);
		Assert.assertArrayEquals(new int[] { 0xFF123456, 0xFFABCDEF }, pixels);

		client.textureProvider = textureProvider;
		manager.sync(true, 2, 100.0d);

		Assert.assertNotEquals(0xFF123456, pixels[0]);
	}

	@Test
	public void disablingBandingRestoresTextureBackedPixels()
	{
		int[] pixels = new int[] { 0xFF123456, 0xFFABCDEF };
		Texture texture = proxy(Texture.class, method("getPixels", pixels));
		TextureProvider textureProvider = proxy(
			TextureProvider.class,
			method("getTextures", new Texture[] { texture }),
			method("load", new int[] { 0xFF000000, 0xFFFFFFFF }),
			method("getBrightness", 0.8d)
		);
		Client client = proxy(Client.class, method("getTextureProvider", textureProvider));
		NpcSnapTextureBandingManager manager = new NpcSnapTextureBandingManager(client);

		manager.sync(true, 2, 100.0d);
		Assert.assertNotEquals(0xFF123456, pixels[0]);

		manager.sync(false, 2, 100.0d);
		Assert.assertArrayEquals(new int[] { 0xFF123456, 0xFFABCDEF }, pixels);
	}

	@Test
	public void textureQualityDownscalesThenRestoresOriginalDimensions()
	{
		int[] pixels = new int[] {
			1, 2, 3, 4,
			5, 6, 7, 8,
			9, 10, 11, 12,
			13, 14, 15, 16
		};

		int[] resampled = NpcSnapTextureBandingManager.resampleTexturePixels(pixels, 50.0d);

		Assert.assertEquals(pixels.length, resampled.length);
		Assert.assertArrayEquals(new int[] {
			1, 1, 3, 3,
			1, 1, 3, 3,
			9, 9, 11, 11,
			9, 9, 11, 11
		}, resampled);
	}

	@Test
	public void fullTextureQualityPreservesPixels()
	{
		int[] pixels = new int[] {1, 2, 3, 4};

		Assert.assertArrayEquals(pixels, NpcSnapTextureBandingManager.resampleTexturePixels(pixels, 100.0d));
	}

	@Test
	public void settingChangesRebuildFromOriginalPixels()
	{
		int[] pixels = new int[] {
			0xFF101010, 0xFF202020,
			0xFF303030, 0xFF404040
		};
		int[] original = pixels.clone();
		TextureProvider textureProvider = proxy(
			TextureProvider.class,
			method("getTextures", new Texture[1]),
			method("load", pixels),
			method("getBrightness", 0.8d)
		);
		Client client = proxy(Client.class, method("getTextureProvider", textureProvider));
		NpcSnapTextureBandingManager manager = new NpcSnapTextureBandingManager(client);

		manager.sync(true, 2, 50.0d);
		manager.sync(true, 64, 100.0d);
		manager.sync(false, 64, 100.0d);

		Assert.assertArrayEquals(original, pixels);
	}

	@Test
	public void recreatedProviderRestoresOldProviderAndAppliesToNewProvider()
	{
		int[] firstPixels = new int[] {0xFF123456, 0xFFABCDEF};
		int[] secondPixels = new int[] {0xFF234567, 0xFFBCDEF0};
		int[] firstOriginal = firstPixels.clone();
		TextureProvider firstProvider = provider(firstPixels);
		TextureProvider secondProvider = provider(secondPixels);
		MutableTextureClient client = new MutableTextureClient();
		client.textureProvider = firstProvider;
		NpcSnapTextureBandingManager manager = new NpcSnapTextureBandingManager(client.proxy());

		manager.sync(true, 2, 100.0d);
		client.textureProvider = secondProvider;
		manager.sync(true, 2, 100.0d);

		Assert.assertArrayEquals(firstOriginal, firstPixels);
		Assert.assertNotEquals(0xFF234567, secondPixels[0]);
	}

	private static TextureProvider provider(int[] pixels)
	{
		return proxy(
			TextureProvider.class,
			method("getTextures", new Texture[1]),
			method("load", pixels),
			method("getBrightness", 0.8d)
		);
	}

	private static final class MutableTextureClient implements InvocationHandler
	{
		private TextureProvider textureProvider;

		private Client proxy()
		{
			return (Client) Proxy.newProxyInstance(
				Client.class.getClassLoader(),
				new Class<?>[] { Client.class },
				this
			);
		}

		@Override
		public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args)
		{
			if ("getTextureProvider".equals(method.getName()))
			{
				return textureProvider;
			}

			return null;
		}
	}
}
