package com.kierenboal.npcsnap.rendering;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Client;
import net.runelite.api.NodeCache;
import net.runelite.api.SpritePixels;
import net.runelite.api.widgets.Widget;
import org.junit.Test;

public class NpcSnapUiTextureManagerTest
{
	@Test
	public void syncAppliesAndRestoresOverrides()
	{
		TestClientHarness harness = new TestClientHarness(widget(100, 5));
		NpcSnapUiTextureManager manager = new NpcSnapUiTextureManager(harness.client, spriteId ->
			new NpcSnapUiTextureManager.SpriteSnapshot(new int[] {0x00123456}, 1, 1, 1, 1, 0, 0));

		manager.sync(true, 4, 100.0d);

		assertEquals(1, harness.spriteOverrides.size());
		assertEquals(0, harness.widgetOverrides.size());
		assertEquals(1, manager.getAppliedSpriteOverrideCount());
		assertEquals(0, manager.getAppliedWidgetOverrideCount());
		assertEquals(1, harness.widgetCacheResets.get());

		manager.sync(false, 4, 100.0d);

		assertEquals(0, harness.spriteOverrides.size());
		assertEquals(0, harness.widgetOverrides.size());
		assertEquals(0, manager.getAppliedSpriteOverrideCount());
		assertEquals(0, manager.getAppliedWidgetOverrideCount());
		assertEquals(2, harness.widgetCacheResets.get());
	}

	@Test
	public void changingBandCountReprocessesFromOriginalPixels()
	{
		int[] originalPixels = {
			0x00102040,
			0x0090A0B0,
			0x00224466,
			0x00000000
		};
		TestClientHarness harness = new TestClientHarness(widget(200, 8));
		NpcSnapUiTextureManager manager = new NpcSnapUiTextureManager(harness.client, spriteId ->
			new NpcSnapUiTextureManager.SpriteSnapshot(originalPixels.clone(), 2, 2, 2, 2, 0, 0));

		manager.sync(true, 6, 100.0d);
		manager.markDirty();
		manager.sync(true, 2, 100.0d);

		SpritePixels spritePixels = harness.spriteOverrides.get(8);
		assertArrayEquals(NpcSnapColorBanding.bandSpritePixels(originalPixels, 2), spritePixels.getPixels());
	}

	@Test
	public void syncDiscoversNewStateSpriteIdsWithoutDirtyMark()
	{
		AtomicInteger spriteId = new AtomicInteger(5);
		TestClientHarness harness = new TestClientHarness(widget(250, spriteId));
		NpcSnapUiTextureManager manager = new NpcSnapUiTextureManager(harness.client, id ->
			new NpcSnapUiTextureManager.SpriteSnapshot(new int[] {id}, 1, 1, 1, 1, 0, 0));

		manager.sync(true, 4, 100.0d);
		spriteId.set(6);
		manager.sync(true, 4, 100.0d);
		manager.sync(true, 4, 100.0d);

		assertEquals(2, harness.spriteOverrides.size());
		assertNotNull(harness.spriteOverrides.get(5));
		assertNotNull(harness.spriteOverrides.get(6));
		assertEquals(0, harness.widgetOverrides.size());
		assertEquals(2, manager.getAppliedSpriteOverrideCount());
		assertEquals(0, manager.getAppliedWidgetOverrideCount());
		assertEquals(2, harness.widgetCacheResets.get());
	}

	@Test
	public void widgetLoadedAppliesOverridesWithoutPriorSync()
	{
		TestClientHarness harness = new TestClientHarness(widget(300, 9));
		NpcSnapUiTextureManager manager = new NpcSnapUiTextureManager(harness.client, spriteId ->
			new NpcSnapUiTextureManager.SpriteSnapshot(new int[] {0x00123456}, 1, 1, 1, 1, 0, 0));

		manager.onWidgetLoaded(true, 4, 100.0d);

		assertEquals(1, harness.spriteOverrides.size());
		assertEquals(0, harness.widgetOverrides.size());
		assertEquals(1, manager.getAppliedSpriteOverrideCount());
		assertEquals(0, manager.getAppliedWidgetOverrideCount());
		assertEquals(1, harness.widgetCacheResets.get());
	}

	@Test
	public void restoreOnlyRemovesAppliedSpriteOverrides()
	{
		TestClientHarness harness = new TestClientHarness(widget(350, 5));
		harness.spriteOverrides.put(99, spritePixels(new int[] {0x00000099}, 1, 1));
		NpcSnapUiTextureManager manager = new NpcSnapUiTextureManager(harness.client, spriteId ->
			new NpcSnapUiTextureManager.SpriteSnapshot(new int[] {0x00123456}, 1, 1, 1, 1, 0, 0));

		manager.sync(true, 4, 100.0d);
		manager.restore();

		assertEquals(1, harness.spriteOverrides.size());
		assertNotNull(harness.spriteOverrides.get(99));
		assertEquals(0, harness.widgetOverrides.size());
		assertEquals(0, manager.getAppliedSpriteOverrideCount());
		assertEquals(0, manager.getAppliedWidgetOverrideCount());
	}

	@Test
	public void spriteSnapshotPreservesSpriteBoundsMetadata()
	{
		SpritePixels spritePixels = spritePixels(new int[] {0xFF010203}, 1, 1);
		spritePixels.setMaxWidth(12);
		spritePixels.setMaxHeight(16);
		spritePixels.setOffsetX(3);
		spritePixels.setOffsetY(5);

		NpcSnapUiTextureManager.SpriteSnapshot snapshot = NpcSnapUiTextureManager.SpriteSnapshot.of(spritePixels);

		assertNotNull(snapshot);
		assertEquals(12, snapshot.getMaxWidth());
		assertEquals(16, snapshot.getMaxHeight());
		assertEquals(3, snapshot.getOffsetX());
		assertEquals(5, snapshot.getOffsetY());
	}

