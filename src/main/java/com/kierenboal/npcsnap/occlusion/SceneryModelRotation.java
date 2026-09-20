package com.kierenboal.npcsnap.occlusion;

import net.runelite.api.Perspective;

/** Applies the same 0..2047 model orientation used by RuneLite projection. */
public final class SceneryModelRotation
{
	private SceneryModelRotation()
	{
	}

	public static float x(int orientation, float x, float z)
	{
		int angle = orientation & 2047;
		return x * Perspective.COSINEF[angle] + z * Perspective.SINEF[angle];
	}

	public static float z(int orientation, float x, float z)
	{
		int angle = orientation & 2047;
		return z * Perspective.COSINEF[angle] - x * Perspective.SINEF[angle];
	}
}
