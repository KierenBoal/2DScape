package com.kierenboal.npcsnap.rendering;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NpcSnapColorBandingTest
{
	@Test
	public void snapRgbKeepsDarkSaturatedRedVisible()
	{
		int snapped = NpcSnapColorBanding.snapRgb(74, 7, 0, 16);

		assertTrue(((snapped >> 16) & 0xFF) > 0);
		assertTrue(((snapped >> 16) & 0xFF) > ((snapped >> 8) & 0xFF));
		assertEquals(0, snapped & 0xFF);
	}

	@Test
	public void snapRgbKeepsDarkSaturatedBlueVisible()
	{
		int snapped = NpcSnapColorBanding.snapRgb(0, 22, 74, 16);

		assertEquals(0, (snapped >> 16) & 0xFF);
		assertTrue((snapped & 0xFF) > 0);
		assertTrue((snapped & 0xFF) > ((snapped >> 8) & 0xFF));
	}
}
