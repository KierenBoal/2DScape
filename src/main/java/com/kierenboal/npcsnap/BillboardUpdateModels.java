package com.kierenboal.npcsnap;

import net.runelite.api.coords.LocalPoint;

final class BillboardUpdateState
{
	double qualityScale;
	private int nextEligibleGameCycle;
	private int lastRedrawGameCycle;
	private long lastRedrawPositionKey;
	private int lastRedrawAnimationHash;
	private int lastRedrawModelStateHash;
	private int lastRedrawTextureStateHash;
	private int lastRedrawViewHash;
	private long lastObservedPositionKey;
	private int lastObservedAnimationHash;
	private int lastObservedModelStateHash;
	private int lastObservedTextureStateHash;
	private boolean hasObservation;

	BillboardUpdateState(double qualityScale)
	{
		this.qualityScale = qualityScale;
		this.nextEligibleGameCycle = Integer.MIN_VALUE;
		this.lastRedrawGameCycle = Integer.MIN_VALUE;
		this.lastRedrawPositionKey = Long.MIN_VALUE;
		this.lastRedrawAnimationHash = Integer.MIN_VALUE;
		this.lastRedrawModelStateHash = Integer.MIN_VALUE;
		this.lastRedrawTextureStateHash = Integer.MIN_VALUE;
		this.lastRedrawViewHash = Integer.MIN_VALUE;
		this.lastObservedPositionKey = Long.MIN_VALUE;
		this.lastObservedAnimationHash = Integer.MIN_VALUE;
		this.lastObservedModelStateHash = Integer.MIN_VALUE;
		this.lastObservedTextureStateHash = Integer.MIN_VALUE;
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
		return snapshot != null
			&& hasObservation
			&& lastObservedAnimationHash != Integer.MIN_VALUE
			&& lastObservedAnimationHash != snapshot.animationHash;
	}

	boolean hasModelChanged(UpdateHeuristicSnapshot snapshot)
	{
		return snapshot != null
			&& hasObservation
			&& lastObservedModelStateHash != Integer.MIN_VALUE
			&& lastObservedModelStateHash != snapshot.modelStateHash;
	}

	boolean hasTextureChanged(UpdateHeuristicSnapshot snapshot)
	{
		return snapshot != null
			&& hasObservation
			&& lastObservedTextureStateHash != Integer.MIN_VALUE
			&& lastObservedTextureStateHash != snapshot.textureStateHash;
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

	boolean hasAnimationChangedSinceRedraw(UpdateHeuristicSnapshot snapshot)
	{
		return snapshot != null
			&& lastRedrawAnimationHash != Integer.MIN_VALUE
			&& lastRedrawAnimationHash != snapshot.animationHash;
	}

	boolean hasModelChangedSinceRedraw(UpdateHeuristicSnapshot snapshot)
	{
		return snapshot != null
			&& lastRedrawModelStateHash != Integer.MIN_VALUE
			&& lastRedrawModelStateHash != snapshot.modelStateHash;
	}

	boolean hasTextureChangedSinceRedraw(UpdateHeuristicSnapshot snapshot)
	{
		return snapshot != null
			&& lastRedrawTextureStateHash != Integer.MIN_VALUE
			&& lastRedrawTextureStateHash != snapshot.textureStateHash;
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
		lastObservedModelStateHash = snapshot.modelStateHash;
		lastObservedTextureStateHash = snapshot.textureStateHash;
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
			lastRedrawModelStateHash = snapshot.modelStateHash;
			lastRedrawTextureStateHash = snapshot.textureStateHash;
			lastRedrawViewHash = snapshot.viewHash;
		}
	}
}

final class FrameUpdatePlan
{
	final double qualityScale;
	final boolean forceHoverInteractionRedraw;
	final BillboardUpdateState updateState;
	final UpdateHeuristicSnapshot snapshot;
	final int gameCycle;
	final int baseRefreshInterval;
	final double fullQualityScale;
	final int minimumAnimatedRedrawInterval;

	FrameUpdatePlan(
		double qualityScale,
		boolean forceHoverInteractionRedraw,
		BillboardUpdateState updateState,
		UpdateHeuristicSnapshot snapshot,
		int gameCycle,
		int baseRefreshInterval,
		double fullQualityScale,
		int minimumAnimatedRedrawInterval)
	{
		this.qualityScale = qualityScale;
		this.forceHoverInteractionRedraw = forceHoverInteractionRedraw;
		this.updateState = updateState;
		this.snapshot = snapshot;
		this.gameCycle = gameCycle;
		this.baseRefreshInterval = baseRefreshInterval;
		this.fullQualityScale = fullQualityScale;
		this.minimumAnimatedRedrawInterval = minimumAnimatedRedrawInterval;
	}

	void markRedrawSucceeded()
	{
		if (updateState != null)
		{
			updateState.advance(gameCycle, baseRefreshInterval, fullQualityScale, snapshot, minimumAnimatedRedrawInterval);
		}
	}
}

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

final class BillboardUpdatePriority
{
	private BillboardUpdatePriority()
	{
	}

	static double stateChangeScore(BillboardUpdateState state, UpdateHeuristicSnapshot snapshot)
	{
		if (state == null || snapshot == null)
		{
			return 0.0d;
		}

		double score = 0.0d;
		if (state.hasModelChanged(snapshot) || state.hasModelChangedSinceRedraw(snapshot))
		{
			score += 25_000.0d;
		}

		if (state.hasAnimationChanged(snapshot) || state.hasAnimationChangedSinceRedraw(snapshot))
		{
			score += 12_000.0d;
		}

		if (state.hasViewChangedSinceRedraw(snapshot))
		{
			score += 6_000.0d;
		}

		if (state.hasMoved(snapshot))
		{
			score += 1_200.0d;
		}
		else if (state.hasTextureChanged(snapshot) || state.hasTextureChangedSinceRedraw(snapshot) || snapshot.animated)
		{
			score += 450.0d;
		}

		return score;
	}
}

final class BillboardUpdateScheduler
{
	private BillboardUpdateScheduler()
	{
	}

	static double initialQualityScale(boolean hasCachedBillboard, double fullQualityScale, int nearPriorityIndex, int maxDrawsPerFrame)
	{
		return hasCachedBillboard
			? BillboardRenderQuality.clamp(fullQualityScale)
			: BillboardRenderQuality.seededScale(fullQualityScale, nearPriorityIndex, maxDrawsPerFrame);
	}

	static boolean allTileObjectPartsCached(Iterable<ObjectRenderablePart> parts, java.util.function.Predicate<net.runelite.api.Renderable> hasCache)
	{
		if (parts == null || hasCache == null)
		{
			return false;
		}

		boolean sawRenderablePart = false;
		for (ObjectRenderablePart part : parts)
		{
			if (part == null || part.renderable == null)
			{
				continue;
			}

			sawRenderablePart = true;
			if (!hasCache.test(part.renderable))
			{
				return false;
			}
		}

		return sawRenderablePart;
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
