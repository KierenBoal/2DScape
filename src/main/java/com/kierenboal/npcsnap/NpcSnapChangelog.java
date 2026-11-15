package com.kierenboal.npcsnap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Local release history, announced once per RuneLite configuration profile. */
final class NpcSnapChangelog
{
	static final String LAST_SEEN_VERSION_KEY = "lastSeenVersion";
	static final String BASELINE_VERSION = "1.0.0";
	private static final int MAX_RELEASES = 5;
	private static final int MAX_ANNOUNCEMENT_LINES = 10;
	private static final List<Release> RELEASES = List.of(
		new Release("1.0.1", "World object exports fixed; boat hiding; XP bubble scaling; occlusion edge fixes"),
		new Release("1.0.2", "Improved rotation calculation with camera location; Version system added; Projectile arc and height rendering fixed; Billboarded world objects no longer clip other sprites; World object occlusion and overlapping sprite ordering stabilized; Optional actor world-plane projection fits sprites to live bounds; Actor world-plane sprites now face the camera on yaw"),
		new Release("1.0.3", "Improved click accuracy mode in Rendering config (less RSC, better accuracy though); Retro hitsplats now respect damage types and max hits."),
		new Release("1.0.4", "Land/boat/animation occlusion fixes; Skilling XP bubble improvements; Unified frame rates across players/projectiles/objects/etc; Improved transparency blending"),
		new Release("1.0.5", "Reduced changelog spam; Roof and hidden static scenery now occlude correctly; Camera clipping occlusion depth now match real rendering"),
		new Release("1.1.0", "Backend RetroNPC Swapper support (pending compatibility)"),
		new Release("1.1.1", "Retro NPC Swapper support"),
		new Release("1.1.2", "Config cleanup; Outlines improved & plugin override; RSC chat colours fixes; Optional wildy/PvP toggle"),
		new Release("1.2.0", "Added new shadow settings (legacy option avaliable);Fixed ground clipping visuals;Improved click accuracy mode improvements"),
		new Release("1.2.1", "Code reduction and deprecated unused featuers"));
	static final String CURRENT_VERSION = RELEASES.get(RELEASES.size() - 1).version;

	private final List<Release> releases;
	private final String currentVersion;
	private volatile boolean pending = true;

	NpcSnapChangelog()
	{
		this(RELEASES);
	}

	NpcSnapChangelog(List<Release> releases)
	{
		this.releases = List.copyOf(releases);
		String previous = BASELINE_VERSION;
		for (Release release : this.releases)
		{
			if (compareVersions(release.version, previous) <= 0)
			{
				throw new IllegalArgumentException("Changelog releases must increase in version order");
			}
			previous = release.version;
		}
		currentVersion = previous;
	}

	void requestCheck()
	{
		pending = true;
	}

	void check(boolean loggedIn, Supplier<String> readVersion, Consumer<String> sendMessage,
		Consumer<String> saveVersion)
	{
		check(loggedIn, false, readVersion, sendMessage, saveVersion);
	}

	void check(boolean loggedIn, boolean developerMode, Supplier<String> readVersion, Consumer<String> sendMessage,
		Consumer<String> saveVersion)
	{
		if (!pending || !loggedIn)
		{
			return;
		}
		if (developerMode)
		{
			if (!releases.isEmpty())
			{
				announce(previousReleaseVersion(),
					Collections.singletonList(releases.get(releases.size() - 1)), sendMessage);
			}
			pending = false;
			return;
		}
		String seenVersion = readVersion.get();
		try
		{
			parseVersion(seenVersion);
		}
		catch (IllegalArgumentException ex)
		{
			seenVersion = BASELINE_VERSION;
		}
		// Never move the cache backwards when an older development build is run.
		if (compareVersions(seenVersion, currentVersion) < 0)
		{
			List<Release> missedReleases = new ArrayList<>();
			for (Release release : releases)
			{
				if (compareVersions(release.version, seenVersion) > 0)
				{
					missedReleases.add(release);
				}
			}
			if (!missedReleases.isEmpty())
			{
				announce(seenVersion, missedReleases, sendMessage);
			}
			// Advance only after the changelog has been sent to chat.
			saveVersion.accept(currentVersion);
		}
		pending = false;
	}

	private String previousReleaseVersion()
	{
		return releases.size() < 2 ? BASELINE_VERSION : releases.get(releases.size() - 2).version;
	}

	private static void announce(String fromVersion, List<Release> releases, Consumer<String> sendMessage)
	{
		int firstRelease = Math.max(0, releases.size() - MAX_RELEASES);
		List<String> changes = new ArrayList<>();
		for (int i = firstRelease; i < releases.size(); i++)
		{
			Release release = releases.get(i);
			for (String change : release.changes)
			{
				changes.add("- " + change + " (v" + release.version + ")");
			}
		}
		int firstChange = Math.max(0, changes.size() - (MAX_ANNOUNCEMENT_LINES - 1));
		sendMessage.accept("2DScape has updated from v" + fromVersion + " to v"
			+ releases.get(releases.size() - 1).version);
		for (int i = firstChange; i < changes.size(); i++)
		{
			sendMessage.accept(changes.get(i));
		}
	}

	static int compareVersions(String left, String right)
	{
		int[] leftParts = parseVersion(left);
		int[] rightParts = parseVersion(right);
		for (int i = 0; i < leftParts.length; i++)
		{
			int comparison = Integer.compare(leftParts[i], rightParts[i]);
			if (comparison != 0)
			{
				return comparison;
			}
		}
		return 0;
	}

	private static int[] parseVersion(String version)
	{
		if (version == null || !version.matches("[0-9]+\\.[0-9]+\\.[0-9]+"))
		{
			throw new IllegalArgumentException("Expected a major.minor.patch version");
		}
		return Arrays.stream(version.split("\\.")).mapToInt(Integer::parseInt).toArray();
	}

	static final class Release
	{
		final String version;
		final List<String> changes;

		Release(String version, String change)
		{
			parseVersion(version);
			if (change == null || change.isBlank()
				|| change.indexOf('\n') >= 0 || change.indexOf('\r') >= 0)
			{
				throw new IllegalArgumentException("Changelog text must contain semicolon-separated changes on one line");
			}
			List<String> entries = Arrays.stream(change.split(";"))
				.map(String::trim)
				.filter(entry -> !entry.isEmpty())
				.collect(Collectors.toList());
			if (entries.isEmpty())
			{
				throw new IllegalArgumentException("Changelog must contain at least one change");
			}
			this.version = version;
			this.changes = List.copyOf(entries);
		}
	}
}
