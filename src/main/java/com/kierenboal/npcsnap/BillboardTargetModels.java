package com.kierenboal.npcsnap;

import java.util.List;
import java.util.Map;
import net.runelite.api.Actor;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.GraphicsObject;
import net.runelite.api.NPC;
import net.runelite.api.Projectile;
import net.runelite.api.Renderable;
import net.runelite.api.TileItem;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;

enum BillboardTargetType
{
	NPC,
	PLAYER,
	ACTOR_SPOT_ANIM,
	PROJECTILE,
	GRAPHICS_OBJECT,
	GROUND_ITEM,
	TILE_OBJECT
}

enum VerticalAnchor
{
	BOTTOM,
	CENTER
}

final class BillboardTarget
{
	final BillboardTargetType type;
	final ClassifiedObjectType classifiedType;
	final Renderable renderable;
	final Actor parentActor;
	final TileObject tileObject;
	final ObservedTileObject observedTileObject;
	final GroundItemBillboard groundItem;
	final double depth;
	final int renderPriority;
	final PriorityTileKey priorityTileKey;
	final BillboardTargetKey targetKey;

	private BillboardTarget(
		BillboardTargetType type,
		ClassifiedObjectType classifiedType,
		Renderable renderable,
		Actor parentActor,
		TileObject tileObject,
		ObservedTileObject observedTileObject,
		GroundItemBillboard groundItem,
		double depth,
		int renderPriority,
		PriorityTileKey priorityTileKey,
		BillboardTargetKey targetKey
	)
	{
		this.type = type;
		this.classifiedType = classifiedType;
		this.renderable = renderable;
		this.parentActor = parentActor;
		this.tileObject = tileObject;
		this.observedTileObject = observedTileObject;
		this.groundItem = groundItem;
		this.depth = depth;
		this.renderPriority = renderPriority;
		this.priorityTileKey = priorityTileKey;
		this.targetKey = targetKey;
	}

	static BillboardTarget forRenderable(
		BillboardTargetType type,
		ClassifiedObjectType classifiedType,
		Renderable renderable,
		double depth,
		LocalPoint localPoint,
		int plane
	)
	{
		int renderPriority = ObjectClassifier.renderPriority(classifiedType);
		return new BillboardTarget(
			type,
			classifiedType,
			renderable,
			null,
			null,
			null,
			null,
			depth,
			renderPriority,
			PriorityTileKey.of(localPoint, plane, renderPriority),
			BillboardTargetKey.forRenderable(type, renderable, localPoint, plane)
		);
	}

	static BillboardTarget forActorSpotAnim(ActorSpotAnim actorSpotAnim, Actor actor, double depth)
	{
		LocalPoint localPoint = actor.getLocalLocation();
		int plane = actor.getWorldView().getPlane();
		int renderPriority = ObjectClassifier.renderPriority(ClassifiedObjectType.EFFECT);
		return new BillboardTarget(
			BillboardTargetType.ACTOR_SPOT_ANIM,
			ClassifiedObjectType.EFFECT,
			actorSpotAnim,
			actor,
			null,
			null,
			null,
			depth,
			renderPriority,
			PriorityTileKey.of(localPoint, plane, renderPriority),
			BillboardTargetKey.forActorSpotAnim(actorSpotAnim, actor, localPoint, plane)
		);
	}

	static BillboardTarget forGroundItem(TileItem item, GroundItemBillboard groundItem, double depth)
	{
		int renderPriority = ObjectClassifier.renderPriority(ClassifiedObjectType.GROUND_ITEM);
		return new BillboardTarget(
			BillboardTargetType.GROUND_ITEM,
			ClassifiedObjectType.GROUND_ITEM,
			item,
			null,
			null,
			null,
			groundItem,
			depth,
			renderPriority,
			PriorityTileKey.of(groundItem.localPoint, groundItem.plane, renderPriority),
			BillboardTargetKey.forGroundItem(item, groundItem)
		);
	}

	static BillboardTarget forTileObject(ObservedTileObject observedTileObject, double depth)
	{
		ClassifiedObjectType classifiedType = observedTileObject != null ? observedTileObject.classifiedType : ClassifiedObjectType.UNKNOWN;
		int renderPriority = ObjectClassifier.renderPriority(classifiedType);
		return new BillboardTarget(
			BillboardTargetType.TILE_OBJECT,
			classifiedType,
			null,
			null,
			observedTileObject.tileObject,
			observedTileObject,
			null,
			depth,
			renderPriority,
			PriorityTileKey.of(firstLocalPoint(observedTileObject), firstPlane(observedTileObject), renderPriority),
			BillboardTargetKey.forTileObject(observedTileObject.tileObject, firstLocalPoint(observedTileObject), firstPlane(observedTileObject))
		);
	}

	double getDepth()
	{
		return depth;
	}

	int getRenderPriority()
	{
		return renderPriority;
	}

	PriorityTileKey getPriorityTileKey()
	{
		return priorityTileKey;
	}

