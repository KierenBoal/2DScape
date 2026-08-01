package com.kierenboal.npcsnap.targeting;

import net.runelite.api.Actor;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;

/** Resolves nested-world-view coordinates (used by sailing) into the top-level scene. */
public final class WorldViewLocationResolver
{
	private WorldViewLocationResolver()
	{
	}

	public static LocalPoint toMainWorld(WorldView topLevel, Actor actor)
	{
		if (actor == null)
		{
			return null;
		}
		return toMainWorld(topLevel, actor.getWorldView(), actor.getLocalLocation());
	}

	public static WorldEntity owningEntity(WorldView topLevel, WorldView source)
	{
		if (topLevel == null || source == null || source.isTopLevel() || topLevel.worldEntities() == null)
		{
			return null;
		}
		for (WorldEntity entity : topLevel.worldEntities())
		{
			if (entity != null && entity.getWorldView() == source)
			{
				return entity;
			}
		}
		return null;
	}

	public static int toMainWorldOrientation(WorldView topLevel, Actor actor)
	{
		if (actor == null)
		{
			return 0;
		}
		WorldEntity owner = owningEntity(topLevel, actor.getWorldView());
		return Math.floorMod(actor.getCurrentOrientation() + (owner != null ? owner.getOrientation() : 0), 2048);
	}

	public static LocalPoint toMainWorld(WorldView topLevel, WorldView source, LocalPoint point)
	{
		if (point == null || source == null || source.isTopLevel())
		{
			return point;
		}
		if (topLevel == null)
		{
			return null;
		}
		if (topLevel.worldEntities() == null)
		{
			return null;
		}
		WorldEntity owner = owningEntity(topLevel, source);
		return owner != null ? owner.transformToMainWorld(point) : null;
	}
}
