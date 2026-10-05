package com.kierenboal.npcsnap.state;

import java.util.EnumSet;

public final class FrameUpdatePlan
{
	public final double qualityScale;
	public final boolean forceHoverInteractionRedraw;
	public final boolean forceActorResizeRedraw;
	public final EnumSet<BillboardRedrawReason> redrawReasons = EnumSet.noneOf(BillboardRedrawReason.class);
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
		this(qualityScale, forceHoverInteractionRedraw, false, updateState, snapshot,
			gameCycle, baseRefreshInterval, fullQualityScale, minimumAnimatedRedrawInterval);
	}

	public FrameUpdatePlan(
		double qualityScale,
		boolean forceHoverInteractionRedraw,
		boolean forceActorResizeRedraw,
		BillboardUpdateState updateState,
		UpdateHeuristicSnapshot snapshot,
		int gameCycle,
		int baseRefreshInterval,
		double fullQualityScale,
		int minimumAnimatedRedrawInterval)
	{
		this.qualityScale = qualityScale;
		this.forceHoverInteractionRedraw = forceHoverInteractionRedraw;
		this.forceActorResizeRedraw = forceActorResizeRedraw;
		if (forceHoverInteractionRedraw)
		{
			redrawReasons.add(BillboardRedrawReason.INTERACTION_CHANGE);
		}
		if (forceActorResizeRedraw)
		{
			redrawReasons.add(BillboardRedrawReason.ACTOR_RESIZE);
		}
		this.updateState = updateState;
		this.snapshot = snapshot;
		this.gameCycle = gameCycle;
		this.baseRefreshInterval = baseRefreshInterval;
		this.fullQualityScale = fullQualityScale;
		this.minimumAnimatedRedrawInterval = minimumAnimatedRedrawInterval;
	}

	public boolean isForcedRedraw()
	{
		return forceHoverInteractionRedraw || forceActorResizeRedraw;
	}

	public void markRedrawSucceeded()
	{
		if (updateState != null)
		{
			updateState.advance(gameCycle, baseRefreshInterval, fullQualityScale, snapshot, minimumAnimatedRedrawInterval);
		}
	}
}

