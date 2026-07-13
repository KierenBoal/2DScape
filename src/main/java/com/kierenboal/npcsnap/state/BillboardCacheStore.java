package com.kierenboal.npcsnap.state;

import com.kierenboal.npcsnap.BillboardConstants;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import net.runelite.api.Renderable;

public final class BillboardCacheStore
{
	private final Map<Renderable, CachedBillboard> entries = new IdentityHashMap<>();

	public CachedBillboard get(Renderable renderable)
	{
		return entries.get(renderable);
	}

	public boolean contains(Renderable renderable)
	{
		return entries.containsKey(renderable);
	}

	public void put(Renderable renderable, CachedBillboard cached)
	{
		CachedBillboard previous = entries.put(renderable, cached);
		if (previous != null && previous != cached)
		{
			previous.flush();
		}
	}

	public void remove(Renderable renderable)
	{
		CachedBillboard cached = entries.remove(renderable);
		if (cached != null)
		{
			cached.flush();
		}
	}

	public void clear()
	{
		for (CachedBillboard cached : entries.values())
		{
			cached.flush();
		}
		entries.clear();
	}

	public void expire(long nowMillis)
	{
		Iterator<CachedBillboard> iterator = entries.values().iterator();
		while (iterator.hasNext())
		{
			CachedBillboard cached = iterator.next();
			if (nowMillis - cached.lastUsedMillis() > BillboardConstants.CACHE_TTL_MILLIS)
			{
				cached.flush();
				iterator.remove();
			}
		}
	}
}
