package com.kierenboal.npcsnap.targeting;

/** The built-in groups of world objects a player can choose to turn into sprites. */
public enum SceneryCategory
{
	TREE,
	ROCK,
	PLANT,
	FIRE,
	LOG,
	OTHER,
	// Objects that must never be converted by a render-target setting.
	EXCLUDED
}
