package com.kierenboal.npcsnap.rendering;

import net.runelite.api.Actor;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.Client;
import net.runelite.api.GraphicsObject;
import net.runelite.api.Perspective;
import net.runelite.api.gameval.SpotanimID;

public final class BillboardEffectGeometry
{
	private BillboardEffectGeometry()
	{
	}

	public static int actorSpotVerticalOffset(Actor actor, ActorSpotAnim spotAnimation)
	{
		if (spotAnimation == null)
		{
			return 0;
		}

		// Binding impacts are drawn around the actor's feet in the client. Their
		// model origin is not a reliable ground point, so the caller aligns the
		// lowest visible sprite pixel to the actor's base instead.
		if (isGroundContactEffect(spotAnimation))
		{
			return 0;
		}

		// The spot animation is anchored to the actor's world location. Its own
		// height is the displacement; adding actor animation height counts it twice.
		return Math.max(0, spotAnimation.getHeight());
	}

	public static VerticalAnchor actorSpotVerticalAnchor(ActorSpotAnim spotAnimation)
	{
		return isGroundContactEffect(spotAnimation) ? VerticalAnchor.GROUND_CONTACT : VerticalAnchor.BOTTOM;
	}

	public static boolean isGroundContactEffect(ActorSpotAnim spotAnimation)
	{
		if (spotAnimation == null)
		{
			return false;
		}

		int id = spotAnimation.getId();
		return id == SpotanimID.BIND_IMPACT
			|| id == SpotanimID.SNARE_IMPACT
			|| id == SpotanimID.ENTANGLE_IMPACT;
	}

	public static int graphicsVerticalOffset(Client client, GraphicsObject object)
	{
		return (int) Math.round(BillboardDepthCalculator.worldHeight(
			Perspective.getTileHeight(client, object.getLocation(), object.getLevel()), object.getZ()));
	}
}
