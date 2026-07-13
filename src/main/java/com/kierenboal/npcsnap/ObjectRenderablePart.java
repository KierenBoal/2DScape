package com.kierenboal.npcsnap;

import net.runelite.api.Renderable;
import net.runelite.api.coords.LocalPoint;

final class ObjectRenderablePart
{
	final Renderable renderable;
	final LocalPoint localPoint;
	final int plane;

	ObjectRenderablePart(Renderable renderable, LocalPoint localPoint, int plane)
	{
		this.renderable = renderable;
		this.localPoint = localPoint;
		this.plane = plane;
	}
}

