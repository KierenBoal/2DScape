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
		double verticalOffset = BillboardProjectileGeometry.verticalOffset(client, projectile) + (projectile.getModelHeight() / 2.0);
		return cameraDistance(localPoint, projectile.getFloor(), verticalOffset);
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

		return cameraDistance(groundItem.localPoint, groundItem.plane, item.getModelHeight() / 2.0);
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
