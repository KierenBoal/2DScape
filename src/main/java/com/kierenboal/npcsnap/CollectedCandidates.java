package com.kierenboal.npcsnap;

import java.util.List;
import java.util.Map;
import net.runelite.api.Actor;

final class CollectedCandidates
{
	final List<BillboardTarget> candidates;
	final Map<OccupiedTileKey, Actor> topActorsByTile;
	final Map<OccupiedTileKey, List<Actor>> actorsByTile;

	CollectedCandidates(
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

