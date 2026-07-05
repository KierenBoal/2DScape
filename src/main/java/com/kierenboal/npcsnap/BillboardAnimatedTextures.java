package com.kierenboal.npcsnap;

import net.runelite.api.Actor;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.Texture;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.kit.KitType;

final class BillboardAnimatedTextures
{
	private static final float ANIMATED_TEXTURE_V_SCROLL_PER_SECOND = -0.25f;
	private static final int[] ANIMATED_TEXTURE_IDS = {
		ItemID.TZHAAR_CAPE_FIRE,
		ItemID.TZHAAR_CAPE_FIRE_DUMMY,
		ItemID.TZHAAR_CAPE_FIRE_TROUVER,
		ItemID.TZHAAR_CAPE_FIRE_BROKEN,
		ItemID.INFERNAL_CAPE,
		ItemID.INFERNAL_CAPE_DUMMY,
		ItemID.INFERNAL_CAPE_TROUVER,
		ItemID.INFERNAL_CAPE_BROKEN,
		ItemID.BR_INFERNAL_CAPE
	};

	private BillboardAnimatedTextures()
	{
	}

	static int findAnimatedTextureId(Actor actor)
	{
		if (!(actor instanceof Player))
		{
			return -1;
		}

		PlayerComposition composition = ((Player) actor).getPlayerComposition();
		if (composition == null)
		{
			return -1;
		}

		int capeId = normalizeEquipmentItemId(composition.getEquipmentId(KitType.CAPE));
		if (isAnimatedTextureId(capeId))
		{
			return capeId;
		}

		int[] equipmentIds = composition.getEquipmentIds();
		if (equipmentIds == null)
		{
			return -1;
		}

		for (int equipmentId : equipmentIds)
		{
			int normalizedId = normalizeEquipmentItemId(equipmentId);
			if (isAnimatedTextureId(normalizedId))
			{
				return normalizedId;
			}
		}

		return -1;
	}

	static boolean isAnimatedTextureId(int textureId)
	{
		for (int animatedTextureId : ANIMATED_TEXTURE_IDS)
		{
			if (animatedTextureId == textureId)
			{
				return true;
			}
		}

		return false;
	}

	static float vOffset(long nowMillis, Texture texture, boolean frameSnappingEnabled, int animationFrameCount)
	{
		double speedMultiplier = texture != null && texture.getAnimationSpeed() > 0
			? texture.getAnimationSpeed()
			: 1.0d;
		double offset = (nowMillis / 1000.0d) * ANIMATED_TEXTURE_V_SCROLL_PER_SECOND * speedMultiplier;
		return snappedOffset(offset, frameSnappingEnabled, animationFrameCount);
	}

	static int offsetStateHash(int animatedTextureId, long nowMillis, boolean frameSnappingEnabled, int animationFrameCount)
	{
		if (!isAnimatedTextureId(animatedTextureId))
		{
			return 0;
		}

		return Float.floatToIntBits(vOffset(nowMillis, null, frameSnappingEnabled, animationFrameCount));
	}

	private static float snappedOffset(double offset, boolean frameSnappingEnabled, int animationFrameCount)
	{
		offset = BillboardGeometryUtils.wrapUnit(offset);
		if (!frameSnappingEnabled)
		{
			return (float) offset;
		}

		int visibleFrameCount = Math.max(1, animationFrameCount);
		double snapped = Math.round(offset * visibleFrameCount) / (double) visibleFrameCount;
		return (float) BillboardGeometryUtils.wrapUnit(snapped);
	}

	private static int normalizeEquipmentItemId(int equipmentId)
	{
		if (equipmentId >= PlayerComposition.ITEM_OFFSET)
		{
			return equipmentId - PlayerComposition.ITEM_OFFSET;
		}

		return equipmentId;
	}
}
