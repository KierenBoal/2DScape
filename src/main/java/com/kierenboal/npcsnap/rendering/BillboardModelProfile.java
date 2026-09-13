package com.kierenboal.npcsnap.rendering;

import com.kierenboal.npcsnap.BillboardConstants;

import net.runelite.api.Model;

/**
 * Classifies models that need ground-plane billboard handling instead of the
 * ordinary vertical-plane handling.
 */
final class BillboardModelProfile
{
	private static final int MAX_LOW_PROFILE_MODEL_HEIGHT = BillboardConstants.LOCAL_TILE_SIZE / 4;
	private static final double MAX_LOW_PROFILE_VERTICAL_RATIO = 0.10d;

	private BillboardModelProfile()
	{
	}

	static boolean isLowProfile(Model model, int renderableModelHeight)
	{
		Bounds bounds = bounds(model);
		if (bounds == null)
		{
			return false;
		}

		if (renderableModelHeight >= 0 && renderableModelHeight <= MAX_LOW_PROFILE_MODEL_HEIGHT)
		{
			return true;
		}

		return bounds.verticalSpan < Math.max(2.0d, bounds.horizontalSpan * MAX_LOW_PROFILE_VERTICAL_RATIO);
	}

	static boolean supportsVerticalPlaneOcclusion(Model model)
	{
		Bounds bounds = bounds(model);
		return bounds != null
			&& bounds.verticalSpan >= Math.max(2.0d, bounds.horizontalSpan * MAX_LOW_PROFILE_VERTICAL_RATIO);
	}

	private static Bounds bounds(Model model)
	{
		if (model == null)
		{
			return null;
		}
		int vertexCount = model.getVerticesCount();
		if (vertexCount <= 0)
		{
			return null;
		}

		float[] verticesX = model.getVerticesX();
		float[] verticesY = model.getVerticesY();
		float[] verticesZ = model.getVerticesZ();
		if (verticesX == null || verticesY == null || verticesZ == null)
		{
			return null;
		}

		// RuneLite models may retain padded backing arrays. Only arrays shorter
		// than the advertised vertex count are inconsistent and unusable.
		if (verticesX.length < vertexCount || verticesY.length < vertexCount || verticesZ.length < vertexCount)
		{
			return null;
		}

		float minX = Float.POSITIVE_INFINITY;
		float minY = Float.POSITIVE_INFINITY;
		float minZ = Float.POSITIVE_INFINITY;
		float maxX = Float.NEGATIVE_INFINITY;
		float maxY = Float.NEGATIVE_INFINITY;
		float maxZ = Float.NEGATIVE_INFINITY;
		for (int i = 0; i < vertexCount; i++)
		{
			if (!Float.isFinite(verticesX[i]) || !Float.isFinite(verticesY[i]) || !Float.isFinite(verticesZ[i]))
			{
				return null;
			}

			minX = Math.min(minX, verticesX[i]);
			minY = Math.min(minY, verticesY[i]);
			minZ = Math.min(minZ, verticesZ[i]);
			maxX = Math.max(maxX, verticesX[i]);
			maxY = Math.max(maxY, verticesY[i]);
			maxZ = Math.max(maxZ, verticesZ[i]);
		}

		if (!Float.isFinite(minX) || !Float.isFinite(minY) || !Float.isFinite(minZ)
			|| !Float.isFinite(maxX) || !Float.isFinite(maxY) || !Float.isFinite(maxZ))
		{
			return null;
		}

		double verticalSpan = maxY - minY;
		double horizontalSpan = Math.max(maxX - minX, maxZ - minZ);
		if (!Double.isFinite(verticalSpan) || !Double.isFinite(horizontalSpan))
		{
			return null;
		}

		return new Bounds(verticalSpan, horizontalSpan);
	}

	private static final class Bounds
	{
		private final double verticalSpan;
		private final double horizontalSpan;

		private Bounds(double verticalSpan, double horizontalSpan)
		{
			this.verticalSpan = verticalSpan;
			this.horizontalSpan = horizontalSpan;
		}
	}
}
