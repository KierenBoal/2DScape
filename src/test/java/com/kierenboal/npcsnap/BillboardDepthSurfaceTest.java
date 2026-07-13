package com.kierenboal.npcsnap;

import java.awt.Rectangle;
import net.runelite.api.Client;
import org.junit.Test;

import static com.kierenboal.npcsnap.TestProxies.method;
import static com.kierenboal.npcsnap.TestProxies.proxy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BillboardDepthSurfaceTest
{
	@Test
	public void centerPixelMatchesCenterlineDepthClosely()
	{
		BillboardDepthCalculator calculator = new BillboardDepthCalculator(client(0, 4096));
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
		double centerlineDepth = calculator.cameraForwardDepth(0, 0, 50.0d);

		assertTrue(Math.abs(centerPixelDepth - centerlineDepth) < 2.0d);
	}

	@Test
	public void topAndBottomPixelsDifferWithCameraPitch()
	{
		BillboardDepthSurface surface = new BillboardDepthSurface(
			new BillboardDepthCalculator(client(0, 4096)),
			0,
			0,
			0.0d,
			100,
			new Rectangle(-10, -100, 20, 100),
			new Rectangle(0, 0, 20, 100),
			0,
			0
		);

		assertTrue(surface.depthAt(10, 0, 20, 100) > surface.depthAt(10, 99, 20, 100));
	}

	@Test
	public void leftAndRightPixelsDoNotDriftAcrossCameraFacingPlane()
	{
		BillboardDepthSurface surface = new BillboardDepthSurface(
			new BillboardDepthCalculator(client(4096, 0)),
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
	public void spriteRenderYawDoesNotChangeWorldDepthSurface()
	{
		BillboardDepthCalculator calculator = new BillboardDepthCalculator(client(0, 4096));
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
	public void sourceRowControlsDepthWhenBillboardDrawBoundsDifferFromModelProjection()
	{
		BillboardDepthSurface surface = new BillboardDepthSurface(
			new BillboardDepthCalculator(client(0, 4096)),
			0,
			0,
			0.0d,
			100,
			new Rectangle(-10, -200, 20, 100),
			new Rectangle(400, 100, 20, 100),
			0,
			0
		);

		assertEquals(surface.depthAt(10, 49, 20, 100, -500), surface.depthAt(10, 49, 20, 100, 5_000), 0.01d);
	}

	@Test
	public void invalidSurfaceReturnsNan()
	{
		assertFalse(Double.isFinite(BillboardDepthSurface.invalid().depthAt(0, 0, 1, 1)));
	}

	private static Client client(int yaw, int pitch)
	{
		return proxy(
			Client.class,
			method("getCameraFpX", 0.0f),
			method("getCameraFpY", 0.0f),
			method("getCameraFpZ", 0.0f),
			method("getCameraYaw", yaw),
			method("getCameraPitch", pitch)
		);
	}
}
