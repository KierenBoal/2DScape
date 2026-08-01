package com.kierenboal.npcsnap.targeting;

import com.kierenboal.npcsnap.features.GroundItemBillboard;
import com.kierenboal.npcsnap.rendering.BillboardPaintOrder;

import net.runelite.api.Actor;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.Renderable;
import net.runelite.api.TileItem;
import net.runelite.api.TileObject;
import net.runelite.api.WorldEntity;
import net.runelite.api.coords.LocalPoint;

public final class BillboardTarget
{
	public final BillboardTargetType type;
	public final ClassifiedObjectType classifiedType;
	public final Renderable renderable;
	public final Actor parentActor;
	public final TileObject tileObject;
	public final ObservedTileObject observedTileObject;
	public final GroundItemBillboard groundItem;
	public final WorldEntity worldEntity;
	public final double depth;
	public final int renderPriority;
	public final PriorityTileKey priorityTileKey;
	public final BillboardTargetKey targetKey;

	private BillboardTarget(
		BillboardTargetType type,
		ClassifiedObjectType classifiedType,
		Renderable renderable,
		Actor parentActor,
		TileObject tileObject,
		ObservedTileObject observedTileObject,
		GroundItemBillboard groundItem,
		WorldEntity worldEntity,
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
		this.worldEntity = worldEntity;
		this.depth = depth;
		this.renderPriority = renderPriority;
		this.priorityTileKey = priorityTileKey;
		this.targetKey = targetKey;
	}

	public static BillboardTarget forRenderable(
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
			null,
			depth,
			renderPriority,
			PriorityTileKey.of(localPoint, plane, renderPriority),
			BillboardTargetKey.forRenderable(type, renderable, localPoint, plane)
		);
	}

	public static BillboardTarget forActorSpotAnim(ActorSpotAnim actorSpotAnim, Actor actor, double depth)
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
			null,
			depth,
			renderPriority,
			PriorityTileKey.of(localPoint, plane, renderPriority),
			BillboardTargetKey.forActorSpotAnim(actorSpotAnim, actor, localPoint, plane)
		);
	}

	public static BillboardTarget forGroundItem(TileItem item, GroundItemBillboard groundItem, double depth)
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
			null,
			depth,
			renderPriority,
			PriorityTileKey.of(groundItem.localPoint, groundItem.plane, renderPriority),
			BillboardTargetKey.forGroundItem(item, groundItem)
		);
	}

	public static BillboardTarget forTileObject(ObservedTileObject observedTileObject, double depth)
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
			null,
			depth,
			renderPriority,
			PriorityTileKey.of(firstLocalPoint(observedTileObject), firstPlane(observedTileObject), renderPriority),
			BillboardTargetKey.forTileObject(observedTileObject.tileObject, firstLocalPoint(observedTileObject), firstPlane(observedTileObject))
		);
	}

	public static BillboardTarget forBoat(WorldEntity worldEntity, ObservedTileObject composite, double depth)
	{
		int renderPriority = ObjectClassifier.renderPriority(ClassifiedObjectType.BOAT);
		LocalPoint location = worldEntity != null ? worldEntity.getLocalLocation() : null;
		return new BillboardTarget(
			BillboardTargetType.BOAT,
			ClassifiedObjectType.BOAT,
			worldEntity != null && worldEntity.getWorldView() != null ? worldEntity.getWorldView().getScene() : null,
			null,
			null,
			composite,
			null,
			worldEntity,
			depth,
			renderPriority,
			PriorityTileKey.of(location, 0, renderPriority),
			BillboardTargetKey.forBoat(worldEntity)
		);
	}

	public double getDepth()
	{
		return depth;
	}

	public int getRenderPriority()
	{
		return renderPriority;
	}

	public PriorityTileKey getPriorityTileKey()
	{
		return priorityTileKey;
	}

	public long priorityGroupSortKey()
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

