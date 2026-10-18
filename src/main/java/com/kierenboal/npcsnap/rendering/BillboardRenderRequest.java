package com.kierenboal.npcsnap.rendering;

import com.kierenboal.npcsnap.NpcSnapDebug;

import java.awt.Color;
import net.runelite.api.Model;
import net.runelite.api.Renderable;
import net.runelite.api.coords.LocalPoint;

public final class BillboardRenderRequest
{
	public final Renderable renderable;
	public final Model model;
	public final LocalPoint localPoint;
	public final int plane;
	public final int verticalOffset;
	public final int relativeYaw;
	public final int relativePitch;
	public final boolean lowProfile;
	public final int animationId;
	public final int animationFrame;
	public final int poseAnimationId;
	public final int poseAnimationFrame;
	public final int animatedTextureId;
	public final boolean shouldHoverOutline;
	public final boolean shouldInteractOutline;
	public final Color hoverOutlineColor;
	public final Color interactionOutlineColor;
	public final VerticalAnchor verticalAnchor;
	public final NpcSnapDebug.FrameDebugInfo frameDebugInfo;
	public final com.kierenboal.npcsnap.targeting.WorldViewLocationResolver.ResolvedLocation sceneLocation;
	public final double resolvedWorldHeight;

	private BillboardRenderRequest(BillboardRenderRequest original,
		com.kierenboal.npcsnap.targeting.WorldViewLocationResolver.ResolvedLocation location, int adjustedOffset, int mainPlane)
	{
		renderable = original.renderable;
		model = original.model;
		localPoint = location.mainPoint;
		plane = mainPlane;
		verticalOffset = adjustedOffset;
		relativeYaw = original.relativeYaw;
		relativePitch = original.relativePitch;
		lowProfile = original.lowProfile;
		animationId = original.animationId;
		animationFrame = original.animationFrame;
		poseAnimationId = original.poseAnimationId;
		poseAnimationFrame = original.poseAnimationFrame;
		animatedTextureId = original.animatedTextureId;
		shouldHoverOutline = original.shouldHoverOutline;
		shouldInteractOutline = original.shouldInteractOutline;
		hoverOutlineColor = original.hoverOutlineColor;
		interactionOutlineColor = original.interactionOutlineColor;
		verticalAnchor = original.verticalAnchor;
		frameDebugInfo = original.frameDebugInfo;
		sceneLocation = location;
		resolvedWorldHeight = location.height - original.verticalOffset;
	}

	public BillboardRenderRequest atSceneLocation(net.runelite.api.Client client,
		com.kierenboal.npcsnap.targeting.WorldViewLocationResolver.ResolvedLocation location)
	{
		if (location == null)
		{
			return null;
		}
		int mainPlane = client.getTopLevelWorldView().getPlane();
		double terrain = net.runelite.api.Perspective.getTileHeight(client, location.mainPoint, mainPlane);
		return new BillboardRenderRequest(this, location, verticalOffset + (int) Math.round(terrain - location.height), mainPlane);
	}

	public BillboardRenderRequest(
		Renderable renderable,
		Model model,
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
		boolean shouldHoverOutline,
		boolean shouldInteractOutline,
		VerticalAnchor verticalAnchor,
		NpcSnapDebug.FrameDebugInfo frameDebugInfo)
	{
		this(
			renderable, model, localPoint, plane, verticalOffset, relativeYaw, relativePitch,
			animationId, animationFrame, poseAnimationId, poseAnimationFrame, animatedTextureId,
			shouldHoverOutline, shouldInteractOutline, null, null, verticalAnchor, frameDebugInfo, false);
	}

	public BillboardRenderRequest(
		Renderable renderable,
		Model model,
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
		boolean shouldHoverOutline,
		boolean shouldInteractOutline,
		Color hoverOutlineColor,
		Color interactionOutlineColor,
		VerticalAnchor verticalAnchor,
		NpcSnapDebug.FrameDebugInfo frameDebugInfo)
	{
		this(
			renderable, model, localPoint, plane, verticalOffset, relativeYaw, relativePitch,
			animationId, animationFrame, poseAnimationId, poseAnimationFrame, animatedTextureId,
			shouldHoverOutline, shouldInteractOutline, hoverOutlineColor, interactionOutlineColor,
			verticalAnchor, frameDebugInfo, false);
	}

	public BillboardRenderRequest(
		Renderable renderable,
		Model model,
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
		boolean shouldHoverOutline,
		boolean shouldInteractOutline,
		Color hoverOutlineColor,
		Color interactionOutlineColor,
		VerticalAnchor verticalAnchor,
		NpcSnapDebug.FrameDebugInfo frameDebugInfo,
		boolean lowProfile
	)
	{
		this.renderable = renderable;
		this.model = model;
		this.localPoint = localPoint;
		this.plane = plane;
		this.verticalOffset = verticalOffset;
		this.relativeYaw = relativeYaw;
		this.relativePitch = relativePitch;
		this.lowProfile = lowProfile;
		this.animationId = animationId;
		this.animationFrame = animationFrame;
		this.poseAnimationId = poseAnimationId;
		this.poseAnimationFrame = poseAnimationFrame;
		this.animatedTextureId = animatedTextureId;
		this.shouldHoverOutline = shouldHoverOutline;
		this.shouldInteractOutline = shouldInteractOutline;
		this.hoverOutlineColor = hoverOutlineColor;
		this.interactionOutlineColor = interactionOutlineColor;
		this.verticalAnchor = verticalAnchor;
		this.frameDebugInfo = frameDebugInfo;
		this.sceneLocation = null;
		this.resolvedWorldHeight = Double.NaN;
	}
}

