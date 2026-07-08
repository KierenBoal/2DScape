package com.kierenboal.npcsnap;

import java.awt.Rectangle;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NpcBillboardOverlayOcclusionTest
{
	@Test
	public void expandedOcclusionInterestAddsSamplingMargin()
	{
		assertEquals(
			new Rectangle(84, 184, 64, 84),
			NpcBillboardOverlay.expandedOcclusionInterest(new Rectangle(100, 200, 32, 52), 16)
		);
	}

	@Test
	public void expandedOcclusionInterestLeavesZeroMarginUnchanged()
	{
		Rectangle bounds = new Rectangle(100, 200, 32, 52);

		assertEquals(bounds, NpcBillboardOverlay.expandedOcclusionInterest(bounds, 0));
	}
}
