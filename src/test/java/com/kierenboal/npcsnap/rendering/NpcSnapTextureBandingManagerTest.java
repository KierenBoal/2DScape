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

		manager.sync(true, 2);
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

		manager.sync(true, 2);
		Assert.assertArrayEquals(new int[] { 0xFF123456, 0xFFABCDEF }, pixels);

		client.textureProvider = textureProvider;
		manager.sync(true, 2);

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

		manager.sync(true, 2);
		Assert.assertNotEquals(0xFF123456, pixels[0]);

		manager.sync(false, 2);
		Assert.assertArrayEquals(new int[] { 0xFF123456, 0xFFABCDEF }, pixels);
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
