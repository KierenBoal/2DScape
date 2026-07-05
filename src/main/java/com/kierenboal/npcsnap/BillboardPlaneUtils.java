package com.kierenboal.npcsnap;

final class BillboardPlaneUtils
{
	private BillboardPlaneUtils()
	{
	}

	static boolean shouldRenderTargetPlane(int currentPlane, int targetPlane)
	{
		return currentPlane == targetPlane;
	}
}
