package com.kierenboal.npcsnap;

import net.runelite.api.GameState;
import net.runelite.api.events.StatChanged;

final class LoginXpDropGuard
{
	private static final int GRACE_TICKS = 10;

	private int gameTick;
	private int graceUntilTick = Integer.MIN_VALUE;
	private int ignoredDropTick = Integer.MIN_VALUE;

	void advanceTick()
	{
		gameTick++;
	}

	void onLoggedIn()
	{
		graceUntilTick = gameTick + GRACE_TICKS;
		ignoredDropTick = Integer.MIN_VALUE;
	}

	void onLoggedOut()
	{
		graceUntilTick = Integer.MIN_VALUE;
		ignoredDropTick = Integer.MIN_VALUE;
	}

	boolean shouldIgnore(StatChanged change, GameState gameState, SkillingActivityTracker tracker)
	{
		if (change == null
			|| gameState != GameState.LOGGED_IN
			|| gameTick > graceUntilTick
			|| !tracker.isTrackedXpIncrease(change.getSkill(), change.getXp()))
		{
			return false;
		}

		if (ignoredDropTick == Integer.MIN_VALUE)
		{
			ignoredDropTick = gameTick;
			return true;
		}

		return ignoredDropTick == gameTick;
	}
}
