package com.kierenboal.npcsnap;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public final class IdentitySets
{
	private IdentitySets()
	{
	}

	public static <T> Set<T> create()
	{
		return Collections.newSetFromMap(new IdentityHashMap<>());
	}

	public static <T> Set<T> copyOf(Collection<? extends T> source)
	{
		Set<T> copy = create();
		copy.addAll(source);
		return copy;
	}
}
