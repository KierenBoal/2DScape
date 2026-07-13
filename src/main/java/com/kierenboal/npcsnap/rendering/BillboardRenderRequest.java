package com.kierenboal.npcsnap.rendering;

import com.kierenboal.npcsnap.NpcSnapDebug;

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
	public final int animationId;
	public final int animationFrame;
	public final int poseAnimationId;
	public final int poseAnimationFrame;
	public final int animatedTextureId;
	public final boolean shouldHoverOutline;
	public final boolean shouldInteractOutline;
	public final VerticalAnchor verticalAnchor;
	public final NpcSnapDebug.FrameDebugInfo frameDebugInfo;

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

