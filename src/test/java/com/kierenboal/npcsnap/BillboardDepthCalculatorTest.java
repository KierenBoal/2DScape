package com.kierenboal.npcsnap;

import net.runelite.api.Client;
import org.junit.Test;

import static com.kierenboal.npcsnap.TestProxies.method;
import static com.kierenboal.npcsnap.TestProxies.proxy;
import static org.junit.Assert.assertTrue;

public class BillboardDepthCalculatorTest
{
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
}
