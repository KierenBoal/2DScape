package com.kierenboal.npcsnap;

import com.kierenboal.npcsnap.occlusion.BillboardOcclusionQuality;
import com.kierenboal.npcsnap.rendering.AnimationFrameSnapper;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.AABB;
import net.runelite.api.Animation;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.Model;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;

import static com.kierenboal.npcsnap.TestProxies.method;
import static com.kierenboal.npcsnap.TestProxies.methodSupplier;
import static com.kierenboal.npcsnap.TestProxies.proxy;
import static org.junit.Assert.*;

/** Exercises scheduling and sprite-cache commits with synthetic actors, never a game client. */
public class NpcBillboardResizeTest
{
	private final List<NPC> npcs = new ArrayList<>();
	private final AtomicInteger cycle = new AtomicInteger();
	private final AtomicInteger budget = new AtomicInteger(2);
	private final AtomicInteger percent = new AtomicInteger(10);
	private final AtomicInteger boundsMeasurements = new AtomicInteger();
	private final NpcSnapConfig config = new NpcSnapConfig()
	{
		@Override public boolean applyToPlayers() { return false; }
		@Override public boolean applyToProjectiles() { return false; }
		@Override public boolean applyToGraphicsObjects() { return false; }
		@Override public boolean applyToGroundItems() { return false; }
		@Override public boolean useRetroOverheads() { return false; }
		@Override public BillboardOcclusionQuality billboardOcclusionQuality() { return BillboardOcclusionQuality.OFF; }
		@Override public int billboardMaxDrawsPerFrame() { return budget.get(); }
		@Override public int redrawOnClickboxResizePercent() { return percent.get(); }
	};
	private final WorldView view = proxy(WorldView.class,
		method("getId", WorldView.TOPLEVEL), method("isTopLevel", true), method("contains", true),
		method("getSizeX", 16), method("getSizeY", 16),
		method("getTileSettings", new byte[4][16][16]), method("getTileHeights", new int[4][17][17]),
		method("npcs", proxy(IndexedObjectSet.class, methodSupplier("iterator", npcs::iterator))));
	private final Player localPlayer = proxy(Player.class, method("getWorldView", view),
		method("getLocalLocation", new LocalPoint(512, 1024)));
	private final Client client = proxy(Client.class, method("getTopLevelWorldView", view), method("getWorldView", view),
		method("getLocalPlayer", localPlayer), method("getGameState", GameState.LOGGED_IN), method("isClientThread", true),
		method("getScale", 512), method("get3dZoom", 512), method("getViewportWidth", 800),
		method("getViewportHeight", 600), methodSupplier("getGameCycle", cycle::get));
	private final AnimationFrameSnapper snapper = new AnimationFrameSnapper(proxy(Client.class,
		method("loadAnimation", proxy(Animation.class, method("getNumFrames", 100)))));
	private final NpcBillboardOverlay overlay = new NpcBillboardOverlay(client, null, config,
		new NpcSnapDebug(client, config), snapper, null);

	@Test
	public void forcedResizeBypassesCadenceAndMatchingCacheButStillRespectsDrawBudget()
	{
		AtomicInteger firstSize = new AtomicInteger(50);
		AtomicInteger secondSize = new AtomicInteger(50);
		NPC first = actor(firstSize, new AtomicInteger(2), 512);
		NPC second = actor(secondSize, new AtomicInteger(2), 768);
		frame(); // Both sprites initially captured.
		assertFalse(overlay.shouldForceActorResizeRedraw(first));
		assertFalse(overlay.shouldForceActorResizeRedraw(second));

		// Change only AABB dimensions: frames, vertices, source bounds and cache keys
		// remain identical, and the normal animation cadence is still closed.
		firstSize.set(70);
		secondSize.set(70);
		budget.set(1);
		cycle.incrementAndGet();
		beginFrame();
		assertTrue(overlay.shouldForceActorResizeRedraw(first));
		assertTrue(overlay.shouldForceActorResizeRedraw(second));
		drawFrame();
		boolean firstPending = overlay.shouldForceActorResizeRedraw(first);
		boolean secondPending = overlay.shouldForceActorResizeRedraw(second);
		assertTrue("Exactly one new baseline should commit within the draw budget", firstPending ^ secondPending);

		cycle.incrementAndGet();
		beginFrame();
		overlay.shouldForceActorResizeRedraw(first);
		overlay.shouldForceActorResizeRedraw(second);
		drawFrame();
		assertFalse(overlay.shouldForceActorResizeRedraw(first));
		assertFalse(overlay.shouldForceActorResizeRedraw(second));
	}

	@Test
	public void failedRasterizationRetainsTheDisplayedBaselineAndRetries()
	{
		AtomicInteger size = new AtomicInteger(50);
		AtomicInteger faces = new AtomicInteger(2);
		NPC actor = actor(size, faces, 512);
		frame();
		size.set(70);
		faces.set(0);
		cycle.incrementAndGet();
		beginFrame();
		assertTrue(overlay.shouldForceActorResizeRedraw(actor));
		drawFrame();
		assertTrue(overlay.shouldForceActorResizeRedraw(actor));
		faces.set(2);
		cycle.incrementAndGet();
		beginFrame();
		assertTrue(overlay.shouldForceActorResizeRedraw(actor));
		drawFrame();
		assertFalse(overlay.shouldForceActorResizeRedraw(actor));
	}

	@Test
	public void disablingOrClearingCacheRemovesResizeForcing()
	{
		AtomicInteger size = new AtomicInteger(50);
		NPC actor = actor(size, new AtomicInteger(2), 512);
		frame();
		size.set(70);
		percent.set(0);
		assertFalse(overlay.shouldForceActorResizeRedraw(actor));
		percent.set(10);
		assertTrue(overlay.shouldForceActorResizeRedraw(actor));
		overlay.clearBillboardCache();
		assertFalse(overlay.shouldForceActorResizeRedraw(actor));
	}

