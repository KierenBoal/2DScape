package com.kierenboal.npcsnap;

import net.runelite.api.Model;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class BillboardModelStateHashTest
{
	@Test
	public void nullModelHasStableZeroHash()
	{
		assertEquals(0, BillboardModelStateHash.hash(null));
	}

	@Test
	public void hashTracksGeometryHeightAndTransparencySamples()
	{
		Model base = model(3, 100, new float[] {1, 2, 3}, new byte[] {0, 1, 2});
		Model changedMiddle = model(3, 100, new float[] {1, 9, 3}, new byte[] {0, 1, 2});
		Model changedHeight = model(3, 101, new float[] {1, 2, 3}, new byte[] {0, 1, 2});
		Model changedAlpha = model(3, 100, new float[] {1, 2, 3}, new byte[] {0, 9, 2});

		assertEquals(BillboardModelStateHash.hash(base), BillboardModelStateHash.hash(base));
		assertNotEquals(BillboardModelStateHash.hash(base), BillboardModelStateHash.hash(changedMiddle));
		assertNotEquals(BillboardModelStateHash.hash(base), BillboardModelStateHash.hash(changedHeight));
		assertNotEquals(BillboardModelStateHash.hash(base), BillboardModelStateHash.hash(changedAlpha));
	}

	@Test
	public void nullAndEmptyArraysProduceDifferentHashes()
	{
		Model nullArrays = model(0, 0, null, null);
		Model emptyArrays = model(0, 0, new float[0], new byte[0]);

		assertNotEquals(BillboardModelStateHash.hash(nullArrays), BillboardModelStateHash.hash(emptyArrays));
	}

	private static Model model(int vertexCount, int height, float[] vertices, byte[] transparencies)
	{
		return TestProxies.proxy(Model.class,
			TestProxies.method("getVerticesCount", vertexCount),
			TestProxies.method("getModelHeight", height),
			TestProxies.method("getVerticesX", vertices),
			TestProxies.method("getVerticesY", vertices),
			TestProxies.method("getVerticesZ", vertices),
			TestProxies.method("getFaceTransparencies", transparencies));
	}
}
