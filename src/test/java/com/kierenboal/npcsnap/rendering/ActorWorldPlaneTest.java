package com.kierenboal.npcsnap.rendering;

import java.awt.Rectangle;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;

import static com.kierenboal.npcsnap.TestProxies.method;
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
			Rectangle content = new Rectangle(-30, -100, 80, 100);
			Rectangle image = new Rectangle(-40, -110, 100, 120);
			BillboardDrawGeometry geometry = ActorWorldPlane.project(camera, new LocalPoint(0, 1000), 0,
				0, image, content);
			assertNotNull(geometry);
			assertEquals(new Point(385, 250), geometry.canvasPoint(0.1, 1.0 / 12.0));
			assertEquals(new Point(425, 300), geometry.canvasPoint(0.9, 11.0 / 12.0));
			assertEquals(new Point(405, 250), geometry.contentTopCenter());
			assertEquals(new Point(380, 245), geometry.canvasPoint(0, 0));
			assertEquals(new Point(430, 305), geometry.canvasPoint(1, 1));
			assertEquals(new Point(400, 300), geometry.canvasPoint(0.4, 11.0 / 12.0));
			assertTrue(geometry.bounds.contains(geometry.polygon().getBounds()));
		}
	}

	@Test
	public void perspectiveMappingRoundTripsAndDepthVariesAcrossARow()
	{
		BillboardDepthCalculator camera = camera(false, 0, 0);
		Rectangle image = new Rectangle(-150, -240, 300, 240);
		BillboardDrawGeometry geometry = ActorWorldPlane.project(camera, new LocalPoint(-400, 1000),
			0, Math.atan2(400, 1000), image, image);
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
		Rectangle image = new Rectangle(-50, -160, 100, 160);
		for (int yaw : new int[] {0, 512, 1024})
		{
			for (int pitch : new int[] {0, 1024, 2048, 3072})
			{
				BillboardDrawGeometry cpu = ActorWorldPlane.project(camera(false, yaw, pitch), new LocalPoint(500, 1500), 0,
					Math.atan2(-500, 1500), image, image);
				BillboardDrawGeometry gpu = ActorWorldPlane.project(camera(true, yaw, pitch), new LocalPoint(500, 1500), 0,
					Math.atan2(-500, 1500), image, image);
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
	public void planeFacesCameraAndPreservesCapturedSizeThroughoutAnOrbit()
	{
		Rectangle image = new Rectangle(-50, -100, 100, 100);
		LocalPoint location = new LocalPoint(0, 1000);
		for (boolean gpu : new boolean[] {false, true})
		{
			for (int yaw = 0; yaw < 16384; yaw += 2048)
			{
				double angle = yaw * 2.0 * Math.PI / 16384;
				int cameraX = (int) Math.round(1000 * Math.sin(angle));
				int cameraY = (int) Math.round(1000 - 1000 * Math.cos(angle));
				Client client = cameraClient(gpu, yaw, 0, cameraX, cameraY);
				double bearing = ActorWorldPlane.viewingBearing(client, location);
				BillboardDrawGeometry geometry = ActorWorldPlane.project(new BillboardDepthCalculator(client), location,
					0, bearing, image, image);
				assertNotNull(geometry);
				// Raster bounds round outward; measure the projected corners instead.
				assertEquals(50, geometry.canvasPoint(1, 0).getX() - geometry.canvasPoint(0, 0).getX(), 1);
				assertEquals(50, geometry.canvasPoint(0, 1).getY() - geometry.canvasPoint(0, 0).getY(), 1);
				assertEquals(geometry.projectedQuad.depthAt(0, 0), geometry.projectedQuad.depthAt(1, 0), 0.1);
				assertEquals(geometry.projectedQuad.depthAt(0.5, 0), geometry.projectedQuad.depthAt(0.5, 1), 1e-8);
			}
		}
	}

	@Test
	public void coincidentCameraUsesItsCpuOrGpuYawAndPositionConventions()
	{
		LocalPoint location = new LocalPoint(512, 1024);
		Client cpu = proxy(Client.class, method("getCameraX", 512), method("getCameraY", 1024),
			method("getCameraYaw", 4096), method("getCameraFpYaw", 0.7f));
		assertEquals(Math.PI / 2, ActorWorldPlane.viewingBearing(cpu, location), 1e-8);
		Client gpu = proxy(Client.class, method("isGpu", true), method("getCameraFpX", 512.0f),
			method("getCameraFpY", 1024.0f), method("getCameraYaw", 4096), method("getCameraFpYaw", 0.7f));
		assertEquals(0.7f, ActorWorldPlane.viewingBearing(gpu, location), 1e-8);
		Client offsetGpu = proxy(Client.class, method("isGpu", true), method("getCameraX", 512),
			method("getCameraY", 1024), method("getCameraFpX", 112.0f), method("getCameraFpY", 24.0f));
		assertEquals(Math.atan2(-400, 1000), ActorWorldPlane.viewingBearing(offsetGpu, location), 1e-8);
	}

	@Test
	public void differentCapturesSupplyTheirOwnWidthHeightAndOrigin()
	{
		Rectangle image = new Rectangle(-50, -100, 100, 100);
		Rectangle next = new Rectangle(-10, -200, 50, 200);
		BillboardDrawGeometry a = ActorWorldPlane.project(camera(false, 0, 0), new LocalPoint(0, 1000), 0, 0, image, image);
		BillboardDrawGeometry b = ActorWorldPlane.project(camera(false, 0, 0), new LocalPoint(0, 1000), 0, 0, next, next);
		assertEquals(50, a.bounds.width);
		assertEquals(50, a.bounds.height);
		assertEquals(25, b.bounds.width);
		assertEquals(100, b.bounds.height);
		assertEquals(new Point(395, 200), b.canvasPoint(0, 0));
		assertEquals(new Point(420, 300), b.canvasPoint(1, 1));
	}

	@Test
	public void invalidNearPlaneTinyAndOversizedProjectionsAreRejected()
	{
		Rectangle image = new Rectangle(-100, -200, 200, 200);
		assertNull(ActorWorldPlane.project(camera(false, 0, 0), new LocalPoint(50, 50), 0,
			-Math.PI / 4, image, image));
		assertNull(ActorWorldPlane.project(camera(false, 0, 0), new LocalPoint(0, 1000000), 0,
			0, image, image));
		Rectangle oversized = new Rectangle(-10000, -20000, 20000, 20000);
		assertNull(ActorWorldPlane.project(camera(false, 0, 0), new LocalPoint(0, 1000), 0, 0, oversized, oversized));
		assertNull(ActorWorldPlane.project(camera(false, 0, 0), null, 0, 0, image, image));
		assertNull(ActorWorldPlane.project(camera(false, 0, 0), new LocalPoint(0, 1000), Double.NaN,
			0, image, image));
	}

	private static BillboardDepthCalculator camera(boolean gpu, int yaw, int pitch)
	{
		return new BillboardDepthCalculator(cameraClient(gpu, yaw, pitch, 0, 0));
	}

	private static Client cameraClient(boolean gpu, int yaw, int pitch, int cameraX, int cameraY)
	{
		return proxy(Client.class, method("isGpu", gpu),
			method("getCameraX", cameraX), method("getCameraY", cameraY),
			method("getCameraFpX", (float) cameraX), method("getCameraFpY", (float) cameraY),
			method("getCameraYaw", yaw), method("getCameraPitch", pitch),
			method("getCameraFpYaw", (float) (yaw * 2.0 * Math.PI / 16384)),
			method("getCameraFpPitch", (float) (pitch * 2.0 * Math.PI / 16384)),
			method("getScale", 500), method("getViewportWidth", 800), method("getViewportHeight", 600));
	}
}
