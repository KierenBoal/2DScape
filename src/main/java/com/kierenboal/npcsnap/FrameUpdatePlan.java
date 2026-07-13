package com.kierenboal.npcsnap;

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

