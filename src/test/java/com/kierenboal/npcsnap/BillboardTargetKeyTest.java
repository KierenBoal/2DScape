package com.kierenboal.npcsnap;

import net.runelite.api.TileItem;
import net.runelite.api.coords.LocalPoint;
import org.junit.Assert;
import org.junit.Test;

import static com.kierenboal.npcsnap.TestProxies.method;
import static com.kierenboal.npcsnap.TestProxies.proxy;

public class BillboardTargetKeyTest
{
	@Test
	public void keysForSameRenderableAndLocationAreEqual()
	{
		TileItem item = proxy(TileItem.class, method("getId", 4151));
		GroundItemBillboard groundItem = new GroundItemBillboard(0, new LocalPoint(128, 256));

		BillboardTargetKey first = BillboardTargetKey.forGroundItem(item, groundItem);
		BillboardTargetKey second = BillboardTargetKey.forGroundItem(item, groundItem);

		Assert.assertEquals(first, second);
		Assert.assertEquals(first.hashCode(), second.hashCode());
	}

	@Test
	public void queueOrderUsesStableEntityAndLocationBeforeIdentity()
	{
		TileItem lowerIdItem = proxy(TileItem.class, method("getId", 100));
		TileItem higherIdItem = proxy(TileItem.class, method("getId", 200));
		GroundItemBillboard groundItem = new GroundItemBillboard(0, new LocalPoint(128, 128));

		BillboardTargetKey lowerIdKey = BillboardTargetKey.forGroundItem(lowerIdItem, groundItem);
		BillboardTargetKey higherIdKey = BillboardTargetKey.forGroundItem(higherIdItem, groundItem);

		Assert.assertTrue(BillboardTargetKey.compareForQueueOrder(lowerIdKey, higherIdKey) < 0);
		Assert.assertTrue(BillboardTargetKey.compareForQueueOrder(higherIdKey, lowerIdKey) > 0);
	}
}
