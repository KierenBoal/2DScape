package com.kierenboal.npcsnap.features;

import net.runelite.api.coords.LocalPoint;

public final class GroundItemBillboard
{
	public final int plane;
	public final LocalPoint localPoint;

	public GroundItemBillboard(int plane, LocalPoint localPoint)
	{
		this.plane = plane;
		this.localPoint = localPoint;
	}
}

