package com.kierenboal.npcsnap.occlusion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SceneryModelRotationTest
{
	@Test
	public void rotatesObjectVertexAtQuarterTurn()
	{
		assertEquals(0f, SceneryModelRotation.x(512, 100f, 0f), 0.01f);
		assertEquals(-100f, SceneryModelRotation.z(512, 100f, 0f), 0.01f);
	}

	@Test
	public void wrapsModelOrientation()
	{
		assertEquals(SceneryModelRotation.x(512, 40f, 60f),
			SceneryModelRotation.x(2560, 40f, 60f), 0.001f);
	}
}
