package com.kierenboal.npcsnap.features;

import java.util.List;
import net.runelite.api.Skill;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SkillingBubbleAnimationTest
{
	@Test
	public void skillFadesInStaysOpaqueAndFadesOut()
	{
		SkillingBubbleAnimation animation = activeAnimation(Skill.MINING, 1_000L, 10_000L);

		assertEquals(0f, animation.skillAlpha(Skill.MINING, 1_000L), 0f);
		assertEquals(0.5f, animation.skillAlpha(Skill.MINING, 1_250L), 0.001f);
		assertEquals(1f, animation.skillAlpha(Skill.MINING, 1_500L), 0f);
		assertEquals(1f, animation.skillAlpha(Skill.MINING, 11_000L), 0f);
		assertEquals(0.5f, animation.skillAlpha(Skill.MINING, 12_250L), 0.001f);
		assertEquals(0f, animation.skillAlpha(Skill.MINING, 13_500L), 0f);
	}

	@Test
	public void missingSkillStateIsTransparent()
	{
		SkillingBubbleAnimation animation = new SkillingBubbleAnimation(new SkillingActivityTracker());

		assertEquals(0f, animation.skillAlpha(Skill.MINING, 1_000L), 0f);
		assertEquals(0f, animation.bubbleAlpha(List.of(), 1_000L), 0f);
		assertEquals(1f, animation.introProgress(List.of(), 1_000L), 0f);
	}

	@Test
	public void bubbleUsesMostVisibleSkillAndSlowestIntro()
	{
		SkillingActivityTracker tracker = new SkillingActivityTracker();
		activate(tracker, Skill.MINING, 1_000L, 10_000L);
		activate(tracker, Skill.FISHING, 1_200L, 10_000L);
		SkillingBubbleAnimation animation = new SkillingBubbleAnimation(tracker);

		assertEquals(1f, animation.bubbleAlpha(List.of(Skill.MINING, Skill.FISHING), 1_500L), 0.001f);
		assertEquals(0.6f, animation.introProgress(List.of(Skill.MINING, Skill.FISHING), 1_500L), 0.001f);
	}

	private static SkillingBubbleAnimation activeAnimation(Skill skill, long start, long timeout)
	{
		SkillingActivityTracker tracker = new SkillingActivityTracker();
		activate(tracker, skill, start, timeout);
		return new SkillingBubbleAnimation(tracker);
	}

	private static void activate(SkillingActivityTracker tracker, Skill skill, long start, long timeout)
	{
		tracker.seedXp(skill, 100);
		tracker.recordXp(skill, 101, start, timeout);
	}
}
