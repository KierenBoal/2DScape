package com.kierenboal.npcsnap.occlusion;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import com.kierenboal.npcsnap.NpcSnapConfig;
import com.kierenboal.npcsnap.rendering.BillboardDepthCalculator;
import com.kierenboal.npcsnap.state.BillboardPerformanceMetrics;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Projection;
import net.runelite.api.FloatProjection;
import net.runelite.api.Model;
import net.runelite.api.Renderable;
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
	public void boatDetailsAreNotTrackedButTheirRendererCallsStillForward()
	{
		TestClient client = new TestClient();
		Renderer renderer = new Renderer();
		client.callbacks = renderer;
		BillboardSceneVisibility visibility = new BillboardSceneVisibility(() -> client.callbacks, value -> client.callbacks = value);
		Scene scene = proxy(Scene.class, method("getWorldViewId", 7));
		Renderable renderable = proxy(Renderable.class);
		Model model = proxy(Model.class);
		visibility.beginFrame();
		client.callbacks.preSceneDraw(scene, (Projection) null, 0, 0, 0, 0, 0, 0, 0, 3, Set.of());
		for (int id : new int[] {net.runelite.api.gameval.ObjectID.SAILING_TELEPORTATION_FOCUS,
			net.runelite.api.gameval.ObjectID.SAILING_BOAT_SAIL_KANDARIN_3X8_COTTON})
			client.callbacks.drawDynamic(null, scene, proxy(net.runelite.api.TileObject.class, method("getId", id)), renderable, model, 0, 0, 0, 0);
		assertEquals(2, renderer.dynamicCalls);
		assertTrue(visibility.snapshot(scene).drawnModels.isEmpty());
		client.callbacks.drawDynamic(null, scene, proxy(net.runelite.api.TileObject.class,
			method("getId", net.runelite.api.gameval.ObjectID.SAILING_BOAT_HULL_KANDARIN_3X8_IRONWOOD)), renderable, model, 0, 0, 0, 0);
		assertSame(model, visibility.snapshot(scene).drawnModels.get(renderable));
		assertEquals(3, renderer.dynamicCalls);
		visibility.stop();
	}

	@Test
	public void recordsModelsFromBothDynamicDrawCallbacksForThisFrameOnly()
	{
		TestClient client = new TestClient();
		Renderer renderer = new Renderer();
		client.callbacks = renderer;
		BillboardSceneVisibility visibility = new BillboardSceneVisibility(() -> client.callbacks, value -> client.callbacks = value);
		Scene scene = proxy(Scene.class, method("getWorldViewId", 7));
		Renderable facility = proxy(Renderable.class), sail = proxy(Renderable.class);
		Model facilityModel = proxy(Model.class), sailModel = proxy(Model.class);
		visibility.beginFrame();
		client.callbacks.preSceneDraw(scene, (Projection) null, 0, 0, 0, 0, 0, 0, 0, 3, Set.of());
		client.callbacks.drawPass(null, scene, DrawCallbacks.PASS_OPAQUE);
		client.callbacks.drawDynamic(null, scene, null, facility, facilityModel, 0, 0, 0, 0);
		client.callbacks.drawDynamic(2, null, scene, null, sail, sailModel, 0, 0, 0, 0);
		assertSame(facilityModel, visibility.snapshot(scene).drawnModels.get(facility));
		assertSame(sailModel, visibility.snapshot(scene).drawnModels.get(sail));
		assertTrue(visibility.snapshot(scene).zbufRenderer);
		assertEquals(2, renderer.dynamicCalls);
		visibility.beginFrame();
		client.callbacks.preSceneDraw(scene, (Projection) null, 0, 0, 0, 0, 0, 0, 0, 3, Set.of());
		assertTrue(visibility.snapshot(scene).drawnModels.isEmpty());
		assertFalse(visibility.snapshot(scene).zbufRenderer);
		visibility.stop();
	}

	@Test
	public void capturesAffineRendererMatrixBeforeItsStorageIsReused()
	{
		TestClient client = new TestClient();
		client.callbacks = new Renderer();
		BillboardSceneVisibility visibility = new BillboardSceneVisibility(() -> client.callbacks, value -> client.callbacks = value);
		float[] matrix = {0, 0, -1, 0, 0, 1, 0, 0, 1, 0, 0, 0, 512, -100, 1024, 1};
		FloatProjection projection = proxy(FloatProjection.class, method("getProjection", matrix));
		Scene scene = proxy(Scene.class, method("getWorldViewId", 7));
		visibility.beginFrame();
		client.callbacks.preSceneDraw(scene, projection, 0, 0, 0, 0, 0, 0, 0, 3, Set.of());
		Projection captured = visibility.snapshot(scene).projection;
		float[] out = new float[3];
		assertSame(out, captured.project(20, -40, 70, out));
		assertArrayEquals(new float[] {582, -140, 1004}, out, 0f);
		matrix[12] = 20_000;
		assertArrayEquals(out, captured.project(20, -40, 70), 0f);
		visibility.beginFrame();
		assertNull(visibility.snapshot(scene));
		visibility.stop();
	}

	@Test
	public void smallBoatSceneUsesItsOwnVisibilityCoordinates()
	{
		TestClient client = new TestClient();
		client.callbacks = new Renderer();
		BillboardSceneVisibility visibility = new BillboardSceneVisibility(() -> client.callbacks, value -> client.callbacks = value);
		Client api = proxy(Client.class, method("isGpu", true));
		BillboardWorldOcclusionCollector collector = new BillboardWorldOcclusionCollector(api,
			proxy(NpcSnapConfig.class), new BillboardDepthCalculator(api), new BillboardPerformanceMetrics(),
			org.slf4j.LoggerFactory.getLogger(getClass()), visibility);
		byte[][][] flags = new byte[4][16][16];
		int[][][] roofs = new int[4][16][16];
		Scene scene = proxy(Scene.class, method("getWorldViewId", 7), method("getExtendedTileSettings", flags), method("getRoofs", roofs));
		Tile upper = proxy(Tile.class, method("getPlane", 1), method("getSceneLocation", new Point(2, 9)));
		BillboardWorldOcclusionCollector.TileCandidate candidate = BillboardWorldOcclusionCollector.TileCandidate.direct(upper, 1, true);
		visibility.beginFrame();
		client.callbacks.preSceneDraw(scene, (Projection) null, 0, 0, 0, 0, 0, 0, 0, 0, Set.of());
		assertFalse(collector.upperPlaneVisibility(scene, candidate, 0, false).accepted);
		flags[1][2][9] = Constants.TILE_FLAG_VIS_BELOW;
		assertTrue(collector.upperPlaneVisibility(scene, candidate, 0, false).accepted);
		flags[1][2][9] = 0;
		roofs[0][2][9] = 42;
		client.callbacks.preSceneDraw(scene, (Projection) null, 0, 0, 0, 0, 0, 0, 0, 3, Set.of(42));
		assertFalse(collector.upperPlaneVisibility(scene, candidate, 0, false).accepted);
		visibility.stop();
	}

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

	@Test
	public void rendererSwitchDuringLoadingWaitsToInstallObserver()
	{
		TestClient client = new TestClient();
		Renderer gpu = new Renderer();
		Renderer hd = new Renderer();
		client.callbacks = gpu;
		AtomicReference<GameState> state = new AtomicReference<>(GameState.LOGGED_IN);
		List<DrawCallbacks> writes = new ArrayList<>();
		BillboardSceneVisibility visibility = new BillboardSceneVisibility(
			() -> client.callbacks, value -> { writes.add(value); client.callbacks = value; },
			state::get, task -> task.getAsBoolean());

		visibility.beginFrame();
		assertSame(gpu, BillboardSceneVisibility.renderer(client.callbacks));
		assertEquals(1, writes.size());
		client.callbacks = hd;
		state.set(GameState.LOADING);
		visibility.beginFrame();
		assertSame(hd, client.callbacks);
		assertEquals(1, writes.size());
		assertNull(visibility.snapshot(proxy(Scene.class)));

		state.set(GameState.LOGGED_IN);
		visibility.beginFrame();
		assertNotSame(hd, client.callbacks);
		assertEquals(2, writes.size());
		assertSame(hd, BillboardSceneVisibility.renderer(client.callbacks));
		Scene scene = proxy(Scene.class);
		client.callbacks.preSceneDraw(scene, (Projection) null, 0, 0, 0, 0, 0, 0, 0, 3, Set.of(42));
		assertFalse(visibility.snapshot(scene).visible(1, 42));
		visibility.stop();
		assertSame(hd, client.callbacks);
	}

	@Test
	public void stoppingDuringLoadingWaitsBeforeRestoringCallbacks()
	{
		TestClient client = new TestClient();
		Renderer renderer = new Renderer();
		client.callbacks = renderer;
		AtomicReference<GameState> state = new AtomicReference<>(GameState.LOGGED_IN);
		List<BooleanSupplier> queued = new ArrayList<>();
		BillboardSceneVisibility visibility = new BillboardSceneVisibility(
			() -> client.callbacks, value -> client.callbacks = value, state::get, queued::add);

		visibility.beginFrame();
		DrawCallbacks wrapper = client.callbacks;
		state.set(GameState.LOADING);
		visibility.stop();
		assertFalse(queued.get(0).getAsBoolean());
		assertSame(wrapper, client.callbacks);
		state.set(GameState.LOGGED_IN);
		assertTrue(queued.get(0).getAsBoolean());
		assertSame(renderer, client.callbacks);
	}

	@Test
	public void stopDefersCallbackRestorationToTheClientThreadDispatcher()
	{
		TestClient client = new TestClient();
		Renderer renderer = new Renderer();
		client.callbacks = renderer;
		List<BooleanSupplier> queued = new ArrayList<>();
		BillboardSceneVisibility visibility = new BillboardSceneVisibility(
			() -> client.callbacks,
			value -> client.callbacks = value,
			() -> GameState.LOGGED_IN,
			queued::add);

		visibility.beginFrame();
		DrawCallbacks wrapper = client.callbacks;
		visibility.stop();

		assertSame(wrapper, client.callbacks);
		assertEquals(1, queued.size());
		assertTrue(queued.get(0).getAsBoolean());
		assertSame(renderer, client.callbacks);
	}

	private static final class TestClient
	{
		private DrawCallbacks callbacks;
	}

	private static final class Renderer implements DrawCallbacks
	{
		private int sceneCalls;
		private int overlayColor;
		private int dynamicCalls;
		@Override public void drawDynamic(Projection projection, Scene scene, net.runelite.api.TileObject object,
			Renderable renderable, Model model, int orientation, int x, int y, int z) { dynamicCalls++; }
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
