package com.kierenboal.npcsnap;

import org.junit.Assert;
import org.junit.Test;

public class BillboardPlaneUtilsTest
{
	@Test
	public void samePlaneTargetsRender()
	{
		Assert.assertTrue(BillboardPlaneUtils.shouldRenderTargetPlane(1, 1));
	}

	@Test
	public void differentPlaneTargetsDoNotRender()
	{
		Assert.assertFalse(BillboardPlaneUtils.shouldRenderTargetPlane(1, 0));
	}
}
