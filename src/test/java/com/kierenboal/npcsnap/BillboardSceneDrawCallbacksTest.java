package com.kierenboal.npcsnap;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.runelite.api.GameObject;
import net.runelite.api.ItemLayer;
import net.runelite.api.NPC;
import net.runelite.api.Renderable;
import net.runelite.api.TileObject;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BillboardSceneDrawCallbacksTest
{
	@Test
	public void disabledAndUiCallbacksPassThroughWithoutTracking()
	{
		FakeOverlay overlay = new FakeOverlay();
		Renderable renderable = TestProxies.proxy(Renderable.class);
		BillboardSceneDrawCallbacks disabled = callbacks(false, false, false, overlay);

		assertTrue(disabled.addEntity(renderable, false));
		assertTrue(disabled.draw(renderable, false));
		assertTrue(callbacks(true, false, false, overlay).draw(renderable, true));
		assertTrue(overlay.noted.isEmpty());
	}

	@Test
	public void addEntityAlwaysPreservesActorInteraction()
	{
		FakeOverlay overlay = new FakeOverlay();
		NPC npc = TestProxies.proxy(NPC.class);
		overlay.hiddenRenderables.add(npc);

		assertTrue(callbacks(true, false, false, overlay).addEntity(npc, false));
		assertTrue(overlay.noted.contains(npc));
	}

	@Test
	public void regularDrawAndNonActorEntityRespectRenderableSuppression()
	{
		FakeOverlay overlay = new FakeOverlay();
		Renderable renderable = TestProxies.proxy(Renderable.class);
		overlay.hiddenRenderables.add(renderable);
		BillboardSceneDrawCallbacks callbacks = callbacks(true, false, false, overlay);

		assertFalse(callbacks.addEntity(renderable, false));
		assertFalse(callbacks.draw(renderable, false));
		assertTrue(overlay.noted.contains(renderable));
	}

	@Test
	public void gameObjectIsObservedAndSuppressedAsAWhole()
	{
		FakeOverlay overlay = new FakeOverlay();
		Renderable part = TestProxies.proxy(Renderable.class);
		GameObject object = TestProxies.proxy(GameObject.class, TestProxies.method("getRenderable", part));
		overlay.hiddenTileObjects.add(object);

		assertFalse(callbacks(true, true, false, overlay).drawObject(object));
		assertTrue(overlay.observed.contains(object));
	}

	@Test
	public void gameObjectPartIsSuppressedWhenWholeObjectIsNotYetCached()
	{
		FakeOverlay overlay = new FakeOverlay();
		Renderable part = TestProxies.proxy(Renderable.class);
		GameObject object = TestProxies.proxy(GameObject.class, TestProxies.method("getRenderable", part));
		overlay.hiddenRenderables.add(part);

		assertFalse(callbacks(true, true, false, overlay).drawObject(object));
		assertTrue(overlay.observed.contains(object));
	}

	@Test
	public void itemLayerTracksAllPartsAndHidesIfAnyPartIsHidden()
	{
		FakeOverlay overlay = new FakeOverlay();
		Renderable bottom = TestProxies.proxy(Renderable.class);
		Renderable middle = TestProxies.proxy(Renderable.class);
		Renderable top = TestProxies.proxy(Renderable.class);
		ItemLayer layer = TestProxies.proxy(ItemLayer.class,
			TestProxies.method("getBottom", bottom),
			TestProxies.method("getMiddle", middle),
			TestProxies.method("getTop", top));
		overlay.hiddenRenderables.add(middle);

		assertFalse(callbacks(true, false, false, overlay).drawObject(layer));
		assertTrue(overlay.noted.contains(bottom));
		assertTrue(overlay.noted.contains(middle));
		assertTrue(overlay.noted.contains(top));
	}

	@Test
	public void objectObservationRequiresAnEnabledObjectCategory()
	{
		FakeOverlay overlay = new FakeOverlay();
		Renderable part = TestProxies.proxy(Renderable.class);
		GameObject object = TestProxies.proxy(GameObject.class, TestProxies.method("getRenderable", part));

		assertTrue(callbacks(true, false, false, overlay).drawObject(object));
		assertTrue(overlay.observed.isEmpty());
	}

	private static BillboardSceneDrawCallbacks callbacks(
		boolean enabled, boolean objects, boolean graphicsObjects, FakeOverlay overlay)
	{
		NpcSnapConfig config = TestProxies.proxy(NpcSnapConfig.class,
			TestProxies.method("enable2dBillboardSprites", enabled),
			TestProxies.method("applyToObjects", objects),
			TestProxies.method("applyToGraphicsObjects", graphicsObjects));
		return new BillboardSceneDrawCallbacks(config, overlay);
	}

	private static final class FakeOverlay implements BillboardSceneDrawCallbacks.OverlayAccess
	{
		private final Set<Renderable> noted = identitySet();
		private final Set<TileObject> observed = identitySet();
		private final Set<Renderable> hiddenRenderables = identitySet();
		private final Set<TileObject> hiddenTileObjects = identitySet();

		@Override
		public void noteSceneRenderable(Renderable renderable)
		{
			if (renderable != null)
			{
				noted.add(renderable);
			}
		}

		@Override
		public void observeTileObject(TileObject tileObject)
		{
			observed.add(tileObject);
		}

		@Override
		public boolean shouldHideRenderable(Renderable renderable)
		{
			return hiddenRenderables.contains(renderable);
		}

		@Override
		public boolean shouldHideTileObject(TileObject tileObject)
		{
			return hiddenTileObjects.contains(tileObject);
		}

		private static <T> Set<T> identitySet()
		{
			return Collections.newSetFromMap(new IdentityHashMap<>());
		}
	}
}
