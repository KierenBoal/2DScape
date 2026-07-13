package com.kierenboal.npcsnap.state;

import com.kierenboal.npcsnap.TestProxies;

import net.runelite.api.Renderable;
import net.runelite.api.TileObject;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BillboardVisibilityStateTest
{
	@Test
	public void clientThreadReadsLiveSelections()
	{
		BillboardVisibilityState state = new BillboardVisibilityState();
		Renderable active = TestProxies.proxy(Renderable.class);
		Renderable suppressed = TestProxies.proxy(Renderable.class);
		state.activeBillboards.add(active);
		state.suppressedRenderables.add(suppressed);

		assertTrue(state.shouldHideRenderable(active, true));
		assertTrue(state.shouldHideRenderable(suppressed, true));
		assertFalse(state.shouldHideRenderable(active, false));
	}

	@Test
	public void publishedSnapshotsAreStableUntilRepublished()
	{
		BillboardVisibilityState state = new BillboardVisibilityState();
		Renderable first = TestProxies.proxy(Renderable.class);
		Renderable later = TestProxies.proxy(Renderable.class);
		state.activeBillboards.add(first);
		state.publishSnapshots();
		state.activeBillboards.remove(first);
		state.activeBillboards.add(later);

		assertTrue(state.shouldHideRenderable(first, false));
		assertFalse(state.shouldHideRenderable(later, false));
		state.publishSnapshots();
		assertFalse(state.shouldHideRenderable(first, false));
		assertTrue(state.shouldHideRenderable(later, false));
	}

	@Test
	public void tileObjectsUseLiveOrPublishedStateByThread()
	{
		BillboardVisibilityState state = new BillboardVisibilityState();
		TileObject object = TestProxies.proxy(TileObject.class);
		state.activeTileObjects.add(object);

		assertTrue(state.hasActiveTileObject(object, true));
		assertFalse(state.hasActiveTileObject(object, false));
		state.publishSnapshots();
		assertTrue(state.hasActiveTileObject(object, false));
		state.clearActiveTileObjects();
		assertFalse(state.hasActiveTileObject(object, true));
		assertFalse(state.hasActiveTileObject(object, false));
	}

	@Test
	public void clearSelectionsAndSnapshotsResetBothViews()
	{
		BillboardVisibilityState state = new BillboardVisibilityState();
		Renderable renderable = TestProxies.proxy(Renderable.class);
		state.activeBillboards.add(renderable);
		state.publishSnapshots();

		state.clearSelections();
		assertFalse(state.shouldHideRenderable(renderable, true));
		assertTrue(state.shouldHideRenderable(renderable, false));
		state.clearSnapshots();
		assertFalse(state.shouldHideRenderable(renderable, false));
	}
}
