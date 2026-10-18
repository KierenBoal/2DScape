package com.kierenboal.npcsnap.rendering;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NpcSnapColorBandingTest
{
	@Test
	public void copyAndInPlaceBandingQuantizePixelsAndPreserveAlpha()
	{
		int[] original = {
			0x00000000, 0xFF808080, 0x80FFFFFF
		};
		int[] expected = {0x001A1A1A, 0xFF7F7F7F, 0x80E6E6E6};

		int[] banded = NpcSnapColorBanding.bandPixels(original, 3);

		assertNotSame(original, banded);
		assertArrayEquals(expected, banded);
		assertArrayEquals(new int[] {0x00000000, 0xFF808080, 0x80FFFFFF}, original);
		int[] copied = original.clone();
		NpcSnapColorBanding.applyBandsInPlace(copied, 3);
		assertArrayEquals(expected, copied);
	}

	@Test
	public void spriteBandingKeepsZeroTransparentAndVisiblePixelsAsNonZeroRgb()
	{
		int[] original = {
			0x00000000,
			0x00FFFFFF,
			0xFF000000
		};

		int[] banded = NpcSnapColorBanding.bandSpritePixels(original, 3);

		assertArrayEquals(new int[] {0, 0x00E6E6E6, 0x001A1A1A}, banded);
	}

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
