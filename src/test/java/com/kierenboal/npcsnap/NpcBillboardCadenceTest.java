package com.kierenboal.npcsnap;

import com.kierenboal.npcsnap.occlusion.BillboardOcclusionQuality;
import com.kierenboal.npcsnap.rendering.ActorWorldPlane;
import com.kierenboal.npcsnap.rendering.BillboardDepthCalculator;
import com.kierenboal.npcsnap.rendering.BillboardDrawGeometry;
import com.kierenboal.npcsnap.rendering.BillboardRenderRequest;
import com.kierenboal.npcsnap.rendering.VerticalAnchor;
import java.awt.Rectangle;
import com.kierenboal.npcsnap.targeting.BillboardTargetType;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Actor;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.Animation;
import net.runelite.api.Client;
import net.runelite.api.Deque;
import net.runelite.api.DynamicObject;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.GraphicsObject;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.IterableHashTable;
import net.runelite.api.Model;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Projectile;
import net.runelite.api.Renderable;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;

import static com.kierenboal.npcsnap.TestProxies.*;
import static org.junit.Assert.*;

/** Real overlay scheduling/caching/rasterization with synthetic models; no game input. */
public class NpcBillboardCadenceTest
{
	@Test
	public void everyBillboardHoldsItsImageOnTheSameFractionalFpsSchedule()
	{
		for (BillboardTargetType type : BillboardTargetType.values())
		{
			for (int fps : new int[] {2, 4, 10, 50})
			{
				Fixture f = new Fixture(type, fps, true);
				int previous = f.frame(0);
				assertTrue(type + " must actually rasterize", previous > 0);
				double deadline = 50d / fps;
				for (int cycle = 1; cycle <= 50; cycle++)
				{
					int current = f.frame(cycle);
					if (cycle >= Math.ceil(deadline - 1e-9))
					{
						assertTrue(type + " must capture at cycle " + cycle + " / " + fps, current > previous);
						deadline += 50d / fps;
					}
					else assertEquals(type + " bypassed cadence at " + cycle + " / " + fps, previous, current);
					assertEquals("Repeated renders in one cycle must hold the image", current, f.frame(cycle));
					previous = current;
				}
			}
		}
	}

	@Test
	public void disabledLimitCapturesNativeProgressionForEveryType()
	{
		for (BillboardTargetType type : BillboardTargetType.values())
		{
			Fixture f = new Fixture(type, 2, false);
			int previous = f.frame(0);
			assertTrue(type.toString(), f.frame(1) > previous);
		}
	}

	@Test
	public void identityChangesAndLargeResizesBypassAndRestartEveryTimer()
	{
		for (BillboardTargetType type : BillboardTargetType.values())
		{
			Fixture f = new Fixture(type, 2, true);
			int first = f.frame(0);
			f.identity.incrementAndGet();
			int changed = f.frame(1);
			assertTrue(type + " identity change", changed > first);
			assertEquals(type + " resets its timer", changed, f.frame(25));
			assertTrue(type + " resumes after reset", f.frame(26) > changed);
			f.resizePercent.set(10);
			f.size.set(3);
			int resized = f.frame(27);
			assertTrue(type + " resize exception", resized > changed);
			assertEquals(type + " resize baseline commits", resized, f.frame(28));
		}
	}

	@Test
	public void appearanceChangesForceNewImagesEvenWithOtherwiseMatchingCacheKeys()
	{
		for (BillboardTargetType type : BillboardTargetType.values())
		{
			Fixture f = new Fixture(type, 2, true);
			int first = f.frame(0);
			f.color.addAndGet(100);
			int changed = f.frame(1);
			assertTrue(type + " appearance change", changed > first);
			assertEquals(type + " holds the updated appearance", changed, f.frame(2));
		}
	}

	@Test
	public void flightRotationIsCappedButCameraSelectionChangesAreImmediate()
	{
		Fixture f = new Fixture(BillboardTargetType.PROJECTILE, 2, true);
		int first = f.frame(0);
		f.orientation.set(128);
		assertEquals(first, f.frame(1));
		f.yaw.set(64);
		assertTrue(f.frame(2) > first);
	}

