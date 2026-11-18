package com.kierenboal.npcsnap.state;

import lombok.AllArgsConstructor;
@AllArgsConstructor
public final class TextureCacheEntry implements TimedCacheEntry
{
	public final int[] pixels;
	public final int width;
	public final int height;
	private long lastUsedMillis;


	public void touch(long nowMillis)
	{
		lastUsedMillis = nowMillis;
	}

	@Override
	public long lastUsedMillis()
	{
		return lastUsedMillis;
	}
}

