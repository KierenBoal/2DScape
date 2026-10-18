package com.kierenboal.npcsnap.state;

import com.kierenboal.npcsnap.rendering.BillboardRenderQuality;

public final class BillboardUpdateState
{
	public double qualityScale;
	private double nextEligibleGameCycle;
	private UpdateHeuristicSnapshot captured;
	private double previousInterval;
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
		return !hasCachedBillboard || gameCycle >= Math.ceil(nextEligibleGameCycle - 1e-9);
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

	public boolean hasCaptureException(UpdateHeuristicSnapshot snapshot, int resizePercent)
	{
		return captured != null && snapshot != null
			&& (captured.animationIdentityHash != snapshot.animationIdentityHash
				|| captured.appearanceHash != snapshot.appearanceHash
				|| captured.viewHash != snapshot.viewHash
				|| (!snapshot.animated && captured.modelStateHash != snapshot.modelStateHash)
				|| resized(snapshot, resizePercent));
	}

	public boolean resized(UpdateHeuristicSnapshot snapshot, int percent)
	{
		return captured != null && snapshot != null && snapshot.geometry != null
			&& snapshot.geometry.exceeds(captured.geometry, percent);
	}

	public void resetCadence()
	{
		nextEligibleGameCycle = Integer.MIN_VALUE;
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

		return Math.max(0, gameCycle - (int) Math.ceil(nextEligibleGameCycle - 1e-9));
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

	public void advance(int gameCycle, int baseRefreshInterval, double fullQualityScale, UpdateHeuristicSnapshot snapshot, double minimumAnimatedRedrawInterval)
	{
		double clampedFullQuality = BillboardRenderQuality.clamp(fullQualityScale);
		double clampedCurrentQuality = Math.max(BillboardRenderQuality.MIN_RENDER_QUALITY, Math.min(clampedFullQuality, qualityScale));
		double interval = snapshot != null && snapshot.animated ? Math.max(1d, minimumAnimatedRedrawInterval) : 1d;
		boolean reset = captured == null || previousInterval != interval || !isReady(gameCycle, true)
			|| hasCaptureException(snapshot, 0);
		if (reset || nextEligibleGameCycle == Integer.MIN_VALUE)
		{
			nextEligibleGameCycle = gameCycle + interval;
		}
		else
		{
			// Preserve the fractional phase, skip missed deadlines, and never catch up in bursts.
			nextEligibleGameCycle += (Math.floor((gameCycle - nextEligibleGameCycle) / interval) + 1) * interval;
		}
		nextEligibleGameCycle = Math.max(gameCycle + 1d, nextEligibleGameCycle);
		previousInterval = interval;
		captured = snapshot;
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

