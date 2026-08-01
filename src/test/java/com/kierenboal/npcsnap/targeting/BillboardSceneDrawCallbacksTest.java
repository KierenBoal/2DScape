package com.kierenboal.npcsnap.targeting;

import com.kierenboal.npcsnap.NpcSnapConfig;
import com.kierenboal.npcsnap.TestProxies;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.runelite.api.GameObject;
import net.runelite.api.ItemLayer;
import net.runelite.api.NPC;
import net.runelite.api.Renderable;
import net.runelite.api.Scene;
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
	public void uiCallbacksOnlySuppressActorsEligibleForOverheadReplacement()
	{
		FakeOverlay overlay = new FakeOverlay();
		NPC npc = TestProxies.proxy(NPC.class);

		assertTrue(callbacks(true, false, false, overlay).addEntity(npc, true));
		overlay.hiddenActor2d.add(npc);
		assertFalse(callbacks(true, false, false, overlay).addEntity(npc, true));
		assertFalse(callbacks(true, false, false, overlay).draw(npc, true));
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
	public void addEntityPreservesNestedSceneInteractionWhileDrawSuppressesItsPixels()
	{
		FakeOverlay overlay = new FakeOverlay();
		Scene scene = TestProxies.proxy(Scene.class);
		overlay.hiddenRenderables.add(scene);
		BillboardSceneDrawCallbacks callbacks = callbacks(true, false, false, overlay);

		assertTrue(callbacks.addEntity(scene, false));
		assertFalse(callbacks.draw(scene, false));
		assertTrue(overlay.noted.contains(scene));
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
	public void boatPartKeepsInteractionTraversalButSuppressesFinalDraw()
	{
		FakeOverlay overlay = new FakeOverlay();
		Renderable part = TestProxies.proxy(Renderable.class);
		GameObject object = TestProxies.proxy(GameObject.class, TestProxies.method("getRenderable", part));
		overlay.hiddenRenderables.add(part);
		overlay.interactionRenderables.add(part);
		BillboardSceneDrawCallbacks callbacks = callbacks(true, false, false, overlay);

		assertTrue(callbacks.drawObject(object));
		assertTrue(callbacks.addEntity(part, false));
		assertFalse(callbacks.draw(part, false));
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
		assertTrue(overlay.noted.contains(part));
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
		assertTrue(overlay.noted.contains(part));
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
		private final Set<Renderable> hiddenActor2d = identitySet();
		private final Set<Renderable> interactionRenderables = identitySet();

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
		public boolean shouldKeepRenderableInteraction(Renderable renderable)
		{
			return interactionRenderables.contains(renderable);
		}

		@Override
		public boolean shouldHideActor2d(Renderable renderable)
		{
			return hiddenActor2d.contains(renderable);
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
