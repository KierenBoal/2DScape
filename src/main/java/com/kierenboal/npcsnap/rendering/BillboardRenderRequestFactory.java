package com.kierenboal.npcsnap.rendering;

import com.kierenboal.npcsnap.features.GroundItemBillboard;
import com.kierenboal.npcsnap.NpcSnapConfig;
import com.kierenboal.npcsnap.NpcSnapDebug;
import com.kierenboal.npcsnap.targeting.BillboardInteractionState;
import com.kierenboal.npcsnap.targeting.BillboardTarget;
import com.kierenboal.npcsnap.targeting.BillboardTargetType;
import com.kierenboal.npcsnap.targeting.ObjectRenderablePart;
import com.kierenboal.npcsnap.targeting.WorldViewLocationResolver;

import java.awt.Color;
import net.runelite.api.Actor;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.Client;
import net.runelite.api.DynamicObject;
import net.runelite.api.GraphicsObject;
import net.runelite.api.Model;
import net.runelite.api.Projectile;
import net.runelite.api.Renderable;
import net.runelite.api.TileItem;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;

public final class BillboardRenderRequestFactory
{
	private final Client client;
	private final NpcSnapConfig config;
	private final NpcSnapDebug debug;
	private final BillboardOrientationCalculator orientationCalculator;
	private final BillboardInteractionState interactionState;

	public BillboardRenderRequestFactory(
		Client client,
		NpcSnapConfig config,
		NpcSnapDebug debug,
		BillboardOrientationCalculator orientationCalculator,
		BillboardInteractionState interactionState)
	{
		this.client = client;
		this.config = config;
		this.debug = debug;
		this.orientationCalculator = orientationCalculator;
		this.interactionState = interactionState;
	}

	public BillboardRenderRequest build(BillboardTarget target)
	{
		switch (target.type)
		{
			case NPC:
			case PLAYER:
				return buildActor((Actor) target.renderable);
			case ACTOR_SPOT_ANIM:
				return buildActorSpotAnimation((ActorSpotAnim) target.renderable, target.parentActor);
			case PROJECTILE:
				return buildProjectile((Projectile) target.renderable);
			case GRAPHICS_OBJECT:
				return buildGraphicsObject((GraphicsObject) target.renderable);
			case GROUND_ITEM:
				return buildGroundItem((TileItem) target.renderable, target.groundItem);
			default:
				return null;
		}
	}

	public BillboardRenderRequest build(BillboardTarget target, ObjectRenderablePart part)
	{
		if (target.type != BillboardTargetType.TILE_OBJECT || part == null)
		{
			return null;
		}

		Model model = safeModel(part.renderable);
		if (model == null)
		{
			return null;
		}
		DynamicObject dynamicObject = part.renderable instanceof DynamicObject ? (DynamicObject) part.renderable : null;
		int originalFrame = dynamicObject != null ? dynamicObject.getAnimFrame() : -1;
		// DynamicObject has no public frame setter. Its model is the live frame;
		// the sprite update scheduler holds that capture for the configured interval.
		int animationId = dynamicObject != null && dynamicObject.getAnimation() != null
			? dynamicObject.getAnimation().getId()
			: -1;
		NpcSnapDebug.FrameDebugInfo frameDebugInfo = originalFrame >= 0
			? NpcSnapDebug.FrameDebugInfo.of(animationId, originalFrame, originalFrame)
			: null;
		boolean lowProfile = isLowProfile(part.renderable, model);
		return objectRequest(request(
			part.renderable, model, part.localPoint, part.plane, 0,
			// World objects follow the same camera pitch as actors. Low-profile is
			// still carried on the request for depth/occlusion decisions, but it must
			// not activate the ground-item minimum pitch here.
			orientationCalculator.relativeYaw(), orientationCalculator.relativePitch(),
			animationId, originalFrame, -1, -1, -1, false, false, lowProfile,
			VerticalAnchor.BOTTOM, frameDebugInfo), part);
	}

