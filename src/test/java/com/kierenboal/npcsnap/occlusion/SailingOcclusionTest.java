package com.kierenboal.npcsnap.occlusion;

import com.kierenboal.npcsnap.NpcSnapConfig;
import com.kierenboal.npcsnap.rendering.BillboardDepthCalculator;
import com.kierenboal.npcsnap.state.BillboardPerformanceMetrics;
import java.awt.Rectangle;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.hooks.DrawCallbacks;
import net.runelite.api.gameval.ObjectID;
import org.junit.Test;
import static com.kierenboal.npcsnap.TestProxies.*;
import static org.junit.Assert.*;

public class SailingOcclusionTest
{
	@Test public void invisibleHullModelsStayInvisibleWithoutFallbackHulls()
	{
		Fixture fixture = new Fixture();
		fixture.collect();
		assertFalse(fixture.collector.occluders().isEmpty());
		fixture.modelTransparency.set(255);
		fixture.collect();
		assertTrue(fixture.collector.occluders().isEmpty());
		fixture.modelTransparency.set(0);
		fixture.collect();
		assertFalse(fixture.collector.occluders().isEmpty());
		fixture.gpuFlags.set(DrawCallbacks.GPU | DrawCallbacks.ZBUF);
		fixture.facilityRenderable.set(proxy(DynamicObject.class, method("getModel", fixture.model)));
		fixture.collect();
		assertTrue(fixture.collector.occluders().isEmpty());
		fixture.facilityRenderable.set(proxy(DynamicObject.class, method("getModelZbuf", fixture.model)));
		fixture.collect();
		assertFalse(fixture.collector.occluders().isEmpty());
		fixture.drawnModel = fixture.model;
		fixture.facilityRenderable.set(proxy(DynamicObject.class)); // No static model: this part is animated.
		fixture.collect();
		assertFalse(fixture.collector.occluders().isEmpty());
		fixture.modelTransparency.set(255);
		fixture.collect();
		assertTrue(fixture.collector.occluders().isEmpty());
	}

	@Test public void facilitiesAndSailsAreSkippedBeforeRenderableOrModelRetrieval()
	{
		Fixture fixture = new Fixture();
		fixture.collect();
		assertFalse(fixture.collector.occluders().isEmpty());
		int reads = fixture.renderableReads.get();
		for (int id : new int[] {ObjectID.SAILING_TELEPORTATION_FOCUS, ObjectID.SAILING_RUNE_CANNON,
			ObjectID.SAILING_BOAT_SAIL_KANDARIN_3X8_COTTON})
		{
			fixture.objectId.set(id);
			fixture.collect();
			assertTrue(fixture.collector.occluders().isEmpty());
			assertEquals(reads, fixture.renderableReads.get());
			assertEquals(Long.valueOf(0), fixture.metrics.counters().get("Faces inspected"));
		}
		fixture.objectId.set(ObjectID.SAILING_BOAT_SLOOP_TRIM_SHARK);
		fixture.collect();
		assertFalse(fixture.collector.occluders().isEmpty());
	}

	@Test public void rotatedRendererGeometryUsesItsOwnTransformAndHeightOnce()
	{
		Fixture fixture = new Fixture();
		fixture.parentHeight.set(200);
		fixture.shift.set(20_000); // Actor projection deliberately differs from the scenery renderer.
		float cosine = (float) Math.cos(Math.PI / 6), sine = .5f;
		float offsetX = 512 - 64 * (cosine + sine), offsetY = 1024 - 64 * (cosine - sine);
		float[] matrix = {cosine, 0, -sine, 0, 0, 1, 0, 0, sine, 0, cosine, 0, offsetX, -80, offsetY, 1};
		fixture.rendererProjection = proxy(FloatProjection.class, method("getProjection", matrix));
		fixture.collect();
		assertEquals(1, fixture.collector.occluders().size());
		BillboardDepthCalculator depth = new BillboardDepthCalculator(fixture.client);
		int[] sourceX = {-36, 164, 64}, sourceY = {64, 64, 64}, heights = {-120, -120, -320};
		Point[] points = new Point[3];
		float[] depths = new float[3];
		for (int i = 0; i < 3; i++)
		{
			int x = Math.round(offsetX + sourceX[i] * cosine + sourceY[i] * sine);
			int y = Math.round(offsetY + sourceY[i] * cosine - sourceX[i] * sine);
			points[i] = depth.localToCanvas(x, y, heights[i]);
			depths[i] = (float) depth.cameraForwardDepth(x, y, heights[i]);
		}
		BillboardOcclusionMask actual = new BillboardOcclusionMask(), expected = new BillboardOcclusionMask();
		Rectangle interest = new Rectangle(300, 100, 200, 220);
		actual.prepare(fixture.collector.occluders(), BillboardOcclusionQuality.HIGH, 0, 0, 800, 600, interest);
		expected.prepare(Collections.singletonList(BillboardOcclusionMask.Occluder.triangle(
			points[0].getX(), points[0].getY(), depths[0], points[1].getX(), points[1].getY(), depths[1],
			points[2].getX(), points[2].getY(), depths[2])), BillboardOcclusionQuality.HIGH, 0, 0, 800, 600, interest);
		for (int y = 100; y < 320; y += 2)
			for (int x = 300; x < 500; x += 2)
				for (double actorDepth : new double[] {1000, 1100, 2000})
					assertEquals(expected.transmittanceAt(x, y, actorDepth), actual.transmittanceAt(x, y, actorDepth));
		matrix[12] += 20_000;
		fixture.collect();
		assertTrue(fixture.collector.occluders().isEmpty());
	}

