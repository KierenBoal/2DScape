package com.kierenboal.npcsnap;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

import org.junit.Test;

public class NpcSnapColorBandingTest
{
	@Test
	public void bandPixelsReturnsNewArrayAndPreservesTransparentPixels()
	{
		int[] original = {
			0x00010203,
			0xFF336699
		};

		int[] banded = NpcSnapColorBanding.bandPixels(original, 4);

		assertNotSame(original, banded);
		assertEquals(0, banded[0] >>> 24);
		assertArrayEquals(new int[] {0x00010203, original[1]}, original);
	}

	@Test
	public void applyBandsInPlaceMatchesBandPixels()
	{
		int[] original = {
			0xFF102030,
			0xFF90A0B0,
			0x80224466
		};

		int[] copied = original.clone();
		int[] banded = NpcSnapColorBanding.bandPixels(original, 6);
		NpcSnapColorBanding.applyBandsInPlace(copied, 6);

		assertArrayEquals(banded, copied);
	}

	@Test
	public void bandSpritePixelsKeepsFullyTransparentPixelsZero()
	{
		int[] original = {
			0x00000000,
			0x00102030,
			0x0090A0B0
		};

		int[] banded = NpcSnapColorBanding.bandSpritePixels(original, 5);

		assertEquals(0, banded[0]);
		assertEquals(0, banded[1] >>> 24);
		assertEquals(0, banded[2] >>> 24);
		assertEquals(0, banded[0]);
		assertEquals(0, banded[1] & 0xFF000000);
		assertEquals(0, banded[2] & 0xFF000000);
	}

	@Test
	public void bandSpritePixelsKeepsVisibleBlackNonZero()
	{
		int[] banded = NpcSnapColorBanding.bandSpritePixels(new int[] {0x00010101}, 1);

		assertEquals(false, banded[0] == 0);
	}
}
