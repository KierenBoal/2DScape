package com.kierenboal.npcsnap.state;

public final class FrameUpdatePlan
{
	public final double qualityScale;
	public final boolean forceHoverInteractionRedraw;
	public final BillboardUpdateState updateState;
	public final UpdateHeuristicSnapshot snapshot;
	public final int gameCycle;
	public final int baseRefreshInterval;
	public final double fullQualityScale;
	public final int minimumAnimatedRedrawInterval;

	public FrameUpdatePlan(
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

	public void markRedrawSucceeded()
	{
		if (updateState != null)
		{
			updateState.advance(gameCycle, baseRefreshInterval, fullQualityScale, snapshot, minimumAnimatedRedrawInterval);
		}
	}
}

