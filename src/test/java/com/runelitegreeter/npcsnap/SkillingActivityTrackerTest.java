package com.runelitegreeter.npcsnap;

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
		assertTrue(tracker.getActiveSkills(1_000L).isEmpty());
	}

	@Test
	public void xpIncreaseActivatesTrackedSkill()
	{
		SkillingActivityTracker tracker = new SkillingActivityTracker();
		tracker.recordXp(Skill.MINING, 100, 1_000L, 10_000L);

		boolean activated = tracker.recordXp(Skill.MINING, 125, 2_000L, 10_000L);

		assertTrue(activated);
		assertEquals(List.of(Skill.MINING), tracker.getActiveSkills(2_000L));
	}

	@Test
	public void repeatedXpRefreshesExpiry()
	{
		SkillingActivityTracker tracker = new SkillingActivityTracker();
		tracker.recordXp(Skill.WOODCUTTING, 100, 500L, 10_000L);
		tracker.recordXp(Skill.WOODCUTTING, 125, 1_000L, 10_000L);
		tracker.recordXp(Skill.WOODCUTTING, 150, 9_000L, 10_000L);

		assertEquals(List.of(Skill.WOODCUTTING), tracker.getActiveSkills(18_999L));
		assertTrue(tracker.getActiveSkills(19_000L).isEmpty());
	}

	@Test
	public void expiredSkillsAreRemoved()
	{
		SkillingActivityTracker tracker = new SkillingActivityTracker();
		tracker.recordXp(Skill.FISHING, 100, 500L, 10_000L);
		tracker.recordXp(Skill.FISHING, 125, 1_000L, 10_000L);

		assertTrue(tracker.getActiveSkills(11_000L).isEmpty());
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
		assertTrue(tracker.getActiveSkills(1_000L).isEmpty());
	}

	@Test
	public void multipleNonCombatSkillsRemainActiveTogether()
	{
		SkillingActivityTracker tracker = new SkillingActivityTracker();
		tracker.recordXp(Skill.COOKING, 100, 500L, 10_000L);
		tracker.recordXp(Skill.FARMING, 100, 500L, 10_000L);
		tracker.recordXp(Skill.COOKING, 125, 1_000L, 10_000L);
		tracker.recordXp(Skill.FARMING, 125, 2_000L, 10_000L);

		assertEquals(List.of(Skill.COOKING, Skill.FARMING), tracker.getActiveSkills(2_000L));
	}
}
