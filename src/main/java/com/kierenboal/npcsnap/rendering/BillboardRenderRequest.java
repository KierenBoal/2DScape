package com.kierenboal.npcsnap.rendering;

import com.kierenboal.npcsnap.NpcSnapDebug;

import com.kierenboal.npcsnap.targeting.WorldViewLocationResolver.ResolvedLocation;
import net.runelite.api.Model;
import net.runelite.api.Renderable;
import net.runelite.api.coords.LocalPoint;

@lombok.AllArgsConstructor(access = lombok.AccessLevel.PRIVATE)
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
	public final VerticalAnchor verticalAnchor;
	public final NpcSnapDebug.FrameDebugInfo frameDebugInfo;
	public final ResolvedLocation sceneLocation;
	public final double resolvedWorldHeight;

	public BillboardRenderRequest atSceneLocation(net.runelite.api.Client client,
		ResolvedLocation location)
	{
		if (location == null)
		{
			return null;
		}
		int mainPlane = client.getTopLevelWorldView().getPlane();
		double terrain = net.runelite.api.Perspective.getTileHeight(client, location.mainPoint, mainPlane);
		return new BillboardRenderRequest(renderable, model, location.mainPoint, mainPlane,
			verticalOffset + (int) Math.round(terrain - location.height), relativeYaw, relativePitch, lowProfile,
			animationId, animationFrame, poseAnimationId, poseAnimationFrame, animatedTextureId,
			verticalAnchor, frameDebugInfo, location, location.height - verticalOffset);
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
		VerticalAnchor verticalAnchor,
		NpcSnapDebug.FrameDebugInfo frameDebugInfo,
		boolean lowProfile
	)
	{
		this(renderable, model, localPoint, plane, verticalOffset, relativeYaw, relativePitch, lowProfile,
			animationId, animationFrame, poseAnimationId, poseAnimationFrame, animatedTextureId,
			verticalAnchor, frameDebugInfo, null, Double.NaN);
	}
}
