package com.kierenboal.npcsnap;

final class TextureSample
{
	final TextureCacheEntry entry;
	final float uOffset;
	final float vOffset;
	final int stateHash;

	TextureSample(TextureCacheEntry entry, float uOffset, float vOffset, int stateHash)
	{
		this.entry = entry;
		this.uOffset = uOffset;
		this.vOffset = vOffset;
		this.stateHash = stateHash;
	}
}

