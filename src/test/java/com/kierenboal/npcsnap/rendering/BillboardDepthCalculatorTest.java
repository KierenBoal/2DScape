package com.kierenboal.npcsnap.rendering;

import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import org.junit.Test;

import static com.kierenboal.npcsnap.TestProxies.method;
import static com.kierenboal.npcsnap.TestProxies.proxy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BillboardDepthCalculatorTest
{
	@Test
	public void canvasProjectionPreservesSubpixelCoordinates()
	{
		Client client = proxy(
			Client.class,
			method("isGpu", false),
			method("getCameraX", 0),
			method("getCameraY", 0),
			method("getCameraZ", 0),
			method("getCameraYaw", 0),
			method("getCameraPitch", 0),
			method("getScale", 512),
			method("getViewportXOffset", 10),
			method("getViewportYOffset", 20),
			method("getViewportWidth", 800),
			method("getViewportHeight", 600)
		);
		BillboardDepthCalculator calculator = new BillboardDepthCalculator(client);

		BillboardCanvasPoint projected = calculator.projectCanvasPoint(1.0d, 1000.0d, -64.0d);

		assertNotNull(projected);
		assertEquals(410.512d, projected.x, 0.0001d);
		assertEquals(287.232d, projected.y, 0.0001d);
		assertEquals(1000.0d, projected.depth, 0.0001d);
	}

	@Test
	public void fullHeightProjectionChangesLeanSideAcrossCameraAxis()
	{
		BillboardDepthCalculator calculator = new BillboardDepthCalculator(projectionClient(0, 1024));
		BillboardCanvasPoint rightBase = calculator.projectCanvasPoint(200.0d, 1000.0d, 0.0d);
		BillboardCanvasPoint rightTop = calculator.projectCanvasPoint(200.0d, 1000.0d, -400.0d);
		BillboardCanvasPoint leftBase = calculator.projectCanvasPoint(-200.0d, 1000.0d, 0.0d);
		BillboardCanvasPoint leftTop = calculator.projectCanvasPoint(-200.0d, 1000.0d, -400.0d);

		assertNotNull(rightBase);
		assertNotNull(rightTop);
		assertNotNull(leftBase);
		assertNotNull(leftTop);
		assertTrue(rightTop.x > rightBase.x);
		assertTrue(leftTop.x < leftBase.x);
		assertEquals(
			BillboardGeometryUtils.projectionShearSlope(rightBase.verticalRight, rightBase.verticalUp),
			-BillboardGeometryUtils.projectionShearSlope(leftBase.verticalRight, leftBase.verticalUp),
			0.00001d);
	}

	@Test
	public void verticalDirectionMatchesProjectedAxisRegardlessOfModelHeight()
	{
		BillboardDepthCalculator calculator = new BillboardDepthCalculator(projectionClient(0, 2048));
		BillboardCanvasPoint base = calculator.projectCanvasPoint(200.0d, 1000.0d, 0.0d);
		for (int height : new int[] {1, 20, 100, 400})
		{
			BillboardCanvasPoint top = calculator.projectCanvasPoint(200.0d, 1000.0d, -height);
			assertEquals((top.x - base.x) / (base.y - top.y),
				base.verticalRight / base.verticalUp, 1e-10d);
		}
	}

	@Test
	public void projectionMatchesRuneLiteAcrossGpuCpuYawPitchAndViewportOffsets()
	{
		for (boolean gpu : new boolean[] {false, true})
		{
			for (int yaw : new int[] {0, 1337, 4096, 12000, 16383})
			{
				for (int pitch : new int[] {0, 1024, 2048, 3584})
				{
					Client client = referenceProjectionClient(gpu, yaw, pitch);
					BillboardDepthCalculator calculator = new BillboardDepthCalculator(client);
					double angle = yaw * Math.PI / 8192.0d;
					for (int lateral : new int[] {-300, 0, 300})
					{
						int x = 1000 + (int) Math.round(lateral * Math.cos(angle) - 1024 * Math.sin(angle));
						int y = 2000 + (int) Math.round(lateral * Math.sin(angle) + 1024 * Math.cos(angle));
						Point reference = Perspective.localToCanvas(client, x, y, -300);
						BillboardCanvasPoint point = calculator.projectCanvasPoint(x, y, -300);
						assertNotNull(reference);
						assertNotNull(point);
						// CPU projection truncates intermediate fixed-point rotations;
						// GPU only rounds the final screen point.
						assertEquals(reference.getX(), point.x, gpu ? 0.501d : 2.5d);
						assertEquals(reference.getY(), point.y, gpu ? 0.501d : 2.5d);
					}
				}
			}
		}
	}

	@Test
	public void nearPlaneAndInvalidProjectionsAreRejected()
	{
		BillboardDepthCalculator calculator = new BillboardDepthCalculator(projectionClient(0, 0));
		assertNull(calculator.projectCanvasPoint(0, 49, 0));
		assertNull(calculator.projectCanvasPoint(0, -100, 0));
		assertNull(calculator.projectCanvasPoint(Double.NaN, 100, 0));
		assertNotNull(calculator.projectCanvasPoint(0, 50, 0));
	}

	@Test
	public void overheadAndBehindVerticalAxisDoNotInvertTheSpriteLean()
	{
		BillboardDepthCalculator calculator = new BillboardDepthCalculator(referenceProjectionClient(true, 0, 4096));
		for (int forward : new int[] {-300, -1, 0, 1, 100})
		{
			BillboardCanvasPoint base = calculator.projectCanvasPoint(1300, 2000 + forward, 0);
			assertNotNull(base);
			assertEquals(0.0d, BillboardGeometryUtils.projectionShearSlope(base.verticalRight, base.verticalUp), 0.0d);
		}
	}

	@Test
	public void forwardDepthIncreasesAlongCameraViewDirection()
	{
		Client client = proxy(
			Client.class,
			method("getCameraFpX", 0.0f),
			method("getCameraFpY", 0.0f),
			method("getCameraFpZ", 0.0f),
			method("getCameraYaw", 0),
			method("getCameraPitch", 0)
		);
		BillboardDepthCalculator calculator = new BillboardDepthCalculator(client);

		assertTrue(calculator.cameraForwardDepth(0, 256, 0.0d) > calculator.cameraForwardDepth(0, 128, 0.0d));
	}

	@Test
	public void forwardDepthIsNegativeBehindCamera()
	{
		Client client = proxy(
			Client.class,
			method("getCameraFpX", 0.0f),
			method("getCameraFpY", 0.0f),
			method("getCameraFpZ", 0.0f),
			method("getCameraYaw", 0),
			method("getCameraPitch", 0)
		);
		BillboardDepthCalculator calculator = new BillboardDepthCalculator(client);

		assertTrue(calculator.cameraForwardDepth(0, -128, 0.0d) < 0.0d);
	}

	@Test
	public void forwardDepthIncludesCameraPitchVerticalComponent()
	{
		Client client = proxy(
			Client.class,
			method("getCameraFpX", 0.0f),
			method("getCameraFpY", 0.0f),
			method("getCameraFpZ", 0.0f),
			method("getCameraYaw", 0),
			method("getCameraPitch", 4096)
		);
		BillboardDepthCalculator calculator = new BillboardDepthCalculator(client);

		assertTrue(calculator.cameraForwardDepth(0, 0, 256.0d) > calculator.cameraForwardDepth(0, 0, 128.0d));
	}

	@Test
	public void cameraAnglesAreConvertedFromClientUnitsToTrigTableUnits()
	{
		Client client = proxy(
			Client.class,
			method("getCameraFpX", 0.0f),
			method("getCameraFpY", 0.0f),
			method("getCameraFpZ", 0.0f),
			method("getCameraYaw", 14337),
			method("getCameraPitch", 1453)
		);
		BillboardDepthCalculator calculator = new BillboardDepthCalculator(client);

		assertTrue(Math.abs(calculator.cameraYawIndex() - 1792) <= 1);
		assertTrue(Math.abs(calculator.cameraPitchIndex() - 182) <= 1);
	}

	private static Client projectionClient(int yaw, int pitch)
	{
		return proxy(
			Client.class,
			method("isGpu", false),
			method("getCameraX", 0),
			method("getCameraY", 0),
			method("getCameraZ", 0),
			method("getCameraYaw", yaw),
			method("getCameraPitch", pitch),
			method("getScale", 512),
			method("getViewportXOffset", 0),
			method("getViewportYOffset", 0),
			method("getViewportWidth", 800),
			method("getViewportHeight", 600)
		);
	}

	private static Client referenceProjectionClient(boolean gpu, int yaw, int pitch)
	{
		return proxy(Client.class,
			method("isGpu", gpu),
			method("getCameraX", 1000), method("getCameraY", 2000), method("getCameraZ", -1200),
			method("getCameraFpX", 1000.0f), method("getCameraFpY", 2000.0f), method("getCameraFpZ", -1200.0f),
			method("getCameraYaw", yaw), method("getCameraPitch", pitch),
			method("getCameraFpYaw", (float) (yaw * Math.PI / 8192.0d)),
			method("getCameraFpPitch", (float) (pitch * Math.PI / 8192.0d)),
			method("getScale", 777),
			method("getViewportXOffset", 51), method("getViewportYOffset", 23),
			method("getViewportWidth", 801), method("getViewportHeight", 601));
	}
}
