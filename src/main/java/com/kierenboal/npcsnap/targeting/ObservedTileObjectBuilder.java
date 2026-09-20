package com.kierenboal.npcsnap.targeting;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Actor;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GraphicsObject;
import net.runelite.api.GroundObject;
import net.runelite.api.Projectile;
import net.runelite.api.Renderable;
import net.runelite.api.TileItem;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.coords.LocalPoint;

public final class ObservedTileObjectBuilder
{
	private ObservedTileObjectBuilder()
	{
	}

	public static ObservedTileObject build(TileObject tileObject)
	{
		if (tileObject == null || tileObject.getLocalLocation() == null)
		{
			return null;
		}

		List<ObjectRenderablePart> parts = new ArrayList<>(2);
		if (tileObject instanceof GameObject)
		{
			GameObject object = (GameObject) tileObject;
			addObjectRenderablePart(parts, object.getRenderable(), tileObject.getLocalLocation(),
				tileObject.getPlane(), tileObject.getZ(), object.getModelOrientation());
		}
		else if (tileObject instanceof GroundObject)
		{
			addObjectRenderablePart(parts, ((GroundObject) tileObject).getRenderable(), tileObject.getLocalLocation(), tileObject.getPlane(), tileObject.getZ());
		}
		else if (tileObject instanceof WallObject)
		{
			WallObject wallObject = (WallObject) tileObject;
			addObjectRenderablePart(parts, wallObject.getRenderable1(), tileObject.getLocalLocation(), tileObject.getPlane(), tileObject.getZ());
			addObjectRenderablePart(parts, wallObject.getRenderable2(), tileObject.getLocalLocation(), tileObject.getPlane(), tileObject.getZ());
		}
		else if (tileObject instanceof DecorativeObject)
		{
			DecorativeObject decorativeObject = (DecorativeObject) tileObject;
			addObjectRenderablePart(
				parts,
				decorativeObject.getRenderable(),
				offsetLocalPoint(tileObject.getLocalLocation(), decorativeObject.getXOffset(), decorativeObject.getYOffset()),
				tileObject.getPlane(), tileObject.getZ()
			);
			addObjectRenderablePart(
				parts,
				decorativeObject.getRenderable2(),
				offsetLocalPoint(tileObject.getLocalLocation(), decorativeObject.getXOffset2(), decorativeObject.getYOffset2()),
				tileObject.getPlane(), tileObject.getZ()
			);
		}

		return parts.isEmpty() ? null : new ObservedTileObject(tileObject, parts, ClassifiedObjectType.UNKNOWN);
	}

	private static void addObjectRenderablePart(List<ObjectRenderablePart> parts, Renderable renderable, LocalPoint localPoint, int plane, int worldHeight)
	{
		addObjectRenderablePart(parts, renderable, localPoint, plane, worldHeight, 0);
	}

	private static void addObjectRenderablePart(List<ObjectRenderablePart> parts, Renderable renderable,
		LocalPoint localPoint, int plane, int worldHeight, int modelOrientation)
	{
		if (renderable == null || localPoint == null
			|| renderable instanceof Actor
			|| renderable instanceof ActorSpotAnim
			|| renderable instanceof Projectile
			|| renderable instanceof GraphicsObject
			|| renderable instanceof TileItem)
		{
			return;
		}

		parts.add(new ObjectRenderablePart(renderable, localPoint, plane, worldHeight, modelOrientation));
	}

	private static LocalPoint offsetLocalPoint(LocalPoint base, int xOffset, int yOffset)
	{
		return base == null ? null : new LocalPoint(base.getX() + xOffset, base.getY() + yOffset);
	}
}
