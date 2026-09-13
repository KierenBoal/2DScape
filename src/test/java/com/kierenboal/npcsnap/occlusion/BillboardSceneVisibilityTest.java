package com.kierenboal.npcsnap.occlusion;

import java.util.HashSet;
import java.util.Set;
import com.kierenboal.npcsnap.NpcSnapConfig;
import com.kierenboal.npcsnap.rendering.BillboardDepthCalculator;
import com.kierenboal.npcsnap.state.BillboardPerformanceMetrics;
import net.runelite.api.Client;
import net.runelite.api.Projection;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.Point;
import net.runelite.api.Constants;
import net.runelite.api.hooks.DrawCallbacks;
import org.junit.Test;
import static com.kierenboal.npcsnap.TestProxies.proxy;
import static com.kierenboal.npcsnap.TestProxies.method;
import static org.junit.Assert.*;

public class BillboardSceneVisibilityTest
{
	@Test
	public void collectorKeepsGpuUpperSceneryAndLinkedTilesButRejectsHiddenRoofs()
	{
		TestClient client = new TestClient();
		client.callbacks = new Renderer();
		BillboardSceneVisibility visibility = new BillboardSceneVisibility(() -> client.callbacks, value -> client.callbacks = value);
		Client api = proxy(Client.class, method("isGpu", true));
		BillboardWorldOcclusionCollector collector = new BillboardWorldOcclusionCollector(api,
			proxy(NpcSnapConfig.class), new BillboardDepthCalculator(api), new BillboardPerformanceMetrics(),
			org.slf4j.LoggerFactory.getLogger(getClass()), visibility);
		byte[][][] flags = new byte[4][104][104];
		int[][][] roofs = new int[4][104][104];
		roofs[0][2][2] = 42;
		Scene scene = proxy(Scene.class, method("getExtendedTileSettings", flags), method("getRoofs", roofs));
		Tile upper = proxy(Tile.class, method("getPlane", 1), method("getSceneLocation", new Point(2, 2)));
		Tile linked = proxy(Tile.class, method("getPlane", 3), method("getSceneLocation", new Point(2, 2)));
		BillboardWorldOcclusionCollector.TileCandidate direct = BillboardWorldOcclusionCollector.TileCandidate.direct(upper, 1, false);
		BillboardWorldOcclusionCollector.TileCandidate bridge = BillboardWorldOcclusionCollector.TileCandidate.bridge(linked, 0);
		visibility.beginFrame();
		client.callbacks.preSceneDraw(scene, (Projection) null, 0, 0, 0, 0, 0, 0, 0, 3, Set.of(42));
		assertFalse(collector.upperPlaneVisibility(scene, direct, 0, false).accepted);
		assertTrue(collector.upperPlaneVisibility(scene, bridge, 0, false).accepted);
		roofs[0][2][2] = 17;
		assertTrue(collector.upperPlaneVisibility(scene, direct, 0, false).accepted);
		roofs[0][2][2] = 0;
		assertTrue(collector.upperPlaneVisibility(scene, direct, 0, false).accepted);
		client.callbacks.preSceneDraw(scene, (Projection) null, 0, 0, 0, 0, 0, 0, 0, 0, Set.of());
		assertFalse(collector.upperPlaneVisibility(scene, direct, 0, false).accepted);
		assertTrue(collector.upperPlaneVisibility(scene, bridge, 0, false).accepted);
		flags[1][2][2] = Constants.TILE_FLAG_VIS_BELOW;
		assertTrue(collector.upperPlaneVisibility(scene, direct, 0, false).accepted);
	}

	@Test
	public void selectiveRoofsAndNativeFloorRangeUseRendererDecisions()
	{
		BillboardSceneVisibility.Snapshot selective = new BillboardSceneVisibility.Snapshot(0, 0, 3, Set.of(42));
		assertFalse(selective.visible(1, 42));
		assertTrue(selective.visible(1, 17));
		assertTrue(selective.visible(2, 0));
		assertTrue(selective.visible(0, 42));
		BillboardSceneVisibility.Snapshot nativeRemoval = new BillboardSceneVisibility.Snapshot(0, 0, 0, Set.of());
		assertFalse(nativeRemoval.visible(1, 0));
		assertTrue(nativeRemoval.visible(0, 0)); // Includes linked geometry and VIS_BELOW.
		assertFalse(new BillboardSceneVisibility.Snapshot(1, 1, 3, Set.of()).visible(0, 0));
	}

