package com.runelitegreeter.npcsnap;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;
import net.runelite.api.Skill;

@Singleton
class SkillingActivityTracker
{
	private static final EnumSet<Skill> TRACKED_SKILLS = EnumSet.of(
		Skill.MINING,
		Skill.AGILITY,
		Skill.SMITHING,
		Skill.HERBLORE,
		Skill.FISHING,
		Skill.THIEVING,
		Skill.COOKING,
		Skill.CRAFTING,
		Skill.FIREMAKING,
		Skill.FLETCHING,
		Skill.WOODCUTTING,
		Skill.RUNECRAFT,
		Skill.FARMING,
		Skill.CONSTRUCTION,
		Skill.HUNTER,
		Skill.SAILING
	);

	private final Map<Skill, Integer> lastXp = new EnumMap<>(Skill.class);
	private final Map<Skill, Long> activeUntilMillis = new EnumMap<>(Skill.class);

	boolean recordXp(Skill skill, int xp, long nowMillis, long timeoutMillis)
	{
		if (skill == null)
		{
			return false;
		}

		Integer previousXp = lastXp.put(skill, xp);
		if (previousXp == null || xp <= previousXp || !TRACKED_SKILLS.contains(skill))
		{
			return false;
		}

		activeUntilMillis.put(skill, nowMillis + Math.max(0L, timeoutMillis));
		return true;
	}

	List<Skill> getActiveSkills(long nowMillis)
	{
		List<Skill> activeSkills = new ArrayList<>();
		activeUntilMillis.entrySet().removeIf(entry -> entry.getValue() <= nowMillis);
		for (Skill skill : Skill.values())
		{
			Long expiresAt = activeUntilMillis.get(skill);
			if (expiresAt != null && expiresAt > nowMillis)
			{
				activeSkills.add(skill);
			}
		}

		return activeSkills;
	}

	void clear()
	{
		lastXp.clear();
		activeUntilMillis.clear();
	}
}