	long priorityGroupSortKey()
	{
		return priorityTileKey != null ? priorityTileKey.sortKey() : BillboardPaintOrder.NO_PRIORITY_GROUP;
	}

	private static LocalPoint firstLocalPoint(ObservedTileObject observedTileObject)
	{
		if (observedTileObject == null)
		{
			return null;
		}

		for (ObjectRenderablePart part : observedTileObject.parts)
		{
			if (part.localPoint != null)
			{
				return part.localPoint;
			}
		}

		return null;
	}

	private static int firstPlane(ObservedTileObject observedTileObject)
	{
		if (observedTileObject == null)
		{
			return -1;
		}

		for (ObjectRenderablePart part : observedTileObject.parts)
		{
			return part.plane;
		}

		return -1;
	}
}

final class BillboardTargetKey
{
	private final long orderKey;
	private final long uniqueKey;

	private BillboardTargetKey(long orderKey, long uniqueKey)
	{
		this.orderKey = orderKey;
		this.uniqueKey = uniqueKey;
	}

	static BillboardTargetKey forRenderable(BillboardTargetType type, Renderable renderable, LocalPoint localPoint, int plane)
	{
		int entityId = renderableEntityId(type, renderable);
		long locationKey = locationKey(localPoint, plane);
		long orderKey = composeOrderKey(type.ordinal(), entityId, locationKey);
		long uniqueKey = composeUniqueKey(orderKey, System.identityHashCode(renderable));
		return new BillboardTargetKey(orderKey, uniqueKey);
	}

	static BillboardTargetKey forActorSpotAnim(ActorSpotAnim actorSpotAnim, Actor actor, LocalPoint localPoint, int plane)
	{
		long locationKey = locationKey(localPoint, plane);
		long orderKey = composeOrderKey(BillboardTargetType.ACTOR_SPOT_ANIM.ordinal(), actorSpotAnim.getId(), locationKey);
		long uniqueKey = composeUniqueKey(orderKey, System.identityHashCode(actorSpotAnim) ^ System.identityHashCode(actor));
		return new BillboardTargetKey(orderKey, uniqueKey);
	}

	static BillboardTargetKey forGroundItem(TileItem item, GroundItemBillboard groundItem)
	{
		LocalPoint localPoint = groundItem != null ? groundItem.localPoint : null;
		int plane = groundItem != null ? groundItem.plane : -1;
		long locationKey = locationKey(localPoint, plane);
		long orderKey = composeOrderKey(BillboardTargetType.GROUND_ITEM.ordinal(), item.getId(), locationKey);
		long uniqueKey = composeUniqueKey(orderKey, System.identityHashCode(item));
		return new BillboardTargetKey(orderKey, uniqueKey);
	}

	static BillboardTargetKey forTileObject(TileObject tileObject, LocalPoint localPoint, int plane)
	{
		int objectId = tileObject != null ? tileObject.getId() : -1;
		long locationKey = locationKey(localPoint, plane);
		long orderKey = composeOrderKey(BillboardTargetType.TILE_OBJECT.ordinal(), objectId, locationKey);
		long uniqueKey = composeUniqueKey(orderKey, System.identityHashCode(tileObject));
		return new BillboardTargetKey(orderKey, uniqueKey);
	}

	static int compareForQueueOrder(BillboardTargetKey left, BillboardTargetKey right)
	{
		int byOrder = Long.compare(left.orderKey, right.orderKey);
		if (byOrder != 0)
		{
			return byOrder;
		}

		return Long.compare(left.uniqueKey, right.uniqueKey);
	}

	long stableSortOrder()
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

final class PriorityTileKey
{
	private final int plane;
	private final int tileX;
	private final int tileY;

	private PriorityTileKey(int plane, int tileX, int tileY)
	{
		this.plane = plane;
		this.tileX = tileX;
		this.tileY = tileY;
	}

	static PriorityTileKey of(LocalPoint localPoint, int plane, int renderPriority)
	{
		if (renderPriority == BillboardConstants.RENDER_PRIORITY_NONE || localPoint == null || plane < 0)
		{
			return null;
		}

		int tileX = localPoint.getX() / BillboardConstants.LOCAL_TILE_SIZE;
		int tileY = localPoint.getY() / BillboardConstants.LOCAL_TILE_SIZE;
		return new PriorityTileKey(plane, tileX, tileY);
	}

	long sortKey()
	{
		return ((long) plane << 48)
			^ ((long) (tileX & 0xFFFFFF) << 24)
			^ (tileY & 0xFFFFFFL);
	}

	@Override
	public boolean equals(Object other)
	{
		if (this == other)
		{
			return true;
		}

		if (!(other instanceof PriorityTileKey))
		{
			return false;
		}

		PriorityTileKey that = (PriorityTileKey) other;
		return plane == that.plane && tileX == that.tileX && tileY == that.tileY;
	}

