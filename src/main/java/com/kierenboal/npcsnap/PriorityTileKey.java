package com.kierenboal.npcsnap;

import net.runelite.api.coords.LocalPoint;

final class PriorityTileKey
{
	private final int plane;
	private final int tileX;
	private final int tileY;

	private PriorityTileKey(int plane, int tileX, int tileY)
	{
		this.plane = plane;
		this.tileX = tileX;
		this.tileY = tileY;
	}

	static PriorityTileKey of(LocalPoint localPoint, int plane, int renderPriority)
	{
		if (renderPriority == BillboardConstants.RENDER_PRIORITY_NONE || localPoint == null || plane < 0)
		{
			return null;
		}

		int tileX = localPoint.getX() / BillboardConstants.LOCAL_TILE_SIZE;
		int tileY = localPoint.getY() / BillboardConstants.LOCAL_TILE_SIZE;
		return new PriorityTileKey(plane, tileX, tileY);
	}

	long sortKey()
	{
		return ((long) plane << 48)
			^ ((long) (tileX & 0xFFFFFF) << 24)
			^ (tileY & 0xFFFFFFL);
	}

	@Override
	public boolean equals(Object other)
	{
		if (this == other)
		{
			return true;
		}

		if (!(other instanceof PriorityTileKey))
		{
			return false;
		}

		PriorityTileKey that = (PriorityTileKey) other;
		return plane == that.plane && tileX == that.tileX && tileY == that.tileY;
	}

	@Override
	public int hashCode()
	{
		int result = plane;
		result = 31 * result + tileX;
		result = 31 * result + tileY;
		return result;
	}
}

