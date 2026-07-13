package com.kierenboal.npcsnap;

import java.util.List;

final class BuiltFaces
{
	final List<FaceDraw> faces;
	final int textureStateHash;

	BuiltFaces(List<FaceDraw> faces, int textureStateHash)
	{
		this.faces = faces;
		this.textureStateHash = textureStateHash;
	}
}

