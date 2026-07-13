package com.kierenboal.npcsnap;

import net.runelite.api.Actor;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.Renderable;
import net.runelite.api.TileItem;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;

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

