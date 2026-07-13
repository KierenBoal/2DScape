package com.kierenboal.npcsnap;

import com.kierenboal.npcsnap.occlusion.BillboardOcclusionRegions;
import com.kierenboal.npcsnap.rendering.BillboardRenderResult;
import com.kierenboal.npcsnap.rendering.PreparedBillboardDraw;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NpcBillboardOverlayOcclusionTest
{
	@Test
	public void expandedOcclusionInterestAddsSamplingMargin()
	{
		assertEquals(
			new Rectangle(84, 184, 64, 84),
			BillboardOcclusionRegions.expanded(new Rectangle(100, 200, 32, 52), 16)
		);
	}

	@Test
	public void expandedOcclusionInterestLeavesZeroMarginUnchanged()
	{
		Rectangle bounds = new Rectangle(100, 200, 32, 52);

		assertEquals(bounds, BillboardOcclusionRegions.expanded(bounds, 0));
	}

	@Test
	public void clippedRegionsSkipInvalidDrawsAndClipToViewport()
	{
		PreparedBillboardDraw partlyVisible = draw(new Rectangle(-5, 5, 10, 10));
		PreparedBillboardDraw outside = draw(new Rectangle(50, 50, 5, 5));

		assertEquals(
			List.of(new Rectangle(0, 3, 7, 14)),
			BillboardOcclusionRegions.clippedDrawRegions(
				List.of(partlyVisible, outside, new PreparedBillboardDraw(null, null, 0)),
				new Rectangle(0, 0, 20, 20),
				2));
	}

	@Test
	public void unionIgnoresNullAndEmptyRegions()
	{
		assertEquals(
			new Rectangle(1, 2, 12, 12),
			BillboardOcclusionRegions.union(List.of(
				new Rectangle(), new Rectangle(1, 2, 3, 4), new Rectangle(10, 10, 3, 4))));
		assertEquals(null, BillboardOcclusionRegions.union(List.of()));
	}

	private static PreparedBillboardDraw draw(Rectangle bounds)
	{
		BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		return new PreparedBillboardDraw(null, new BillboardRenderResult(bounds, image, bounds), 0);
	}
}
