package com.kierenboal.npcsnap.targeting;

import com.kierenboal.npcsnap.BillboardConstants;
import com.kierenboal.npcsnap.features.GroundItemBillboard;
import com.kierenboal.npcsnap.NpcSnapConfig;
import com.kierenboal.npcsnap.occlusion.BillboardPlaneUtils;
import com.kierenboal.npcsnap.rendering.BillboardProjectileGeometry;
import com.kierenboal.npcsnap.rendering.BillboardRenderRequest;
import com.kierenboal.npcsnap.rendering.BillboardRenderRequestFactory;

import java.awt.Polygon;
import java.awt.Rectangle;
import java.util.function.BiPredicate;
import java.util.function.Function;
import net.runelite.api.Actor;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.Client;
import net.runelite.api.GraphicsObject;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Projectile;
import net.runelite.api.Renderable;
import net.runelite.api.TileItem;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;

public final class BillboardTargetEligibility
{
	private final Client client;
	private final NpcSnapConfig config;
	private final BillboardRenderRequestFactory requestFactory;
	private final Function<BillboardRenderRequest, Rectangle> projectedBounds;
	private final BiPredicate<Renderable, BillboardRenderRequest> renderedInFront;

	public BillboardTargetEligibility(
		Client client,
		NpcSnapConfig config,
		BillboardRenderRequestFactory requestFactory,
		Function<BillboardRenderRequest, Rectangle> projectedBounds,
		BiPredicate<Renderable, BillboardRenderRequest> renderedInFront)
	{
		this.client = client;
		this.config = config;
		this.requestFactory = requestFactory;
		this.projectedBounds = projectedBounds;
		this.renderedInFront = renderedInFront;
	}

	public boolean actor(LocalPoint localPlayerLocation, LocalPoint actorLocation, Actor actor, Rectangle viewport)
	{
		WorldView actorWorldView = actor.getWorldView();
		boolean projectedNested = !actorWorldView.isTopLevel();
		actorLocation = WorldViewLocationResolver.toMainWorld(client.getTopLevelWorldView(), actorWorldView, actorLocation);
		int actorPlane = projectedNested ? 0 : actorWorldView.getPlane();
		if (!hasEligibleLocation(localPlayerLocation, actorLocation, actorPlane))
		{
			return false;
		}

		Polygon tilePolygon = actor.getCanvasTilePoly();
		if (tilePolygon != null && tilePolygon.getBounds().intersects(viewport))
		{
			return true;
		}

		int verticalOffset = Math.max(0, actor.getAnimationHeightOffset());
		Point canvasPoint = Perspective.localToCanvas(
			client, actorLocation, actorPlane, verticalOffset + (actor.getModelHeight() / 2));
		if (isInside(viewport, canvasPoint))
		{
			return true;
		}

		BillboardRenderRequest request = requestFactory.buildActor(actor);
		return projectedOrInFront(actor, request, viewport);
	}

	public boolean projectile(LocalPoint localPlayerLocation, Projectile projectile, Rectangle viewport)
	{
		LocalPoint location = BillboardProjectileGeometry.localPoint(projectile);
		if (!hasEligibleLocation(localPlayerLocation, location, projectile.getFloor()) || projectile.getModel() == null)
		{
			return false;
		}

		Point canvasPoint = Perspective.localToCanvas(
			client, location, projectile.getFloor(), BillboardProjectileGeometry.verticalOffset(client, projectile));
		if (isInside(viewport, canvasPoint))
		{
			return true;
		}

		BillboardRenderRequest request = requestFactory.buildProjectile(projectile);
		return projectedOrInFront(projectile, request, viewport);
	}

	public boolean graphicsObject(LocalPoint localPlayerLocation, GraphicsObject object, Rectangle viewport)
	{
		LocalPoint location = object.getLocation();
		if (!hasEligibleLocation(localPlayerLocation, location, object.getLevel()) || object.getModel() == null)
		{
			return false;
		}

		Point canvasPoint = Perspective.localToCanvas(client, location, object.getLevel(), Math.max(0, object.getZ()));
		if (isInside(viewport, canvasPoint))
		{
			return true;
		}

		BillboardRenderRequest request = requestFactory.buildGraphicsObject(object);
		return projectedOrInFront(object, request, viewport);
	}

