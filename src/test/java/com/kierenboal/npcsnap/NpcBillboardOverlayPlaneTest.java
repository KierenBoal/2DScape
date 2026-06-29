package com.kierenboal.npcsnap;

import org.junit.Assert;
import org.junit.Test;

public class NpcBillboardOverlayPlaneTest
{
	@Test
	public void samePlaneTargetsRender()
	{
		Assert.assertTrue(NpcBillboardOverlay.shouldRenderTargetPlane(1, 1));
	}

	@Test
	public void differentPlaneTargetsDoNotRender()
	{
		Assert.assertFalse(NpcBillboardOverlay.shouldRenderTargetPlane(1, 0));
	}
}
