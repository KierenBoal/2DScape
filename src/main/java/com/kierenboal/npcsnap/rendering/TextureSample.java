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
}

