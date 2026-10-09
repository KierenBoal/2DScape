package com.kierenboal.npcsnap.rendering;

import java.awt.Rectangle;
import net.runelite.api.AABB;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;

import static com.kierenboal.npcsnap.TestProxies.method;
import static com.kierenboal.npcsnap.TestProxies.methodSupplier;
import static com.kierenboal.npcsnap.TestProxies.proxy;
import static org.junit.Assert.*;

public class ActorWorldPlaneTest
{
	@Test
	public void asymmetricBoundsAndPaddingMapIntoTheSameWorldPlane()
	{
		for (boolean gpu : new boolean[] {false, true})
		{
			BillboardDepthCalculator camera = camera(gpu, 0, 0);
			ActorWorldPlane plane = new ActorWorldPlane(20, -80, 10, 50, 80, 10);
			Rectangle content = new Rectangle(-40, -100, 80, 100);
			Rectangle image = new Rectangle(-50, -110, 100, 120);
			BillboardDrawGeometry geometry = plane.project(camera, new LocalPoint(0, 1000), 0,
				0, 0, 0, image, content);
			assertNotNull(geometry);
			// The unpadded top-left maps to the asymmetric live box's (-30, -160).
			assertEquals(new Point(385, 220), geometry.canvasPoint(0.1, 1.0 / 12.0));
			assertEquals(new Point(435, 300), geometry.canvasPoint(0.9, 11.0 / 12.0));
			assertEquals(new Point(410, 220), geometry.contentTopCenter());
			assertTrue(geometry.bounds.contains(geometry.polygon().getBounds()));
		}
	}

	@Test
	public void perspectiveMappingRoundTripsAndDepthVariesAcrossARow()
	{
		BillboardDepthCalculator camera = camera(false, 0, 0);
		Rectangle image = new Rectangle(-150, -240, 300, 240);
		BillboardDrawGeometry geometry = new ActorWorldPlane(0, -120, 0, 150, 120, 20)
			.project(camera, new LocalPoint(0, 1000), 0, 0, 2048, 0, image, image);
		assertNotNull(geometry);
		BillboardProjectedQuad.Sample sample = new BillboardProjectedQuad.Sample();
		for (double u : new double[] {0.1, 0.3, 0.5, 0.9})
		{
			for (double v : new double[] {0.1, 0.5, 0.9})
			{
				Point point = geometry.canvasPoint(u, v);
				assertTrue(geometry.projectedQuad.sample(point.getX(), point.getY(), sample));
				assertEquals(u, sample.u, 0.015);
				assertEquals(v, sample.v, 0.015);
				assertEquals(geometry.projectedQuad.depthAt(sample.u, sample.v), sample.depth, 1e-7);
			}
		}
		Point left = geometry.canvasPoint(0.2, 0.5), right = geometry.canvasPoint(0.8, 0.5);
		assertTrue(geometry.projectedQuad.sample(left.getX(), 240, sample));
		double leftDepth = sample.depth;
		assertTrue(geometry.projectedQuad.sample(right.getX(), 240, sample));
		assertTrue(sample.depth > leftDepth);
		assertFalse(geometry.projectedQuad.sample(-1000, 0, sample));
	}

	@Test
	public void cameraPitchAndYawAgreeBetweenCpuAndGpu()
	{
		ActorWorldPlane plane = new ActorWorldPlane(20, -80, 0, 50, 80, 10);
		Rectangle image = new Rectangle(-50, -160, 100, 160);
		for (int yaw : new int[] {0, 512, 1024})
		{
			for (int pitch : new int[] {0, 1024, 2048, 3072})
			{
				BillboardDrawGeometry cpu = plane.project(camera(false, yaw, pitch), new LocalPoint(500, 1500), 0,
					256, 2048, -0.3, image, image);
				BillboardDrawGeometry gpu = plane.project(camera(true, yaw, pitch), new LocalPoint(500, 1500), 0,
					256, 2048, -0.3, image, image);
				assertNotNull(cpu);
				assertNotNull(gpu);
				for (double u : new double[] {0, 0.5, 1})
				{
					Point a = cpu.canvasPoint(u, 0.5), b = gpu.canvasPoint(u, 0.5);
					assertEquals(a.getX(), b.getX(), 1.0);
					assertEquals(a.getY(), b.getY(), 1.0);
				}
			}
		}
	}

