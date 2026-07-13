package com.kierenboal.npcsnap.occlusion;

import com.kierenboal.npcsnap.rendering.PreparedBillboardDraw;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

public final class BillboardOcclusionRegions
{
	private BillboardOcclusionRegions()
	{
	}

	public static List<Rectangle> clippedDrawRegions(
		List<PreparedBillboardDraw> preparedDraws,
		Rectangle viewportBounds,
		int margin)
	{
		List<Rectangle> regions = new ArrayList<>(preparedDraws.size());
		for (PreparedBillboardDraw draw : preparedDraws)
		{
			if (draw == null || draw.bounds == null || draw.bounds.isEmpty())
			{
				continue;
			}

			Rectangle clipped = viewportBounds.intersection(expanded(draw.bounds, margin));
			if (!clipped.isEmpty())
			{
				regions.add(clipped);
			}
		}
		return regions;
	}

	public static Rectangle expanded(Rectangle bounds, int margin)
	{
		if (bounds == null || bounds.isEmpty() || margin <= 0)
		{
			return bounds;
		}
		return new Rectangle(
			bounds.x - margin,
			bounds.y - margin,
			bounds.width + (margin * 2),
			bounds.height + (margin * 2));
	}

	public static Rectangle union(List<Rectangle> regions)
	{
		if (regions == null || regions.isEmpty())
		{
			return null;
		}

		Rectangle union = null;
		for (Rectangle region : regions)
		{
			if (region != null && !region.isEmpty())
			{
				union = union == null ? new Rectangle(region) : union.union(region);
			}
		}
		return union;
	}
}
