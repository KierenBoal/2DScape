package com.kierenboal.npcsnap;

import com.kierenboal.npcsnap.rendering.AnimationFrameSnapper;
import com.kierenboal.npcsnap.rendering.BillboardDrawGeometry;
import com.kierenboal.npcsnap.rendering.BillboardRenderRequest;
import com.kierenboal.npcsnap.rendering.VerticalAnchor;
import java.awt.Rectangle;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.AABB;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.Model;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;

import static com.kierenboal.npcsnap.TestProxies.method;
import static com.kierenboal.npcsnap.TestProxies.methodSupplier;
import static com.kierenboal.npcsnap.TestProxies.proxy;
import static org.junit.Assert.*;

public class NpcBillboardWorldPlaneTest
{
	@Test
	public void preSnapBoundsAreCopiedOncePerCycleAndMissingBoundsFallBack()
	{
		Fixture f = new Fixture();
		assertNull(f.draw(f.actor, 0, 0).projectedQuad);
		f.overlay.captureActorWorldPlane(f.actor);
		BillboardDrawGeometry initial = f.draw(f.actor, 0, 0);
		assertNotNull(initial.projectedQuad);
		assertEquals(1, f.measurements.get());
		// A snapped model, or a later model lookup, can overwrite its AABB backing data.
		f.halfWidth.set(100);
		f.overlay.captureActorWorldPlane(f.actor);
		assertEquals(initial.bounds, f.draw(f.actor, 0, 0).bounds);
		assertEquals(1, f.measurements.get());
		f.cycle.incrementAndGet();
		assertNull("Previous-cycle measurements cannot be used", f.draw(f.actor, 0, 0).projectedQuad);
		f.overlay.captureActorWorldPlane(f.actor);
		assertEquals(2, f.measurements.get());
		assertTrue(f.draw(f.actor, 0, 0).bounds.width > initial.bounds.width);
		f.overlay.clearInteractionIfMatches(f.actor);
		assertNull(f.draw(f.actor, 0, 0).projectedQuad);
	}

	@Test
	public void geometryUsesDisplayedYawWhileRequestWaitsForRedraw()
	{
		Fixture f = new Fixture();
		f.overlay.captureActorWorldPlane(f.actor);
		BillboardDrawGeometry initial = f.draw(f.actor, 0, 0);
		BillboardDrawGeometry held = f.draw(f.actor, 2048, 0);
		BillboardDrawGeometry refreshed = f.draw(f.actor, 2048, 2048);
		assertEquals(initial.polygon().getBounds(), held.polygon().getBounds());
		assertEquals(initial.projectedQuad.depthAt(0, 0), held.projectedQuad.depthAt(0, 0), 1e-8);
		assertNotEquals(held.projectedQuad.depthAt(0, 0), refreshed.projectedQuad.depthAt(0, 0), 1e-8);
		f.orientation.set(256);
		assertNotEquals(held.projectedQuad.depthAt(0, 0), f.draw(f.actor, 2048, 0).projectedQuad.depthAt(0, 0), 1e-8);
	}

	@Test
	public void nestedOrientationIncludesTheOwningWorldEntity()
	{
		Fixture f = new Fixture();
		WorldView nested = proxy(WorldView.class);
		WorldEntity owner = proxy(WorldEntity.class, method("getWorldView", nested),
			method("getOrientation", 256), method("transformToMainWorld", f.location));
		f.owner = owner;
		Actor actor = proxy(Actor.class, method("getWorldView", nested), method("getModel", f.model),
			method("getLocalLocation", new LocalPoint(128, 128, nested)), method("getCurrentOrientation", 0));
		f.overlay.captureActorWorldPlane(actor);
		f.orientation.set(256);
		f.overlay.captureActorWorldPlane(f.actor);
		BillboardDrawGeometry a = f.draw(actor, 2048, 2048), b = f.draw(f.actor, 2048, 2048);
		assertNotNull(a.projectedQuad);
		assertEquals(b.bounds, a.bounds);
		assertEquals(b.projectedQuad.depthAt(0, 0), a.projectedQuad.depthAt(0, 0), 1e-8);
	}

	@Test
	public void toggleOffRestoresTheOriginalGeometryAndClearDropsMeasurements()
	{
		Fixture f = new Fixture();
		f.overlay.captureActorWorldPlane(f.actor);
		assertNotNull(f.draw(f.actor, 2048, 2048).projectedQuad);
		f.enabled.set(false);
		assertNull(f.draw(f.actor, 2048, 2048).projectedQuad);
		f.overlay.clearBillboardCache();
		f.enabled.set(true);
		assertNull(f.draw(f.actor, 2048, 2048).projectedQuad);
		f.overlay.captureActorWorldPlane(f.actor);
		assertNotNull(f.draw(f.actor, 2048, 2048).projectedQuad);
	}

	private static final class Fixture
	{
		private final AtomicBoolean enabled = new AtomicBoolean(true);
		private final AtomicInteger cycle = new AtomicInteger();
		private final AtomicInteger measurements = new AtomicInteger();
		private final AtomicInteger halfWidth = new AtomicInteger(50);
		private final AtomicInteger orientation = new AtomicInteger();
		private WorldEntity owner;
		private final WorldView view = proxy(WorldView.class, method("isTopLevel", true),
			method("getTileSettings", new byte[4][16][16]), method("getTileHeights", new int[4][17][17]),
			methodSupplier("worldEntities", () -> proxy(IndexedObjectSet.class,
				methodSupplier("iterator", () -> owner == null ? Collections.emptyIterator() : Collections.singleton(owner).iterator()))));
		private final LocalPoint location = new LocalPoint(512, 1024, view);
		private final AABB box = proxy(AABB.class, methodSupplier("getExtremeX", halfWidth::get),
			method("getCenterY", -50), method("getExtremeY", 50), method("getExtremeZ", 10));
		private final Model model = proxy(Model.class, methodSupplier("getAABB", () -> {
			measurements.incrementAndGet(); return box;
		}));
		private final Actor actor = proxy(Actor.class, method("getWorldView", view), method("getModel", model),
			method("getLocalLocation", location), method("getFootprintSize", 1),
			methodSupplier("getCurrentOrientation", orientation::get));
		private final Client client = proxy(Client.class, method("getTopLevelWorldView", view), method("getWorldView", view),
			method("getCameraX", 512), method("getCameraZ", -300), method("getScale", 512),
			method("getViewportWidth", 800), method("getViewportHeight", 600), methodSupplier("getGameCycle", cycle::get));
		private final NpcSnapConfig config = new NpcSnapConfig()
		{
			@Override public boolean enableActorWorldPlaneProjection() { return enabled.get(); }
			@Override public boolean enableAnimationFrameSnapping() { return false; }
		};
		private final NpcBillboardOverlay overlay = new NpcBillboardOverlay(client, null, config,
			new NpcSnapDebug(client, config), new AnimationFrameSnapper(client), null);

		private BillboardDrawGeometry draw(Actor target, int requestedYaw, int capturedYaw)
		{
			BillboardRenderRequest request = new BillboardRenderRequest(target, model, location, 0, 0,
				requestedYaw, 0, -1, -1, -1, -1, -1, false, false, VerticalAnchor.BOTTOM, null);
			Rectangle image = new Rectangle(-50, -100, 100, 100);
			BillboardDrawGeometry geometry = overlay.buildDrawGeometry(request, target, image, image, capturedYaw);
			assertNotNull(geometry);
			return geometry;
		}
	}
}
