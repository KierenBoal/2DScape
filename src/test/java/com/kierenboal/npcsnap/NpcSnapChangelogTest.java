package com.kierenboal.npcsnap;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class NpcSnapChangelogTest
{
	@Test
	public void missingVersionShowsAllReleasesThenSavesCurrentVersion()
	{
		Memory memory = new Memory(null);
		memory.check(new NpcSnapChangelog(), true);
		assertEquals(List.of(
			"2DScape (v1.0.1):", "* World object exports fixed", "* boat hiding", "* XP bubble scaling", "* occlusion edge fixes",
			"2DScape (v1.0.2):", "* Improved rotation calculation with camera location", "* Version system added",
			"* Projectile arc and height rendering fixed", "* Billboarded world objects no longer clip other sprites", "* World object occlusion and overlapping sprite ordering stabilized", "* Optional actor world-plane projection fits sprites to live bounds", "* Actor world-plane sprites now face the camera on yaw", "2DScape (v1.0.3):", "* Improved click accuracy mode in Rendering config: better clickboxes, less RSC", "* Retro hitsplats now respect damage types, colours, shapes and max hits", "* Undocumented hitsplats use stable pastel shapes without adjacent repeats", "* Retro hitsplats persist for at least two game ticks"), memory.messages);
		assertEquals("1.0.3", memory.version);
		assertEquals(List.of("read", "message", "message", "message", "message", "message", "message", "message", "message", "message", "message", "message", "message", "message", "message", "message", "message", "message", "message", "save"), memory.events);
		for (String message : memory.messages)
		{
			if (message.startsWith("* "))
			{
				assertTrue(message.substring(2).length() <= 80);
			}
		}
	}

	@Test
	public void storedBaselineShowsAllAndPreviousReleaseShowsOnlyTheDelta()
	{
		Memory baseline = new Memory("1.0.0");
		baseline.check(new NpcSnapChangelog(), true);
		assertEquals(18, baseline.messages.size());
		Memory previous = new Memory("1.0.1");
		previous.check(new NpcSnapChangelog(), true);
		assertEquals(13, previous.messages.size());
		assertEquals("2DScape (v1.0.2):", previous.messages.get(0));
	}

	@Test
	public void currentOrNewerVersionDoesNotAnnounceOrMoveCacheBackwards()
	{
		for (String version : List.of("1.0.3", "1.1.2", "2.0.0"))
		{
			Memory memory = new Memory(version);
			memory.check(new NpcSnapChangelog(), true);
			assertEquals(List.of("read"), memory.events);
			assertEquals(version, memory.version);
		}
	}

	@Test
	public void invalidSavedVersionsUseTheBaseline()
	{
		for (String version : List.of("", "oops", "1.0", "-1.0.1", "1.0.2-beta", "999999999999.0.0"))
		{
			Memory memory = new Memory(version);
			memory.check(new NpcSnapChangelog(), true);
			assertEquals(18, memory.messages.size());
			assertEquals("1.0.3", memory.version);
		}
	}

	@Test
	public void waitsForLoginAndAvoidsRepeatsAcrossTicksLoginsAndRestarts()
	{
		Memory memory = new Memory(null);
		NpcSnapChangelog changelog = new NpcSnapChangelog();
		memory.check(changelog, false);
		assertTrue(memory.events.isEmpty());
		memory.check(changelog, true);
		memory.check(changelog, true);
		assertEquals(List.of("read", "message", "message", "message", "message", "message", "message", "message", "message", "message", "message", "message", "message", "message", "message", "message", "message", "message", "message", "save"), memory.events);
		changelog.requestCheck();
		memory.check(changelog, false);
		memory.check(changelog, true);
		memory.check(new NpcSnapChangelog(), true);
		assertEquals(18, memory.messages.size());
		assertEquals(List.of("read", "message", "message", "message", "message", "message", "message", "message", "message", "message", "message", "message", "message", "message", "message", "message", "message", "message", "message", "save", "read", "read"), memory.events);
	}

	@Test
	public void profileChangeReadsTheNewProfilesVersion()
	{
		NpcSnapChangelog changelog = new NpcSnapChangelog();
		new Memory("1.0.3").check(changelog, true);
		changelog.requestCheck();
		Memory newProfile = new Memory("1.0.0");
		newProfile.check(changelog, true);
		assertEquals(18, newProfile.messages.size());
		assertEquals("1.0.3", newProfile.version);
	}

	@Test
	public void skippedReleasesAreAnnouncedInOrderAcrossMinorAndMajorVersions()
	{
		NpcSnapChangelog changelog = new NpcSnapChangelog(List.of(
			new NpcSnapChangelog.Release("1.0.1", "First change"),
			new NpcSnapChangelog.Release("1.0.10", "Tenth patch"),
			new NpcSnapChangelog.Release("1.1.0", "Minor release"),
			new NpcSnapChangelog.Release("1.1.2", "Latest release")));
		Memory memory = new Memory("1.0.0");
		memory.check(changelog, true);
		assertEquals(List.of("2DScape (v1.0.1):", "* First change", "2DScape (v1.0.10):", "* Tenth patch",
			"2DScape (v1.1.0):", "* Minor release", "2DScape (v1.1.2):", "* Latest release"), memory.messages);
		assertEquals("1.1.2", memory.version);
		assertTrue(NpcSnapChangelog.compareVersions("1.0.10", "1.0.2") > 0);
		assertTrue(NpcSnapChangelog.compareVersions("2.0.0", "1.99.99") > 0);
	}

	@Test
	public void failureToSendDoesNotAdvanceVersionAndCanBeRetried()
	{
		NpcSnapChangelog changelog = new NpcSnapChangelog();
		Memory memory = new Memory("1.0.0");
		try
		{
			changelog.check(true, () -> memory.version, message -> {
				throw new IllegalStateException("chat unavailable");
			}, version -> memory.version = version);
			fail("Expected chat failure");
		}
		catch (IllegalStateException expected)
		{
			assertEquals("1.0.0", memory.version);
		}
		memory.check(changelog, true);
		assertEquals(18, memory.messages.size());
		assertEquals("1.0.3", memory.version);
	}

	@Test
	public void eightyCharacterChangeIsAllowed()
	{
		NpcSnapChangelog.Release release = new NpcSnapChangelog.Release("1.0.3", "a".repeat(80) + ";" + "b".repeat(80));
		assertEquals(List.of("a".repeat(80), "b".repeat(80)), release.changes);
	}

	@Test
	public void semicolonsCreateSeparateBulletsAndTrimOrSkipEmptyEntries()
	{
		Memory memory = new Memory("1.0.0");
		memory.check(new NpcSnapChangelog(List.of(new NpcSnapChangelog.Release("1.0.1", " a; b ;; ;c; d; "))), true);
		assertEquals(List.of("2DScape (v1.0.1):", "* a", "* b", "* c", "* d"), memory.messages);
		assertEquals("1.0.1", memory.version);
	}

	@Test
	public void tenChangesAreAllShownEvenWhenTotalTextExceedsEightyCharacters()
	{
		Memory memory = new Memory("1.0.0");
		memory.check(new NpcSnapChangelog(List.of(new NpcSnapChangelog.Release("1.0.1",
			"Fix number 1; Fix number 2; Fix number 3; Fix number 4; Fix number 5; Fix number 6; Fix number 7; Fix number 8; Fix number 9; Fix number 10"))), true);
		assertEquals(List.of("2DScape (v1.0.1):", "* Fix number 1", "* Fix number 2", "* Fix number 3",
			"* Fix number 4", "* Fix number 5", "* Fix number 6", "* Fix number 7", "* Fix number 8",
			"* Fix number 9", "* Fix number 10"), memory.messages);
	}

	@Test(expected = IllegalArgumentException.class)
	public void longerEntryInAListIsRejected()
	{
		new NpcSnapChangelog.Release("1.0.3", "Short change;" + "a".repeat(81));
	}

	@Test(expected = IllegalArgumentException.class)
	public void onlyEmptyEntriesAreRejected()
	{
		new NpcSnapChangelog.Release("1.0.3", " ; ; ");
	}

	@Test(expected = IllegalArgumentException.class)
	public void longerChangeIsRejected()
	{
		new NpcSnapChangelog.Release("1.0.3", "a".repeat(81));
	}

	@Test(expected = IllegalArgumentException.class)
	public void multilineChangeIsRejected()
	{
		new NpcSnapChangelog.Release("1.0.3", "First line\nSecond line");
	}

	@Test(expected = IllegalArgumentException.class)
	public void outOfOrderHistoryIsRejected()
	{
		new NpcSnapChangelog(List.of(new NpcSnapChangelog.Release("1.0.10", "Later"),
			new NpcSnapChangelog.Release("1.0.2", "Earlier")));
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
