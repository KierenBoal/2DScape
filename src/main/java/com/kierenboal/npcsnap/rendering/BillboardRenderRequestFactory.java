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
import net.runelite.api.Projectile;
import net.runelite.api.TileItem;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;

public final class BillboardRenderRequestFactory
{
	private final Client client;
	private final NpcSnapConfig config;
	private final NpcSnapDebug debug;
	private final AnimationFrameSnapper animationFrameSnapper;
	private final BillboardOrientationCalculator orientationCalculator;
	private final BillboardInteractionState interactionState;

	public BillboardRenderRequestFactory(
		Client client,
		NpcSnapConfig config,
		NpcSnapDebug debug,
		AnimationFrameSnapper animationFrameSnapper,
		BillboardOrientationCalculator orientationCalculator,
		BillboardInteractionState interactionState)
	{
		this.client = client;
		this.config = config;
		this.debug = debug;
		this.animationFrameSnapper = animationFrameSnapper;
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
		if ((target.type != BillboardTargetType.TILE_OBJECT && target.type != BillboardTargetType.BOAT) || part == null)
		{
			return null;
		}

		DynamicObject dynamicObject = part.renderable instanceof DynamicObject ? (DynamicObject) part.renderable : null;
		int originalFrame = dynamicObject != null ? dynamicObject.getAnimFrame() : -1;
		int snappedFrame = dynamicObject != null
			? snapFrame(dynamicObject.getAnimation(), originalFrame, config.enableAnimationFrameSnapping())
			: -1;
		int animationId = dynamicObject != null && dynamicObject.getAnimation() != null
			? dynamicObject.getAnimation().getId()
			: target.tileObject.getId();
		NpcSnapDebug.FrameDebugInfo frameDebugInfo = snappedFrame >= 0
			? NpcSnapDebug.FrameDebugInfo.of(animationId, originalFrame, snappedFrame)
			: null;
		return request(
			part.renderable, part.renderable.getModel(), part.localPoint, part.plane, 0,
			orientationCalculator.relativeYaw(), orientationCalculator.relativePitch(),
			animationId, snappedFrame, -1, -1, -1, false, false, VerticalAnchor.BOTTOM, frameDebugInfo);
	}

	public BillboardRenderRequest buildActor(Actor actor)
	{
		LocalPoint mainWorldLocation = WorldViewLocationResolver.toMainWorld(client.getTopLevelWorldView(), actor);
		boolean player = actor instanceof Player;
		boolean enabled = player ? config.enablePlayerInteractionOutline() : config.enableNpcInteractionOutline();
		boolean interactionOutline = enabled && actor == interactionState.frameInteractionTarget();
		boolean hoverOutline = !interactionOutline
			&& enabled
			&& actor == interactionState.frameHoveredTarget();
		Color hoverColor = player ? config.playerHoverOutlineColor() : config.npcHoverOutlineColor();
		Color interactionColor = player ? config.playerInteractionOutlineColor() : config.npcInteractionOutlineColor();
		return request(
			actor, actor.getModel(), mainWorldLocation,
			actor.getWorldView().isTopLevel() ? actor.getWorldView().getPlane() : 0,
			Math.max(0, actor.getAnimationHeightOffset()),
			orientationCalculator.relativeYaw(actor,
				WorldViewLocationResolver.toMainWorldOrientation(client.getTopLevelWorldView(), actor)),
			orientationCalculator.relativePitch(actor),
			actor.getAnimation(), actor.getAnimationFrame(), actor.getPoseAnimation(), actor.getPoseAnimationFrame(),
			BillboardAnimatedTextures.findAnimatedTextureId(actor), hoverOutline, interactionOutline,
			hoverColor, interactionColor,
			VerticalAnchor.BOTTOM, debug.actorFrameDebugInfo(actor));
	}

	public BillboardRenderRequest buildStaticObjectPart(ObjectRenderablePart part)
	{
		return request(
			part.renderable, part.renderable.getModel(), part.localPoint, part.plane, 0,
			orientationCalculator.relativeYaw(), orientationCalculator.relativePitch(),
			-1, -1, -1, -1, -1, false, false, VerticalAnchor.BOTTOM, null);
	}

