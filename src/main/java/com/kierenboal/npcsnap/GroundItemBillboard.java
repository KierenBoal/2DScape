package com.kierenboal.npcsnap;

import net.runelite.api.coords.LocalPoint;

final class GroundItemBillboard
{
	final int plane;
	final LocalPoint localPoint;

	GroundItemBillboard(int plane, LocalPoint localPoint)
	{
		this.plane = plane;
		this.localPoint = localPoint;
	}
}

