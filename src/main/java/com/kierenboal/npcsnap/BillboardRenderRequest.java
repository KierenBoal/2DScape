package com.kierenboal.npcsnap;

import net.runelite.api.Model;
import net.runelite.api.Renderable;
import net.runelite.api.coords.LocalPoint;

final class BillboardRenderRequest
{
	final Renderable renderable;
	final Model model;
	final LocalPoint localPoint;
	final int plane;
	final int verticalOffset;
	final int relativeYaw;
	final int relativePitch;
	final int animationId;
	final int animationFrame;
	final int poseAnimationId;
	final int poseAnimationFrame;
	final int animatedTextureId;
	final boolean shouldHoverOutline;
	final boolean shouldInteractOutline;
	final VerticalAnchor verticalAnchor;
	final NpcSnapDebug.FrameDebugInfo frameDebugInfo;

	BillboardRenderRequest(
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
		NpcSnapDebug.FrameDebugInfo frameDebugInfo
	)
	{
		this.renderable = renderable;
		this.model = model;
		this.localPoint = localPoint;
		this.plane = plane;
		this.verticalOffset = verticalOffset;
		this.relativeYaw = relativeYaw;
		this.relativePitch = relativePitch;
		this.animationId = animationId;
		this.animationFrame = animationFrame;
		this.poseAnimationId = poseAnimationId;
		this.poseAnimationFrame = poseAnimationFrame;
		this.animatedTextureId = animatedTextureId;
		this.shouldHoverOutline = shouldHoverOutline;
		this.shouldInteractOutline = shouldInteractOutline;
		this.verticalAnchor = verticalAnchor;
		this.frameDebugInfo = frameDebugInfo;
	}
}

