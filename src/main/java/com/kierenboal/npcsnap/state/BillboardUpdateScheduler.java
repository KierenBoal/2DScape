package com.kierenboal.npcsnap.state;

import com.kierenboal.npcsnap.rendering.BillboardRenderQuality;
import com.kierenboal.npcsnap.targeting.ObjectRenderablePart;

public final class BillboardUpdateScheduler
{
	private BillboardUpdateScheduler()
	{
	}

	public static double initialQualityScale(boolean hasCachedBillboard, double fullQualityScale, int nearPriorityIndex, int maxDrawsPerFrame)
	{
		return hasCachedBillboard
			? BillboardRenderQuality.clamp(fullQualityScale)
			: BillboardRenderQuality.seededScale(fullQualityScale, nearPriorityIndex, maxDrawsPerFrame);
	}

	public static boolean allTileObjectPartsCached(Iterable<ObjectRenderablePart> parts, java.util.function.Predicate<net.runelite.api.Renderable> hasCache)
	{
		if (parts == null || hasCache == null)
		{
			return false;
		}

		boolean sawRenderablePart = false;
		for (ObjectRenderablePart part : parts)
		{
			if (part == null || part.renderable == null)
			{
				continue;
			}

			sawRenderablePart = true;
			if (!hasCache.test(part.renderable))
			{
				return false;
			}
		}

		return sawRenderablePart;
	}
}

