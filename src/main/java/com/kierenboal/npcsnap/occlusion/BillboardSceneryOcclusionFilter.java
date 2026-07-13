package com.kierenboal.npcsnap.occlusion;

import com.kierenboal.npcsnap.BillboardConstants;

import java.awt.Rectangle;
import java.awt.Shape;
import net.runelite.api.GameObject;
import net.runelite.api.Renderable;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;

public final class BillboardSceneryOcclusionFilter
{
	public static final int MIN_SCENERY_MODEL_HEIGHT = BillboardConstants.LOCAL_TILE_SIZE;

	private BillboardSceneryOcclusionFilter()
	{
	}

	public static boolean isOccluder(TileObject tileObject)
	{
		if (tileObject instanceof WallObject)
		{
			return true;
		}

		if (!(tileObject instanceof GameObject))
		{
			return false;
		}

		Renderable renderable = ((GameObject) tileObject).getRenderable();
		return renderable != null && renderable.getModelHeight() >= MIN_SCENERY_MODEL_HEIGHT;
	}

	public static boolean intersectsInterest(TileObject tileObject, Rectangle interestBounds)
	{
		if (interestBounds == null)
		{
			return true;
		}

		if (tileObject instanceof WallObject)
		{
			WallObject wallObject = (WallObject) tileObject;
			return intersects(wallObject.getConvexHull(), interestBounds)
				|| intersects(wallObject.getConvexHull2(), interestBounds)
				|| intersects(tileObject.getClickbox(), interestBounds);
		}

		if (tileObject instanceof GameObject)
		{
			return intersects(((GameObject) tileObject).getConvexHull(), interestBounds)
				|| intersects(tileObject.getClickbox(), interestBounds);
		}

		return false;
	}

	private static boolean intersects(Shape shape, Rectangle interestBounds)
	{
		return shape != null && shape.getBounds().intersects(interestBounds);
	}
}