	@Test
	public void ungroupedRoofAboveFirstFloorIsRejectedAfterCurrentFrameSceneCallback()
	{
		TestClient client = new TestClient();
		client.callbacks = new Renderer();
		BillboardSceneVisibility visibility = new BillboardSceneVisibility(() -> client.callbacks, value -> client.callbacks = value);
		Client api = proxy(Client.class, method("isGpu", true));
		BillboardWorldOcclusionCollector collector = new BillboardWorldOcclusionCollector(api,
			proxy(NpcSnapConfig.class), new BillboardDepthCalculator(api), new BillboardPerformanceMetrics(),
			org.slf4j.LoggerFactory.getLogger(getClass()), visibility);
		byte[][][] flags = new byte[4][104][104];
		flags[1][59][40] = Constants.TILE_FLAG_UNDER_ROOF;
		Scene scene = proxy(Scene.class, method("getExtendedTileSettings", flags),
			method("getRoofs", new int[4][104][104]));
		Tile roof = proxy(Tile.class, method("getPlane", 2), method("getSceneLocation", new Point(59, 40)));
		BillboardWorldOcclusionCollector.TileCandidate candidate = BillboardWorldOcclusionCollector.TileCandidate.direct(roof, 2, false);
		visibility.beginFrame(); // BeforeRender: target selection only; roof state is not ready.
		assertNull(visibility.snapshot(scene));
		client.callbacks.preSceneDraw(scene, (Projection) null, 0, 0, 0, 0, 0, 0, 1, 1, Set.of());
		// ABOVE_SCENE: collect occluders now, after the renderer's decision.
		assertFalse(collector.upperPlaneVisibility(scene, candidate, 1, false).accepted);
		visibility.beginFrame();
		client.callbacks.preSceneDraw(scene, (Projection) null, 0, 0, 0, 0, 0, 0, 1, 3, Set.of());
		assertTrue(collector.upperPlaneVisibility(scene, candidate, 1, false).accepted);
	}

	@Test
	public void capturesCurrentFrameAndForwardsRendererCalls()
	{
		TestClient client = new TestClient();
		Renderer renderer = new Renderer();
		client.callbacks = renderer;
		BillboardSceneVisibility visibility = new BillboardSceneVisibility(() -> client.callbacks, value -> client.callbacks = value);
		Scene scene = proxy(Scene.class);
		visibility.beginFrame();
		DrawCallbacks wrapper = client.callbacks;
		assertSame(renderer, BillboardSceneVisibility.renderer(wrapper));
		Set<Integer> roofs = new HashSet<>(Set.of(42));
		wrapper.preSceneDraw(scene, (Projection) null, 1, 2, 3, 4, 5, 0, 0, 3, roofs);
		assertEquals(1, renderer.sceneCalls);
		assertFalse(visibility.snapshot(scene).visible(1, 42));
		roofs.clear();
		assertFalse(visibility.snapshot(scene).visible(1, 42));
		wrapper.draw(123);
		assertEquals(123, renderer.overlayColor);
		assertTrue(wrapper.zoneInFrustum(0, 0, 0, 0));
		visibility.beginFrame();
		assertSame(wrapper, client.callbacks);
		assertNull(visibility.snapshot(scene));
		wrapper.preSceneDraw(scene, 1, 2, 3, 4, 5, 0, 0, 0, Set.of());
		assertEquals(2, renderer.sceneCalls);
		assertFalse(visibility.snapshot(scene).visible(1, 0));
		assertNull(visibility.snapshot(proxy(Scene.class)));
		visibility.stop();
		assertSame(renderer, client.callbacks);
		assertNull(visibility.snapshot(scene));
	}

	@Test
	public void rendererSwitchAndShutdownPreserveCallbackOwnership()
	{
		TestClient client = new TestClient();
		BillboardSceneVisibility visibility = new BillboardSceneVisibility(() -> client.callbacks, value -> client.callbacks = value);
		visibility.beginFrame();
		assertNull(client.callbacks);
		client.callbacks = new Renderer();
		visibility.beginFrame();
		Renderer replacement = new Renderer();
		client.callbacks = replacement;
		assertNull(visibility.snapshot(proxy(Scene.class)));
		visibility.stop();
		assertSame(replacement, client.callbacks);
		visibility.beginFrame();
		assertNotSame(replacement, client.callbacks);
		visibility.stop();
		assertSame(replacement, client.callbacks);
	}

	private static final class TestClient
	{
		private DrawCallbacks callbacks;
	}

	private static final class Renderer implements DrawCallbacks
	{
		private int sceneCalls;
		private int overlayColor;
		@Override public void draw(int color) { overlayColor = color; }
		@Override public void swapScene(Scene scene) { }
		@Override public boolean zoneInFrustum(int x, int z, int max, int min) { return true; }
		@Override public void preSceneDraw(Scene scene, Projection projection,
			float x, float y, float z, float pitch, float yaw,
			int min, int level, int max, Set<Integer> roofs) { sceneCalls++; }
		@Override public void preSceneDraw(Scene scene,
			float x, float y, float z, float pitch, float yaw,
			int min, int level, int max, Set<Integer> roofs) { sceneCalls++; }
	}
}
