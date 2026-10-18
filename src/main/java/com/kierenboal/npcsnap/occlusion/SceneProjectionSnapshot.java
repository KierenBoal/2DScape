package com.kierenboal.npcsnap.occlusion;

import net.runelite.api.FloatProjection;
import net.runelite.api.Projection;

/** Owns the affine entity matrix used to draw a scene, even if the renderer reuses it. */
final class SceneProjectionSnapshot implements Projection
{
	private final float[] matrix;

	private SceneProjectionSnapshot(float[] matrix)
	{
		this.matrix = matrix.clone();
	}

	static Projection copy(Projection projection)
	{
		return projection instanceof FloatProjection
			? new SceneProjectionSnapshot(((FloatProjection) projection).getProjection()) : projection;
	}

	@Override
	public float[] project(float x, float y, float z)
	{
		return project(x, y, z, new float[3]);
	}

	@Override
	public float[] project(float x, float y, float z, float[] out)
	{
		out[0] = matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12];
		out[1] = matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13];
		out[2] = matrix[2] * x + matrix[6] * y + matrix[10] * z + matrix[14];
		return out;
	}
}
