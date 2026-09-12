package com.kierenboal.npcsnap.features;

import net.runelite.api.Skill;
import net.runelite.api.gameval.SpriteID.Staticons2;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SkillingThoughtBubbleOverlayTest
{
	@Test
	public void sailingUsesTheNamedSailingSkillSprite()
	{
		assertEquals(Staticons2.SAILING, SkillingThoughtBubbleOverlay.skillSpriteId(Skill.SAILING));
	}
}
