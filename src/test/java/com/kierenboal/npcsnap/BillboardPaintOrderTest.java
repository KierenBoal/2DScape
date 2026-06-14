package com.kierenboal.npcsnap;

import java.awt.Rectangle;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class BillboardPaintOrderTest
{
	@Test
	public void overlappingBillboardsSortByScreenBottom()
	{
		List<String> ordered = BillboardPaintOrder.sort(Arrays.asList(
			new BillboardPaintOrder.Entry<>("near-high", new Rectangle(20, 10, 30, 40), 1, BillboardPaintOrder.NO_PRIORITY_GROUP, 50, 100.0d, 2L),
			new BillboardPaintOrder.Entry<>("far-low", new Rectangle(25, 30, 30, 40), 1, BillboardPaintOrder.NO_PRIORITY_GROUP, 70, 140.0d, 1L)
		));

		Assert.assertEquals(Arrays.asList("near-high", "far-low"), ordered);
	}

	@Test
	public void sameTilePriorityStillKeepsGroundItemsUnderActors()
	{
		long sameTile = 1234L;
		List<String> ordered = BillboardPaintOrder.sort(Arrays.asList(
			new BillboardPaintOrder.Entry<>("actor", new Rectangle(40, 20, 24, 36), 1, sameTile, 56, 120.0d, 2L),
			new BillboardPaintOrder.Entry<>("ground", new Rectangle(42, 34, 20, 12), 0, sameTile, 46, 90.0d, 1L)
		));

		Assert.assertEquals(Arrays.asList("ground", "actor"), ordered);
	}

	@Test
	public void nonOverlappingGroupsStillSortByDepth()
	{
		List<String> ordered = BillboardPaintOrder.sort(Arrays.asList(
			new BillboardPaintOrder.Entry<>("near-left", new Rectangle(10, 20, 20, 20), 1, BillboardPaintOrder.NO_PRIORITY_GROUP, 40, 80.0d, 1L),
			new BillboardPaintOrder.Entry<>("far-right", new Rectangle(200, 20, 20, 20), 1, BillboardPaintOrder.NO_PRIORITY_GROUP, 40, 140.0d, 2L)
		));

		Assert.assertEquals(Arrays.asList("far-right", "near-left"), ordered);
	}
}
