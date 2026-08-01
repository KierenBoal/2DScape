package com.kierenboal.npcsnap.rendering;

import com.kierenboal.npcsnap.TestProxies;

import java.awt.Rectangle;
import net.runelite.api.Client;
import net.runelite.api.Model;
import org.junit.Test;

import static com.kierenboal.npcsnap.TestProxies.method;
import static com.kierenboal.npcsnap.TestProxies.proxy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BillboardDepthSurfaceTest
{
	@Test
	public void pixelsUseWorldVerticalPlaneThroughLocalAnchor()
	{
		BillboardDepthCalculator calculator = new BillboardDepthCalculator(client(0, 2048));
		BillboardDepthSurface surface = new BillboardDepthSurface(
			calculator,
			0,
			0,
			0.0d,
			100,
			new Rectangle(-10, -100, 20, 100),
			new Rectangle(0, 0, 20, 100),
			0,
			0
		);

		double centerPixelDepth = surface.depthAt(9, 49, 20, 100);
		double anchorDepth = calculator.cameraForwardDepthOnVerticalPlane(0, 0, 50);

		assertEquals(anchorDepth, centerPixelDepth, 0.01d);
	}

	@Test
	public void topAndBottomPixelsFollowVerticalPlaneWithCameraPitch()
	{
		BillboardDepthSurface surface = new BillboardDepthSurface(
			new BillboardDepthCalculator(client(0, 2048)),
			0,
			0,
			0.0d,
			100,
			new Rectangle(-10, -100, 20, 100),
			new Rectangle(0, 0, 20, 100),
			0,
			0
		);

		assertTrue(surface.depthAt(10, 0, 20, 100) < surface.depthAt(10, 99, 20, 100));
	}

	@Test
	public void leftAndRightPixelsDoNotDriftAcrossCameraFacingPlane()
	{
		BillboardDepthSurface surface = new BillboardDepthSurface(
			new BillboardDepthCalculator(client(0, 2048)),
			0,
			0,
			0.0d,
			100,
			new Rectangle(-10, -100, 20, 100),
			new Rectangle(0, 0, 20, 100),
			0,
			0
		);

		double leftDepth = surface.depthAt(0, 50, 20, 100);
		double rightDepth = surface.depthAt(19, 50, 20, 100);

		assertTrue(Math.abs(leftDepth - rightDepth) < 1.0d);
	}

	@Test
	public void fartherVerticalPlaneStaysBehindAtEveryScreenRow()
	{
		BillboardDepthCalculator calculator = new BillboardDepthCalculator(client(0, 2048));
		BillboardDepthSurface near = new BillboardDepthSurface(
			calculator, 0, 0, 0.0d, 100,
			new Rectangle(-10, -100, 20, 100),
			new Rectangle(0, 0, 20, 100),
			0, 0);
		BillboardDepthSurface far = new BillboardDepthSurface(
			calculator, 0, 256, 0.0d, 100,
			new Rectangle(-10, -100, 20, 100),
			new Rectangle(0, 0, 20, 100),
			0, 0);

		for (int sourceY : new int[]{0, 50, 99})
		{
			assertTrue(near.depthAt(10, sourceY, 20, 100) < far.depthAt(10, sourceY, 20, 100));
		}
	}

	@Test
	public void spriteRenderYawDoesNotChangeWorldDepthSurface()
	{
		BillboardDepthCalculator calculator = new BillboardDepthCalculator(client(0, 2048));
		BillboardDepthSurface forwardSprite = new BillboardDepthSurface(
			calculator,
			0,
			0,
			0.0d,
			100,
			new Rectangle(-10, -100, 20, 100),
			new Rectangle(0, 0, 20, 100),
			0,
			0
		);
		BillboardDepthSurface turnedSprite = new BillboardDepthSurface(
			calculator,
			0,
			0,
			0.0d,
			100,
			new Rectangle(-10, -100, 20, 100),
			new Rectangle(0, 0, 20, 100),
			4096,
			4096
		);

		assertTrue(Math.abs(forwardSprite.depthAt(10, 80, 20, 100) - turnedSprite.depthAt(10, 80, 20, 100)) < 1.0d);
	}

	@Test
	public void canvasRowControlsVerticalPlaneIntersection()
	{
		BillboardDepthSurface surface = new BillboardDepthSurface(
			new BillboardDepthCalculator(client(0, 2048)),
			0,
			0,
			0.0d,
			100,
			new Rectangle(-10, -200, 20, 100),
			new Rectangle(400, 100, 20, 100),
			0,
			0
		);

		assertTrue(surface.depthAt(10, 49, 20, 100, -50) < surface.depthAt(10, 49, 20, 100, 150));
	}

	@Test
	public void invalidSurfaceReturnsNan()
	{
		assertFalse(Double.isFinite(BillboardDepthSurface.invalid().depthAt(0, 0, 1, 1)));
	}

	@Test
	public void flatModelsBypassVerticalPlaneWorldOcclusion()
	{
		Model flat = proxy(Model.class,
			method("getVerticesCount", 4),
			method("getVerticesX", new float[] {-50, 50, 50, -50}),
			method("getVerticesY", new float[] {0, 0, -1, -1}),
			method("getVerticesZ", new float[] {-50, -50, 50, 50}));
		Model upright = proxy(Model.class,
			method("getVerticesCount", 4),
			method("getVerticesX", new float[] {-20, 20, 20, -20}),
			method("getVerticesY", new float[] {0, 0, -100, -100}),
			method("getVerticesZ", new float[] {-20, -20, 20, 20}));

		assertFalse(BillboardDepthSurface.supportsVerticalPlaneOcclusion(flat));
		assertTrue(BillboardDepthSurface.supportsVerticalPlaneOcclusion(upright));
	}

	private static Client client(int yaw, int pitch)
	{
		return proxy(
			Client.class,
			method("getCameraFpX", 0.0f),
			method("getCameraFpY", -1_000.0f),
			method("getCameraFpZ", 0.0f),
			method("getCameraYaw", yaw),
			method("getCameraPitch", pitch),
			method("get3dZoom", 512),
			method("getViewportYOffset", 0),
			method("getViewportHeight", 100)
		);
	}
}
