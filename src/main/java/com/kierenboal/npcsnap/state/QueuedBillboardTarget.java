package com.kierenboal.npcsnap.state;

import com.kierenboal.npcsnap.targeting.BillboardTargetKey;

public final class QueuedBillboardTarget
{
	public final BillboardTargetKey key;
	private final double priorityScore;
	private final int queueIndex;

	public QueuedBillboardTarget(BillboardTargetKey key, double priorityScore, int queueIndex)
	{
		this.key = key;
		this.priorityScore = priorityScore;
		this.queueIndex = queueIndex;
	}

	public double getPriorityScore()
	{
		return priorityScore;
	}

	public int getQueueIndex()
	{
		return queueIndex;
	}
}

