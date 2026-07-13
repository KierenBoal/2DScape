package com.kierenboal.npcsnap.targeting;

import com.kierenboal.npcsnap.BillboardConstants;
import com.kierenboal.npcsnap.features.GroundItemBillboard;

import net.runelite.api.Actor;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.GraphicsObject;
import net.runelite.api.NPC;
import net.runelite.api.Projectile;
import net.runelite.api.Renderable;
import net.runelite.api.TileItem;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;

public final class BillboardTargetKey
{
	private final long orderKey;
	private final long uniqueKey;

	private BillboardTargetKey(long orderKey, long uniqueKey)
	{
		this.orderKey = orderKey;
		this.uniqueKey = uniqueKey;
	}

	public static BillboardTargetKey forRenderable(BillboardTargetType type, Renderable renderable, LocalPoint localPoint, int plane)
	{
		int entityId = renderableEntityId(type, renderable);
		long locationKey = locationKey(localPoint, plane);
		long orderKey = composeOrderKey(type.ordinal(), entityId, locationKey);
		long uniqueKey = composeUniqueKey(orderKey, System.identityHashCode(renderable));
		return new BillboardTargetKey(orderKey, uniqueKey);
	}

	public static BillboardTargetKey forActorSpotAnim(ActorSpotAnim actorSpotAnim, Actor actor, LocalPoint localPoint, int plane)
	{
		long locationKey = locationKey(localPoint, plane);
		long orderKey = composeOrderKey(BillboardTargetType.ACTOR_SPOT_ANIM.ordinal(), actorSpotAnim.getId(), locationKey);
		long uniqueKey = composeUniqueKey(orderKey, System.identityHashCode(actorSpotAnim) ^ System.identityHashCode(actor));
		return new BillboardTargetKey(orderKey, uniqueKey);
	}

	public static BillboardTargetKey forGroundItem(TileItem item, GroundItemBillboard groundItem)
	{
		LocalPoint localPoint = groundItem != null ? groundItem.localPoint : null;
		int plane = groundItem != null ? groundItem.plane : -1;
		long locationKey = locationKey(localPoint, plane);
		long orderKey = composeOrderKey(BillboardTargetType.GROUND_ITEM.ordinal(), item.getId(), locationKey);
		long uniqueKey = composeUniqueKey(orderKey, System.identityHashCode(item));
		return new BillboardTargetKey(orderKey, uniqueKey);
	}

	public static BillboardTargetKey forTileObject(TileObject tileObject, LocalPoint localPoint, int plane)
	{
		int objectId = tileObject != null ? tileObject.getId() : -1;
		long locationKey = locationKey(localPoint, plane);
		long orderKey = composeOrderKey(BillboardTargetType.TILE_OBJECT.ordinal(), objectId, locationKey);
		long uniqueKey = composeUniqueKey(orderKey, System.identityHashCode(tileObject));
		return new BillboardTargetKey(orderKey, uniqueKey);
	}

	public static int compareForQueueOrder(BillboardTargetKey left, BillboardTargetKey right)
	{
		int byOrder = Long.compare(left.orderKey, right.orderKey);
		if (byOrder != 0)
		{
			return byOrder;
		}

		return Long.compare(left.uniqueKey, right.uniqueKey);
	}

	public long stableSortOrder()
	{
		return uniqueKey;
	}

	private static int renderableEntityId(BillboardTargetType type, Renderable renderable)
	{
		if (renderable == null)
		{
			return -1;
		}

		switch (type)
		{
			case NPC:
				return ((NPC) renderable).getId();
			case PROJECTILE:
				return ((Projectile) renderable).getId();
			case GRAPHICS_OBJECT:
				return ((GraphicsObject) renderable).getId();
			case GROUND_ITEM:
				return ((TileItem) renderable).getId();
			default:
				return System.identityHashCode(renderable);
		}
	}

	private static long composeOrderKey(int typeOrdinal, int entityId, long locationKey)
	{
		long key = ((long) typeOrdinal & 0xFFL) << 56;
		key |= ((long) entityId & 0xFFFFFFL) << 32;
		key |= locationKey & 0xFFFFFFFFL;
		return key;
	}

	private static long composeUniqueKey(long orderKey, int identityHash)
	{
		return (orderKey * 31L) ^ (identityHash & 0xFFFFFFFFL);
	}

	private static long locationKey(LocalPoint localPoint, int plane)
	{
		if (localPoint == null)
		{
			return plane & 0x3L;
		}

		long tileX = (localPoint.getX() / BillboardConstants.LOCAL_TILE_SIZE) & 0x7FFL;
		long tileY = (localPoint.getY() / BillboardConstants.LOCAL_TILE_SIZE) & 0x7FFL;
		long planeBits = plane & 0x3L;
		return (planeBits << 22) | (tileX << 11) | tileY;
	}

	@Override
	public boolean equals(Object other)
	{
		if (this == other)
		{
			return true;
		}

		if (!(other instanceof BillboardTargetKey))
		{
			return false;
		}

		BillboardTargetKey that = (BillboardTargetKey) other;
		return orderKey == that.orderKey && uniqueKey == that.uniqueKey;
	}

	@Override
	public int hashCode()
	{
		int result = Long.hashCode(orderKey);
		result = (31 * result) + Long.hashCode(uniqueKey);
		return result;
	}
}

