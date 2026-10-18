package com.kierenboal.npcsnap.targeting;

import com.kierenboal.npcsnap.BillboardConstants;
import com.kierenboal.npcsnap.features.GroundItemBillboard;
import com.kierenboal.npcsnap.NpcSnapConfig;
import com.kierenboal.npcsnap.occlusion.BillboardPlaneUtils;
import com.kierenboal.npcsnap.rendering.BillboardProjectileGeometry;
import com.kierenboal.npcsnap.rendering.BillboardEffectGeometry;
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
		WorldViewLocationResolver.ResolvedLocation location = projectedNested ? WorldViewLocationResolver.resolve(client, actor) : null;
		if (projectedNested && location == null) return false;
		actorLocation = projectedNested ? location.mainPoint : actorLocation;
		int actorPlane = projectedNested ? client.getTopLevelWorldView().getPlane() : actorWorldView.getPlane();
		if (!hasEligibleLocation(localPlayerLocation, actorLocation, actorPlane))
		{
			return false;
		}

		Polygon tilePolygon = actor.getCanvasTilePoly();
		if (tilePolygon != null && tilePolygon.getBounds().intersects(viewport))
		{
			return true;
		}

		int verticalOffset = actor.getAnimationHeightOffset();
		Point canvasPoint = projectedNested
			? Perspective.localToCanvas(client, actorLocation.getX(), actorLocation.getY(),
				(int) Math.round(location.height) - verticalOffset - actor.getModelHeight() / 2)
			: Perspective.localToCanvas(client, actorLocation, actorPlane, verticalOffset + actor.getModelHeight() / 2);
		if (isInside(viewport, canvasPoint))
		{
			return true;
		}

		BillboardRenderRequest request = requestFactory.buildActor(actor);
		return projectedOrInFront(actor, request, viewport);
	}

	public boolean projectile(LocalPoint localPlayerLocation, Projectile projectile, Rectangle viewport)
	{
		if (!projectileInWorld(localPlayerLocation, projectile))
		{
			return false;
		}

		Point canvasPoint = Perspective.localToCanvas(
			client, (int) projectile.getX(), (int) projectile.getY(), (int) Math.round(projectile.getZ()));
		if (isInside(viewport, canvasPoint))
		{
			return true;
		}

		BillboardRenderRequest request = requestFactory.buildProjectile(projectile);
		return projectedOrInFront(projectile, request, viewport);
	}

	public boolean projectileInWorld(LocalPoint localPlayerLocation, Projectile projectile)
	{
		return projectile != null
			&& projectile.getModel() != null
			&& hasEligibleLocation(localPlayerLocation, BillboardProjectileGeometry.localPoint(projectile), projectile.getFloor());
	}

	public boolean graphicsObject(LocalPoint localPlayerLocation, GraphicsObject object, Rectangle viewport)
	{
		LocalPoint location = object.getLocation();
		if (!hasEligibleLocation(localPlayerLocation, location, object.getLevel()) || object.getModel() == null)
		{
			return false;
		}

		Point canvasPoint = Perspective.localToCanvas(client, location, object.getLevel(),
			BillboardEffectGeometry.graphicsVerticalOffset(client, object));
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

		LocalPoint localPlayerLocation = WorldViewLocationResolver.toMainWorld(client, client.getLocalPlayer());
		boolean nested = !actor.getWorldView().isTopLevel();
		WorldViewLocationResolver.ResolvedLocation location = nested ? WorldViewLocationResolver.resolve(client, actor) : null;
		if (nested && location == null) return false;
		LocalPoint actorLocation = nested ? location.mainPoint : actor.getLocalLocation();
		int plane = nested ? client.getTopLevelWorldView().getPlane() : actor.getWorldView().getPlane();
		if (!hasEligibleLocation(localPlayerLocation, actorLocation, plane)
			|| spotAnimation.getModel() == null)
		{
			return false;
		}

		int verticalOffset = BillboardEffectGeometry.actorSpotVerticalOffset(spotAnimation);
		Point canvasPoint = nested
			? Perspective.localToCanvas(client, actorLocation.getX(), actorLocation.getY(),
				(int) Math.round(location.height) - verticalOffset - spotAnimation.getModelHeight() / 2)
			: Perspective.localToCanvas(client, actorLocation, plane, verticalOffset + spotAnimation.getModelHeight() / 2);
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
			if (part.localPoint == null || part.renderable == null) continue;
			LocalPoint mainPoint = part.localPoint;
			int plane = part.plane;
			WorldViewLocationResolver.ResolvedLocation location = null;
			if (part.localPoint.getWorldView() != WorldView.TOPLEVEL)
			{
				WorldView view = client.getWorldView(part.localPoint.getWorldView());
				if (view == null) continue;
				double height = part.worldHeight != Integer.MIN_VALUE ? part.worldHeight
					: view.getTileHeight(part.localPoint.getX(), part.localPoint.getY(), part.plane);
				location = WorldViewLocationResolver.resolve(client, view, part.localPoint, part.plane, height);
				if (location == null) continue;
				mainPoint = location.mainPoint;
				plane = client.getTopLevelWorldView().getPlane();
			}
			if (!hasEligibleLocation(localPlayerLocation, mainPoint, plane))
			{
				continue;
			}

			Point canvasPoint = location != null
				? Perspective.localToCanvas(client, mainPoint.getX(), mainPoint.getY(),
					(int) Math.round(location.height) - Math.max(0, part.renderable.getModelHeight() / 2))
				: Perspective.localToCanvas(client, mainPoint, plane, Math.max(0, part.renderable.getModelHeight() / 2));
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
