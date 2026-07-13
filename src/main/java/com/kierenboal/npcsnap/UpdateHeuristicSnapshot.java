package com.kierenboal.npcsnap;

import net.runelite.api.coords.LocalPoint;

final class UpdateHeuristicSnapshot
{
	final double depth;
	final long positionKey;
	final int animationHash;
	final int modelStateHash;
	final int textureStateHash;
	final int viewHash;
	final boolean animated;

	UpdateHeuristicSnapshot(
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

	static UpdateHeuristicSnapshot empty()
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

	static UpdateHeuristicSnapshot forDepth(double depth)
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

	static UpdateHeuristicSnapshot fromRequest(BillboardRenderRequest request, double depth, int animatedTextureOffsetStateHash)
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

	boolean hasPosition()
	{
		return positionKey != Long.MIN_VALUE;
	}

	static long positionKey(LocalPoint localPoint, int plane)
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

