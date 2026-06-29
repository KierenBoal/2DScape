package com.kierenboal.npcsnap;

import org.junit.Assert;
import org.junit.Test;

public class NpcBillboardOverlayAngleTest
{
	@Test
	public void convertsLegacyActorYawToBillboardUnits()
	{
		Assert.assertEquals(4096, NpcBillboardOverlay.angleToBillboardUnits(512, 2048));
		Assert.assertEquals(8192, NpcBillboardOverlay.angleToBillboardUnits(1024, 2048));
		Assert.assertEquals(12288, NpcBillboardOverlay.angleToBillboardUnits(1536, 2048));
	}

	@Test
	public void preservesCameraYawAlreadyInBillboardUnits()
	{
		Assert.assertEquals(4096, NpcBillboardOverlay.angleToBillboardUnits(4096, 16384));
		Assert.assertEquals(8192, NpcBillboardOverlay.angleToBillboardUnits(8192, 16384));
		Assert.assertEquals(12288, NpcBillboardOverlay.angleToBillboardUnits(12288, 16384));
	}

	@Test
	public void wrapsConvertedAngles()
	{
		Assert.assertEquals(0, NpcBillboardOverlay.angleToBillboardUnits(2048, 2048));
		Assert.assertEquals(4096, NpcBillboardOverlay.angleToBillboardUnits(2560, 2048));
		Assert.assertEquals(12288, NpcBillboardOverlay.angleToBillboardUnits(-512, 2048));
	}
}
