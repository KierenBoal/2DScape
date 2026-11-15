package com.kierenboal.npcsnap.targeting;

import com.kierenboal.npcsnap.BillboardConstants;

import net.runelite.api.Actor;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.GraphicsObject;
import net.runelite.api.coords.LocalPoint;

import lombok.EqualsAndHashCode;

@EqualsAndHashCode
public final class EffectDedupKey
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

	public static EffectDedupKey of(GraphicsObject graphicsObject)
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

	public static EffectDedupKey of(ActorSpotAnim actorSpotAnim, Actor actor)
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
}
