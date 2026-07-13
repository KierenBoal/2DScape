package com.kierenboal.npcsnap;

final class TextureCacheEntry implements TimedCacheEntry
{
	final int[] pixels;
	final int width;
	final int height;
	private long lastUsedMillis;

	TextureCacheEntry(int[] pixels, int width, int height, long lastUsedMillis)
	{
		this.pixels = pixels;
		this.width = width;
		this.height = height;
		this.lastUsedMillis = lastUsedMillis;
	}

	void touch(long nowMillis)
	{
		lastUsedMillis = nowMillis;
	}

	@Override
	public long lastUsedMillis()
	{
		return lastUsedMillis;
	}
}

