package com.kierenboal.npcsnap.occlusion;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.runelite.api.Tile;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BillboardTileVisitsTest
{
	@Test
	public void overlappingCorridorsPreserveCoordinateOrderAcrossPlanesAndSceneEdges()
	{
		Tile[][][] tiles = new Tile[4][24][19];
		tiles[1] = null;
		tiles[2][3] = null;
		tiles[3][4] = new Tile[7];
		for (int radius : new int[] {2, 4, 6, 8, 10})
		{
			BillboardTileVisits visits = new BillboardTileVisits();
			visits.reset(tiles, radius);
			Set<String> seen = new HashSet<>();
			List<String> expected = new ArrayList<>();
			List<String> actual = new ArrayList<>();
			Random random = new Random(42);
			int expanded = 0;
			for (int target = 0; target < 80; target++)
			{
				int targetX = random.nextInt(32) - 4;
				int targetY = random.nextInt(27) - 4;
				for (int step = 0; step <= 32; step++)
				{
					int centerX = -12 + (int) Math.round((targetX + 12) * step / 32.0);
					int centerY = -12 + (int) Math.round((targetY + 12) * step / 32.0);
					boolean expand = visits.addCenter(centerX, centerY);
					expanded += expand ? 1 : 0;
					for (int x = centerX - radius; x <= centerX + radius; x++)
					{
						for (int y = centerY - radius; y <= centerY + radius; y++)
						{
							for (int plane = 0; plane < tiles.length; plane++)
							{
								if (tiles[plane] == null || x < 0 || x >= tiles[plane].length
									|| tiles[plane][x] == null || y < 0 || y >= tiles[plane][x].length)
								{
									continue;
								}
								String key = plane + ":" + x + ":" + y;
								if (seen.add(key))
								{
									expected.add(key);
								}
								if (expand && visits.addCoordinate(plane, x, y))
								{
									actual.add(key);
								}
							}
						}
					}
				}
			}
			assertEquals("radius=" + radius, expected, actual);
			assertTrue("Most shared corridor centers should be skipped", expanded < 80 * 33 / 2);
		}
	}

	@Test
	public void resetHandlesChangedSceneDimensionsAndQualityWithoutStaleVisits()
	{
		BillboardTileVisits visits = new BillboardTileVisits();
		visits.reset(new Tile[4][4][7], 2);
		assertTrue(visits.addCenter(-2, -2));
		assertFalse(visits.addCenter(-2, -2));
		assertFalse(visits.addCenter(-3, 0));
		assertTrue(visits.addCenter(5, 8));
		assertFalse(visits.addCenter(6, 8));
		assertTrue(visits.addCoordinate(0, 3, 6));
		assertTrue(visits.addCoordinate(1, 3, 6));
		assertFalse(visits.addCoordinate(0, 3, 6));
		visits.reset(new Tile[4][12][13], 10);
		assertTrue(visits.addCenter(-2, -2));
		assertTrue(visits.addCoordinate(0, 3, 6));
		assertTrue(visits.addCoordinate(3, 11, 12));
		visits.reset(new Tile[1][1][1], 2);
		assertTrue(visits.addCoordinate(0, 0, 0));
		assertFalse(visits.addCenter(3, 0));
	}
}
