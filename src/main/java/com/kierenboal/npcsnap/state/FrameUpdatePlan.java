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
	public final double minimumAnimatedRedrawInterval;
	public boolean forceStateChangeRedraw;
	private boolean completed;
	private int expectedParts = 1;
	private final java.util.Set<net.runelite.api.Renderable> successfulParts =
		java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

	public FrameUpdatePlan(
		double qualityScale,
		boolean forceHoverInteractionRedraw,
		BillboardUpdateState updateState,
		UpdateHeuristicSnapshot snapshot,
		int gameCycle,
		int baseRefreshInterval,
		double fullQualityScale,
		double minimumAnimatedRedrawInterval)
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
		double minimumAnimatedRedrawInterval)
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
		return forceHoverInteractionRedraw || forceActorResizeRedraw || forceStateChangeRedraw;
	}

	public boolean isCompleted()
	{
		return completed;
	}

	public void expectParts(int count)
	{
		expectedParts = Math.max(1, count);
	}

	public void partSucceeded(net.runelite.api.Renderable part)
	{
		successfulParts.add(part);
		if (successfulParts.size() >= expectedParts) markRedrawSucceeded();
	}

	public void markRedrawSucceeded()
	{
		if (completed) return;
		completed = true;
		if (updateState != null)
		{
			if (isForcedRedraw()) updateState.resetCadence();
			updateState.advance(gameCycle, baseRefreshInterval, fullQualityScale, snapshot, minimumAnimatedRedrawInterval);
		}
	}
}

