package com.kierenboal.npcsnap.targeting;

import com.kierenboal.npcsnap.BillboardConstants;

import net.runelite.api.coords.LocalPoint;

import lombok.EqualsAndHashCode;

@EqualsAndHashCode
public final class PriorityTileKey
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

	public static PriorityTileKey of(LocalPoint localPoint, int plane, int renderPriority)
	{
		if (renderPriority == BillboardConstants.RENDER_PRIORITY_NONE || localPoint == null || plane < 0)
		{
			return null;
		}

		int tileX = localPoint.getX() / BillboardConstants.LOCAL_TILE_SIZE;
		int tileY = localPoint.getY() / BillboardConstants.LOCAL_TILE_SIZE;
		return new PriorityTileKey(plane, tileX, tileY);
	}

	public long sortKey()
	{
		return ((long) plane << 48)
			^ ((long) (tileX & 0xFFFFFF) << 24)
			^ (tileY & 0xFFFFFFL);
	}
}
