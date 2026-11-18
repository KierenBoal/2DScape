package com.kierenboal.npcsnap.rendering;

import com.kierenboal.npcsnap.state.TextureCacheEntry;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class TextureSample
{
	public final TextureCacheEntry entry;
	public final float uOffset;
	public final float vOffset;
	public final int stateHash;

	public int pixel(double u, double v)
	{
		return pixel(u, v, false);
	}

	public int pixel(double u, double v, boolean clampU)
	{
		double shiftedU = u + uOffset;
		double s = clampU ? Math.max(0, shiftedU) : BillboardGeometryUtils.wrapUnit(shiftedU);
		int x = Math.min(entry.width - 1, (int) (s * entry.width));
		int y = Math.min(entry.height - 1, (int) (BillboardGeometryUtils.wrapUnit(v + vOffset) * entry.height));
		return entry.pixels[y * entry.width + x];
	}
}

