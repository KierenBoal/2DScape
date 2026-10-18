package com.kierenboal.npcsnap.occlusion;

import com.kierenboal.npcsnap.NpcSnapConfig;
import com.kierenboal.npcsnap.rendering.BillboardDepthCalculator;
import com.kierenboal.npcsnap.state.BillboardPerformanceMetrics;
import java.awt.Rectangle;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.Client;
import net.runelite.api.DynamicObject;
import net.runelite.api.GameObject;
import net.runelite.api.Model;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.hooks.DrawCallbacks;
import org.junit.Test;

import static com.kierenboal.npcsnap.TestProxies.*;
import static org.junit.Assert.*;

public class AnimatedSceneryOcclusionTest
{
	@Test
	public void animatedGeometryDoesNotDisappearWhenRenderableHeightIsResetBetweenFrames()
	{
		Fixture fixture = new Fixture();
		for (int frame = 0; frame < 50; frame++)
		{
			fixture.begin();
			// Scene drawing can leave DynamicObject's cached height unset. The
			// rendered geometry is unchanged, including on animation boundaries.
			fixture.firstHeight.set(frame % 14 == 3 ? 0 : 200);
			fixture.drawFirst();
			BillboardOcclusionMask mask = fixture.collect();
			assertEquals("Unchanged flag coverage at frame " + frame, 128, mask.transmittanceAt(400, 250, 2000));
		}
	}

	@Test
	public void temporaryAnimatedObjectsUseTheRenderedPoseOnEveryFrame()
	{
		Fixture fixture = new Fixture();
		for (int frame = 0; frame < 12; frame++)
		{
			fixture.begin();
			fixture.callbacks.get().drawPass(null, fixture.scene, DrawCallbacks.PASS_OPAQUE);
			boolean moved = (frame & 1) != 0;
			fixture.callbacks.get().drawTemp(null, fixture.scene, fixture.firstObject,
				fixture.setModel(moved ? 120 : 0, moved ? 128 : 0), 0, 512, 0, 1024);
			fixture.setModel(20_000, 255); // Renderer storage is reused before overlays run.
			BillboardOcclusionMask mask = fixture.collect();
			assertEquals("Original flag position in frame " + frame, moved ? 255 : 0,
				mask.transmittanceAt(400, 250, 2000));
			assertEquals("Animated flag position in frame " + frame, moved ? 128 : 255,
				mask.transmittanceAt(460, 250, 2000));
		}
		assertEquals("Do not regenerate the unanimated fallback", 0, fixture.zbufReads.get());
		assertEquals("Forward every renderer draw exactly once", 12, fixture.tempCalls.get());
	}

	@Test
	public void anotherObjectsAnimationCannotCreateAnOpaqueGhostBesideThePortal()
	{
		Fixture fixture = new Fixture();
		fixture.begin();
		fixture.drawFirst();
		fixture.drawSecond();
		BillboardOcclusionMask mask = fixture.collect();
		assertEquals("Partial portal transparency", 128, mask.transmittanceAt(400, 250, 2000));
		assertEquals("Unrelated NPC stays visible beside the portal", 255, mask.transmittanceAt(600, 250, 2000));
	}

	@Test
	public void rendererModelsKeepTheirOwnShapeAndTransparencyAfterSharedStorageIsReused()
	{
		Fixture fixture = new Fixture();
		for (int frame = 0; frame < 4; frame++)
		{
			fixture.begin();
			if ((frame & 1) == 0)
			{
				fixture.drawFirst();
				fixture.drawSecond();
			}
			else
			{
				fixture.drawSecond();
				fixture.drawFirst();
			}
			// A third lookup can even overwrite the pool's face indices and colors.
			fixture.setModel(20_000, 255);
			fixture.a[0] = 2;
			fixture.colors[0] = -2;
			BillboardOcclusionMask mask = fixture.collect();
			assertEquals("Portal opacity in frame " + frame, 128, mask.transmittanceAt(400, 250, 2000));
			assertEquals("No ghost geometry beside the portal", 255, mask.transmittanceAt(600, 250, 2000));
		}
	}

	@Test
	public void regeneratedObjectsSharingOneModelDoNotReuseAnotherObjectsBounds()
	{
		Fixture fixture = new Fixture();
		fixture.secondLocation.set(new LocalPoint(512, 1024));
		fixture.begin();
		// No renderer snapshots: each getModel() regenerates the same model buffer.
		BillboardOcclusionMask mask = fixture.collect();
		assertEquals(128, mask.transmittanceAt(400, 250, 2000));
		assertEquals(255, mask.transmittanceAt(600, 250, 2000));
	}

