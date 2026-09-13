package com.kierenboal.npcsnap.rendering;

import net.runelite.api.Model;
import org.junit.Test;

import static com.kierenboal.npcsnap.TestProxies.method;
import static com.kierenboal.npcsnap.TestProxies.proxy;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BillboardModelProfileTest
{
	@Test
	public void shortModelsAreLowProfileAtAndBelowBoundary()
	{
		Model model = model(
			new float[] {-20, 20, 20, -20},
			new float[] {0, 0, -40, -40},
			new float[] {-20, -20, 20, 20});

		assertTrue(BillboardModelProfile.isLowProfile(model, 0));
		assertTrue(BillboardModelProfile.isLowProfile(model, 32));
		assertFalse(BillboardModelProfile.isLowProfile(model, 33));
	}

	@Test
	public void broadFlatModelUsesAspectRatioRule()
	{
		Model model = model(
			new float[] {-100, 100, 100, -100},
			new float[] {0, 0, -1, -1},
			new float[] {-100, -100, 100, 100});

		assertTrue(BillboardModelProfile.isLowProfile(model, 200));
	}

	@Test
	public void uprightSmallFootprintIsNotAspectFlat()
	{
		Model model = model(
			new float[] {-10, 10, 10, -10},
			new float[] {0, 0, -100, -100},
			new float[] {-10, -10, 10, 10});

		assertFalse(BillboardModelProfile.isLowProfile(model, 100));
	}

	@Test
	public void paddedRuntimeVertexArraysRemainUsable()
	{
		Model model = proxy(Model.class,
			method("getVerticesCount", 4),
			method("getVerticesX", new float[] {-20, 20, 20, -20, Float.NaN}),
			method("getVerticesY", new float[] {0, 0, -100, -100, Float.NaN}),
			method("getVerticesZ", new float[] {-20, -20, 20, 20, Float.NaN}));

		assertFalse(BillboardModelProfile.isLowProfile(model, 100));
		assertTrue(BillboardModelProfile.supportsVerticalPlaneOcclusion(model));
	}

	@Test
	public void missingOrInvalidGeometryFailsClosed()
	{
		assertFalse(BillboardModelProfile.isLowProfile(null, 1));
		assertFalse(BillboardModelProfile.isLowProfile(proxy(Model.class), 1));
		assertFalse(BillboardModelProfile.isLowProfile(proxy(Model.class,
			method("getVerticesCount", 2),
			method("getVerticesX", new float[] {0, 1}),
			method("getVerticesY", new float[] {0}),
			method("getVerticesZ", new float[] {0, 1})), 1));
		assertFalse(BillboardModelProfile.isLowProfile(proxy(Model.class,
			method("getVerticesCount", 1),
			method("getVerticesX", new float[] {Float.NaN}),
			method("getVerticesY", new float[] {0}),
			method("getVerticesZ", new float[] {0})), 1));
	}

	private static Model model(float[] verticesX, float[] verticesY, float[] verticesZ)
	{
		return proxy(Model.class,
			method("getVerticesCount", verticesX.length),
			method("getVerticesX", verticesX),
			method("getVerticesY", verticesY),
			method("getVerticesZ", verticesZ));
	}
}
