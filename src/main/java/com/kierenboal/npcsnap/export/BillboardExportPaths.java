package com.kierenboal.npcsnap.export;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import net.runelite.client.util.Filepath;

public final class BillboardExportPaths
{
	private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

	private BillboardExportPaths()
	{
	}

	public static String sanitizeName(String name, String fallback)
	{
		String value = stripTags(name);
		value = value.replaceAll("[\\p{Cntrl}\\\\/:*?\"<>|~]", " ").trim().replaceAll("\\s+", "_");
		value = value.replaceAll("[. ]+$", "");
		return value.isEmpty() ? fallback : value;
	}

	public static String stripTags(String value)
	{
		return value == null ? "" : value.replaceAll("<[^>]*>", "");
	}

	public static Filepath uniqueExportDirectory(Filepath root, LocalDateTime time, String name)
	{
		Filepath candidate = root.joinSegment(TIMESTAMP.format(time) + "_" + name);
		return unique(candidate);
	}

	public static Filepath unique(Filepath candidate)
	{
		if (!candidate.exists())
		{
			return candidate;
		}
		Filepath parent = candidate.getParent();
		for (int suffix = 2; ; suffix++)
		{
			Filepath next = parent.joinSegment(candidate.getFileName() + "_" + suffix);
			if (!next.exists())
			{
				return next;
			}
		}
	}

	public static Filepath uniquePng(Filepath candidate)
	{
		if (!candidate.exists())
		{
			return candidate;
		}
		String filename = candidate.getFileName();
		String base = filename.toLowerCase().endsWith(".png")
			? filename.substring(0, filename.length() - 4)
			: filename;
		Filepath parent = candidate.getParent();
		for (int suffix = 2; ; suffix++)
		{
			Filepath next = parent.joinSegment(base + "_" + suffix + ".png");
			if (!next.exists())
			{
				return next;
			}
		}
	}
}