	@Test
	public void livePoseFloorCommitsOnlyForSuccessfulDrawsWithinTheBudget()
	{
		AtomicInteger firstSize = new AtomicInteger(50);
		AtomicInteger secondSize = new AtomicInteger(50);
		AtomicInteger rawFrame = new AtomicInteger();
		NPC first = actor(firstSize, new AtomicInteger(2), 512, rawFrame);
		NPC second = actor(secondSize, new AtomicInteger(2), 768, rawFrame);
		frame();
		firstSize.set(70);
		secondSize.set(70);
		rawFrame.set(17);
		budget.set(1);
		cycle.incrementAndGet();
		beginFrame();
		snapper.snapActorAnimationFrame(first, 1, 17, 4, true, false);
		snapper.snapActorAnimationFrame(second, 1, 17, 4, true, false);
		assertTrue(overlay.shouldForceActorResizeRedraw(first));
		assertTrue(overlay.shouldForceActorResizeRedraw(second));
		drawFrame();
		int firstNextFrame = snapper.snapActorAnimationFrame(first, 1, 18, 4, true, false);
		int secondNextFrame = snapper.snapActorAnimationFrame(second, 1, 18, 4, true, false);
		assertTrue("Only the sprite actually rasterized may advance its live pose floor",
			(firstNextFrame == 17 && secondNextFrame == 0) || (firstNextFrame == 0 && secondNextFrame == 17));
	}

	@Test
	public void failedRedrawDoesNotAdvanceTheLivePoseFloor()
	{
		AtomicInteger size = new AtomicInteger(50);
		AtomicInteger faces = new AtomicInteger(2);
		AtomicInteger rawFrame = new AtomicInteger();
		NPC actor = actor(size, faces, 512, rawFrame);
		frame();
		size.set(70);
		faces.set(0);
		rawFrame.set(17);
		cycle.incrementAndGet();
		beginFrame();
		snapper.snapActorAnimationFrame(actor, 1, 17, 4, true, false);
		assertTrue(overlay.shouldForceActorResizeRedraw(actor));
		drawFrame();
		assertEquals(0, snapper.snapActorAnimationFrame(actor, 1, 18, 4, true, false));
		assertTrue(overlay.shouldForceActorResizeRedraw(actor));
	}

	@Test
	public void repeatedResizeChecksReuseTheObservationWithinOneRenderFrame()
	{
		AtomicInteger size = new AtomicInteger(50);
		NPC actor = actor(size, new AtomicInteger(2), 512);
		frame();
		size.set(70);
		beginFrame();
		assertTrue(overlay.shouldForceActorResizeRedraw(actor));
		int measured = boundsMeasurements.get();
		assertTrue(overlay.shouldForceActorResizeRedraw(actor));
		assertEquals(measured, boundsMeasurements.get());
		beginFrame();
		assertTrue(overlay.shouldForceActorResizeRedraw(actor));
		assertEquals(measured + 1, boundsMeasurements.get());
	}

	private NPC actor(AtomicInteger boxHalfWidth, AtomicInteger faces, int localX)
	{
		return actor(boxHalfWidth, faces, localX, new AtomicInteger());
	}

	private NPC actor(AtomicInteger boxHalfWidth, AtomicInteger faces, int localX, AtomicInteger animationFrame)
	{
		AABB box = proxy(AABB.class, methodSupplier("getExtremeX", boxHalfWidth::get),
			method("getExtremeY", 50), method("getExtremeZ", 10), method("getCenterY", -50));
		Model model = proxy(Model.class, method("getVerticesCount", 4), method("getModelHeight", 100),
			method("getVerticesX", new float[] { -50, 50, 50, -50 }),
			method("getVerticesY", new float[] { -100, -100, 0, 0 }), method("getVerticesZ", new float[4]),
			methodSupplier("getAABB", () -> { boundsMeasurements.incrementAndGet(); return box; }),
			methodSupplier("getFaceCount", faces::get),
			method("getFaceIndices1", new int[] { 0, 0 }), method("getFaceIndices2", new int[] { 1, 2 }),
			method("getFaceIndices3", new int[] { 2, 3 }), method("getFaceColors1", new int[] { 5000, 5000 }),
			method("getFaceColors2", new int[] { 5000, 5000 }), method("getFaceColors3", new int[] { 5000, 5000 }));
		NPC actor = proxy(NPC.class, method("getId", npcs.size() + 1), method("getIndex", npcs.size() + 1),
			method("getWorldView", view), method("getLocalLocation", new LocalPoint(localX, 1024)),
			method("getAnimation", 1), method("getPoseAnimation", -1), method("getModel", model),
			methodSupplier("getAnimationFrame", animationFrame::get),
			method("getModelHeight", 100), method("getCanvasTilePoly", new Polygon(new int[] { 500, 510, 510 },
				new int[] { 250, 250, 260 }, 3)));
		npcs.add(actor);
		return actor;
	}

	private void frame()
	{
		beginFrame();
		drawFrame();
	}

	private void beginFrame()
	{
		overlay.setActive(true);
		for (NPC actor : npcs)
		{
			overlay.noteSceneRenderable(actor);
		}
		overlay.beginFrame();
	}

	private void drawFrame()
	{
		overlay.prepareFrame(view);
		BufferedImage canvas = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = canvas.createGraphics();
		try
		{
			overlay.render(graphics);
		}
		finally
		{
			graphics.dispose();
		}
	}
}
