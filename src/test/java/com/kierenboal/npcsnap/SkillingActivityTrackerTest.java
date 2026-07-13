package com.kierenboal.npcsnap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import net.runelite.api.Skill;
import org.junit.Test;

public class SkillingActivityTrackerTest
{
	@Test
	public void firstObservedXpSeedsWithoutActivating()
	{
		SkillingActivityTracker tracker = new SkillingActivityTracker();

		boolean activated = tracker.recordXp(Skill.MINING, 100, 1_000L, 10_000L);

		assertFalse(activated);
		assertTrue(tracker.getRenderableSkills(1_000L, 2_500L).isEmpty());
	}

	@Test
	public void xpIncreaseActivatesTrackedSkill()
	{
		SkillingActivityTracker tracker = new SkillingActivityTracker();
		tracker.recordXp(Skill.MINING, 100, 1_000L, 10_000L);

		boolean activated = tracker.recordXp(Skill.MINING, 125, 2_000L, 10_000L);

		assertTrue(activated);
		assertEquals(List.of(Skill.MINING), tracker.getRenderableSkills(2_000L, 2_500L));
	}

	@Test
	public void repeatedXpRefreshesExpiry()
	{
		SkillingActivityTracker tracker = new SkillingActivityTracker();
		tracker.recordXp(Skill.WOODCUTTING, 100, 500L, 10_000L);
		tracker.recordXp(Skill.WOODCUTTING, 125, 1_000L, 10_000L);
		tracker.recordXp(Skill.WOODCUTTING, 150, 9_000L, 10_000L);

		assertEquals(List.of(Skill.WOODCUTTING), tracker.getRenderableSkills(18_999L, 2_500L));
		assertEquals(List.of(Skill.WOODCUTTING), tracker.getRenderableSkills(19_000L, 2_500L));
	}

	@Test
	public void expiredSkillsAreRemoved()
	{
		SkillingActivityTracker tracker = new SkillingActivityTracker();
		tracker.recordXp(Skill.FISHING, 100, 500L, 10_000L);
		tracker.recordXp(Skill.FISHING, 125, 1_000L, 10_000L);

		assertEquals(List.of(Skill.FISHING), tracker.getRenderableSkills(11_000L, 2_500L));
		assertTrue(tracker.getRenderableSkills(13_500L, 2_500L).isEmpty());
	}

	@Test
	public void combatAndOmittedSkillsAreIgnored()
	{
		SkillingActivityTracker tracker = new SkillingActivityTracker();
		tracker.recordXp(Skill.ATTACK, 100, 500L, 10_000L);
		tracker.recordXp(Skill.PRAYER, 100, 500L, 10_000L);
		tracker.recordXp(Skill.SLAYER, 100, 500L, 10_000L);

		assertFalse(tracker.recordXp(Skill.ATTACK, 125, 1_000L, 10_000L));
		assertFalse(tracker.recordXp(Skill.PRAYER, 125, 1_000L, 10_000L));
		assertFalse(tracker.recordXp(Skill.SLAYER, 125, 1_000L, 10_000L));
		assertTrue(tracker.getRenderableSkills(1_000L, 2_500L).isEmpty());
	}

	@Test
	public void multipleNonCombatSkillsRemainActiveTogether()
	{
		SkillingActivityTracker tracker = new SkillingActivityTracker();
		tracker.recordXp(Skill.COOKING, 100, 500L, 10_000L);
		tracker.recordXp(Skill.FARMING, 100, 500L, 10_000L);
		tracker.recordXp(Skill.COOKING, 125, 1_000L, 10_000L);
		tracker.recordXp(Skill.FARMING, 125, 2_000L, 10_000L);

		assertEquals(List.of(Skill.COOKING, Skill.FARMING), tracker.getRenderableSkills(2_000L, 2_500L));
	}

	@Test
	public void seededXpAllowsFirstObservedGainToActivate()
	{
		SkillingActivityTracker tracker = new SkillingActivityTracker();
		tracker.seedXp(Skill.MINING, 100);

		boolean activated = tracker.recordXp(Skill.MINING, 125, 2_000L, 10_000L);

		assertTrue(activated);
		assertEquals(List.of(Skill.MINING), tracker.getRenderableSkills(2_000L, 2_500L));
		assertEquals(2_000L, tracker.getVisibleFromMillis(Skill.MINING));
	}

	@Test
	public void refreshWithinActiveWindowKeepsOriginalFadeInStart()
	{
		SkillingActivityTracker tracker = new SkillingActivityTracker();
		tracker.seedXp(Skill.WOODCUTTING, 100);
		tracker.recordXp(Skill.WOODCUTTING, 125, 1_000L, 10_000L);
		tracker.recordXp(Skill.WOODCUTTING, 150, 5_000L, 10_000L);

		assertEquals(1_000L, tracker.getVisibleFromMillis(Skill.WOODCUTTING));
		assertEquals(15_000L, tracker.getActiveUntilMillis(Skill.WOODCUTTING));
	}

	@Test
	public void zeroTimeoutExpiresAtTheEventTime()
	{
		SkillingActivityTracker tracker = new SkillingActivityTracker();
		tracker.seedXp(Skill.MINING, 100);

		assertTrue(tracker.recordXp(Skill.MINING, 125, 2_000L, -1L));
		assertTrue(tracker.getRenderableSkills(2_000L, 0L).isEmpty());
	}

	@Test
	public void clearRemovesXpSeedsAndVisibleState()
	{
		SkillingActivityTracker tracker = new SkillingActivityTracker();
		tracker.seedXp(Skill.MINING, 100);
		tracker.recordXp(Skill.MINING, 125, 2_000L, 10_000L);

		tracker.clear();

		assertFalse(tracker.isTrackedXpIncrease(Skill.MINING, 150));
		assertEquals(0L, tracker.getVisibleFromMillis(Skill.MINING));
		assertEquals(0L, tracker.getActiveUntilMillis(Skill.MINING));
		assertTrue(tracker.getRenderableSkills(2_000L, 1_000L).isEmpty());
	}
}
