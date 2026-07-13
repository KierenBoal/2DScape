package com.kierenboal.npcsnap;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BillboardAngleUtilsTest
{
	@Test
	public void convertsAndWrapsLegacyAngles()
	{
		assertEquals(0, BillboardAngleUtils.angleToBillboardUnits(0, 2048));
		assertEquals(4096, BillboardAngleUtils.angleToBillboardUnits(512, 2048));
		assertEquals(12288, BillboardAngleUtils.angleToBillboardUnits(-512, 2048));
		assertEquals(0, BillboardAngleUtils.angleToBillboardUnits(2048, 2048));
	}

	@Test
	public void choosesNearestCombatFacingIncludingWrapAround()
	{
		assertEquals(BillboardAngleUtils.COMBAT_YAW, BillboardAngleUtils.combatYaw(0));
		assertEquals(BillboardAngleUtils.COMBAT_YAW, BillboardAngleUtils.combatYaw(4096));
		assertEquals(BillboardAngleUtils.OPPOSITE_COMBAT_YAW, BillboardAngleUtils.combatYaw(12000));
		assertEquals(BillboardAngleUtils.OPPOSITE_COMBAT_YAW, BillboardAngleUtils.combatYaw(-4096));
	}

	@Test
	public void snapsYawAcrossTheCircularBoundary()
	{
		assertEquals(0, BillboardAngleUtils.snapJauByAngles(0, 4));
		assertEquals(4096, BillboardAngleUtils.snapJauByAngles(3000, 4));
		assertEquals(0, BillboardAngleUtils.snapJauByAngles(16383, 4));
		assertEquals(0, BillboardAngleUtils.snapJauByAngles(-1, 4));
		assertEquals(0, BillboardAngleUtils.snapJauByAngles(1234, 0));
	}

	@Test
	public void clampsAndSnapsPitchToConfiguredRange()
	{
		int minimum = 1024;
		assertEquals(minimum, BillboardAngleUtils.snapPitchByAngles(-1, minimum, 4));
		assertEquals(BillboardAngleUtils.MAX_PITCH, BillboardAngleUtils.snapPitchByAngles(Integer.MAX_VALUE, minimum, 4));
		assertEquals(minimum, BillboardAngleUtils.snapPitchByAngles(2000, minimum, 1));
		assertEquals(BillboardAngleUtils.MAX_PITCH, BillboardAngleUtils.snapPitchByAngles(2000, Integer.MAX_VALUE, 4));
	}
}
