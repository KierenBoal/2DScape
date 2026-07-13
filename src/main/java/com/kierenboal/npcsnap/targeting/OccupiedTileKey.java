package com.kierenboal.npcsnap.targeting;

import com.kierenboal.npcsnap.BillboardConstants;

import net.runelite.api.coords.LocalPoint;

public final class OccupiedTileKey
{
	private final int plane;
	private final int tileX;
	private final int tileY;

	private OccupiedTileKey(int plane, int tileX, int tileY)
	{
		this.plane = plane;
		this.tileX = tileX;
		this.tileY = tileY;
	}

	public static OccupiedTileKey of(LocalPoint localPoint, int plane)
	{
		if (localPoint == null)
		{
			return null;
		}

		return new OccupiedTileKey(
			plane,
			localPoint.getX() / BillboardConstants.LOCAL_TILE_SIZE,
			localPoint.getY() / BillboardConstants.LOCAL_TILE_SIZE
		);
	}

	@Override
	public boolean equals(Object other)
	{
		if (this == other)
		{
			return true;
		}

		if (!(other instanceof OccupiedTileKey))
		{
			return false;
		}

		OccupiedTileKey that = (OccupiedTileKey) other;
		return plane == that.plane
			&& tileX == that.tileX
			&& tileY == that.tileY;
	}

	@Override
	public int hashCode()
	{
		int result = Integer.hashCode(plane);
		result = (31 * result) + Integer.hashCode(tileX);
		result = (31 * result) + Integer.hashCode(tileY);
		return result;
	}
}