	private static final class Fixture
	{
		final float[] x = {-100, 100, 0};
		final int[] a = {0}, colors = {1234};
		final byte[] alpha = {(byte) 128};
		final java.util.concurrent.atomic.AtomicInteger zbufReads = new java.util.concurrent.atomic.AtomicInteger();
		final java.util.concurrent.atomic.AtomicInteger tempCalls = new java.util.concurrent.atomic.AtomicInteger();
		final java.util.concurrent.atomic.AtomicInteger firstHeight = new java.util.concurrent.atomic.AtomicInteger(200);
		final Model shared = proxy(Model.class, method("getVerticesCount", 3), method("getFaceCount", 1),
			method("getVerticesX", x), method("getVerticesY", new float[] {0, 0, -200}), method("getVerticesZ", new float[3]),
			method("getFaceIndices1", a), method("getFaceIndices2", new int[] {1}), method("getFaceIndices3", new int[] {2}),
			method("getFaceColors3", colors), method("getFaceTransparencies", alpha));
		final DynamicObject first = proxy(DynamicObject.class, methodSupplier("getModelHeight", firstHeight::get),
			methodSupplier("getModel", () -> setModel(2000, 0)),
			methodSupplier("getModelZbuf", () -> { zbufReads.incrementAndGet(); return setModel(2000, 0); }));
		final DynamicObject second = proxy(DynamicObject.class, method("getModelHeight", 200), methodSupplier("getModel", () -> setModel(0, 128)));
		final AtomicReference<LocalPoint> secondLocation = new AtomicReference<>(new LocalPoint(1024, 1024));
		final GameObject firstObject = proxy(GameObject.class, method("getLocalLocation", new LocalPoint(512, 1024)), method("getRenderable", first));
		final GameObject secondObject = proxy(GameObject.class, methodSupplier("getLocalLocation", secondLocation::get), method("getRenderable", second));
		final AtomicReference<DrawCallbacks> callbacks = new AtomicReference<>();
		final BillboardSceneVisibility visibility;
		final BillboardWorldOcclusionCollector collector;
		final WorldView view;
		final Scene scene;

		Fixture()
		{
			Tile[][][] tiles = new Tile[4][16][16];
			tiles[0][4][8] = proxy(Tile.class, method("getPlane", 0), method("getSceneLocation", new Point(4, 8)),
				method("getGameObjects", new GameObject[] {firstObject, secondObject}));
			scene = proxy(Scene.class, method("getTiles", tiles), method("getWorldViewId", WorldView.TOPLEVEL));
			view = proxy(WorldView.class, method("isTopLevel", true), method("getScene", scene),
				method("getTileSettings", new byte[4][16][16]), method("getTileHeights", new int[4][17][17]));
			Client client = proxy(Client.class, method("getWorldView", view), method("getTopLevelWorldView", view),
				method("getLocalPlayer", proxy(Player.class, method("getWorldView", view))),
				method("getCameraX", 512), method("getCameraFpX", 512f), method("getScale", 512),
				method("getViewportWidth", 800), method("getViewportHeight", 600));
			callbacks.set(new DrawCallbacks()
			{
				@Override public void drawTemp(net.runelite.api.Projection projection, Scene scene, GameObject object,
					Model model, int orientation, int x, int y, int z) { tempCalls.incrementAndGet(); }
				@Override public void draw(int color) { }
				@Override public void swapScene(Scene scene) { }
			});
			visibility = new BillboardSceneVisibility(callbacks::get, callbacks::set);
			collector = new BillboardWorldOcclusionCollector(client, new NpcSnapConfig() {}, new BillboardDepthCalculator(client),
				new BillboardPerformanceMetrics(), org.slf4j.LoggerFactory.getLogger(getClass()), visibility);
		}

		Model setModel(int center, int transparency)
		{
			x[0] = center - 100; x[1] = center + 100; x[2] = center;
			a[0] = 0;
			colors[0] = 1234;
			alpha[0] = (byte) transparency;
			return shared;
		}

		void begin()
		{
			collector.resetFrame();
			visibility.beginFrame();
			callbacks.get().preSceneDraw(scene, (net.runelite.api.Projection) null, 0, 0, 0, 0, 0, 0, 0, 3, Collections.emptySet());
		}

		void drawFirst()
		{
			callbacks.get().drawDynamic(null, scene, firstObject, first, setModel(0, 128), 0, 512, 0, 1024);
		}

		void drawSecond()
		{
			callbacks.get().drawDynamic(1, null, scene, secondObject, second, setModel(400, 0), 0, 1024, 0, 1024);
		}

		BillboardOcclusionMask collect()
		{
			Rectangle region = new Rectangle(300, 100, 400, 220);
			collector.addInterest(region, new Rectangle(0, 0, 800, 600));
			collector.collectTerrainOccluders(view, List.of(new BillboardWorldOcclusionCollector.TraceTarget(new LocalPoint(512, 1024), 0)),
				BillboardOcclusionQuality.HIGH);
			BillboardOcclusionMask mask = new BillboardOcclusionMask();
			mask.prepare(collector.occluders(), BillboardOcclusionQuality.HIGH, 0, 0, 800, 600, region);
			return mask;
		}
	}
}
