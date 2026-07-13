package com.kierenboal.npcsnap;

final class BillboardUpdateScheduler
{
	private BillboardUpdateScheduler()
	{
	}

	static double initialQualityScale(boolean hasCachedBillboard, double fullQualityScale, int nearPriorityIndex, int maxDrawsPerFrame)
	{
		return hasCachedBillboard
			? BillboardRenderQuality.clamp(fullQualityScale)
			: BillboardRenderQuality.seededScale(fullQualityScale, nearPriorityIndex, maxDrawsPerFrame);
	}

	static boolean allTileObjectPartsCached(Iterable<ObjectRenderablePart> parts, java.util.function.Predicate<net.runelite.api.Renderable> hasCache)
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

