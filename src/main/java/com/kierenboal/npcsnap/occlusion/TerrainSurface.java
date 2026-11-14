package com.kierenboal.npcsnap.occlusion;

/** Identity is in the owning scene, before nested-world projection into the main scene. */
public final class TerrainSurface
{
	public final int worldView, plane, tileX, tileY;
	public final boolean upperSurface;
	public TerrainSurface(int worldView, int plane, int tileX, int tileY, boolean upperSurface)
	{
		this.worldView=worldView; this.plane=plane; this.tileX=tileX; this.tileY=tileY; this.upperSurface=upperSurface;
	}
	@Override public String toString()
	{
		return "view="+worldView+":plane="+plane+":tile="+tileX+","+tileY+":upper="+upperSurface;
	}
}