	@Test
	public void spriteSnapshotBuildsPaddedCanvasFromOffsets()
	{
		NpcSnapUiTextureManager.SpriteSnapshot snapshot = new NpcSnapUiTextureManager.SpriteSnapshot(
			new int[] {
				0x00010203, 0x00040506,
				0x00070809, 0x000A0B0C
			},
			2,
			2,
			5,
			4,
			1,
			1
		);

		int[] canvas = snapshot.toCanvasPixels();

		assertEquals(20, canvas.length);
		assertEquals(0, canvas[0]);
		assertEquals(0x00010203, canvas[6]);
		assertEquals(0x00040506, canvas[7]);
		assertEquals(0x00070809, canvas[11]);
		assertEquals(0x000A0B0C, canvas[12]);
	}

	@Test
	public void spriteSnapshotResamplesInsideOriginalFootprint()
	{
		NpcSnapUiTextureManager.SpriteSnapshot snapshot = new NpcSnapUiTextureManager.SpriteSnapshot(
			new int[] {
				0x00010203, 0x00040506,
				0x00070809, 0x000A0B0C
			},
			2,
			2,
			6,
			6,
			2,
			2
		);

		int[] canvas = snapshot.toResampledCanvasPixels(50.0d);

		assertEquals(36, canvas.length);
		assertEquals(0x00010203, canvas[(2 * 6) + 2]);
		assertEquals(0x00010203, canvas[(2 * 6) + 3]);
		assertEquals(0x00010203, canvas[(3 * 6) + 2]);
		assertEquals(0x00010203, canvas[(3 * 6) + 3]);
	}

	private static Widget widget(int id, int spriteId, Widget... children)
	{
		return widget(id, new AtomicInteger(spriteId), children);
	}

	private static Widget widget(int id, AtomicInteger spriteId, Widget... children)
	{
		return (Widget) Proxy.newProxyInstance(
			Widget.class.getClassLoader(),
			new Class<?>[] {Widget.class},
			(proxy, method, args) ->
			{
				switch (method.getName())
				{
					case "getId":
						return id;
					case "getSpriteId":
						return spriteId.get();
					case "getChildren":
						return children;
					case "getDynamicChildren":
					case "getStaticChildren":
					case "getNestedChildren":
						return null;
					default:
						return defaultValue(method.getReturnType());
				}
			});
	}

	private static SpritePixels spritePixels(int[] pixels, int width, int height)
	{
		int[] storedPixels = pixels;
		AtomicInteger maxWidth = new AtomicInteger(width);
		AtomicInteger maxHeight = new AtomicInteger(height);
		AtomicInteger offsetX = new AtomicInteger();
		AtomicInteger offsetY = new AtomicInteger();
		return (SpritePixels) Proxy.newProxyInstance(
			SpritePixels.class.getClassLoader(),
			new Class<?>[] {SpritePixels.class},
			(proxy, method, args) ->
			{
				switch (method.getName())
				{
					case "getPixels":
						return storedPixels;
					case "getWidth":
						return width;
					case "getHeight":
						return height;
					case "getMaxWidth":
						return maxWidth.get();
					case "getMaxHeight":
						return maxHeight.get();
					case "getOffsetX":
						return offsetX.get();
					case "getOffsetY":
						return offsetY.get();
					case "setMaxWidth":
						maxWidth.set((Integer) args[0]);
						return null;
					case "setMaxHeight":
						maxHeight.set((Integer) args[0]);
						return null;
					case "setOffsetX":
						offsetX.set((Integer) args[0]);
						return null;
					case "setOffsetY":
						offsetY.set((Integer) args[0]);
						return null;
					default:
						return defaultValue(method.getReturnType());
				}
			});
	}

	private static Object defaultValue(Class<?> returnType)
	{
		if (!returnType.isPrimitive())
		{
			return null;
		}

		if (boolean.class.equals(returnType))
		{
			return false;
		}

		if (char.class.equals(returnType))
		{
			return '\0';
		}

		return 0;
	}

	private static final class TestClientHarness
	{
		private final Map<Integer, SpritePixels> spriteOverrides = new HashMap<>();
		private final Map<Integer, SpritePixels> widgetOverrides = new HashMap<>();
		private final AtomicInteger widgetCacheResets = new AtomicInteger();
		private final Client client;

		private TestClientHarness(Widget... widgetRoots)
		{
			NodeCache widgetSpriteCache = (NodeCache) Proxy.newProxyInstance(
				NodeCache.class.getClassLoader(),
				new Class<?>[] {NodeCache.class},
				(proxy, method, args) ->
				{
					if ("reset".equals(method.getName()))
					{
						widgetCacheResets.incrementAndGet();
					}

					return null;
				});

			InvocationHandler handler = (proxy, method, args) ->
			{
				switch (method.getName())
				{
					case "getWidgetRoots":
						return widgetRoots;
					case "getSpriteOverrides":
						return spriteOverrides;
					case "getWidgetSpriteOverrides":
						return widgetOverrides;
					case "getWidgetSpriteCache":
						return widgetSpriteCache;
					case "createSpritePixels":
						return spritePixels((int[]) args[0], (Integer) args[1], (Integer) args[2]);
					default:
						return defaultValue(method.getReturnType());
				}
			};

			client = (Client) Proxy.newProxyInstance(
				Client.class.getClassLoader(),
				new Class<?>[] {Client.class},
				handler);
		}
	}
}