	public BillboardRenderRequest buildActor(Actor actor)
	{
		LocalPoint mainWorldLocation = WorldViewLocationResolver.toMainWorld(client, actor);
		if (mainWorldLocation == null && !actor.getWorldView().isTopLevel()) return null;
		boolean player = actor instanceof Player;
		boolean enabled = player ? config.enablePlayerInteractionOutline() : config.enableNpcInteractionOutline();
		boolean interactionOutline = enabled && actor == interactionState.frameInteractionTarget();
		boolean hoverOutline = !interactionOutline
			&& enabled
			&& actor == interactionState.frameHoveredTarget();
		Color hoverColor = player ? config.playerHoverOutlineColor() : config.npcHoverOutlineColor();
		Color interactionColor = player ? config.playerInteractionOutlineColor() : config.npcInteractionOutlineColor();
		Model model = actor.getModel();
		return actorRequest(request(
			actor, model, mainWorldLocation,
			actor.getWorldView().isTopLevel() ? actor.getWorldView().getPlane() : 0,
			actor.getAnimationHeightOffset(),
			orientationCalculator.relativeActorBodyYaw(actor,
				WorldViewLocationResolver.toMainWorldOrientation(client.getTopLevelWorldView(), actor), mainWorldLocation),
			config.enableActorWorldPlaneProjection() ? 0 : orientationCalculator.relativePitch(actor),
			actor.getAnimation(), actor.getAnimationFrame(), actor.getPoseAnimation(), actor.getPoseAnimationFrame(),
			BillboardAnimatedTextures.findAnimatedTextureId(actor), hoverOutline, interactionOutline,
			hoverColor, interactionColor,
			false, VerticalAnchor.BOTTOM, debug.actorFrameDebugInfo(actor)), actor);
	}

	public BillboardRenderRequest buildStaticObjectPart(ObjectRenderablePart part)
	{
		if (part == null || part.renderable == null)
		{
			return null;
		}

		Model model = safeModel(part.renderable);
		if (model == null)
		{
			return null;
		}
		boolean lowProfile = isLowProfile(part.renderable, model);
		return objectRequest(request(
			part.renderable, model, part.localPoint, part.plane, 0,
			orientationCalculator.relativeYaw(), orientationCalculator.relativePitch(),
			-1, -1, -1, -1, -1, false, false, lowProfile, VerticalAnchor.BOTTOM, null), part);
	}

	public BillboardRenderRequest buildActorSpotAnimation(ActorSpotAnim spotAnimation, Actor actor)
	{
		if (spotAnimation == null || actor == null || actor.getLocalLocation() == null)
		{
			return null;
		}
		LocalPoint localPoint = WorldViewLocationResolver.toMainWorld(client, actor);
		if (localPoint == null)
		{
			return null;
		}
		Model model = spotAnimation.getModel();
		boolean lowProfile = isLowProfile(spotAnimation, model);
		int plane = actor.getWorldView() != null && actor.getWorldView().isTopLevel()
			? actor.getWorldView().getPlane()
			: 0;
		return actorRequest(request(
			spotAnimation, model, localPoint,
			plane,
			BillboardEffectGeometry.actorSpotVerticalOffset(spotAnimation),
			orientationCalculator.relativeYaw(actor), orientationCalculator.relativePitch(lowProfile),
			spotAnimation.getId(), spotAnimation.getFrame(), -1, -1, -1, false, false,
			lowProfile, BillboardEffectGeometry.actorSpotVerticalAnchor(spotAnimation),
			NpcSnapDebug.FrameDebugInfo.of(spotAnimation.getId(), spotAnimation.getFrame(), spotAnimation.getFrame())), actor);
	}

	public BillboardRenderRequest buildProjectile(Projectile projectile)
	{
		int capturedFrame = projectile.getAnimationFrame();
		Model model = projectile.getModel();
		boolean lowProfile = isLowProfile(projectile, model);
		return request(
			projectile, model, BillboardProjectileGeometry.localPoint(projectile), projectile.getFloor(),
			BillboardProjectileGeometry.verticalOffset(client, projectile),
			orientationCalculator.relativeYaw(projectile), orientationCalculator.relativePitch(lowProfile),
			projectile.getId(), capturedFrame, -1, -1, -1, false, false, lowProfile, VerticalAnchor.BOTTOM,
			NpcSnapDebug.FrameDebugInfo.of(projectile.getId(), projectile.getAnimationFrame(), capturedFrame));
	}

	public BillboardRenderRequest buildGraphicsObject(GraphicsObject graphicsObject)
	{
		int capturedFrame = graphicsObject.getAnimationFrame();
		Model model = graphicsObject.getModel();
		boolean lowProfile = isLowProfile(graphicsObject, model);
		return request(
			graphicsObject, model, graphicsObject.getLocation(), graphicsObject.getLevel(),
			BillboardEffectGeometry.graphicsVerticalOffset(client, graphicsObject),
			orientationCalculator.relativeYaw(), orientationCalculator.relativePitch(lowProfile),
			graphicsObject.getId(), capturedFrame, -1, -1, -1, false, false, lowProfile, VerticalAnchor.BOTTOM,
			NpcSnapDebug.FrameDebugInfo.of(graphicsObject.getId(), graphicsObject.getAnimationFrame(), capturedFrame));
	}

