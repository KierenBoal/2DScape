package com.kierenboal.npcsnap.rendering;

import com.kierenboal.npcsnap.features.GroundItemBillboard;
import com.kierenboal.npcsnap.targeting.ObjectRenderablePart;
import com.kierenboal.npcsnap.targeting.ObservedTileObject;

import net.runelite.api.Actor;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.Client;
import net.runelite.api.GraphicsObject;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.TileItem;
import net.runelite.api.coords.LocalPoint;

public final class BillboardDepthCalculator
{
	private static final double MIN_CANVAS_PROJECTION_DEPTH = 50.0d;
	private final Client client;

	public BillboardDepthCalculator(Client client)
	{
		this.client = client;
	}

	public double depth(NPC npc)
	{
		LocalPoint localPoint = npc.getLocalLocation();
		if (localPoint == null)
		{
			return Double.NEGATIVE_INFINITY;
		}

		double verticalOffset = Math.max(0, npc.getAnimationHeightOffset()) + (npc.getModelHeight() / 2.0);
		return cameraDistance(npc, localPoint, verticalOffset);
	}

	public double depth(Player player)
	{
		LocalPoint localPoint = player.getLocalLocation();
		if (localPoint == null)
		{
			return Double.NEGATIVE_INFINITY;
		}

		double verticalOffset = Math.max(0, player.getAnimationHeightOffset()) + (player.getModelHeight() / 2.0);
		return cameraDistance(player, localPoint, verticalOffset);
	}

	public double depth(Projectile projectile)
	{
		LocalPoint localPoint = BillboardProjectileGeometry.localPoint(projectile);
		if (localPoint == null)
		{
			return Double.NEGATIVE_INFINITY;
		}

		int heightOffset = BillboardProjectileGeometry.verticalOffset(client, projectile);
		return cameraDistance(
			localPoint,
			projectile.getFloor(),
			-heightOffset - (projectile.getModelHeight() / 2.0));
	}

	public double depth(GraphicsObject graphicsObject)
	{
		LocalPoint localPoint = graphicsObject.getLocation();
		if (localPoint == null)
		{
			return Double.NEGATIVE_INFINITY;
		}

		double verticalOffset = Math.max(0, graphicsObject.getZ()) + (graphicsObject.getModelHeight() / 2.0);
		return cameraDistance(localPoint, graphicsObject.getLevel(), verticalOffset);
	}

	public double depth(ActorSpotAnim actorSpotAnim, Actor actor)
	{
		LocalPoint localPoint = actor != null ? actor.getLocalLocation() : null;
		if (localPoint == null)
		{
			return Double.NEGATIVE_INFINITY;
		}

		int verticalOffset = Math.max(0, actor.getAnimationHeightOffset() + actorSpotAnim.getHeight());
		return cameraDistance(localPoint, actor.getWorldView().getPlane(), verticalOffset + (actorSpotAnim.getModelHeight() / 2.0));
	}

	public double depth(TileItem item, GroundItemBillboard groundItem)
	{
		if (groundItem == null)
		{
			return Double.NEGATIVE_INFINITY;
		}

		return cameraDistance(
			groundItem.localPoint,
			groundItem.plane,
			groundItem.verticalOffset + (item.getModelHeight() / 2.0));
	}

	public double depth(ObservedTileObject observed)
	{
		double nearest = Double.POSITIVE_INFINITY;
		for (ObjectRenderablePart part : observed.parts)
		{
			if (part.localPoint == null || part.renderable == null)
			{
				continue;
			}

			double distance = cameraDistance(part.localPoint, part.plane, part.renderable.getModelHeight() / 2.0);
			if (distance < nearest)
			{
				nearest = distance;
			}
		}

		return Double.isFinite(nearest) ? nearest : Double.NEGATIVE_INFINITY;
	}

