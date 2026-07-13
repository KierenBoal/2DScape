package com.kierenboal.npcsnap.rendering;

public final class BillboardRenderQuality
{
	public static final double MIN_RENDER_QUALITY = 0.01d;

	private BillboardRenderQuality()
	{
	}

	public static double fromPercent(double qualityPercent)
	{
		return clamp(qualityPercent / 100.0d);
	}

	public static double clamp(double qualityScale)
	{
		return Math.max(MIN_RENDER_QUALITY, Math.min(1.0d, qualityScale));
	}

	public static int key(double qualityScale)
	{
		return (int) Math.round(clamp(qualityScale) * 10_000.0d);
	}

	public static double seededScale(double fullQualityScale, int nearPriorityIndex, int maxUpdatesPerFrame)
	{
		int updatesPerFrame = Math.max(1, maxUpdatesPerFrame);
		int maxBootstrapEntries = updatesPerFrame * 3;
		int clampedPriorityIndex = Math.min(Math.max(0, nearPriorityIndex), Math.max(0, maxBootstrapEntries - 1));
		int qualityTier = clampedPriorityIndex / updatesPerFrame;
		double seededQualityScale = clamp(fullQualityScale);
		for (int i = 0; i < qualityTier; i++)
		{
			seededQualityScale *= 0.5d;
		}

		return clamp(seededQualityScale);
	}
}
