package com.kierenboal.npcsnap.targeting;

import net.runelite.api.Renderable;
import net.runelite.api.coords.LocalPoint;

public final class ObjectRenderablePart
{
	public final Renderable renderable;
	public final LocalPoint localPoint;
	public final int plane;
	public final int worldHeight;

	public ObjectRenderablePart(Renderable renderable, LocalPoint localPoint, int plane)
	{
		this(renderable, localPoint, plane, Integer.MIN_VALUE);
	}

	public ObjectRenderablePart(Renderable renderable, LocalPoint localPoint, int plane, int worldHeight)
	{
		this.renderable = renderable;
		this.localPoint = localPoint;
		this.plane = plane;
		this.worldHeight = worldHeight;
	}
}

