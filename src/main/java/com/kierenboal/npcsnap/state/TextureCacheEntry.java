package com.kierenboal.npcsnap.state;

public final class TextureCacheEntry implements TimedCacheEntry
{
	public final int[] pixels;
	public final int width;
	public final int height;
	private long lastUsedMillis;

	public TextureCacheEntry(int[] pixels, int width, int height, long lastUsedMillis)
	{
		this.pixels = pixels;
		this.width = width;
		this.height = height;
		this.lastUsedMillis = lastUsedMillis;
	}

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

