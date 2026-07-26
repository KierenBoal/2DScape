package com.kierenboal.npcsnap.export;

import com.kierenboal.npcsnap.rendering.BillboardAngleUtils;
import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BillboardExportAnglesTest
{
	@Test
	public void enumeratesEveryConfiguredYaw()
	{
		assertEquals(Arrays.asList(0), BillboardExportAngles.yaws(1));
		assertEquals(Arrays.asList(0, 4096, 8192, 12288), BillboardExportAngles.yaws(4));
		assertEquals(7, BillboardExportAngles.yaws(7).size());
	}

	@Test
	public void enumeratesActorPitchRangeInRendererUnits()
	{
		assertEquals(Arrays.asList(0), BillboardExportAngles.pitches(1, false));
		assertEquals(Arrays.asList(0, -2048, -4096), BillboardExportAngles.pitches(3, false));
	}

	@Test
	public void groundItemPitchStartsAtItsMinimum()
	{
		assertEquals(
			Arrays.asList(-BillboardAngleUtils.GROUND_ITEM_MIN_PITCH),
			BillboardExportAngles.pitches(1, true));
		assertEquals(
			Arrays.asList(-BillboardAngleUtils.GROUND_ITEM_MIN_PITCH, -BillboardAngleUtils.MAX_PITCH),
			BillboardExportAngles.pitches(2, true));
	}
}
