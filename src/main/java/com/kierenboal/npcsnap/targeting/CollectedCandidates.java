package com.kierenboal.npcsnap.targeting;

import java.util.List;
import java.util.Map;
import net.runelite.api.Actor;

public final class CollectedCandidates
{
	public final List<BillboardTarget> candidates;
	public final Map<OccupiedTileKey, Actor> topActorsByTile;
	public final Map<OccupiedTileKey, List<Actor>> actorsByTile;

	public CollectedCandidates(
		List<BillboardTarget> candidates,
		Map<OccupiedTileKey, Actor> topActorsByTile,
		Map<OccupiedTileKey, List<Actor>> actorsByTile
	)
	{
		this.candidates = candidates;
		this.topActorsByTile = topActorsByTile;
		this.actorsByTile = actorsByTile;
	}
}

