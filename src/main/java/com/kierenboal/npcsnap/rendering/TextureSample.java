package com.kierenboal.npcsnap.rendering;

import com.kierenboal.npcsnap.state.TextureCacheEntry;

public final class TextureSample
{
	public final TextureCacheEntry entry;
	public final float uOffset;
	public final float vOffset;
	public final int stateHash;

	public TextureSample(TextureCacheEntry entry, float uOffset, float vOffset, int stateHash)
	{
		this.entry = entry;
		this.uOffset = uOffset;
		this.vOffset = vOffset;
		this.stateHash = stateHash;
	}
}

