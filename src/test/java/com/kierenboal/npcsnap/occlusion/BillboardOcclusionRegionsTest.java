package com.kierenboal.npcsnap.occlusion;

import java.awt.Rectangle;
import org.junit.Test;

import static org.junit.Assert.*;

public class BillboardOcclusionRegionsTest
{
	@Test
	public void indexedCoverageMatchesClippedRectanglesIncludingGaps()
	{
		BillboardOcclusionRegions regions = new BillboardOcclusionRegions();
		Rectangle viewport = new Rectangle(100, 200, 73, 14);
		assertTrue(regions.add(new Rectangle(95, 204, 12, 10), viewport));
		assertTrue(regions.add(new Rectangle(105, 211, 5, 6), viewport));
		assertTrue(regions.add(new Rectangle(163, 198, 15, 3), viewport));
		assertFalse(regions.add(new Rectangle(100, 214, 4, 8), viewport));
		assertEquals(viewport, regions.bounds());

		for (int y = 199; y <= 214; y++)
		{
			for (int x = 99; x <= 173; x++)
			{
				Rectangle pixel = new Rectangle(x, y, 1, 1);
				boolean expected = regions.rectangles().stream().anyMatch(r -> r.intersects(pixel));
				assertEquals(pixel.toString(), expected, regions.contains(x, y));
				for (int size : new int[] {0, 1, 4, 15})
				{
					Rectangle query = new Rectangle(x, y, size, size);
					assertEquals(query.toString(), regions.rectangles().stream().anyMatch(r -> r.intersects(query)),
						regions.intersects(query));
				}
			}
		}
		regions.clear();
		assertNull(regions.bounds());
		assertFalse(regions.contains(164, 200));
	}
}
