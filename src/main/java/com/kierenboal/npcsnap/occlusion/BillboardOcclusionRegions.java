package com.kierenboal.npcsnap.occlusion;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

public final class BillboardOcclusionRegions
{
	private final List<Rectangle> regions = new ArrayList<>();
	private final BitSet pixels = new BitSet();
	private Rectangle viewport;
	private Rectangle bounds;

	List<Rectangle> rectangles()
	{
		return regions;
	}

	Rectangle bounds()
	{
		return bounds;
	}

	void clear()
	{
		regions.clear();
		pixels.clear();
		bounds = null;
	}

	boolean add(Rectangle rectangle, Rectangle viewport)
	{
		Rectangle clipped = viewport.intersection(rectangle);
		if (clipped.isEmpty()) return false;
		this.viewport = viewport;
		regions.add(clipped);
		if (bounds == null) bounds = new Rectangle(clipped);
		else bounds.add(clipped);
		int start = (clipped.y - viewport.y) * viewport.width + clipped.x - viewport.x;
		for (int row = 0; row < clipped.height; row++, start += viewport.width)
		{
			pixels.set(start, start + clipped.width);
		}
		return true;
	}

	boolean contains(int x, int y)
	{
		return bounds != null && bounds.contains(x, y)
			&& pixels.get((y - viewport.y) * viewport.width + x - viewport.x);
	}

	// Callers provide a row segment already clipped to the viewport (at most one mask cell).
	int rowBits(int x, int y, int width)
	{
		int start = (y - viewport.y) * viewport.width + x - viewport.x;
		int end = start + width;
		int bits = 0;
		for (int first = pixels.nextSetBit(start); first >= 0 && first < end; )
		{
			int after = Math.min(end, pixels.nextClearBit(first));
			bits |= ((1 << (after - start)) - 1) & (-1 << (first - start));
			first = after == end ? -1 : pixels.nextSetBit(after);
		}
		return bits;
	}

	boolean intersects(Rectangle rectangle)
	{
		if (bounds == null || rectangle == null || !bounds.intersects(rectangle)) return false;
		if (regions.size() == 1) return true;
		int x = Math.max(bounds.x, rectangle.x);
		int y = Math.max(bounds.y, rectangle.y);
		int endX = (int) Math.min(bounds.x + bounds.width, (long) rectangle.x + rectangle.width);
		int endY = (int) Math.min(bounds.y + bounds.height, (long) rectangle.y + rectangle.height);
		int start = (y - viewport.y) * viewport.width + x - viewport.x;
		int width = endX - x;
		int hit = -1;
		for (int row = y; row < endY; row++, start += viewport.width)
		{
			if (hit < start) hit = pixels.nextSetBit(start);
			if (hit < 0) return false;
			if (hit < start + width) return true;
		}
		return false;
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
}
