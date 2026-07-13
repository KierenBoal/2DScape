package com.kierenboal.npcsnap;

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