	@Test
	public void capturedViewTurnsWithActorButNeverExceedsSixtyDegrees()
	{
		assertEquals(Math.PI / 4, ActorWorldPlane.rightBearing(2048, 0, 0), 1e-8);
		assertEquals(0, ActorWorldPlane.rightBearing(2048, 256, 0), 1e-8);
		assertEquals(-Math.PI / 4, ActorWorldPlane.rightBearing(2048, 512, 0), 1e-8);
		assertEquals(Math.PI / 3, Math.abs(ActorWorldPlane.rightBearing(8192, 0, 0)), 1e-8);
		// Equivalent full-circle orientations cannot introduce a discontinuity.
		assertEquals(ActorWorldPlane.rightBearing(2048, 0, 0), ActorWorldPlane.rightBearing(2048, 2048, 0), 1e-8);
	}

	@Test
	public void snapshotsDoNotRetainMutableAabbData()
	{
		java.util.concurrent.atomic.AtomicInteger width = new java.util.concurrent.atomic.AtomicInteger(50);
		AABB box = proxy(AABB.class, methodSupplier("getExtremeX", width::get),
			method("getCenterY", -50), method("getExtremeY", 50), method("getExtremeZ", 10));
		Model model = proxy(Model.class, method("getAABB", box));
		ActorWorldPlane before = ActorWorldPlane.sample(model);
		width.set(100);
		ActorWorldPlane after = ActorWorldPlane.sample(model);
		Rectangle image = new Rectangle(-50, -100, 100, 100);
		BillboardDrawGeometry a = before.project(camera(false, 0, 0), new LocalPoint(0, 1000), 0, 0, 0, 0, image, image);
		BillboardDrawGeometry b = after.project(camera(false, 0, 0), new LocalPoint(0, 1000), 0, 0, 0, 0, image, image);
		assertEquals(50, a.bounds.width);
		assertEquals(100, b.bounds.width);
		assertNull(ActorWorldPlane.sample(null));
		assertNull(ActorWorldPlane.sample(proxy(Model.class)));
	}

	@Test
	public void invalidNearPlaneTinyAndOversizedProjectionsAreRejected()
	{
		Rectangle image = new Rectangle(-100, -200, 200, 200);
		ActorWorldPlane plane = new ActorWorldPlane(0, -100, 0, 100, 100, 10);
		assertNull(plane.project(camera(false, 0, 0), new LocalPoint(0, 50), 0,
			0, 2048, 0, image, image));
		assertNull(plane.project(camera(false, 0, 0), new LocalPoint(0, 1000000), 0,
			0, 0, 0, image, image));
		assertNull(new ActorWorldPlane(0, -10000, 0, 10000, 10000, 0)
			.project(camera(false, 0, 0), new LocalPoint(0, 1000), 0, 0, 0, 0, image, image));
		assertNull(plane.project(camera(false, 0, 0), null, 0, 0, 0, 0, image, image));
		assertNull(plane.project(camera(false, 0, 0), new LocalPoint(0, 1000), Double.NaN,
			0, 0, 0, image, image));
	}

	private static BillboardDepthCalculator camera(boolean gpu, int yaw, int pitch)
	{
		return new BillboardDepthCalculator(proxy(Client.class, method("isGpu", gpu),
			method("getCameraYaw", yaw), method("getCameraPitch", pitch),
			method("getCameraFpYaw", (float) (yaw * 2.0 * Math.PI / 16384)),
			method("getCameraFpPitch", (float) (pitch * 2.0 * Math.PI / 16384)),
			method("getScale", 500), method("getViewportWidth", 800), method("getViewportHeight", 600)));
	}
}