	@Test public void anotherVisibleBoatCanOccludeTheSameSpriteRegion()
	{
		Fixture fixture = new Fixture(), neighbor = new Fixture();
		fixture.collect();
		int ownFaces = fixture.collector.occluders().size();
		fixture.entities.add(neighbor.entity);
		fixture.collect();
		assertEquals(ownFaces * 2, fixture.collector.occluders().size());
		BillboardOcclusionMask mask = new BillboardOcclusionMask();
		mask.prepare(fixture.collector.occluders(), BillboardOcclusionQuality.HIGH, 0, 0, 800, 600, new Rectangle(300, 100, 200, 220));
		assertEquals(0, mask.transmittanceAt(400, 250, 2000d));
		neighbor.hidden.set(true);
		fixture.collect();
		assertEquals(ownFaces, fixture.collector.occluders().size());
	}

	@Test public void unavailableProjectionSkipsSceneWorkAndSceneReplacementReseeds()
	{
		Fixture fixture = new Fixture();
		fixture.projectionAvailable.set(false);
		fixture.collect();
		assertEquals(0, fixture.sceneReads.get());
		assertTrue(fixture.collector.occluders().isEmpty());
		fixture.projectionAvailable.set(true);
		fixture.collect();
		assertFalse(fixture.collector.occluders().isEmpty());
		fixture.scene.set(proxy(Scene.class, method("getTiles", new Tile[1][1][1])));
		fixture.collect();
		assertTrue(fixture.collector.occluders().isEmpty());
	}

	@Test public void boatHullUsesProjectedGeometryAndHiddenBoatsDoNoWork()
	{
		Fixture fixture = new Fixture();
		fixture.collect();
		assertTrue(fixture.collector.occluders().size() > 0);
		int reads = fixture.sceneReads.get();
		fixture.hidden.set(true);
		fixture.collect();
		assertEquals(0, fixture.collector.occluders().size());
		assertEquals(reads, fixture.sceneReads.get());
		assertEquals(Long.valueOf(1), fixture.metrics.counters().get("Boats skipped"));
		assertEquals(Long.valueOf(0), fixture.metrics.counters().get("Faces inspected"));
	}

	@Test public void geometryMovesWithTheBoatAndSceneUnloadDropsTheIndex()
	{
		Fixture fixture = new Fixture();
		fixture.collect();
		assertTrue(fixture.collector.occluders().size() > 0);
		fixture.shift.set(20_000);
		fixture.collect();
		assertEquals(0, fixture.collector.occluders().size());
		fixture.shift.set(0);
		fixture.tiles[0][0][0] = null;
		fixture.collector.unload(fixture.boat);
		fixture.collect();
		assertEquals(0, fixture.collector.occluders().size());
	}

	@Test public void standaloneDeckSurfacesAreSkippedInHullOnlyMode()
	{
		Fixture fixture = new Fixture();
		fixture.tiles[0][0][0] = proxy(Tile.class, method("getLocalLocation", new LocalPoint(64, 64, fixture.boat)),
			method("getSceneTilePaint", proxy(SceneTilePaint.class)));
		fixture.collect();
		assertEquals(Long.valueOf(0), fixture.metrics.counters().get("Terrain tris checked"));
		fixture.tiles[0][0][0] = proxy(Tile.class, method("getLocalLocation", new LocalPoint(64, 64, fixture.boat)),
			method("getSceneTilePaint", proxy(SceneTilePaint.class, method("getNeColor", 12345678))));
		fixture.collector.unload(fixture.boat);
		fixture.collect();
		assertEquals(Long.valueOf(0), fixture.metrics.counters().get("Terrain tris checked"));
	}