	@Test
	public void failedCaptureRetriesWithoutAdvancingTheCadence()
	{
		Fixture f = new Fixture(BillboardTargetType.GRAPHICS_OBJECT, 2, true);
		f.frame(0);
		f.identity.incrementAndGet();
		f.faceCount.set(0);
		f.frame(1);
		f.faceCount.set(1);
		int failed = f.faceReads.get();
		assertTrue(f.frame(2) > failed);
	}

	@Test
	public void diagnosticsDistinguishTheDisplayedCaptureFromTheLiveNativeFrame()
	{
		Fixture f = new Fixture(BillboardTargetType.NPC, 2, true);
		f.frame(0);
		f.frame(1);
		NpcSnapDebug.FrameDebugInfo held = f.debug.actorFrameDebugInfo((Actor) f.renderable);
		assertEquals(1, held.animationFrame);
		assertEquals(0, held.forcedAnimationFrame);
		f.frame(25);
		NpcSnapDebug.FrameDebugInfo refreshed = f.debug.actorFrameDebugInfo((Actor) f.renderable);
		assertEquals(25 % 7, refreshed.animationFrame);
		assertEquals(refreshed.animationFrame, refreshed.forcedAnimationFrame);
	}

	@Test
	public void cachedActorsUseTheWorldPlaneAndHoldTheirCapturedDimensions()
	{
		for (BillboardTargetType type : new BillboardTargetType[] {BillboardTargetType.NPC, BillboardTargetType.PLAYER})
		{
			Fixture f = new Fixture(type, 2, true);
			f.improved = true;
			f.pitch.set(2048);
			int captures = f.frame(0);
			Actor actor = (Actor) f.renderable;
			Rectangle source = new Rectangle(-50, -100, 101, 101);
			BillboardRenderRequest request = new BillboardRenderRequest(actor, actor.getModel(), actor.getLocalLocation(),
				0, 0, 0, 0, 7, 0, -1, -1, -1, false, false, VerticalAnchor.BOTTOM, null);
			BillboardDrawGeometry first = f.overlay.buildDrawGeometry(request, actor, source, source);
			BillboardDrawGeometry expected = ActorWorldPlane.project(new BillboardDepthCalculator(f.client),
				actor.getLocalLocation(), 0, ActorWorldPlane.viewingBearing(f.client, actor.getLocalLocation()), source, source);
			assertEquals(expected.bounds, first.bounds);
			for (double u : new double[] {0, 1})
			{
				for (double v : new double[] {0, 1})
				{
					assertEquals(expected.canvasPoint(u, v), first.canvasPoint(u, v));
					assertEquals(expected.projectedQuad.depthAt(u, v), first.projectedQuad.depthAt(u, v), 1e-8);
				}
			}
			f.size.set(3);
			assertEquals("The sprite must remain cached between scheduled redraws", captures, f.frame(1));
			assertEquals("Live dimensions must not deform the held capture", first.bounds,
				f.overlay.buildDrawGeometry(request, actor, source, source).bounds);
		}
	}

	private static final class Fixture
	{
		final AtomicInteger cycle = new AtomicInteger();
		final AtomicInteger identity = new AtomicInteger(7);
		final AtomicInteger size = new AtomicInteger(1);
		final AtomicInteger resizePercent = new AtomicInteger();
		final AtomicInteger faceCount = new AtomicInteger(1);
		final AtomicInteger color = new AtomicInteger(5000);
		final AtomicInteger faceReads = new AtomicInteger();
		final AtomicInteger orientation = new AtomicInteger();
		final AtomicInteger yaw = new AtomicInteger();
		final AtomicInteger pitch = new AtomicInteger();
		boolean improved;
		final Client client;
		final NpcSnapDebug debug;
		final NpcBillboardOverlay overlay;
		final WorldView view;
		Renderable renderable;
		final TileObject object;
		final BufferedImage canvas = new BufferedImage(320, 240, BufferedImage.TYPE_INT_ARGB);

