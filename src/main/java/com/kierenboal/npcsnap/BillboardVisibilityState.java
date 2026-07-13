package com.kierenboal.npcsnap;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.runelite.api.Renderable;
import net.runelite.api.TileObject;

final class BillboardVisibilityState
{
	final Set<Renderable> activeBillboards = identitySet();
	final Set<Renderable> suppressedRenderables = identitySet();
	final Set<TileObject> activeTileObjects = identitySet();

	private volatile Set<Renderable> activeBillboardSnapshot = Collections.emptySet();
	private volatile Set<Renderable> suppressedRenderableSnapshot = Collections.emptySet();
	private volatile Set<TileObject> activeTileObjectSnapshot = Collections.emptySet();

	void clearSelections()
	{
		activeBillboards.clear();
		suppressedRenderables.clear();
		activeTileObjects.clear();
	}

	void clearSnapshots()
	{
		activeBillboardSnapshot = Collections.emptySet();
		suppressedRenderableSnapshot = Collections.emptySet();
		activeTileObjectSnapshot = Collections.emptySet();
	}

	void publishSnapshots()
	{
		activeBillboardSnapshot = updatedSnapshot(activeBillboards, activeBillboardSnapshot);
		suppressedRenderableSnapshot = updatedSnapshot(suppressedRenderables, suppressedRenderableSnapshot);
		activeTileObjectSnapshot = updatedSnapshot(activeTileObjects, activeTileObjectSnapshot);
	}

	boolean shouldHideRenderable(Renderable renderable, boolean clientThread)
	{
		return clientThread
			? activeBillboards.contains(renderable) || suppressedRenderables.contains(renderable)
			: activeBillboardSnapshot.contains(renderable) || suppressedRenderableSnapshot.contains(renderable);
	}

	boolean hasActiveTileObject(TileObject tileObject, boolean clientThread)
	{
		return clientThread ? activeTileObjects.contains(tileObject) : activeTileObjectSnapshot.contains(tileObject);
	}

	void clearActiveTileObjects()
	{
		activeTileObjects.clear();
		activeTileObjectSnapshot = Collections.emptySet();
	}

	private static <T> Set<T> copy(Set<T> source)
	{
		Set<T> copy = identitySet();
		copy.addAll(source);
		return copy;
	}

	private static <T> Set<T> updatedSnapshot(Set<T> source, Set<T> currentSnapshot)
	{
		return source.size() == currentSnapshot.size() && currentSnapshot.containsAll(source)
			? currentSnapshot
			: copy(source);
	}

	private static <T> Set<T> identitySet()
	{
		return Collections.newSetFromMap(new IdentityHashMap<>());
	}
}
