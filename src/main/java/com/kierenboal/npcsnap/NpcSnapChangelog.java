package com.kierenboal.npcsnap;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Local release history, announced once per RuneLite configuration profile. */
final class NpcSnapChangelog
{
	static final String LAST_SEEN_VERSION_KEY = "lastSeenVersion";
	static final String BASELINE_VERSION = "1.0.0";
	static final int MAX_CHANGE_LENGTH = 80;
	private static final List<Release> RELEASES = List.of(
		new Release("1.0.1", "World object exports fixed; boat hiding; XP bubble scaling; occlusion edge fixes"),
		new Release("1.0.2", "Improved rotation calculation with camera location; Version system added; Projectile arc and height rendering fixed; Billboarded world objects no longer clip other sprites; World object occlusion and overlapping sprite ordering stabilized; Optional actor world-plane projection fits sprites to live bounds; Actor world-plane sprites now face the camera on yaw"),
		new Release("1.0.3", "Improved click accuracy mode in 'Rendering config' section of config, better clickboxe accuracy, less RSC."));
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
		if (!pending || !loggedIn)
		{
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
			for (Release release : releases)
			{
				if (compareVersions(release.version, seenVersion) > 0)
				{
					sendMessage.accept("2DScape (v" + release.version + "):");
					for (String change : release.changes)
					{
						sendMessage.accept("* " + change);
					}
				}
			}
			// Advance only after all missed changes have actually been sent to chat.
			saveVersion.accept(currentVersion);
		}
		pending = false;
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
			if (entries.isEmpty() || entries.stream().anyMatch(entry -> entry.length() > MAX_CHANGE_LENGTH))
			{
				throw new IllegalArgumentException("Each changelog entry must contain 1 to 80 characters");
			}
			this.version = version;
			this.changes = List.copyOf(entries);
		}
	}
}