	public BillboardRenderRequest buildGroundItem(TileItem item, GroundItemBillboard groundItem)
	{
		if (groundItem == null)
		{
			return null;
		}
		Model model = item.getModel();
		boolean lowProfile = isLowProfile(item, model);
		return request(
			item, model, groundItem.localPoint, groundItem.plane, groundItem.verticalOffset,
			orientationCalculator.relativeGroundItemYaw(), orientationCalculator.relativeGroundItemPitch(),
			item.getId(), item.getQuantity(), -1, -1, item.getId(),
			config.enableGroundItemInteractionOutline() && item == interactionState.frameHoveredTarget()
				&& item != interactionState.frameInteractionTarget(),
			config.enableGroundItemInteractionOutline() && item == interactionState.frameInteractionTarget(),
			config.groundItemHoverOutlineColor(), config.groundItemInteractionOutlineColor(),
			lowProfile, VerticalAnchor.BOTTOM, null);
	}

	private BillboardRenderRequest request(
		net.runelite.api.Renderable renderable,
		net.runelite.api.Model model,
		LocalPoint localPoint,
		int plane,
		int verticalOffset,
		int relativeYaw,
		int relativePitch,
		int animationId,
		int animationFrame,
		int poseAnimationId,
		int poseAnimationFrame,
		int animatedTextureId,
		boolean hoverOutline,
		boolean interactionOutline,
		boolean lowProfile,
		VerticalAnchor verticalAnchor,
		NpcSnapDebug.FrameDebugInfo frameDebugInfo)
	{
		return request(
			renderable, model, localPoint, plane, verticalOffset, relativeYaw, relativePitch,
			animationId, animationFrame, poseAnimationId, poseAnimationFrame, animatedTextureId,
			hoverOutline, interactionOutline, null, null, lowProfile, verticalAnchor, frameDebugInfo);
	}

	private BillboardRenderRequest request(
		net.runelite.api.Renderable renderable,
		net.runelite.api.Model model,
		LocalPoint localPoint,
		int plane,
		int verticalOffset,
		int relativeYaw,
		int relativePitch,
		int animationId,
		int animationFrame,
		int poseAnimationId,
		int poseAnimationFrame,
		int animatedTextureId,
		boolean hoverOutline,
		boolean interactionOutline,
		Color hoverOutlineColor,
		Color interactionOutlineColor,
		boolean lowProfile,
		VerticalAnchor verticalAnchor,
		NpcSnapDebug.FrameDebugInfo frameDebugInfo)
	{
		return new BillboardRenderRequest(
			renderable, model, localPoint, plane, verticalOffset, relativeYaw, relativePitch,
			animationId, animationFrame, poseAnimationId, poseAnimationFrame, animatedTextureId,
			hoverOutline, interactionOutline, hoverOutlineColor, interactionOutlineColor, verticalAnchor, frameDebugInfo,
			lowProfile);
	}

	private boolean isLowProfile(Renderable renderable, Model model)
	{
		return model != null && renderable != null
			&& BillboardModelProfile.isLowProfile(model, renderable.getModelHeight());
	}

	private BillboardRenderRequest actorRequest(BillboardRenderRequest request, Actor actor)
	{
		return actor.getWorldView().isTopLevel() ? request
			: request.atSceneLocation(client, WorldViewLocationResolver.resolve(client, actor));
	}

	private BillboardRenderRequest objectRequest(BillboardRenderRequest request, ObjectRenderablePart part)
	{
		if (part.localPoint.getWorldView() == net.runelite.api.WorldView.TOPLEVEL) return request;
		net.runelite.api.WorldView view = client.getWorldView(part.localPoint.getWorldView());
		if (view == null) return null;
		if (view.isTopLevel()) return request;
		double height = part.worldHeight != Integer.MIN_VALUE ? part.worldHeight
			: view.getTileHeight(part.localPoint.getX(), part.localPoint.getY(), part.plane);
		return request.atSceneLocation(client, WorldViewLocationResolver.resolve(client, view, part.localPoint, part.plane, height));
	}

	private static Model safeModel(Renderable renderable)
	{
		if (renderable == null)
		{
			return null;
		}

		try
		{
			return renderable.getModel();
		}
		catch (RuntimeException ex)
		{
			return null;
		}
	}

}
