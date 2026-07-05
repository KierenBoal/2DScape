package com.kierenboal.npcsnap;

import net.runelite.api.coords.LocalPoint;

final class BillboardUpdateState
{
	double qualityScale;
	private int nextEligibleGameCycle;
	private int lastRedrawGameCycle;
	private long lastRedrawPositionKey;
	private int lastRedrawAnimationHash;
	private int lastRedrawViewHash;
	private long lastObservedPositionKey;
	private int lastObservedAnimationHash;
	private boolean hasObservation;

	BillboardUpdateState(double qualityScale)
	{
		this.qualityScale = qualityScale;
		this.nextEligibleGameCycle = Integer.MIN_VALUE;
		this.lastRedrawGameCycle = Integer.MIN_VALUE;
		this.lastRedrawPositionKey = Long.MIN_VALUE;
		this.lastRedrawAnimationHash = Integer.MIN_VALUE;
		this.lastRedrawViewHash = Integer.MIN_VALUE;
		this.lastObservedPositionKey = Long.MIN_VALUE;
		this.lastObservedAnimationHash = Integer.MIN_VALUE;
		this.hasObservation = false;
	}

	boolean isReady(int gameCycle, boolean hasCachedBillboard)
	{
		return !hasCachedBillboard || gameCycle >= nextEligibleGameCycle;
	}

	boolean hasMoved(UpdateHeuristicSnapshot snapshot)
	{
		return hasObservation
			&& snapshot.hasPosition()
			&& lastObservedPositionKey != Long.MIN_VALUE
			&& lastObservedPositionKey != snapshot.positionKey;
	}

	boolean hasAnimationChanged(UpdateHeuristicSnapshot snapshot)
	{
		return hasObservation && lastObservedAnimationHash != Integer.MIN_VALUE && lastObservedAnimationHash != snapshot.animationHash;
	}

	boolean hasPositionChangedSinceRedraw(UpdateHeuristicSnapshot snapshot)
	{
		return snapshot != null
			&& snapshot.hasPosition()
			&& lastRedrawPositionKey != Long.MIN_VALUE
			&& lastRedrawPositionKey != snapshot.positionKey;
	}

	boolean hasViewChangedSinceRedraw(UpdateHeuristicSnapshot snapshot)
	{
		return snapshot != null
			&& lastRedrawViewHash != Integer.MIN_VALUE
			&& lastRedrawViewHash != snapshot.viewHash;
	}

	int cyclesSinceRedraw(int gameCycle)
	{
		if (lastRedrawGameCycle == Integer.MIN_VALUE)
		{
			return 64;
		}

		return Math.max(0, gameCycle - lastRedrawGameCycle);
	}

	int overdueCycles(int gameCycle)
	{
		if (nextEligibleGameCycle == Integer.MIN_VALUE)
		{
			return cyclesSinceRedraw(gameCycle);
		}

		return Math.max(0, gameCycle - nextEligibleGameCycle);
	}

	void observe(UpdateHeuristicSnapshot snapshot)
	{
		if (snapshot == null)
		{
			return;
		}

		lastObservedPositionKey = snapshot.positionKey;
		lastObservedAnimationHash = snapshot.animationHash;
		hasObservation = true;
	}

	void defer(int gameCycle, int baseRefreshInterval)
	{
		nextEligibleGameCycle = gameCycle + Math.max(1, baseRefreshInterval);
	}

	void advance(int gameCycle, int baseRefreshInterval, double fullQualityScale, UpdateHeuristicSnapshot snapshot, int minimumAnimatedRedrawInterval)
	{
		double clampedFullQuality = BillboardRenderQuality.clamp(fullQualityScale);
		double clampedCurrentQuality = Math.max(BillboardRenderQuality.MIN_RENDER_QUALITY, Math.min(clampedFullQuality, qualityScale));
		double refreshRatio = clampedCurrentQuality / clampedFullQuality;
		int nextDelay = Math.max(1, (int) Math.round(baseRefreshInterval * refreshRatio));
		if (snapshot != null && snapshot.animated)
		{
			nextDelay = Math.max(nextDelay, Math.max(1, minimumAnimatedRedrawInterval));
		}
		nextEligibleGameCycle = gameCycle + nextDelay;
		qualityScale = Math.min(clampedFullQuality, clampedCurrentQuality * 2.0d);
		lastRedrawGameCycle = gameCycle;
		if (snapshot != null)
		{
			lastRedrawPositionKey = snapshot.positionKey;
			lastRedrawAnimationHash = snapshot.animationHash;
			lastRedrawViewHash = snapshot.viewHash;
		}
	}
}

final class FrameUpdatePlan
{
	final double qualityScale;
	final boolean forceHoverInteractionRedraw;

	FrameUpdatePlan(double qualityScale, boolean forceHoverInteractionRedraw)
	{
		this.qualityScale = qualityScale;
		this.forceHoverInteractionRedraw = forceHoverInteractionRedraw;
	}
}

final class UpdateHeuristicSnapshot
{
	final double depth;
	final long positionKey;
	final int animationHash;
	final int viewHash;
	final boolean animated;

	UpdateHeuristicSnapshot(double depth, long positionKey, int animationHash, int viewHash, boolean animated)
	{
		this.depth = depth;
		this.positionKey = positionKey;
		this.animationHash = animationHash;
		this.viewHash = viewHash;
		this.animated = animated;
	}

	static UpdateHeuristicSnapshot empty()
	{
		return new UpdateHeuristicSnapshot(Double.POSITIVE_INFINITY, Long.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, false);
	}

	static UpdateHeuristicSnapshot forDepth(double depth)
	{
		return new UpdateHeuristicSnapshot(depth, Long.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, false);
	}

	static UpdateHeuristicSnapshot fromRequest(BillboardRenderRequest request, double depth, int animatedTextureOffsetStateHash)
	{
		long positionKey = positionKey(request.localPoint, request.plane);
		int animationHash = 1;
		int modelStateHash = BillboardModelStateHash.hash(request.model);
		int viewHash = 1;
		viewHash = (31 * viewHash) + request.relativeYaw;
		viewHash = (31 * viewHash) + request.relativePitch;
		animationHash = (31 * animationHash) + request.animationId;
		animationHash = (31 * animationHash) + request.animationFrame;
		animationHash = (31 * animationHash) + request.poseAnimationId;
		animationHash = (31 * animationHash) + request.poseAnimationFrame;
		animationHash = (31 * animationHash) + request.animatedTextureId;
		animationHash = (31 * animationHash) + modelStateHash;
		animationHash = (31 * animationHash) + animatedTextureOffsetStateHash;
		boolean animated = request.animationId >= 0
			|| request.poseAnimationId >= 0
			|| request.animationFrame >= 0
			|| request.poseAnimationFrame >= 0
			|| modelStateHash != 0
			|| request.animatedTextureId >= 0;
		return new UpdateHeuristicSnapshot(depth, positionKey, animationHash, viewHash, animated);
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

final class QueuedBillboardTarget
{
	final BillboardTargetKey key;
	private final double priorityScore;
	private final int queueIndex;

	QueuedBillboardTarget(BillboardTargetKey key, double priorityScore, int queueIndex)
	{
		this.key = key;
		this.priorityScore = priorityScore;
		this.queueIndex = queueIndex;
	}

	double getPriorityScore()
	{
		return priorityScore;
	}

	int getQueueIndex()
	{
		return queueIndex;
	}
}
