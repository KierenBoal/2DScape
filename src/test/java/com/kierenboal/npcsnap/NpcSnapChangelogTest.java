package com.kierenboal.npcsnap;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class NpcSnapChangelogTest
{
	// Exercise announcement behavior without duplicating the changing release copy.
	private static final List<NpcSnapChangelog.Release> HISTORY = List.of(
		new NpcSnapChangelog.Release("1.0.1", "First change; Second change"),
		new NpcSnapChangelog.Release("1.0.10", "Tenth patch"),
		new NpcSnapChangelog.Release("1.1.0", "Minor release"),
		new NpcSnapChangelog.Release("2.0.0", "Major release"));
	private static final List<String> ALL_MISSED_MESSAGES = List.of(
		"2DScape has updated from v1.0.0 to v2.0.0",
		"- First change (v1.0.1)", "- Second change (v1.0.1)", "- Tenth patch (v1.0.10)",
		"- Minor release (v1.1.0)", "- Major release (v2.0.0)");
	private static final List<String> FROM_1_0_2_MESSAGES = List.of(
		"2DScape has updated from v1.0.2 to v2.0.0",
		"- Tenth patch (v1.0.10)", "- Minor release (v1.1.0)", "- Major release (v2.0.0)");
	private static final List<String> LATEST_MESSAGES = List.of(
		"2DScape has updated from v1.1.0 to v2.0.0", "- Major release (v2.0.0)");

	@Test
	public void missingVersionAnnouncesFromBaselineBeforeSaving()
	{
		Memory memory = new Memory(null);
		memory.check(changelog(), true);
		assertEquals(ALL_MISSED_MESSAGES, memory.messages);
		assertEquals("2.0.0", memory.version);
		assertEquals(expectedAnnounceEvents(ALL_MISSED_MESSAGES), memory.events);
	}

	@Test
	public void olderVersionAnnouncesMissedChangesWithVersions()
	{
		for (String[] scenario : new String[][] {{"1.0.0", "all"}, {"1.0.2", "from1.0.2"}})
		{
			Memory memory = new Memory(scenario[0]);
			memory.check(changelog(), true);
			assertEquals("all".equals(scenario[1]) ? ALL_MISSED_MESSAGES : FROM_1_0_2_MESSAGES, memory.messages);
			assertEquals("2.0.0", memory.version);
		}
	}

	@Test
	public void nearbyVersionsAnnounceOnlyMissedReleasesUsingNumericVersionOrder()
	{
		Memory twoBehind = new Memory("1.0.10");
		twoBehind.check(changelog(), true);
		assertEquals(List.of("2DScape has updated from v1.0.10 to v2.0.0",
			"- Minor release (v1.1.0)", "- Major release (v2.0.0)"), twoBehind.messages);
		Memory oneBehind = new Memory("1.1.0");
		oneBehind.check(changelog(), true);
		assertEquals(LATEST_MESSAGES, oneBehind.messages);
		Memory numericOrder = new Memory("1.0.2");
		numericOrder.check(new NpcSnapChangelog(HISTORY.subList(0, 2)), true);
		assertEquals(List.of("2DScape has updated from v1.0.2 to v1.0.10", "- Tenth patch (v1.0.10)"),
			numericOrder.messages);
	}

	@Test
	public void currentOrNewerVersionDoesNotAnnounceOrMoveCacheBackwards()
	{
		for (String version : List.of("2.0.0", "2.1.0", "3.0.0"))
		{
			Memory memory = new Memory(version);
			memory.check(changelog(), true);
			assertEquals(List.of("read"), memory.events);
			assertEquals(version, memory.version);
		}
	}

	@Test
	public void invalidSavedVersionsAreTreatedAsFirstInstall()
	{
		for (String version : List.of("", "oops", "1.0", "-1.0.1", "1.0.2-beta", "999999999999.0.0"))
		{
			Memory memory = new Memory(version);
			memory.check(changelog(), true);
			assertEquals(ALL_MISSED_MESSAGES, memory.messages);
			assertEquals("2.0.0", memory.version);
		}
	}

	@Test
	public void waitsForLoginAndAvoidsRepeatsAcrossTicksLoginsAndRestarts()
	{
		Memory memory = new Memory(null);
		NpcSnapChangelog changelog = changelog();
		memory.check(changelog, false);
		assertTrue(memory.events.isEmpty());
		memory.check(changelog, true);
		memory.check(changelog, true);
		assertEquals(expectedAnnounceEvents(ALL_MISSED_MESSAGES), memory.events);
		changelog.requestCheck();
		memory.check(changelog, false);
		memory.check(changelog, true);
		memory.check(changelog(), true);
		assertEquals(ALL_MISSED_MESSAGES, memory.messages);
		List<String> expectedEvents = expectedAnnounceEvents(ALL_MISSED_MESSAGES);
		expectedEvents.add("read");
		expectedEvents.add("read");
		assertEquals(expectedEvents, memory.events);
	}

	@Test
	public void profileChangeReadsTheNewProfilesVersion()
	{
		NpcSnapChangelog changelog = changelog();
		new Memory("2.0.0").check(changelog, true);
		changelog.requestCheck();
		Memory newProfile = new Memory("1.0.0");
		newProfile.check(changelog, true);
		assertEquals(ALL_MISSED_MESSAGES, newProfile.messages);
		assertEquals("2.0.0", newProfile.version);
	}

	@Test
	public void failureToSendDoesNotAdvanceVersionAndCanBeRetried()
	{
		NpcSnapChangelog changelog = changelog();
		Memory memory = new Memory("1.0.0");
		assertThrows(IllegalStateException.class, () -> changelog.check(true, () -> memory.version, message -> {
			throw new IllegalStateException("chat unavailable");
		}, version -> memory.version = version));
		assertEquals("1.0.0", memory.version);
		memory.check(changelog, true);
		assertEquals(ALL_MISSED_MESSAGES, memory.messages);
		assertEquals("2.0.0", memory.version);
	}

	@Test
	public void semicolonsCreateSeparateBulletsAndTrimOrSkipEmptyEntries()
	{
		Memory memory = new Memory("1.0.0");
		memory.check(new NpcSnapChangelog(List.of(new NpcSnapChangelog.Release("1.0.1", " a; b ;; ;c; d; "))), true);
		assertEquals(List.of("2DScape has updated from v1.0.0 to v1.0.1", "- a (v1.0.1)",
			"- b (v1.0.1)", "- c (v1.0.1)", "- d (v1.0.1)"), memory.messages);
		assertEquals("1.0.1", memory.version);
	}

	@Test
	public void emptyAndMultilineChangesAreRejected()
	{
		for (String change : new String[] {null, "", " ; ; ", "First line\nSecond line", "First line\rSecond line"})
		{
			assertThrows(IllegalArgumentException.class, () -> new NpcSnapChangelog.Release("1.0.1", change));
		}
	}

	@Test
	public void outOfOrderHistoryIsRejected()
	{
		assertThrows(IllegalArgumentException.class, () -> new NpcSnapChangelog(List.of(
			new NpcSnapChangelog.Release("1.0.10", "Later"), new NpcSnapChangelog.Release("1.0.2", "Earlier"))));
	}

	@Test
	public void runtimeVersionMatchesPluginMetadata() throws Exception
	{
		Properties properties = new Properties();
		try (InputStream input = Files.newInputStream(Paths.get("runelite-plugin.properties")))
		{
			properties.load(input);
		}
		assertEquals(properties.getProperty("version"), NpcSnapChangelog.CURRENT_VERSION);
	}

	private static NpcSnapChangelog changelog()
	{
		return new NpcSnapChangelog(HISTORY);
	}

	private static List<String> expectedAnnounceEvents(List<String> messages)
	{
		List<String> events = new ArrayList<>();
		events.add("read");
		for (int i = 0; i < messages.size(); i++)
		{
			events.add("message");
		}
		events.add("save");
		return events;
	}

	private static final class Memory
	{
		private String version;
		private final List<String> messages = new ArrayList<>();
		private final List<String> events = new ArrayList<>();

		private Memory(String version)
		{
			this.version = version;
		}

		private void check(NpcSnapChangelog changelog, boolean loggedIn)
		{
			changelog.check(loggedIn, () -> {
				events.add("read");
				return version;
			}, message -> {
				events.add("message");
				messages.add(message);
			}, savedVersion -> {
				events.add("save");
				version = savedVersion;
			});
		}
	}
}
