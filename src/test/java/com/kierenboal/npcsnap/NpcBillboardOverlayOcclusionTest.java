package com.kierenboal.npcsnap;

import com.kierenboal.npcsnap.occlusion.BillboardOcclusionRegions;

import java.awt.Rectangle;
import net.runelite.api.Point;
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
	public void groundContactAlignsVisibleContentBottomToActorGroundPoint()
	{
		Rectangle drawRect = NpcBillboardOverlay.alignGroundContact(
			new Rectangle(10, 100, 20, 40),
			new Rectangle(0, 0, 20, 40),
			new Rectangle(2, 5, 16, 25),
			new Point(20, 200));

		assertEquals(new Rectangle(10, 170, 20, 40), drawRect);
	}
}