	public boolean actorSpotAnimation(Actor actor, ActorSpotAnim spotAnimation, Rectangle viewport)
	{
		if (actor == null || spotAnimation == null || viewport == null)
		{
			return false;
		}

		LocalPoint localPlayerLocation = client.getLocalPlayer() != null ? client.getLocalPlayer().getLocalLocation() : null;
		LocalPoint actorLocation = actor.getLocalLocation();
		if (!hasEligibleLocation(localPlayerLocation, actorLocation, actor.getWorldView().getPlane())
			|| spotAnimation.getModel() == null)
		{
			return false;
		}

		int verticalOffset = Math.max(0, actor.getAnimationHeightOffset() + spotAnimation.getHeight());
		Point canvasPoint = Perspective.localToCanvas(
			client, actorLocation, actor.getWorldView().getPlane(), verticalOffset + (spotAnimation.getModelHeight() / 2));
		if (isInside(viewport, canvasPoint))
		{
			return true;
		}

		BillboardRenderRequest request = requestFactory.buildActorSpotAnimation(spotAnimation, actor);
		return projectedOrInFront(spotAnimation, request, viewport);
	}

	public boolean groundItem(LocalPoint localPlayerLocation, TileItem item, GroundItemBillboard groundItem, Rectangle viewport)
	{
		if (groundItem == null || !hasEligibleLocation(localPlayerLocation, groundItem.localPoint, groundItem.plane))
		{
			return false;
		}

		Point canvasPoint = Perspective.localToCanvas(
			client,
			groundItem.localPoint,
			groundItem.plane,
			groundItem.verticalOffset + Math.max(0, item.getModelHeight() / 2));
		if (isInside(viewport, canvasPoint))
		{
			return true;
		}

		BillboardRenderRequest request = requestFactory.buildGroundItem(item, groundItem);
		return projectedOrInFront(item, request, viewport);
	}

	public boolean tileObject(LocalPoint localPlayerLocation, ObservedTileObject observed, Rectangle viewport)
	{
		if (localPlayerLocation == null || observed == null)
		{
			return false;
		}

		for (ObjectRenderablePart part : observed.parts)
		{
			if (part.localPoint == null || part.renderable == null
				|| !hasEligibleLocation(localPlayerLocation, part.localPoint, part.plane))
			{
				continue;
			}

			Point canvasPoint = Perspective.localToCanvas(
				client, part.localPoint, part.plane, Math.max(0, part.renderable.getModelHeight() / 2));
			if (isInside(viewport, canvasPoint))
			{
				return true;
			}

			BillboardRenderRequest request = requestFactory.buildStaticObjectPart(part);
			if (projectedOrInFront(part.renderable, request, viewport))
			{
				return true;
			}
		}
		return false;
	}

	private boolean hasEligibleLocation(LocalPoint player, LocalPoint target, int targetPlane)
	{
		return player != null
			&& target != null
			&& isPlaneEligible(targetPlane)
			&& isWithinRadius(player, target, config.billboardRadiusTiles());
	}

	private boolean isPlaneEligible(int targetPlane)
	{
		return config.renderBillboardsOnAllPlanes()
			|| BillboardPlaneUtils.shouldRenderTargetPlane(client.getPlane(), targetPlane);
	}

	private static boolean isWithinRadius(LocalPoint source, LocalPoint target, int radiusTiles)
	{
		int dx = source.getX() - target.getX();
		int dy = source.getY() - target.getY();
		int radius = radiusTiles * BillboardConstants.LOCAL_TILE_SIZE;
		return (dx * dx) + (dy * dy) <= (radius * radius);
	}

	private boolean projectedOrInFront(Renderable renderable, BillboardRenderRequest request, Rectangle viewport)
	{
		Rectangle bounds = projectedBounds.apply(request);
		return (bounds != null && bounds.intersects(viewport)) || renderedInFront.test(renderable, request);
	}

	private static boolean isInside(Rectangle viewport, Point point)
	{
		return point != null && viewport.contains(point.getX(), point.getY());
	}
}
