package com.kierenboal.npcsnap.state;

public final class TextureCacheEntry implements TimedCacheEntry
{
	public final int[] pixels;
	public final int width;
	public final int height;
	private long lastUsedMillis;
	private Boolean cutouts;

	public TextureCacheEntry(int[] pixels, int width, int height, long nowMillis)
	{
		this.pixels = pixels;
		this.width = width;
		this.height = height;
		lastUsedMillis = nowMillis;
	}

	public boolean hasCutouts()
	{
		if (cutouts == null)
		{
			cutouts = false;
			for (int pixel : pixels)
			{
				if (pixel == 0)
				{
					cutouts = true;
					break;
				}
			}
		}
		return cutouts;
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

