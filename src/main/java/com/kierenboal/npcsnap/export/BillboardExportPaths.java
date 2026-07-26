package com.kierenboal.npcsnap.export;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class BillboardExportPaths
{
	private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

	private BillboardExportPaths()
	{
	}

	public static String sanitizeName(String name, String fallback)
	{
		String value = stripTags(name);
		value = value.replaceAll("[\\\\/:*?\"<>|]", " ").trim().replaceAll("\\s+", "_");
		value = value.replaceAll("[. ]+$", "");
		return value.isEmpty() ? fallback : value;
	}

	public static String stripTags(String value)
	{
		return value == null ? "" : value.replaceAll("<[^>]*>", "");
	}

	public static Path uniqueExportDirectory(Path root, LocalDateTime time, String name)
	{
		Path candidate = root.resolve(TIMESTAMP.format(time) + "_" + name);
		return unique(candidate);
	}

	public static Path unique(Path candidate)
	{
		if (!Files.exists(candidate))
		{
			return candidate;
		}
		for (int suffix = 2; ; suffix++)
		{
			Path next = candidate.resolveSibling(candidate.getFileName() + "_" + suffix);
			if (!Files.exists(next))
			{
				return next;
			}
		}
	}

	public static Path uniquePng(Path candidate)
	{
		if (!Files.exists(candidate))
		{
			return candidate;
		}
		String filename = candidate.getFileName().toString();
		String base = filename.toLowerCase().endsWith(".png")
			? filename.substring(0, filename.length() - 4)
			: filename;
		for (int suffix = 2; ; suffix++)
		{
			Path next = candidate.resolveSibling(base + "_" + suffix + ".png");
			if (!Files.exists(next))
			{
				return next;
			}
		}
	}
}
