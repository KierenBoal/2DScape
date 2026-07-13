package com.kierenboal.npcsnap.targeting;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Actor;
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
			addObjectRenderablePart(parts, ((GameObject) tileObject).getRenderable(), tileObject.getLocalLocation(), tileObject.getPlane());
		}
		else if (tileObject instanceof GroundObject)
		{
			addObjectRenderablePart(parts, ((GroundObject) tileObject).getRenderable(), tileObject.getLocalLocation(), tileObject.getPlane());
		}
		else if (tileObject instanceof WallObject)
		{
			WallObject wallObject = (WallObject) tileObject;
			addObjectRenderablePart(parts, wallObject.getRenderable1(), tileObject.getLocalLocation(), tileObject.getPlane());
			addObjectRenderablePart(parts, wallObject.getRenderable2(), tileObject.getLocalLocation(), tileObject.getPlane());
		}
		else if (tileObject instanceof DecorativeObject)
		{
			DecorativeObject decorativeObject = (DecorativeObject) tileObject;
			addObjectRenderablePart(
				parts,
				decorativeObject.getRenderable(),
				offsetLocalPoint(tileObject.getLocalLocation(), decorativeObject.getXOffset(), decorativeObject.getYOffset()),
				tileObject.getPlane()
			);
			addObjectRenderablePart(
				parts,
				decorativeObject.getRenderable2(),
				offsetLocalPoint(tileObject.getLocalLocation(), decorativeObject.getXOffset2(), decorativeObject.getYOffset2()),
				tileObject.getPlane()
			);
		}

		return parts.isEmpty() ? null : new ObservedTileObject(tileObject, parts, ClassifiedObjectType.UNKNOWN);
	}

	private static void addObjectRenderablePart(List<ObjectRenderablePart> parts, Renderable renderable, LocalPoint localPoint, int plane)
	{
		if (renderable == null || localPoint == null
			|| renderable instanceof Actor
			|| renderable instanceof Projectile
			|| renderable instanceof GraphicsObject
			|| renderable instanceof TileItem)
		{
			return;
		}

		parts.add(new ObjectRenderablePart(renderable, localPoint, plane));
	}

	private static LocalPoint offsetLocalPoint(LocalPoint base, int xOffset, int yOffset)
	{
		return base == null ? null : new LocalPoint(base.getX() + xOffset, base.getY() + yOffset);
	}
}
