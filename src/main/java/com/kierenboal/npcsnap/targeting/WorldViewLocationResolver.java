package com.kierenboal.npcsnap.targeting;

import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Projection;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;

/** Resolves nested-world-view coordinates (used by sailing) into the top-level scene. */
public final class WorldViewLocationResolver
{
	private WorldViewLocationResolver()
	{
	}

	/** Coordinates use RuneLite's X, height (down), Y projection convention. */
	public static ResolvedLocation resolve(Client client, Actor actor)
	{
		if (actor == null || actor.getWorldView() == null || actor.getLocalLocation() == null)
		{
			return null;
		}
		WorldView source = actor.getWorldView();
		LocalPoint point = actor.getLocalLocation();
		double height = source.isTopLevel()
			? Perspective.getFootprintTileHeight(client, point, source.getPlane(), actor.getFootprintSize())
			: source.getTileHeight(point.getX(), point.getY(), source.getPlane());
		return resolve(client, source, point, source.getPlane(), height);
	}

	public static ResolvedLocation resolve(Client client, WorldView source, LocalPoint point, int plane, double height)
	{
		WorldView top = client.getTopLevelWorldView();
		if (top == null || source == null || point == null)
		{
			return null;
		}
		if (source.isTopLevel())
		{
			return new ResolvedLocation(source, point, plane, point, height);
		}
		WorldEntity owner = owningEntity(top, source);
		Projection projection = source.getMainWorldProjection();
		if (owner == null || owner.isHiddenForOverlap() || projection == null || owner.getLocalLocation() == null)
		{
			return null;
		}
		LocalPoint origin = owner.getLocalLocation();
		height += top.getTileHeight(origin.getX(), origin.getY(), top.getPlane());
		float[] projected = projection.project(point.getX(), (float) height, point.getY());
		if (projected == null || projected.length < 3 || !Float.isFinite(projected[0]) || !Float.isFinite(projected[1]) || !Float.isFinite(projected[2]))
		{
			return null;
		}
		return new ResolvedLocation(source, point, plane,
			new LocalPoint(Math.round(projected[0]), Math.round(projected[2]), top), projected[1]);
	}

	public static final class ResolvedLocation
	{
		public final WorldView source;
		public final LocalPoint sourcePoint;
		public final int sourcePlane;
		public final LocalPoint mainPoint;
		public final double height;

		private ResolvedLocation(WorldView source, LocalPoint sourcePoint, int sourcePlane, LocalPoint mainPoint, double height)
		{
			this.source = source;
			this.sourcePoint = sourcePoint;
			this.sourcePlane = sourcePlane;
			this.mainPoint = mainPoint;
			this.height = height;
		}
	}

	public static LocalPoint toMainWorld(WorldView topLevel, Actor actor)
	{
		if (actor == null)
		{
			return null;
		}
		return toMainWorld(topLevel, actor.getWorldView(), actor.getLocalLocation());
	}

	public static LocalPoint toMainWorld(Client client, Actor actor)
	{
		if (actor == null || actor.getWorldView() == null) return null;
		if (actor.getWorldView().isTopLevel()) return actor.getLocalLocation();
		ResolvedLocation location = resolve(client, actor);
		return location != null ? location.mainPoint : null;
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
