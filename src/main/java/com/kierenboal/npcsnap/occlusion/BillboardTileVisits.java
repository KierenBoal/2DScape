package com.kierenboal.npcsnap.occlusion;

import java.util.BitSet;
import net.runelite.api.Tile;

/** Reusable bounded coordinate sets for one collection, including off-scene corridor centers. */
final class BillboardTileVisits
{
	private final BitSet centers = new BitSet();
	private final BitSet coordinates = new BitSet();
	private int width;
	private int height;
	private int radius;
	private int centerWidth;

	void reset(Tile[][][] tiles, int radius)
	{
		centers.clear();
		coordinates.clear();
		width = height = 0;
		this.radius = radius;
		for (Tile[][] plane : tiles)
		{
			if (plane == null)
			{
				continue;
			}
			width = Math.max(width, plane.length);
			for (Tile[] row : plane)
			{
				if (row != null)
				{
					height = Math.max(height, row.length);
				}
			}
		}
		centerWidth = width + 2 * radius;
	}

	boolean addCenter(int x, int y)
	{
		// Centers outside the scene still matter when their square overlaps its edge.
		if (x < -radius || y < -radius || x >= width + radius || y >= height + radius)
		{
			return false;
		}
		return add(centers, (y + radius) * centerWidth + x + radius);
	}

	boolean addCoordinate(int plane, int x, int y)
	{
		// The caller has already checked the actual (possibly ragged) scene row.
		return add(coordinates, (plane * height + y) * width + x);
	}

	private static boolean add(BitSet bits, int index)
	{
		if (bits.get(index))
		{
			return false;
		}
		bits.set(index);
		return true;
	}
}