	public double cameraDistance(LocalPoint localPoint, int plane, double verticalOffset)
	{
		double dx = localPoint.getX() - client.getCameraFpX();
		double dy = localPoint.getY() - client.getCameraFpY();
		double groundHeight = Perspective.getTileHeight(client, localPoint, plane);
		double dz = (groundHeight + verticalOffset) - client.getCameraFpZ();
		return Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
	}

	public double cameraForwardDepth(LocalPoint localPoint, int plane, double verticalOffset)
	{
		if (localPoint == null)
		{
			return Double.NaN;
		}

		double worldHeight = Perspective.getTileHeight(client, localPoint, plane) + verticalOffset;
		return cameraForwardDepth(localPoint.getX(), localPoint.getY(), worldHeight);
	}

	public double cameraForwardDepth(int localX, int localY, double worldHeight)
	{
		double x = localX - client.getCameraFpX();
		double y = localY - client.getCameraFpY();
		double z = worldHeight - client.getCameraFpZ();
		int yaw = cameraYawIndex();
		int pitch = cameraPitchIndex();
		double yawSin = Perspective.SINE[yaw] / 65536.0d;
		double yawCos = Perspective.COSINE[yaw] / 65536.0d;
		double pitchSin = Perspective.SINE[pitch] / 65536.0d;
		double pitchCos = Perspective.COSINE[pitch] / 65536.0d;
		double cameraY = (y * yawCos) - (x * yawSin);
		return (z * pitchSin) + (cameraY * pitchCos);
	}

	public double cameraForwardDepthOnVerticalPlane(int localX, int localY, int canvasY)
	{
		double x = localX - client.getCameraFpX();
		double y = localY - client.getCameraFpY();
		int yaw = cameraYawIndex();
		int pitch = cameraPitchIndex();
		double yawSin = Perspective.SINE[yaw] / 65536.0d;
		double yawCos = Perspective.COSINE[yaw] / 65536.0d;
		double pitchSin = Perspective.SINE[pitch] / 65536.0d;
		double pitchCos = Perspective.COSINE[pitch] / 65536.0d;
		double cameraY = (y * yawCos) - (x * yawSin);
		double zoom = client.get3dZoom();
		if (!Double.isFinite(cameraY) || cameraY <= 0.0d || !Double.isFinite(zoom) || zoom <= 0.0d)
		{
			return Double.NaN;
		}

		double viewportCenterY = client.getViewportYOffset() + (client.getViewportHeight() / 2.0d);
		double screenSlope = (canvasY - viewportCenterY) / zoom;
		double denominator = pitchCos - (screenSlope * pitchSin);
		if (!Double.isFinite(denominator) || denominator <= 0.0d)
		{
			return Double.NaN;
		}

		// Intersect the pixel's camera ray with the yaw-facing world-vertical
		// plane through the target. In camera coordinates:
		// cameraY = depth * (cos(pitch) - screenSlope * sin(pitch)).
		return cameraY / denominator;
	}

	public double cameraRightX()
	{
		int yaw = cameraYawIndex();
		return Perspective.COSINE[yaw] / 65536.0d;
	}

	public double cameraRightY()
	{
		int yaw = cameraYawIndex();
		return Perspective.SINE[yaw] / 65536.0d;
	}

	public double canvasYAtWorldHeight(int localX, int localY, double worldHeight)
	{
		Point point = Perspective.localToCanvas(client, localX, localY, (int) Math.round(worldHeight));
		return point != null ? point.getY() : Double.NaN;
	}

	public BillboardCanvasPoint projectCanvasPoint(LocalPoint localPoint, int plane, double verticalOffset)
	{
		if (localPoint == null || !Double.isFinite(verticalOffset))
		{
			return null;
		}

		double worldZ = Perspective.getTileHeight(client, localPoint, plane) - verticalOffset;
		return projectCanvasPoint(localPoint.getX(), localPoint.getY(), worldZ);
	}

