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
	public void liveBoundsAndGameCyclesCannotStretchTheDisplayedSprite()
	{
		Fixture f = new Fixture();
		BillboardDrawGeometry initial = f.draw(f.actor, 0);
		assertNotNull(initial.projectedQuad);
		f.halfWidth.set(100);
		f.halfHeight.set(200);
		assertEquals(initial.bounds, f.draw(f.actor, 0).bounds);
		f.cycle.incrementAndGet();
		assertEquals(initial.bounds, f.draw(f.actor, 0).bounds);
		assertEquals("Projection must not sample the live model", 0, f.measurements.get());
	}

	@Test
	public void spriteYawAndActorOrientationCannotStretchCachedBounds()
	{
		Fixture f = new Fixture();
		BillboardDrawGeometry initial = f.draw(f.actor, 0);
		for (int spriteYaw : new int[] {2048, 4096, 8192, 12288})
		{
			BillboardDrawGeometry changed = f.draw(f.actor, spriteYaw);
			assertEquals(initial.polygon().getBounds(), changed.polygon().getBounds());
			assertEquals(initial.projectedQuad.depthAt(0, 0), changed.projectedQuad.depthAt(0, 0), 1e-8);
		}
		f.orientation.set(512);
		BillboardDrawGeometry turned = f.draw(f.actor, 4096);
		assertEquals(initial.bounds, turned.bounds);
		assertEquals(initial.projectedQuad.depthAt(0, 0), turned.projectedQuad.depthAt(0, 0), 1e-8);
		assertEquals(initial.contentTopCenter(), turned.contentTopCenter());
	}

	@Test
	public void nestedActorsPreserveCapturedDimensionsAtTheirMainWorldLocation()
	{
		Fixture f = new Fixture();
		WorldView nested = proxy(WorldView.class);
		WorldEntity owner = proxy(WorldEntity.class, method("getWorldView", nested),
			method("getOrientation", 256), method("transformToMainWorld", f.location));
		f.owner = owner;
		Actor actor = proxy(Actor.class, method("getWorldView", nested), method("getModel", f.model),
			method("getLocalLocation", new LocalPoint(128, 128, nested)), method("getCurrentOrientation", 0));
		f.orientation.set(256);
		BillboardDrawGeometry a = f.draw(actor, 2048), b = f.draw(f.actor, 2048);
		assertNotNull(a.projectedQuad);
		assertEquals(b.bounds, a.bounds);
		assertEquals(b.projectedQuad.depthAt(0, 0), a.projectedQuad.depthAt(0, 0), 1e-8);
	}

	@Test
	public void toggleOffRestoresTheOriginalGeometryAndReenablingNeedsNoMeasurement()
	{
		Fixture f = new Fixture();
		assertNotNull(f.draw(f.actor, 2048).projectedQuad);
		f.enabled.set(false);
		assertNull(f.draw(f.actor, 2048).projectedQuad);
		f.overlay.clearBillboardCache();
		f.enabled.set(true);
		assertNotNull(f.draw(f.actor, 2048).projectedQuad);
	}

	private static final class Fixture
	{
		private final AtomicBoolean enabled = new AtomicBoolean(true);
		private final AtomicInteger cycle = new AtomicInteger();
		private final AtomicInteger measurements = new AtomicInteger();
		private final AtomicInteger halfWidth = new AtomicInteger(50);
		private final AtomicInteger halfHeight = new AtomicInteger(50);
		private final AtomicInteger orientation = new AtomicInteger();
		private WorldEntity owner;
		private final WorldView view = proxy(WorldView.class, method("isTopLevel", true),
			method("getTileSettings", new byte[4][16][16]), method("getTileHeights", new int[4][17][17]),
			methodSupplier("worldEntities", () -> proxy(IndexedObjectSet.class,
				methodSupplier("iterator", () -> owner == null ? Collections.emptyIterator() : Collections.singleton(owner).iterator()))));
		private final LocalPoint location = new LocalPoint(512, 1024, view);
		private final AABB box = proxy(AABB.class, methodSupplier("getExtremeX", halfWidth::get),
			method("getCenterY", -50), methodSupplier("getExtremeY", halfHeight::get), method("getExtremeZ", 10));
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

		private BillboardDrawGeometry draw(Actor target, int requestedYaw)
		{
			BillboardRenderRequest request = new BillboardRenderRequest(target, model, location, 0, 0,
				requestedYaw, 0, -1, -1, -1, -1, -1, false, false, VerticalAnchor.BOTTOM, null);
			Rectangle image = new Rectangle(-50, -100, 100, 100);
			BillboardDrawGeometry geometry = overlay.buildDrawGeometry(request, target, image, image);
			assertNotNull(geometry);
			return geometry;
		}
	}
}