	public BillboardRenderRequest buildActorSpotAnimation(ActorSpotAnim spotAnimation, Actor actor)
	{
		if (actor == null || actor.getLocalLocation() == null)
		{
			return null;
		}
		return request(
			spotAnimation, spotAnimation.getModel(), actor.getLocalLocation(), actor.getWorldView().getPlane(),
			Math.max(0, spotAnimation.getHeight()),
			orientationCalculator.relativeYaw(actor), orientationCalculator.relativePitch(),
			spotAnimation.getId(), spotAnimation.getFrame(), -1, -1, -1, false, false,
			VerticalAnchor.BOTTOM,
			NpcSnapDebug.FrameDebugInfo.of(spotAnimation.getId(), spotAnimation.getFrame(), spotAnimation.getFrame()));
	}

	public BillboardRenderRequest buildProjectile(Projectile projectile)
	{
		int snappedFrame = snapFrame(projectile.getAnimation(), projectile.getAnimationFrame(), config.enableAnimationFrameSnapping());
		return request(
			projectile, projectile.getModel(), BillboardProjectileGeometry.localPoint(projectile), projectile.getFloor(),
			BillboardProjectileGeometry.verticalOffset(client, projectile),
			orientationCalculator.relativeYaw(projectile), orientationCalculator.relativePitch(),
			projectile.getId(), snappedFrame, -1, -1, -1, false, false, VerticalAnchor.CENTER,
			NpcSnapDebug.FrameDebugInfo.of(projectile.getId(), projectile.getAnimationFrame(), snappedFrame));
	}

	public BillboardRenderRequest buildGraphicsObject(GraphicsObject graphicsObject)
	{
		int snappedFrame = snapFrame(graphicsObject.getAnimation(), graphicsObject.getAnimationFrame(), config.enableAnimationFrameSnapping());
		return request(
			graphicsObject, graphicsObject.getModel(), graphicsObject.getLocation(), graphicsObject.getLevel(),
			Math.max(0, graphicsObject.getZ()), orientationCalculator.relativeYaw(), orientationCalculator.relativePitch(),
			graphicsObject.getId(), snappedFrame, -1, -1, -1, false, false, VerticalAnchor.BOTTOM,
			NpcSnapDebug.FrameDebugInfo.of(graphicsObject.getId(), graphicsObject.getAnimationFrame(), snappedFrame));
	}

	public BillboardRenderRequest buildGroundItem(TileItem item, GroundItemBillboard groundItem)
	{
		if (groundItem == null)
		{
			return null;
		}
		return request(
			item, item.getModel(), groundItem.localPoint, groundItem.plane, groundItem.verticalOffset,
			orientationCalculator.relativeGroundItemYaw(), orientationCalculator.relativeGroundItemPitch(),
			item.getId(), item.getQuantity(), -1, -1, item.getId(),
			config.enableGroundItemInteractionOutline() && item == interactionState.frameHoveredTarget()
				&& item != interactionState.frameInteractionTarget(),
			config.enableGroundItemInteractionOutline() && item == interactionState.frameInteractionTarget(),
			config.groundItemHoverOutlineColor(), config.groundItemInteractionOutlineColor(),
			VerticalAnchor.BOTTOM, null);
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
		VerticalAnchor verticalAnchor,
		NpcSnapDebug.FrameDebugInfo frameDebugInfo)
	{
		return request(
			renderable, model, localPoint, plane, verticalOffset, relativeYaw, relativePitch,
			animationId, animationFrame, poseAnimationId, poseAnimationFrame, animatedTextureId,
			hoverOutline, interactionOutline, null, null, verticalAnchor, frameDebugInfo);
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
		VerticalAnchor verticalAnchor,
		NpcSnapDebug.FrameDebugInfo frameDebugInfo)
	{
		return new BillboardRenderRequest(
			renderable, model, localPoint, plane, verticalOffset, relativeYaw, relativePitch,
			animationId, animationFrame, poseAnimationId, poseAnimationFrame, animatedTextureId,
			hoverOutline, interactionOutline, hoverOutlineColor, interactionOutlineColor, verticalAnchor, frameDebugInfo);
	}

	private int snapFrame(net.runelite.api.Animation animation, int frame, boolean enabled)
	{
		return animationFrameSnapper.snapFrame(
			animation,
			frame,
			enabled,
			Math.max(1, config.animationFrameCount()),
			config.deterministicAnimationLooping());
	}
}