	public BillboardCanvasPoint projectCanvasPoint(double localX, double localY, double worldZ)
	{
		// Match Perspective.localToCanvas's GPU/CPU camera conventions, retaining
		// subpixel precision until rasterization. RuneLite's vertical axis points
		// down, so world-up is decreasing worldZ.
		double cameraX;
		double cameraY;
		double cameraZ;
		double yawSin;
		double yawCos;
		double pitchSin;
		double pitchCos;
		if (client.isGpu())
		{
			cameraX = client.getCameraFpX();
			cameraY = client.getCameraFpY();
			cameraZ = client.getCameraFpZ();
			double yaw = client.getCameraFpYaw();
			double pitch = client.getCameraFpPitch();
			yawSin = Math.sin(yaw);
			yawCos = Math.cos(yaw);
			pitchSin = Math.sin(pitch);
			pitchCos = Math.cos(pitch);
		}
		else
		{
			cameraX = client.getCameraX();
			cameraY = client.getCameraY();
			cameraZ = client.getCameraZ();
			int yaw = Math.floorMod(client.getCameraYaw(), Perspective.SINE14.length);
			int pitch = Math.floorMod(client.getCameraPitch(), Perspective.SINE14.length);
			yawSin = Perspective.SINE14[yaw] / 65536.0d;
			yawCos = Perspective.COSINE14[yaw] / 65536.0d;
			pitchSin = Perspective.SINE14[pitch] / 65536.0d;
			pitchCos = Perspective.COSINE14[pitch] / 65536.0d;
		}

		double translatedX = localX - cameraX;
		double translatedY = localY - cameraY;
		double translatedZ = worldZ - cameraZ;
		double cameraRight = (translatedX * yawCos) + (translatedY * yawSin);
		double cameraForward = (translatedY * yawCos) - (translatedX * yawSin);
		double cameraVertical = (translatedZ * pitchCos) - (cameraForward * pitchSin);
		double depth = (cameraForward * pitchCos) + (translatedZ * pitchSin);
		double scale = client.getScale();
		if (!Double.isFinite(depth) || depth < MIN_CANVAS_PROJECTION_DEPTH
			|| !Double.isFinite(scale) || scale <= 0.0d)
		{
			return null;
		}

		double canvasX = client.getViewportXOffset() + (client.getViewportWidth() / 2.0d)
			+ ((cameraRight * scale) / depth);
		double canvasY = client.getViewportYOffset() + (client.getViewportHeight() / 2.0d)
			+ ((cameraVertical * scale) / depth);
		// Differentiate the perspective divide with respect to world-up. Removing
		// the common scale/depth factor gives (right*sin(pitch)/depth, forward/depth)
		// for unit-length trig values. Retain the CPU table's rounding below.
		// Unlike a base-to-model-top delta, this never depends on animated height.
		return Double.isFinite(canvasX) && Double.isFinite(canvasY)
			? new BillboardCanvasPoint(canvasX, canvasY, depth,
				cameraRight * pitchSin / depth,
				cameraForward * (pitchCos * pitchCos + pitchSin * pitchSin) / depth)
			: null;
	}

	public int cameraYawIndex()
	{
		return cameraAngleIndex(client.getCameraYaw());
	}

	public int cameraPitchIndex()
	{
		return cameraAngleIndex(client.getCameraPitch());
	}

	private int cameraAngleIndex(int cameraAngle)
	{
		return Math.floorMod(
			(int) Math.round((Math.floorMod(cameraAngle, BillboardAngleUtils.CAMERA_FULL_CIRCLE) * (double) Perspective.SINE.length) / BillboardAngleUtils.CAMERA_FULL_CIRCLE),
			Perspective.SINE.length
		);
	}

	private double cameraDistance(Actor actor, LocalPoint localPoint, double verticalOffset)
	{
		return cameraDistance(localPoint, actor.getWorldView().getPlane(), verticalOffset);
	}
}
