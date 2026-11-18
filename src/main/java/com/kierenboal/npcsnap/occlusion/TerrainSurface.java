package com.kierenboal.npcsnap.occlusion;

import lombok.RequiredArgsConstructor;
/** Identity is in the owning scene, before nested-world projection into the main scene. */
@RequiredArgsConstructor
public final class TerrainSurface
{
	public final int worldView, plane, tileX, tileY;
	public final boolean upperSurface;
	@Override public String toString()
	{
		return "view="+worldView+":plane="+plane+":tile="+tileX+","+tileY+":upper="+upperSurface;
	}
}
