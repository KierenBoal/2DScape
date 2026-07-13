package com.kierenboal.npcsnap.state;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.runelite.api.Renderable;
import net.runelite.api.TileObject;

public final class BillboardVisibilityState
{
	public final Set<Renderable> activeBillboards = identitySet();
	public final Set<Renderable> suppressedRenderables = identitySet();
	public final Set<TileObject> activeTileObjects = identitySet();

	private volatile Set<Renderable> activeBillboardSnapshot = Collections.emptySet();
	private volatile Set<Renderable> suppressedRenderableSnapshot = Collections.emptySet();
	private volatile Set<TileObject> activeTileObjectSnapshot = Collections.emptySet();

	public void clearSelections()
	{
		activeBillboards.clear();
		suppressedRenderables.clear();
		activeTileObjects.clear();
	}

	public void clearSnapshots()
	{
		activeBillboardSnapshot = Collections.emptySet();
		suppressedRenderableSnapshot = Collections.emptySet();
		activeTileObjectSnapshot = Collections.emptySet();
	}

	public void publishSnapshots()
	{
		activeBillboardSnapshot = updatedSnapshot(activeBillboards, activeBillboardSnapshot);
		suppressedRenderableSnapshot = updatedSnapshot(suppressedRenderables, suppressedRenderableSnapshot);
		activeTileObjectSnapshot = updatedSnapshot(activeTileObjects, activeTileObjectSnapshot);
	}

	public boolean shouldHideRenderable(Renderable renderable, boolean clientThread)
	{
		return clientThread
			? activeBillboards.contains(renderable) || suppressedRenderables.contains(renderable)
			: activeBillboardSnapshot.contains(renderable) || suppressedRenderableSnapshot.contains(renderable);
	}

	public boolean hasActiveTileObject(TileObject tileObject, boolean clientThread)
	{
		return clientThread ? activeTileObjects.contains(tileObject) : activeTileObjectSnapshot.contains(tileObject);
	}

	public void clearActiveTileObjects()
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
