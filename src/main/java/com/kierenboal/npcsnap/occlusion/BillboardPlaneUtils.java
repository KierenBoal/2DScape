package com.kierenboal.npcsnap.occlusion;

public final class BillboardPlaneUtils
{
	private BillboardPlaneUtils()
	{
	}

	public static boolean shouldRenderTargetPlane(int currentPlane, int targetPlane)
	{
		return currentPlane == targetPlane;
	}
}
