package com.kierenboal.npcsnap.state;

import com.kierenboal.npcsnap.rendering.BillboardModelStateHash;
import com.kierenboal.npcsnap.rendering.BillboardRenderRequest;

import net.runelite.api.coords.LocalPoint;

public final class UpdateHeuristicSnapshot
{
	public final double depth;
	public final long positionKey;
	public final int animationHash;
	public final int modelStateHash;
	public final int textureStateHash;
	public final int viewHash;
	public final boolean animated;

	public UpdateHeuristicSnapshot(
		double depth,
		long positionKey,
		int animationHash,
		int modelStateHash,
		int textureStateHash,
		int viewHash,
		boolean animated)
	{
		this.depth = depth;
		this.positionKey = positionKey;
		this.animationHash = animationHash;
		this.modelStateHash = modelStateHash;
		this.textureStateHash = textureStateHash;
		this.viewHash = viewHash;
		this.animated = animated;
	}

	public static UpdateHeuristicSnapshot empty()
	{
		return new UpdateHeuristicSnapshot(
			Double.POSITIVE_INFINITY,
			Long.MIN_VALUE,
			Integer.MIN_VALUE,
			Integer.MIN_VALUE,
			Integer.MIN_VALUE,
			Integer.MIN_VALUE,
			false);
	}

	public static UpdateHeuristicSnapshot forDepth(double depth)
	{
		return new UpdateHeuristicSnapshot(
			depth,
			Long.MIN_VALUE,
			Integer.MIN_VALUE,
			Integer.MIN_VALUE,
			Integer.MIN_VALUE,
			Integer.MIN_VALUE,
			false);
	}

	public static UpdateHeuristicSnapshot fromRequest(BillboardRenderRequest request, double depth, int animatedTextureOffsetStateHash)
	{
		long positionKey = positionKey(request.localPoint, request.plane);
		int animationHash = 1;
		int modelStateHash = BillboardModelStateHash.hash(request.model);
		int textureStateHash = 1;
		int viewHash = 1;
		viewHash = (31 * viewHash) + request.relativeYaw;
		viewHash = (31 * viewHash) + request.relativePitch;
		animationHash = (31 * animationHash) + request.animationId;
		animationHash = (31 * animationHash) + request.animationFrame;
		animationHash = (31 * animationHash) + request.poseAnimationId;
		animationHash = (31 * animationHash) + request.poseAnimationFrame;
		textureStateHash = (31 * textureStateHash) + request.animatedTextureId;
		textureStateHash = (31 * textureStateHash) + animatedTextureOffsetStateHash;
		boolean animated = request.animationId >= 0
			|| request.poseAnimationId >= 0
			|| request.animationFrame >= 0
			|| request.poseAnimationFrame >= 0
			|| request.animatedTextureId >= 0;
		return new UpdateHeuristicSnapshot(
			depth,
			positionKey,
			animationHash,
			modelStateHash,
			textureStateHash,
			viewHash,
			animated);
	}

	public boolean hasPosition()
	{
		return positionKey != Long.MIN_VALUE;
	}

	public static long positionKey(LocalPoint localPoint, int plane)
	{
		if (localPoint == null)
		{
			return Long.MIN_VALUE;
		}

		long x = localPoint.getX() & 0x1FFFFL;
		long y = localPoint.getY() & 0x1FFFFL;
		long z = plane & 0x3L;
		return (z << 34) | (x << 17) | y;
	}
}

