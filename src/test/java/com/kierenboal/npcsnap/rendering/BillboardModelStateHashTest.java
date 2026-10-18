package com.kierenboal.npcsnap.rendering;

import com.kierenboal.npcsnap.TestProxies;

import net.runelite.api.Model;
import org.junit.Test;

import static org.junit.Assert.assertNotEquals;

public class BillboardModelStateHashTest
{
	@Test
	public void hashTracksGeometryHeightAndTransparencySamples()
	{
		Model base = model(3, 100, new float[] {1, 2, 3}, new byte[] {0, 1, 2});
		Model changedMiddle = model(3, 100, new float[] {1, 9, 3}, new byte[] {0, 1, 2});
		Model changedHeight = model(3, 101, new float[] {1, 2, 3}, new byte[] {0, 1, 2});
		Model changedAlpha = model(3, 100, new float[] {1, 2, 3}, new byte[] {0, 9, 2});

		assertNotEquals(BillboardModelStateHash.hash(base), BillboardModelStateHash.hash(changedMiddle));
		assertNotEquals(BillboardModelStateHash.hash(base), BillboardModelStateHash.hash(changedHeight));
		assertNotEquals(BillboardModelStateHash.hash(base), BillboardModelStateHash.hash(changedAlpha));
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