	@Override
	public int hashCode()
	{
		int result = plane;
		result = 31 * result + tileX;
		result = 31 * result + tileY;
		return result;
	}
}

final class EffectDedupKey
{
	private final int id;
	private final int plane;
	private final int tileX;
	private final int tileY;

	private EffectDedupKey(int id, int plane, int tileX, int tileY)
	{
		this.id = id;
		this.plane = plane;
		this.tileX = tileX;
		this.tileY = tileY;
	}

	static EffectDedupKey of(GraphicsObject graphicsObject)
	{
		LocalPoint localPoint = graphicsObject.getLocation();
		if (localPoint == null)
		{
			return null;
		}

		return new EffectDedupKey(
			graphicsObject.getId(),
			graphicsObject.getLevel(),
			localPoint.getX() / BillboardConstants.LOCAL_TILE_SIZE,
			localPoint.getY() / BillboardConstants.LOCAL_TILE_SIZE
		);
	}

	static EffectDedupKey of(ActorSpotAnim actorSpotAnim, Actor actor)
	{
		LocalPoint localPoint = actor.getLocalLocation();
		if (localPoint == null)
		{
			return null;
		}

		return new EffectDedupKey(
			actorSpotAnim.getId(),
			actor.getWorldView().getPlane(),
			localPoint.getX() / BillboardConstants.LOCAL_TILE_SIZE,
			localPoint.getY() / BillboardConstants.LOCAL_TILE_SIZE
		);
	}

	@Override
	public boolean equals(Object other)
	{
		if (this == other)
		{
			return true;
		}

		if (!(other instanceof EffectDedupKey))
		{
			return false;
		}

		EffectDedupKey that = (EffectDedupKey) other;
		return id == that.id
			&& plane == that.plane
			&& tileX == that.tileX
			&& tileY == that.tileY;
	}

	@Override
	public int hashCode()
	{
		int result = Integer.hashCode(id);
		result = (31 * result) + Integer.hashCode(plane);
		result = (31 * result) + Integer.hashCode(tileX);
		result = (31 * result) + Integer.hashCode(tileY);
		return result;
	}
}

final class OccupiedTileKey
{
	private final int plane;
	private final int tileX;
	private final int tileY;

	private OccupiedTileKey(int plane, int tileX, int tileY)
	{
		this.plane = plane;
		this.tileX = tileX;
		this.tileY = tileY;
	}

	static OccupiedTileKey of(LocalPoint localPoint, int plane)
	{
		if (localPoint == null)
		{
			return null;
		}

		return new OccupiedTileKey(
			plane,
			localPoint.getX() / BillboardConstants.LOCAL_TILE_SIZE,
			localPoint.getY() / BillboardConstants.LOCAL_TILE_SIZE
		);
	}

	@Override
	public boolean equals(Object other)
	{
		if (this == other)
		{
			return true;
		}

		if (!(other instanceof OccupiedTileKey))
		{
			return false;
		}

		OccupiedTileKey that = (OccupiedTileKey) other;
		return plane == that.plane
			&& tileX == that.tileX
			&& tileY == that.tileY;
	}

	@Override
	public int hashCode()
	{
		int result = Integer.hashCode(plane);
		result = (31 * result) + Integer.hashCode(tileX);
		result = (31 * result) + Integer.hashCode(tileY);
		return result;
	}
}

final class GroundItemBillboard
{
	final int plane;
	final LocalPoint localPoint;

	GroundItemBillboard(int plane, LocalPoint localPoint)
	{
		this.plane = plane;
		this.localPoint = localPoint;
	}
}

final class ObjectRenderablePart
{
	final Renderable renderable;
	final LocalPoint localPoint;
	final int plane;

	ObjectRenderablePart(Renderable renderable, LocalPoint localPoint, int plane)
	{
		this.renderable = renderable;
		this.localPoint = localPoint;
		this.plane = plane;
	}
}

final class ObservedTileObject
{
	final TileObject tileObject;
	final List<ObjectRenderablePart> parts;
	ClassifiedObjectType classifiedType;
	ClassifiedObjectType lastLoggedClassification;
	String lastLoggedClassificationReason;

	ObservedTileObject(TileObject tileObject, List<ObjectRenderablePart> parts, ClassifiedObjectType classifiedType)
	{
		this.tileObject = tileObject;
		this.parts = parts;
		this.classifiedType = classifiedType;
		this.lastLoggedClassification = null;
		this.lastLoggedClassificationReason = null;
	}
}

final class CollectedCandidates
{
	final List<BillboardTarget> candidates;
	final Map<OccupiedTileKey, Actor> topActorsByTile;
	final Map<OccupiedTileKey, List<Actor>> actorsByTile;

	CollectedCandidates(
		List<BillboardTarget> candidates,
		Map<OccupiedTileKey, Actor> topActorsByTile,
		Map<OccupiedTileKey, List<Actor>> actorsByTile
	)
	{
		this.candidates = candidates;
		this.topActorsByTile = topActorsByTile;
		this.actorsByTile = actorsByTile;
	}
}
