package com.kierenboal.npcsnap;

import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.StatChanged;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LoginXpDropGuardTest
{
	@Test
	public void ignoresTrackedLoginBurstOnlyOnItsFirstTick()
	{
		SkillingActivityTracker tracker = new SkillingActivityTracker();
		tracker.seedXp(Skill.WOODCUTTING, 100);
		LoginXpDropGuard guard = new LoginXpDropGuard();
		guard.onLoggedIn();

		assertTrue(guard.shouldIgnore(change(Skill.WOODCUTTING, 110), GameState.LOGGED_IN, tracker));
		assertTrue(guard.shouldIgnore(change(Skill.WOODCUTTING, 120), GameState.LOGGED_IN, tracker));
		guard.advanceTick();
		assertFalse(guard.shouldIgnore(change(Skill.WOODCUTTING, 130), GameState.LOGGED_IN, tracker));
	}

	@Test
	public void doesNotIgnoreUntrackedNonIncreaseLoggedOutOrLateDrops()
	{
		SkillingActivityTracker tracker = new SkillingActivityTracker();
		tracker.seedXp(Skill.WOODCUTTING, 100);
		LoginXpDropGuard guard = new LoginXpDropGuard();
		guard.onLoggedIn();

		assertFalse(guard.shouldIgnore(change(Skill.WOODCUTTING, 100), GameState.LOGGED_IN, tracker));
		assertFalse(guard.shouldIgnore(change(Skill.ATTACK, 110), GameState.LOGGED_IN, tracker));
		assertFalse(guard.shouldIgnore(change(Skill.WOODCUTTING, 110), GameState.LOGIN_SCREEN, tracker));
		for (int i = 0; i < 11; i++)
		{
			guard.advanceTick();
		}
		assertFalse(guard.shouldIgnore(change(Skill.WOODCUTTING, 110), GameState.LOGGED_IN, tracker));
	}

	@Test
	public void logoutCancelsGracePeriod()
	{
		SkillingActivityTracker tracker = new SkillingActivityTracker();
		tracker.seedXp(Skill.WOODCUTTING, 100);
		LoginXpDropGuard guard = new LoginXpDropGuard();
		guard.onLoggedIn();
		guard.onLoggedOut();

		assertFalse(guard.shouldIgnore(change(Skill.WOODCUTTING, 110), GameState.LOGGED_IN, tracker));
	}

	private static StatChanged change(Skill skill, int xp)
	{
		return new StatChanged(skill, xp, 1, 1);
	}
}