		Fixture(BillboardTargetType type, int fps, boolean limited)
		{
			Model model = proxy(Model.class, method("getVerticesCount", 3), method("getModelHeight", 100),
				methodSupplier("getVerticesX", () -> new float[] {-50 * size.get(), 50 * size.get(), cycle.get() % 7}),
				method("getVerticesY", new float[] {0, 0, -100}), method("getVerticesZ", new float[3]),
				methodSupplier("getFaceCount", faceCount::get),
				methodSupplier("getFaceIndices1", () -> { faceReads.incrementAndGet(); return new int[] {0}; }),
				method("getFaceIndices2", new int[] {1}), method("getFaceIndices3", new int[] {2}),
				methodSupplier("getFaceColors1", () -> new int[] {color.get()}), methodSupplier("getFaceColors2", () -> new int[] {color.get()}),
				methodSupplier("getFaceColors3", () -> new int[] {color.get()}));
			Animation animation = proxy(Animation.class, methodSupplier("getId", identity::get), method("getNumFrames", 100));
			// Suppliers defer references until the fixture's renderable has been assigned.
			view = proxy(WorldView.class, method("getId", WorldView.TOPLEVEL), method("isTopLevel", true),
				method("contains", true), method("getSizeX", 16), method("getSizeY", 16),
				method("getTileSettings", new byte[4][16][16]), method("getTileHeights", new int[4][17][17]),
				methodSupplier("npcs", () -> proxy(IndexedObjectSet.class, methodSupplier("iterator", () ->
					renderable instanceof NPC ? Collections.singleton((NPC) renderable).iterator() : Collections.emptyIterator()))),
				methodSupplier("players", () -> proxy(IndexedObjectSet.class, methodSupplier("iterator", () ->
					type == BillboardTargetType.PLAYER ? Collections.singleton((Player) renderable).iterator() : Collections.emptyIterator()))),
				methodSupplier("getGraphicsObjects", () -> proxy(Deque.class, methodSupplier("iterator", () ->
					type == BillboardTargetType.GRAPHICS_OBJECT ? Collections.singleton(renderable).iterator() : Collections.emptyIterator()))));
			LocalPoint location = new LocalPoint(512, 1024);
			ActorSpotAnim spot = proxy(ActorSpotAnim.class, methodSupplier("getId", identity::get),
				methodSupplier("getFrame", () -> cycle.get() % 7), method("getModel", model), method("getModelHeight", 100));
			TestProxies.MethodResult[] actorMethods = {
				method("getWorldView", view), method("getLocalLocation", location), method("getModel", model),
				method("getModelHeight", 100), method("getFootprintSize", 1), methodSupplier("getId", identity::get),
				methodSupplier("getAnimation", identity::get), method("getPoseAnimation", -1),
				methodSupplier("getAnimationFrame", () -> cycle.get() % 7),
				method("getCanvasTilePoly", new Polygon(new int[] {150, 170, 170}, new int[] {100, 100, 120}, 3)),
				method("getSpotAnims", proxy(IterableHashTable.class, methodSupplier("iterator", () -> Collections.singleton(spot).iterator())))
			};
			Actor actor = type == BillboardTargetType.PLAYER ? proxy(Player.class, actorMethods) : proxy(NPC.class, actorMethods);
			Renderable selected;
			switch (type)
			{
				case PROJECTILE:
					selected = proxy(Projectile.class, methodSupplier("getId", identity::get), method("getModel", model),
						method("getModelHeight", 100), method("getX", 512d), method("getY", 1024d), method("getZ", 0d),
						method("getAnimation", animation), methodSupplier("getAnimationFrame", () -> cycle.get() % 7),
						methodSupplier("getOrientation", orientation::get)); break;
				case GRAPHICS_OBJECT:
					selected = proxy(GraphicsObject.class, methodSupplier("getId", identity::get), method("getModel", model),
						method("getModelHeight", 100), method("getLocation", location), method("getAnimation", animation),
						methodSupplier("getAnimationFrame", () -> cycle.get() % 7)); break;
				case GROUND_ITEM:
					selected = proxy(TileItem.class, methodSupplier("getId", identity::get), method("getQuantity", 1),
						method("getModel", model), method("getModelHeight", 100)); break;
				case TILE_OBJECT:
					selected = proxy(DynamicObject.class, method("getModel", model), method("getModelHeight", 100),
						method("getAnimation", animation), methodSupplier("getAnimFrame", () -> cycle.get() % 7)); break;
				default: selected = (Renderable) actor;
			}
			renderable = selected;
			object = type == BillboardTargetType.TILE_OBJECT ? proxy(GameObject.class, method("getWorldView", view),
				method("getId", 9), method("getLocalLocation", location), method("getSceneMinLocation", new Point(4, 8)),
				method("getSceneMaxLocation", new Point(4, 8)), method("getRenderable", renderable)) : null;
			Player local = proxy(Player.class, method("getWorldView", view), method("getLocalLocation", location));
			client = proxy(Client.class, method("getTopLevelWorldView", view), method("getWorldView", view),
				method("getLocalPlayer", local), method("getGameState", GameState.LOGGED_IN), method("isClientThread", true),
				method("getCameraX", 512), methodSupplier("getCameraZ", () -> improved ? -1024 : 0), method("getScale", 512), method("get3dZoom", 512),
				method("getViewportWidth", 320), method("getViewportHeight", 240), methodSupplier("getGameCycle", cycle::get),
				methodSupplier("getCameraPitch", pitch::get), methodSupplier("getCameraYaw", yaw::get), method("getProjectiles", proxy(Deque.class,
					methodSupplier("iterator", () -> type == BillboardTargetType.PROJECTILE
						? Collections.singleton(renderable).iterator() : Collections.emptyIterator()))));
			NpcSnapConfig config = new NpcSnapConfig()
			{
				@Override public boolean applyToNpcs() { return type == BillboardTargetType.NPC; }
				@Override public boolean applyToPlayers() { return type == BillboardTargetType.PLAYER; }
				@Override public boolean applyToObjects() { return type == BillboardTargetType.TILE_OBJECT; }
				@Override public boolean applyToGroundItems() { return type == BillboardTargetType.GROUND_ITEM; }
				@Override public boolean applyToProjectiles() { return type == BillboardTargetType.PROJECTILE; }
				@Override public boolean applyToGraphicsObjects() { return type == BillboardTargetType.GRAPHICS_OBJECT || type == BillboardTargetType.ACTOR_SPOT_ANIM; }
				@Override public boolean useInventorySpritesForGroundItems() { return false; }
				@Override public boolean useRetroOverheads() { return false; }
				@Override public boolean enableActorWorldPlaneProjection() { return improved; }
				@Override public boolean enableBillboardSpriteShadows() { return false; }
				@Override public boolean enablePlayerInteractionOutline() { return false; }
				@Override public boolean enableNpcInteractionOutline() { return false; }
				@Override public boolean enableGroundItemInteractionOutline() { return false; }
				@Override public boolean enableRotationSnapping() { return false; }
				@Override public boolean enableAnimationFrameSnapping() { return limited; }
				@Override public int animationFrameCount() { return fps; }
				@Override public int redrawOnClickboxResizePercent() { return resizePercent.get(); }
				@Override public BillboardOcclusionQuality billboardOcclusionQuality() { return BillboardOcclusionQuality.OFF; }
			};
			debug = new NpcSnapDebug(client, config);
			overlay = new NpcBillboardOverlay(client, null, config, debug, null);
			overlay.setActive(true);
			if (type == BillboardTargetType.GROUND_ITEM)
				overlay.trackGroundItem((TileItem) renderable, proxy(Tile.class, method("getLocalLocation", location)));
		}

		int frame(int gameCycle)
		{
			cycle.set(gameCycle);
			overlay.noteSceneRenderable(renderable);
			if (renderable instanceof Actor)
				for (ActorSpotAnim spot : ((Actor) renderable).getSpotAnims()) overlay.noteSceneRenderable(spot);
			if (object != null) overlay.observeTileObject(object);
			overlay.beginFrame();
			overlay.noteSceneRenderable(renderable);
			if (renderable instanceof Actor)
				for (ActorSpotAnim spot : ((Actor) renderable).getSpotAnims()) overlay.noteSceneRenderable(spot);
			overlay.prepareFrame(view);
			Graphics2D graphics = canvas.createGraphics();
			try { overlay.render(graphics); }
			finally { graphics.dispose(); }
			return faceReads.get();
		}
	}
}
