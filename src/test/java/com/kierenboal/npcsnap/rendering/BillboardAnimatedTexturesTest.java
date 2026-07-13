package com.kierenboal.npcsnap.rendering;

import net.runelite.api.gameval.ItemID;
import org.junit.Assert;
import org.junit.Test;

public class BillboardAnimatedTexturesTest
{
	@Test
	public void recognizesKnownAnimatedCapeTextures()
	{
		Assert.assertTrue(BillboardAnimatedTextures.isAnimatedTextureId(ItemID.TZHAAR_CAPE_FIRE));
		Assert.assertTrue(BillboardAnimatedTextures.isAnimatedTextureId(ItemID.INFERNAL_CAPE));
		Assert.assertTrue(BillboardAnimatedTextures.isAnimatedTextureId(ItemID.BR_INFERNAL_CAPE));
	}

	@Test
	public void rejectsNonAnimatedTextureIds()
	{
		Assert.assertFalse(BillboardAnimatedTextures.isAnimatedTextureId(-1));
		Assert.assertFalse(BillboardAnimatedTextures.isAnimatedTextureId(ItemID.COINS));
	}

	@Test
	public void vOffsetWrapsAnimatedTextureScroll()
	{
		Assert.assertEquals(0.75f, BillboardAnimatedTextures.vOffset(1_000L, null, false, 4), 0.00001f);
	}

	@Test
	public void vOffsetSnapsToVisibleFrameCount()
	{
		Assert.assertEquals(2.0f / 3.0f, BillboardAnimatedTextures.vOffset(1_000L, null, true, 3), 0.00001f);
	}

	@Test
	public void offsetStateHashIgnoresNonAnimatedTextureIds()
	{
		Assert.assertEquals(0, BillboardAnimatedTextures.offsetStateHash(ItemID.COINS, 1_000L, true, 4));
	}
}
