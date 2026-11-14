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

	@Test
	public void overheadPitchSweepRetainsHeightAnchorAndPerspectiveDepth()
	{
		Rectangle image = new Rectangle(-50, -160, 100, 160);
		LocalPoint location = new LocalPoint(0, 1000);
		for (boolean gpu : new boolean[] {false, true})
		{
			double previousHeight = Double.NaN;
			for (int pitch = 3000; pitch <= 4200; pitch++)
			{
				double angle = pitch * 2.0 * Math.PI / 16384;
				int cameraY = (int) Math.round(1000 - 1000 * Math.cos(angle));
				int cameraZ = (int) Math.round(-1000 * Math.sin(angle));
				Client client = proxy(Client.class, method("isGpu", gpu),
					method("getCameraY", cameraY), method("getCameraFpY", (float) cameraY),
					method("getCameraZ", cameraZ), method("getCameraFpZ", (float) cameraZ),
					method("getCameraPitch", pitch), method("getCameraFpPitch", (float) (pitch * 2.0 * Math.PI / 16384)),
					method("getScale", 500), method("getViewportWidth", 800), method("getViewportHeight", 600));
				BillboardDepthCalculator camera = new BillboardDepthCalculator(client);
				BillboardDrawGeometry geometry = ActorWorldPlane.project(camera, location, 0, 0, image, image);
				assertNotNull("pitch=" + pitch, geometry);
				BillboardCanvasPoint base = camera.projectCanvasPoint(0, 1000, 0);
				Point foot = geometry.canvasPoint(0.5, 1);
				assertEquals(base.x, foot.getX(), 0.51);
				assertEquals(base.y, foot.getY(), 0.51);
				BillboardCanvasPoint top = camera.projectCanvasPoint(0, 1000, -160);
				double height = foot.getY() - geometry.contentTopCenter().getY();
				assertTrue(height >= 0.25 * 160 * 500 / base.depth - 1.0);
				assertEquals(top.depth, geometry.projectedQuad.depthAt(0.5, 0), 1e-8);
				assertEquals(base.depth, geometry.projectedQuad.depthAt(0.5, 1), 1e-8);
				Point midpoint = geometry.canvasPoint(0.5, 0.5);
				assertTrue(geometry.projectedQuad.sample(midpoint.getX(), midpoint.getY(), new BillboardProjectedQuad.Sample()));
				if (Double.isFinite(previousHeight)) assertTrue(Math.abs(height - previousHeight) <= 2);
				previousHeight = height;
			}
		}
	}

	@Test
	public void offCentreActorsNeverTurnEdgeOnOrReverseWindingDuringTheHeightClamp()
	{
		Rectangle image = new Rectangle(-50, -160, 100, 160);
		for (boolean gpu : new boolean[] {false, true})
		{
			for (int offsetX : new int[] {-200, 200})
			{
				LocalPoint location = new LocalPoint(offsetX, 700);
				for (int pitch = 2800; pitch <= 4096; pitch += 8)
				{
					double angle = pitch * 2.0 * Math.PI / 16384;
					int cameraY = (int) Math.round(1000 - 1000 * Math.cos(angle));
					int cameraZ = (int) Math.round(-1000 * Math.sin(angle));
					Client client = proxy(Client.class, method("isGpu", gpu),
						method("getCameraY", cameraY), method("getCameraFpY", (float) cameraY),
						method("getCameraZ", cameraZ), method("getCameraFpZ", (float) cameraZ),
						method("getCameraPitch", pitch), method("getCameraFpPitch", (float) angle),
						method("getScale", 500), method("getViewportWidth", 800), method("getViewportHeight", 600));
					BillboardDepthCalculator camera = new BillboardDepthCalculator(client);
					double bearing = ActorWorldPlane.viewingBearing(client, location);
					BillboardDrawGeometry geometry = ActorWorldPlane.project(camera, location, 0, bearing, image, image);
					String context = "gpu=" + gpu + " x=" + offsetX + " pitch=" + pitch;
					assertNotNull(context, geometry);
					Point left = geometry.canvasPoint(0, 0.5), right = geometry.canvasPoint(1, 0.5);
					Point top = geometry.canvasPoint(0.5, 0), bottom = geometry.canvasPoint(0.5, 1);
					double widthX = right.getX() - left.getX(), widthY = right.getY() - left.getY();
					double heightX = bottom.getX() - top.getX(), heightY = bottom.getY() - top.getY();
					double signedHeight = (widthX * heightY - widthY * heightX) / Math.hypot(widthX, widthY);
					BillboardCanvasPoint base = camera.projectCanvasPoint(offsetX, 700, 0);
					assertTrue(context + " signedHeight=" + signedHeight,
						signedHeight >= 0.25 * 160 * 500 / base.depth - 1.5);
					assertEquals(base.x, bottom.getX(), 0.51);
					assertEquals(base.y, bottom.getY(), 0.51);
					for (double u : new double[] {0, 0.5, 1})
					{
						double horizontal = image.x + image.width * u;
						BillboardCanvasPoint rawFoot = camera.projectCanvasPoint(offsetX + Math.cos(bearing) * horizontal,
							700 + Math.sin(bearing) * horizontal, 0);
						Point foot = geometry.canvasPoint(u, 1);
						assertEquals(context + " projected baseline scale changed",
							Math.hypot(rawFoot.x - base.x, rawFoot.y - base.y),
							Math.hypot(foot.getX() - base.x, foot.getY() - base.y), 1.0);
						assertEquals(context + " baseline rolled", base.y, foot.getY(), 0.51);
						for (double v : new double[] {0, 0.5, 1})
						{
							BillboardCanvasPoint raw = camera.projectCanvasPoint(offsetX + Math.cos(bearing) * horizontal,
								700 + Math.sin(bearing) * horizontal, image.y + image.height * v);
							assertEquals(context, raw.depth, geometry.projectedQuad.depthAt(u, v), 1e-8);
						}
					}
					Point middle = geometry.canvasPoint(0.5, 0.5);
					BillboardProjectedQuad.Sample sample = new BillboardProjectedQuad.Sample();
					assertTrue(context, geometry.projectedQuad.sample(middle.getX(), middle.getY(), sample));
					assertEquals(context, 0.5, sample.u, 0.035);
					assertEquals(context, 0.5, sample.v, 0.035);
					double ratio = ActorWorldPlane.heightRatio(camera, location, 0, bearing);
					assertTrue(context, ratio >= 0.25 && ratio <= 1.0);
				}
			}
		}
	}

	@Test
	public void realViewingBearingKeepsSpritesUprightThroughPitch4160()
	{
		Rectangle image = new Rectangle(-50, -160, 100, 160);
		for (boolean gpu : new boolean[] {false, true})
		{
			for (int yaw : new int[] {0, 4096, 8192, 12288})
			{
				double yawAngle = yaw * 2.0 * Math.PI / 16384;
				for (int side : new int[] {-200, 0, 200})
				{
					// Test the followed player and actors displaced toward the camera.
					int forwardOffset = side == 0 ? 0 : -300;
					LocalPoint location = new LocalPoint(
						(int) Math.round(side * Math.cos(yawAngle) - forwardOffset * Math.sin(yawAngle)),
						1000 + (int) Math.round(side * Math.sin(yawAngle) + forwardOffset * Math.cos(yawAngle)));
					Point previousTop = null;
					for (int pitch = 2800; pitch <= 4200; pitch += 4)
					{
						double pitchAngle = pitch * 2.0 * Math.PI / 16384;
						int cameraX = (int) Math.round(1000 * Math.cos(pitchAngle) * Math.sin(yawAngle));
						int cameraY = 1000 - (int) Math.round(1000 * Math.cos(pitchAngle) * Math.cos(yawAngle));
						int cameraZ = (int) Math.round(-1000 * Math.sin(pitchAngle));
						Client client = proxy(Client.class, method("isGpu", gpu),
							method("getCameraX", cameraX), method("getCameraFpX", (float) cameraX),
							method("getCameraY", cameraY), method("getCameraFpY", (float) cameraY),
							method("getCameraZ", cameraZ), method("getCameraFpZ", (float) cameraZ),
							method("getCameraYaw", yaw), method("getCameraFpYaw", (float) yawAngle),
							method("getCameraPitch", pitch), method("getCameraFpPitch", (float) pitchAngle),
							method("getScale", 500), method("getViewportWidth", 800), method("getViewportHeight", 600));
						BillboardDepthCalculator camera = new BillboardDepthCalculator(client);
						double bearing = ActorWorldPlane.viewingBearing(client, location);
						BillboardDrawGeometry geometry = ActorWorldPlane.project(camera, location, 0, bearing, image, image);
						String context = "gpu=" + gpu + " yaw=" + yaw + " side=" + side + " pitch=" + pitch;
						assertNotNull(context, geometry);
						Point left = geometry.canvasPoint(0, 1), right = geometry.canvasPoint(1, 1);
						Point top = geometry.canvasPoint(0.5, 0), feet = geometry.canvasPoint(0.5, 1);
						assertTrue(context + " left/right reversed", right.getX() > left.getX());
						assertEquals(context + " baseline rolled", left.getY(), right.getY(), 1.0);
						assertTrue(context + " head below feet", top.getY() < feet.getY());
						if (previousTop != null)
						{
							assertTrue(context + " head jumped horizontally", Math.abs(top.getX() - previousTop.getX()) <= 3);
							assertTrue(context + " head jumped vertically", Math.abs(top.getY() - previousTop.getY()) <= 3);
						}
						previousTop = top;
					}
				}
			}
		}
	}

	@Test
	public void shadowCoverageStaysAtDefaultAcrossPitchChanges()
	{
		assertEquals(BillboardOutlineRenderer.DEFAULT_SPRITE_SHADOW_HEIGHT_RATIO,
			ActorWorldPlane.shadowHeightRatio(1), 1e-10);
		assertEquals(BillboardOutlineRenderer.DEFAULT_SPRITE_SHADOW_HEIGHT_RATIO,
			ActorWorldPlane.shadowHeightRatio(0.625), 1e-10);
		assertEquals(BillboardOutlineRenderer.DEFAULT_SPRITE_SHADOW_HEIGHT_RATIO,
			ActorWorldPlane.shadowHeightRatio(0.25), 1e-10);
		assertEquals(BillboardOutlineRenderer.DEFAULT_SPRITE_SHADOW_HEIGHT_RATIO,
			ActorWorldPlane.shadowHeightRatio(0), 1e-10);
		assertEquals(BillboardOutlineRenderer.DEFAULT_SPRITE_SHADOW_HEIGHT_RATIO,
			ActorWorldPlane.shadowHeightRatio(2), 1e-10);
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
