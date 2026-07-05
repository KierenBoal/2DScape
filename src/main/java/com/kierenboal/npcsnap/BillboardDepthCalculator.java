package com.kierenboal.npcsnap;

import net.runelite.api.Actor;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.Client;
import net.runelite.api.GraphicsObject;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.TileItem;
import net.runelite.api.coords.LocalPoint;

final class BillboardDepthCalculator
{
	private final Client client;

	BillboardDepthCalculator(Client client)
	{
		this.client = client;
	}

	double depth(NPC npc)
	{
		LocalPoint localPoint = npc.getLocalLocation();
		if (localPoint == null)
		{
			return Double.NEGATIVE_INFINITY;
		}

		double verticalOffset = Math.max(0, npc.getAnimationHeightOffset()) + (npc.getModelHeight() / 2.0);
		return cameraDistance(npc, localPoint, verticalOffset);
	}

	double depth(Player player)
	{
		LocalPoint localPoint = player.getLocalLocation();
		if (localPoint == null)
		{
			return Double.NEGATIVE_INFINITY;
		}

		double verticalOffset = Math.max(0, player.getAnimationHeightOffset()) + (player.getModelHeight() / 2.0);
		return cameraDistance(player, localPoint, verticalOffset);
	}

	double depth(Projectile projectile)
	{
		LocalPoint localPoint = BillboardProjectileGeometry.localPoint(projectile);
		double verticalOffset = BillboardProjectileGeometry.verticalOffset(client, projectile) + (projectile.getModelHeight() / 2.0);
		return cameraDistance(localPoint, projectile.getFloor(), verticalOffset);
	}

	double depth(GraphicsObject graphicsObject)
	{
		LocalPoint localPoint = graphicsObject.getLocation();
		if (localPoint == null)
		{
			return Double.NEGATIVE_INFINITY;
		}

		double verticalOffset = Math.max(0, graphicsObject.getZ()) + (graphicsObject.getModelHeight() / 2.0);
		return cameraDistance(localPoint, graphicsObject.getLevel(), verticalOffset);
	}

	double depth(ActorSpotAnim actorSpotAnim, Actor actor)
	{
		LocalPoint localPoint = actor != null ? actor.getLocalLocation() : null;
		if (localPoint == null)
		{
			return Double.NEGATIVE_INFINITY;
		}

		int verticalOffset = Math.max(0, actor.getAnimationHeightOffset() + actorSpotAnim.getHeight());
		return cameraDistance(localPoint, actor.getWorldView().getPlane(), verticalOffset + (actorSpotAnim.getModelHeight() / 2.0));
	}

	double depth(TileItem item, GroundItemBillboard groundItem)
	{
		if (groundItem == null)
		{
			return Double.NEGATIVE_INFINITY;
		}

		return cameraDistance(groundItem.localPoint, groundItem.plane, item.getModelHeight() / 2.0);
	}

	double depth(ObservedTileObject observed)
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

	double cameraDistance(LocalPoint localPoint, int plane, double verticalOffset)
	{
		double dx = localPoint.getX() - client.getCameraFpX();
		double dy = localPoint.getY() - client.getCameraFpY();
		double groundHeight = Perspective.getTileHeight(client, localPoint, plane);
		double dz = (groundHeight + verticalOffset) - client.getCameraFpZ();
		return Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
	}

	private double cameraDistance(Actor actor, LocalPoint localPoint, double verticalOffset)
	{
		return cameraDistance(localPoint, actor.getWorldView().getPlane(), verticalOffset);
	}
}
