package com.kierenboal.npcsnap.rendering;

import java.awt.Rectangle;
import net.runelite.api.AABB;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.Perspective;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;

/** Geometry of the pose captured in a sprite, independent of its outline padding. */
public final class ActorBillboardBounds
{
	private final Rectangle modelBounds;
	private final Rectangle hullBounds;

	public ActorBillboardBounds(Rectangle modelBounds, Rectangle hullBounds)
	{
		this.modelBounds = usableCopy(modelBounds);
		this.hullBounds = usableCopy(hullBounds);
	}

	public boolean exceedsResizeThreshold(ActorBillboardBounds displayed, int percent)
	{
		if (displayed == null || percent <= 0)
		{
			return false;
		}
		double threshold = Math.min(100, percent) / 100.0d;
		return resized(modelBounds, displayed.modelBounds, threshold)
			|| resized(hullBounds, displayed.hullBounds, threshold);
	}

	private static boolean resized(Rectangle current, Rectangle displayed, double threshold)
	{
		return current != null && displayed != null
			&& (Math.abs((double) current.width - displayed.width) / displayed.width > threshold
				|| Math.abs((double) current.height - displayed.height) / displayed.height > threshold);
	}

	private static Rectangle usableCopy(Rectangle bounds)
	{
		return bounds == null || bounds.width <= 0 || bounds.height <= 0 ? null : new Rectangle(bounds);
	}

	/** Reuses projection buffers; only called on the client thread. */
	public static final class Sampler
	{
		private final Client client;
		private int[] canvasX = new int[0];
		private int[] canvasY = new int[0];
		private final float[] cornersX = new float[8];
		private final float[] cornersY = new float[8];
		private final float[] cornersZ = new float[8];

		public Sampler(Client client)
		{
			this.client = client;
		}

		public ActorBillboardBounds sample(Actor actor, Model model)
		{
			LocalPoint location = actor.getLocalLocation();
			WorldView view = actor.getWorldView();
			if (model == null || location == null || view == null || !view.contains(location))
			{
				return null;
			}
			int orientation = Math.floorMod(actor.getCurrentOrientation(), BillboardAngleUtils.BILLBOARD_FULL_CIRCLE);
			int height = Perspective.getFootprintTileHeight(client, location, view.getPlane(), actor.getFootprintSize())
				- Math.max(0, actor.getAnimationHeightOffset());
			Rectangle hull = null;
			float[] x = model.getVerticesX();
			float[] y = model.getVerticesY();
			float[] z = model.getVerticesZ();
			if (x != null && y != null && z != null)
			{
				int count = Math.min(model.getVerticesCount(), Math.min(x.length, Math.min(y.length, z.length)));
				if (count > 0)
				{
					ensureCapacity(count);
					Perspective.modelToCanvas(client, view, count, location.getX(), location.getY(), height,
						orientation, x, z, y, canvasX, canvasY);
					// The bounds of a convex hull equal the extrema of its projected vertices.
					hull = projectedBounds(count);
				}
			}

			Rectangle box = null;
			AABB aabb = model.getAABB(orientation);
			if (aabb != null)
			{
				for (int i = 0; i < 8; i++)
				{
					cornersX[i] = aabb.getCenterX() + ((i & 1) == 0 ? -aabb.getExtremeX() : aabb.getExtremeX());
					cornersY[i] = aabb.getCenterZ() + ((i & 2) == 0 ? -aabb.getExtremeZ() : aabb.getExtremeZ());
					cornersZ[i] = aabb.getCenterY() + ((i & 4) == 0 ? -aabb.getExtremeY() : aabb.getExtremeY());
				}
				ensureCapacity(8);
				// getAABB already applies orientation, so do not rotate its corners twice.
				Perspective.modelToCanvas(client, view, 8, location.getX(), location.getY(), height,
					0, cornersX, cornersY, cornersZ, canvasX, canvasY);
				box = projectedBounds(8);
			}
			return new ActorBillboardBounds(box, hull);
		}

		private void ensureCapacity(int count)
		{
			if (canvasX.length < count)
			{
				canvasX = new int[count];
				canvasY = new int[count];
			}
		}

		private Rectangle projectedBounds(int count)
		{
			int minX = Integer.MAX_VALUE;
			int minY = Integer.MAX_VALUE;
			int maxX = Integer.MIN_VALUE;
			int maxY = Integer.MIN_VALUE;
			for (int i = 0; i < count; i++)
			{
				if (canvasX[i] == Integer.MIN_VALUE || canvasY[i] == Integer.MIN_VALUE)
				{
					continue;
				}
				minX = Math.min(minX, canvasX[i]);
				minY = Math.min(minY, canvasY[i]);
				maxX = Math.max(maxX, canvasX[i]);
				maxY = Math.max(maxY, canvasY[i]);
			}
			return maxX > minX && maxY > minY ? new Rectangle(minX, minY, maxX - minX, maxY - minY) : null;
		}
	}
}
