package com.kierenboal.npcsnap;

import org.junit.Assert;
import org.junit.Test;

public class BillboardRenderQualityTest
{
	@Test
	public void clampsQualityScaleToSupportedRange()
	{
		Assert.assertEquals(0.01d, BillboardRenderQuality.clamp(0.0d), 0.00001d);
		Assert.assertEquals(0.5d, BillboardRenderQuality.clamp(0.5d), 0.00001d);
		Assert.assertEquals(1.0d, BillboardRenderQuality.clamp(2.0d), 0.00001d);
	}

	@Test
	public void convertsPercentToQualityScale()
	{
		Assert.assertEquals(0.25d, BillboardRenderQuality.fromPercent(25), 0.00001d);
		Assert.assertEquals(1.0d, BillboardRenderQuality.fromPercent(250), 0.00001d);
	}

	@Test
	public void buildsStableIntegerQualityKeys()
	{
		Assert.assertEquals(100, BillboardRenderQuality.key(0.0d));
		Assert.assertEquals(2500, BillboardRenderQuality.key(0.25d));
		Assert.assertEquals(10_000, BillboardRenderQuality.key(2.0d));
	}

	@Test
	public void seededScaleDropsQualityByBootstrapTier()
	{
		Assert.assertEquals(0.8d, BillboardRenderQuality.seededScale(0.8d, 0, 2), 0.00001d);
		Assert.assertEquals(0.4d, BillboardRenderQuality.seededScale(0.8d, 2, 2), 0.00001d);
		Assert.assertEquals(0.2d, BillboardRenderQuality.seededScale(0.8d, 4, 2), 0.00001d);
	}
}
