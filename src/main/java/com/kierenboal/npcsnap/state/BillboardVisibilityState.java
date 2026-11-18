package com.kierenboal.npcsnap.state;

import com.kierenboal.npcsnap.IdentitySets;
import java.util.Collections;
import java.util.Set;
import net.runelite.api.Renderable;
import net.runelite.api.TileObject;

public final class BillboardVisibilityState
{
	public final Set<Renderable> activeBillboards = IdentitySets.create();
	public final Set<Renderable> suppressedRenderables = IdentitySets.create();
	public final Set<TileObject> activeTileObjects = IdentitySets.create();

	private volatile Set<Renderable> activeBillboardSnapshot = Collections.emptySet();
	private volatile Set<Renderable> suppressedRenderableSnapshot = Collections.emptySet();
	private volatile Set<TileObject> activeTileObjectSnapshot = Collections.emptySet();
	private volatile Set<Renderable> drawableActor2dSnapshot = Collections.emptySet();

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
		drawableActor2dSnapshot = Collections.emptySet();
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

	public boolean hasActiveBillboard(Renderable renderable, boolean clientThread)
	{
		return clientThread ? activeBillboards.contains(renderable) : activeBillboardSnapshot.contains(renderable);
	}

	public boolean hadDrawableActor2dBillboard(Renderable renderable)
	{
		return drawableActor2dSnapshot.contains(renderable);
	}

	public void publishDrawableActor2d(Set<Renderable> renderables)
	{
		drawableActor2dSnapshot = IdentitySets.copyOf(renderables);
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

	private static <T> Set<T> updatedSnapshot(Set<T> source, Set<T> currentSnapshot)
	{
		return source.size() == currentSnapshot.size() && currentSnapshot.containsAll(source)
			? currentSnapshot
			: IdentitySets.copyOf(source);
	}
}
