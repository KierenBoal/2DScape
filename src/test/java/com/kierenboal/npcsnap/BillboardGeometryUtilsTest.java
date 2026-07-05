package com.kierenboal.npcsnap;

import org.junit.Assert;
import org.junit.Test;

public class BillboardGeometryUtilsTest
{
	@Test
	public void wrapUnitKeepsValuesInsideUnitInterval()
	{
		Assert.assertEquals(0.25d, BillboardGeometryUtils.wrapUnit(0.25d), 0.00001d);
		Assert.assertEquals(0.25d, BillboardGeometryUtils.wrapUnit(1.25d), 0.00001d);
		Assert.assertEquals(0.75d, BillboardGeometryUtils.wrapUnit(-0.25d), 0.00001d);
	}
}
