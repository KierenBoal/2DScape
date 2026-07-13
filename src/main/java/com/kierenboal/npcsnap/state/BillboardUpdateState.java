package com.kierenboal.npcsnap.state;

import com.kierenboal.npcsnap.rendering.BillboardRenderQuality;

public final class BillboardUpdateState
{
	public double qualityScale;
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

	public BillboardUpdateState(double qualityScale)
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

	public boolean isReady(int gameCycle, boolean hasCachedBillboard)
	{
		return !hasCachedBillboard || gameCycle >= nextEligibleGameCycle;
	}

	public boolean hasMoved(UpdateHeuristicSnapshot snapshot)
	{
		return hasObservation
			&& snapshot.hasPosition()
			&& lastObservedPositionKey != Long.MIN_VALUE
			&& lastObservedPositionKey != snapshot.positionKey;
	}

	public boolean hasAnimationChanged(UpdateHeuristicSnapshot snapshot)
	{
		return snapshot != null
			&& hasObservation
			&& lastObservedAnimationHash != Integer.MIN_VALUE
			&& lastObservedAnimationHash != snapshot.animationHash;
	}

	public boolean hasModelChanged(UpdateHeuristicSnapshot snapshot)
	{
		return snapshot != null
			&& hasObservation
			&& lastObservedModelStateHash != Integer.MIN_VALUE
			&& lastObservedModelStateHash != snapshot.modelStateHash;
	}

	public boolean hasTextureChanged(UpdateHeuristicSnapshot snapshot)
	{
		return snapshot != null
			&& hasObservation
			&& lastObservedTextureStateHash != Integer.MIN_VALUE
			&& lastObservedTextureStateHash != snapshot.textureStateHash;
	}

	public boolean hasPositionChangedSinceRedraw(UpdateHeuristicSnapshot snapshot)
	{
		return snapshot != null
			&& snapshot.hasPosition()
			&& lastRedrawPositionKey != Long.MIN_VALUE
			&& lastRedrawPositionKey != snapshot.positionKey;
	}

	public boolean hasViewChangedSinceRedraw(UpdateHeuristicSnapshot snapshot)
	{
		return snapshot != null
			&& lastRedrawViewHash != Integer.MIN_VALUE
			&& lastRedrawViewHash != snapshot.viewHash;
	}

	public boolean hasAnimationChangedSinceRedraw(UpdateHeuristicSnapshot snapshot)
	{
		return snapshot != null
			&& lastRedrawAnimationHash != Integer.MIN_VALUE
			&& lastRedrawAnimationHash != snapshot.animationHash;
	}

	public boolean hasModelChangedSinceRedraw(UpdateHeuristicSnapshot snapshot)
	{
		return snapshot != null
			&& lastRedrawModelStateHash != Integer.MIN_VALUE
			&& lastRedrawModelStateHash != snapshot.modelStateHash;
	}

	public boolean hasTextureChangedSinceRedraw(UpdateHeuristicSnapshot snapshot)
	{
		return snapshot != null
			&& lastRedrawTextureStateHash != Integer.MIN_VALUE
			&& lastRedrawTextureStateHash != snapshot.textureStateHash;
	}

	public int cyclesSinceRedraw(int gameCycle)
	{
		if (lastRedrawGameCycle == Integer.MIN_VALUE)
		{
			return 64;
		}

		return Math.max(0, gameCycle - lastRedrawGameCycle);
	}

	public int overdueCycles(int gameCycle)
	{
		if (nextEligibleGameCycle == Integer.MIN_VALUE)
		{
			return cyclesSinceRedraw(gameCycle);
		}

		return Math.max(0, gameCycle - nextEligibleGameCycle);
	}

	public void observe(UpdateHeuristicSnapshot snapshot)
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

	public void defer(int gameCycle, int baseRefreshInterval)
	{
		nextEligibleGameCycle = gameCycle + Math.max(1, baseRefreshInterval);
	}

	public void advance(int gameCycle, int baseRefreshInterval, double fullQualityScale, UpdateHeuristicSnapshot snapshot, int minimumAnimatedRedrawInterval)
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

