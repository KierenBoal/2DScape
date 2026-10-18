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
	public void scrollPreservesSubframeMovementAndWrapsAfterAFullCycle()
	{
		Assert.assertEquals(0.875f, BillboardAnimatedTextures.vOffset(500L, null), 0.00001f);
		Assert.assertEquals(0.75f, BillboardAnimatedTextures.vOffset(1_000L, null), 0.00001f);
		Assert.assertEquals(0f, BillboardAnimatedTextures.vOffset(4_000L, null), 0.00001f);
		Assert.assertEquals(0.875f, BillboardAnimatedTextures.vOffset(4_500L, null), 0.00001f);
	}

	@Test
	public void offsetStateHashIgnoresNonAnimatedTextureIds()
	{
		Assert.assertEquals(0, BillboardAnimatedTextures.offsetStateHash(ItemID.COINS, 1_000L));
	}
}
