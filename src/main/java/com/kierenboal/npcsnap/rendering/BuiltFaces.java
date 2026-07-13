package com.kierenboal.npcsnap.rendering;

import java.util.List;

public final class BuiltFaces
{
	public final List<FaceDraw> faces;
	public final int textureStateHash;

	public BuiltFaces(List<FaceDraw> faces, int textureStateHash)
	{
		this.faces = faces;
		this.textureStateHash = textureStateHash;
	}
}

