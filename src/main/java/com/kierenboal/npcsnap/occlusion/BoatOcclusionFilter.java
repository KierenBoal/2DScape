package com.kierenboal.npcsnap.occlusion;

import net.runelite.api.gameval.ObjectID;

/** Restricts boat occlusion to the hull and its trim/gunports. */
final class BoatOcclusionFilter
{
	// Keep detail collection implemented for future work, but bypass its model and
	// fallback-hull processing until facilities and sails produce reliable masks.
	static final boolean INCLUDE_BOAT_DETAILS = false;

	private BoatOcclusionFilter() { }

	static boolean shouldCollect(int id)
	{
		return INCLUDE_BOAT_DETAILS
			|| between(id, ObjectID.SAILING_BOAT_HULL_KANDARIN_1X3_WOOD, ObjectID.SAILING_BOAT_HULL_COLOSSAL01)
			|| between(id, ObjectID.SAILING_BOAT_SKIFF_TRIM_WOODEN, ObjectID.SAILING_BOAT_SLOOP_TRIM_SANDY)
			|| between(id, ObjectID.SAILING_BOAT_HULL_GUNPORTS_KANDARIN_3X8_PORT_MIDSHIPS_DOWN_WOOD,
				ObjectID.SAILING_BOAT_HULL_GUNPORTS_KANDARIN_3X8_PORT_STERN_DOWN_WOOD)
			|| between(id, ObjectID.SAILING_BOAT_HULL_GUNPORTS_KANDARIN_3X8_PORT_MIDSHIPS_DOWN_PIRATE,
				ObjectID.SAILING_BOAT_HULL_KANDARIN_3X8_PIRATE)
			|| between(id, ObjectID.SAILING_BOAT_HULL_GUNPORTS_KANDARIN_3X8_PORT_STERN_UP_WOOD,
				ObjectID.SAILING_BOAT_HULL_GUNPORTS_KANDARIN_3X8_STARBOARD_STERN_UP_WOOD)
			|| between(id, ObjectID.SAILING_BOAT_HULL_GUNPORTS_KANDARIN_3X8_PORT_MIDSHIPS_DOWN_OAK,
				ObjectID.SAILING_BOAT_HULL_GUNPORTS_KANDARIN_3X8_STARBOARD_MIDSHIPS_UP_OAK)
			|| id == ObjectID.SAILING_BOAT_HULL_GUNPORTS_KANDARIN_3X8_STARBOARD_STERN_DOWN_OAK
			|| between(id, ObjectID.SAILING_BOAT_HULL_GUNPORTS_KANDARIN_3X8_STARBOARD_STERN_UP_OAK,
				ObjectID.SAILING_BOAT_HULL_GUNPORTS_KANDARIN_3X8_PORT_MIDSHIPS_UP_TEAK)
			|| between(id, ObjectID.SAILING_BOAT_HULL_GUNPORTS_KANDARIN_3X8_PORT_STERN_DOWN_TEAK,
				ObjectID.SAILING_BOAT_HULL_GUNPORTS_KANDARIN_3X8_STARBOARD_STERN_UP_ROSEWOOD)
			|| id == ObjectID.SAILING_BOAT_SLOOP_TRIM_PIRATE;
	}

	private static boolean between(int id, int first, int last)
	{
		return id >= first && id <= last;
	}
}