	private static final class Fixture
	{
		final AtomicBoolean hidden = new AtomicBoolean();
		final AtomicBoolean projectionAvailable = new AtomicBoolean(true);
		final java.util.concurrent.atomic.AtomicReference<Scene> scene = new java.util.concurrent.atomic.AtomicReference<>();
		final java.util.List<WorldEntity> entities = new java.util.ArrayList<>();
		final WorldEntity entity;
		final AtomicInteger shift = new AtomicInteger(), sceneReads = new AtomicInteger();
		final AtomicInteger parentHeight = new AtomicInteger();
		final AtomicInteger modelTransparency = new AtomicInteger(), gpuFlags = new AtomicInteger();
		final AtomicInteger objectId = new AtomicInteger(ObjectID.SAILING_BOAT_HULL_KANDARIN_3X8_IRONWOOD);
		final AtomicInteger renderableReads = new AtomicInteger();
		final java.util.concurrent.atomic.AtomicReference<Renderable> facilityRenderable = new java.util.concurrent.atomic.AtomicReference<>();
		final Model model;
		final java.util.concurrent.atomic.AtomicReference<DrawCallbacks> callbacks = new java.util.concurrent.atomic.AtomicReference<>();
		final BillboardSceneVisibility visibility;
		Projection rendererProjection;
		Model drawnModel;
		final AtomicLong clock = new AtomicLong();
		final BillboardPerformanceMetrics metrics = new BillboardPerformanceMetrics(clock::get);
		final Tile[][][] tiles = new Tile[1][1][1];
		final WorldView boat, top;
		final Client client;
		final BillboardWorldOcclusionCollector collector;
		Fixture()
		{
			scene.set(proxy(Scene.class, method("getTiles", tiles), method("getWorldViewId", 7)));
			boat = proxy(WorldView.class, method("getId", 7), methodSupplier("getScene", () -> { sceneReads.incrementAndGet(); return scene.get(); }),
				methodSupplier("getMainWorldProjection", () -> projectionAvailable.get() ? projection(448 + shift.get(), 0, 960, 0) : null), method("getTileHeight", -40));
			entity = proxy(WorldEntity.class, method("getWorldView", boat), method("getLocalLocation", new LocalPoint(512, 1024)), methodSupplier("isHiddenForOverlap", hidden::get));
			entities.add(entity);
			top = proxy(WorldView.class, method("isTopLevel", true), methodSupplier("getTileHeight", parentHeight::get), method("worldEntities", proxy(IndexedObjectSet.class,
				methodSupplier("iterator", entities::iterator))));
			model = proxy(Model.class, method("getVerticesCount", 3), method("getFaceCount", 1), method("getModelHeight", 50),
				methodSupplier("getTransparency", () -> (byte) modelTransparency.get()),
				method("getVerticesX", new float[] {-100, 100, 0}), method("getVerticesY", new float[] {0, 0, -200}), method("getVerticesZ", new float[3]),
				method("getFaceIndices1", new int[] {0}), method("getFaceIndices2", new int[] {1}), method("getFaceIndices3", new int[] {2}));
			facilityRenderable.set(model);
			GroundObject facility = proxy(GroundObject.class, method("getWorldView", boat), method("getLocalLocation", new LocalPoint(64, 64, boat)),
				methodSupplier("getId", objectId::get), method("getZ", -40),
				methodSupplier("getRenderable", () -> { renderableReads.incrementAndGet(); return facilityRenderable.get(); }),
				method("getConvexHull", new Rectangle(350, 220, 100, 100)));
			tiles[0][0][0] = proxy(Tile.class, method("getLocalLocation", new LocalPoint(64, 64, boat)), method("getGroundObject", facility));
			client = proxy(Client.class, method("getTopLevelWorldView", top), method("getCameraX", 512), method("getCameraFpX", 512f),
				method("getScale", 512), method("get3dZoom", 512), method("getViewportWidth", 800), method("getViewportHeight", 600));
			callbacks.set(new DrawCallbacks()
			{
				@Override public void draw(int color) { }
				@Override public void swapScene(Scene scene) { }
			});
			visibility = new BillboardSceneVisibility(callbacks::get, callbacks::set);
			collector = new BillboardWorldOcclusionCollector(client, new NpcSnapConfig() {}, new BillboardDepthCalculator(client), metrics, org.slf4j.LoggerFactory.getLogger(getClass()), visibility);
		}
		void collect()
		{
			metrics.beginFrame(true);
			collector.resetFrame();
			visibility.beginFrame();
			if (rendererProjection != null || gpuFlags.get() != 0)
				callbacks.get().preSceneDraw(scene.get(), rendererProjection, 0, 0, 0, 0, 0, 0, 0, 3, Collections.emptySet());
			if (gpuFlags.get() != 0) callbacks.get().drawPass(rendererProjection, scene.get(), DrawCallbacks.PASS_OPAQUE);
			if (drawnModel != null) callbacks.get().drawDynamic(0, rendererProjection, scene.get(), null,
				facilityRenderable.get(), drawnModel, 0, 64, -40, 64);
			collector.addInterest(new Rectangle(300, 100, 200, 220), new Rectangle(0, 0, 800, 600));
			collector.collectNestedOccluders(top, null, BillboardOcclusionQuality.HIGH, Collections.emptySet(), Collections.emptySet());
			collector.publishCounts();
			clock.addAndGet(500_000_000L);
			metrics.finishFrame();
		}
	}
}
