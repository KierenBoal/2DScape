package com.kierenboal.npcsnap.state;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Tracks renderables observed by the renderer across two frame buffers.
 *
 * <p>GPU rendering can call the draw callbacks concurrently. The identity
 * sets therefore need a small, explicit critical section around both writes
 * and frame rollover.</p>
 */
public final class FrameRenderableTracker<T>
{
	private final Object lock = new Object();
	private Set<T> current = identitySet();
	private Set<T> previous = identitySet();

	public void record(T renderable)
	{
		if (renderable == null)
		{
			return;
		}

		synchronized (lock)
		{
			current.add(renderable);
		}
	}

	public void advanceFrame()
	{
		synchronized (lock)
		{
			Set<T> oldPrevious = previous;
			previous = current;
			current = oldPrevious;
			current.clear();
		}
	}

	public boolean containsCurrent(T renderable)
	{
		if (renderable == null)
		{
			return false;
		}

		synchronized (lock)
		{
			return current.contains(renderable);
		}
	}

	public boolean containsPrevious(T renderable)
	{
		if (renderable == null)
		{
			return false;
		}

		synchronized (lock)
		{
			return previous.contains(renderable);
		}
	}

	public Set<T> previousSnapshot()
	{
		synchronized (lock)
		{
			Set<T> snapshot = identitySet();
			snapshot.addAll(previous);
			return snapshot;
		}
	}

	private static <T> Set<T> identitySet()
	{
		return Collections.newSetFromMap(new IdentityHashMap<>());
	}
}
