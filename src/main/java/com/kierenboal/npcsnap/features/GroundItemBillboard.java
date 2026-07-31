package com.kierenboal.npcsnap.features;

import net.runelite.api.coords.LocalPoint;

public final class GroundItemBillboard
{
	public final int plane;
	public final LocalPoint localPoint;
	public final int verticalOffset;

	public GroundItemBillboard(int plane, LocalPoint localPoint)
	{
		this(plane, localPoint, 0);
	}

	public GroundItemBillboard(int plane, LocalPoint localPoint, int verticalOffset)
	{
		this.plane = plane;
		this.localPoint = localPoint;
		this.verticalOffset = Math.max(0, verticalOffset);
	}
}

