package com.kierenboal.npcsnap.export;

import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BillboardExportAnimationFramesTest
{
	@Test
	public void samplesEvenlyAcrossTheCompleteAnimation()
	{
		assertEquals(
			Arrays.asList(0, 12, 24, 36),
			BillboardExportAnimationFrames.sampledFrames(48, 4));
		assertEquals(
			Arrays.asList(0, 9, 18, 27),
			BillboardExportAnimationFrames.sampledFrames(36, 4));
		assertEquals(
			Arrays.asList(0, 1, 2, 3),
			BillboardExportAnimationFrames.sampledFrames(4, 50));
	}

	@Test
	public void alwaysIncludesFrameZero()
	{
		assertEquals(Arrays.asList(0), BillboardExportAnimationFrames.sampledFrames(0, 0));
		assertEquals(Arrays.asList(0), BillboardExportAnimationFrames.sampledFrames(1, 120));
	}
}
