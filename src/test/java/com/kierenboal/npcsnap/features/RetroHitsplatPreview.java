package com.kierenboal.npcsnap.features;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.ImageIO;
import net.runelite.api.HitsplatID;
import net.runelite.client.ui.FontManager;

/** Development-only contact sheet: never reads or writes files from the plugin. */
public final class RetroHitsplatPreview
{
	private static final int UNKNOWN_COUNT = 32;
	private static final int UNKNOWN_START = 1001;
	private static final Row[] KNOWN_ROWS = {
		new Row("Damage", "Damage_hitsplat", HitsplatID.DAMAGE_ME, HitsplatID.DAMAGE_OTHER, HitsplatID.DAMAGE_MAX_ME),
		new Row("Block / hit 0", "Zero_damage_hitsplat", HitsplatID.BLOCK_ME, HitsplatID.BLOCK_OTHER, -1),
		new Row("Shield / cyan", "Shield_hitsplat", HitsplatID.DAMAGE_ME_CYAN, HitsplatID.DAMAGE_OTHER_CYAN, HitsplatID.DAMAGE_MAX_ME_CYAN),
		new Row("Armour / orange", "Armour_hitsplat", HitsplatID.DAMAGE_ME_ORANGE, HitsplatID.DAMAGE_OTHER_ORANGE, HitsplatID.DAMAGE_MAX_ME_ORANGE),
		new Row("Charge / yellow", "Charge_hitsplat", HitsplatID.DAMAGE_ME_YELLOW, HitsplatID.DAMAGE_OTHER_YELLOW, HitsplatID.DAMAGE_MAX_ME_YELLOW),
		new Row("Uncharge / white", "Uncharge_hitsplat", HitsplatID.DAMAGE_ME_WHITE, HitsplatID.DAMAGE_OTHER_WHITE, HitsplatID.DAMAGE_MAX_ME_WHITE),
		new Row("Poise", "Poise_hitsplat", HitsplatID.DAMAGE_ME_POISE, HitsplatID.DAMAGE_OTHER_POISE, HitsplatID.DAMAGE_MAX_ME_POISE),
		new Row("Poison", "Poison_hitsplat", HitsplatID.POISON),
		new Row("Venom", "Venom_hitsplat", HitsplatID.VENOM),
		new Row("Disease", "Disease_hitsplat", HitsplatID.DISEASE),
		new Row("Disease blocked", "Disease_hitsplat", HitsplatID.DISEASE_BLOCKED),
		new Row("Heal", "Heal_hitsplat", HitsplatID.HEAL),
		new Row("Prayer drain", "Prayer_drain_hitsplat", HitsplatID.PRAYER_DRAIN),
		new Row("Corruption", "Corruption_hitsplat", HitsplatID.CORRUPTION),
		new Row("Bleed", "Bleed_hitsplat", HitsplatID.BLEED),
		new Row("Burn", "Burn_hitsplat", HitsplatID.BURN),
		new Row("Sanity drain", "Sanity_hitsplat", HitsplatID.SANITY_DRAIN),
		new Row("Sanity restore", "Heal_hitsplat_(sanity)", HitsplatID.SANITY_RESTORE),
		new Row("Doom", "Doom_stack_hitsplat", HitsplatID.DOOM),
		new Row("Alternate charge", "Alt_charge_hitsplat", HitsplatID.CYAN_UP),
		new Row("Alternate uncharge", "Alt_uncharge_hitsplat", HitsplatID.CYAN_DOWN)
	};
	private static final Row[] ROWS = rows();

	private static Row[] rows()
	{
		Row[] rows = java.util.Arrays.copyOf(KNOWN_ROWS, KNOWN_ROWS.length + UNKNOWN_COUNT);
		for (int i = 0; i < UNKNOWN_COUNT; i++)
		{
			rows[KNOWN_ROWS.length + i] = new Row("Undocumented #" + (i + 1), null, UNKNOWN_START + i);
		}
		return rows;
	}

	private RetroHitsplatPreview()
	{
	}

	public static void main(String[] args) throws IOException
	{
		Path output = Paths.get(args[0]);
		String attempt = args[1];
		Path wikiDirectory = resolveWikiDirectory(args.length > 3 ? Paths.get(args[3]) : Paths.get("tmp_wiki_page"));
		if (!attempt.matches("[a-zA-Z0-9_-]+"))
		{
			throw new IllegalArgumentException("Use a simple attempt name");
		}
		Files.createDirectories(output);
		BufferedImage sheet = new BufferedImage(1180, 170 + ROWS.length * 92, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = sheet.createGraphics();
		try
		{
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			g.setColor(new Color(0x24262B));
			g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
			g.setColor(Color.WHITE);
			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
			g.drawString("Retro vector hitsplats - " + attempt, 20, 27);
			g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
			g.drawString("Wiki backgrounds beside production vector drawing; enlarged views use nearest-neighbour scaling.", 20, 49);
			g.drawString("Native size", 165, 150);
			g.drawString("Normal: wiki / vector (3x)", 280, 150);
			g.drawString("Tinted: wiki / vector (3x)", 535, 150);
			g.drawString("Max: wiki / vector (3x)", 790, 150);
			g.drawString("Wide / zero (1x)", 1040, 150);
			if (args.length > 2 && !args[2].isEmpty())
			{
				BufferedImage reference = ImageIO.read(Paths.get(args[2]).toFile());
				g.drawString("Supplied RSC reference", 20, 76);
				g.drawImage(reference, 175, 60, reference.getWidth() * 3, reference.getHeight() * 3, null);
			}
			for (int i = 0; i < ROWS.length; i++)
			{
				Row row = ROWS[i];
				int y = 165 + i * 92;
				g.setColor(new Color(i % 2 == 0 ? 0x303339 : 0x282B30));
				g.fillRect(0, y, sheet.getWidth(), 92);
				g.setColor(Color.WHITE);
				g.drawString(row.label, 15, y + 34);
				g.setColor(new Color(0xAAB0BB));
				g.drawString("ID " + row.type, 15, y + 54);
				BufferedImage wiki = row.file != null ? wiki(wikiDirectory, row.file + ".png") : null;
				int amount = row.type == HitsplatID.BLOCK_ME || row.type == HitsplatID.DISEASE_BLOCKED ? 0 : 8;
				BufferedImage vector = render(row.type, amount, 32);
				g.drawImage(wiki, 165, y + 28, null);
				g.drawImage(vector, 210, y + 20, null);
				pair(g, wiki, vector, 280, y);
				if (wiki == null)
				{
					g.drawString("No wiki reference", 280, y + 28);
					g.drawString("Synthetic ID", 280, y + 47);
					Color color = RetroHitsplatRenderer.fill(row.type, amount);
					g.drawString(String.format("RGB %d, %d, %d", color.getRed(), color.getGreen(), color.getBlue()), 535, y + 34);
				}
				if (row.tinted >= 0)
				{
					pair(g, wiki(wikiDirectory, row.file + "_(tinted).png"), render(row.tinted, amount, 32), 535, y);
				}
				if (row.max >= 0)
				{
					pair(g, wiki(wikiDirectory, row.file + "_(max_hit).png"), render(row.max, amount, 32), 790, y);
				}
				g.drawImage(render(row.type, row.type == HitsplatID.BLOCK_ME ? 0 : 1234, 80),
					1040, y + (wiki == null ? 5 : 10), null);
				g.drawImage(render(row.type, 0, 80), 1040, y + (wiki == null ? 57 : 48), null);
			}
		}
		finally
		{
			g.dispose();
		}
		Path file = output.resolve(attempt + ".png");
		ImageIO.write(sheet, "png", file.toFile());
		System.out.println(file.toAbsolutePath());
		Path undocumented = output.resolve(attempt + "-undocumented.png");
		ImageIO.write(undocumentedSheet(), "png", undocumented.toFile());
		System.out.println(undocumented.toAbsolutePath());
	}

	private static BufferedImage undocumentedSheet()
	{
		BufferedImage sheet = new BufferedImage(1080, 60 + 8 * 150, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = sheet.createGraphics();
		try
		{
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			g.setColor(new Color(0x24262B));
			g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
			g.setColor(Color.WHITE);
			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
			g.drawString("32 undocumented IDs - no adjacent colour or shape repeats", 16, 25);
			g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
			g.drawString("Read left to right, then down. Enlarged / native / zero. All IDs are synthetic.", 16, 46);
			for (int i = 0; i < UNKNOWN_COUNT; i++)
			{
				int type = UNKNOWN_START + i, x = i % 4 * 270, y = 60 + i / 4 * 150;
				g.setColor(new Color(i % 2 == 0 ? 0x303339 : 0x282B30));
				g.fillRect(x, y, 270, 150);
				g.setColor(Color.WHITE);
				g.drawString(type + "  " + RetroHitsplatRenderer.unknownShape(type), x + 12, y + 19);
				Color color = RetroHitsplatRenderer.fill(type, 8);
				g.setColor(new Color(0xAAB0BB));
				g.drawString(String.format("RGB %d, %d, %d", color.getRed(), color.getGreen(), color.getBlue()), x + 12, y + 36);
				BufferedImage large = render(type, 8, 32);
				g.drawImage(large, x + 10, y + 42, large.getWidth() * 3, large.getHeight() * 3, null);
				g.drawImage(large, x + 166, y + 54, null);
				g.drawImage(render(type, 0, 32), x + 216, y + 54, null);
			}
		}
		finally
		{
			g.dispose();
		}
		return sheet;
	}

	private static void pair(Graphics2D g, BufferedImage wiki, BufferedImage vector, int x, int y)
	{
		if (wiki != null)
		{
			g.drawImage(wiki, x, y + 7, wiki.getWidth() * 3, wiki.getHeight() * 3, null);
		}
		g.drawImage(vector, x + 105, y - 1, vector.getWidth() * 3, vector.getHeight() * 3, null);
	}

	private static Path resolveWikiDirectory(Path savedPage) throws IOException
	{
		Path directory = Files.isRegularFile(savedPage) ? savedPage.toAbsolutePath().getParent() : savedPage;
		if (Files.isRegularFile(directory.resolve("Damage_hitsplat.png")))
		{
			return directory;
		}
		Path assets = directory.resolve("Hitsplat - OSRS Wiki_files");
		if (Files.isDirectory(assets))
		{
			return assets;
		}
		throw new IOException("Point -PhitsplatWikiDirectory at the saved HTML, its folder, or the *_files folder: " + savedPage);
	}

	private static BufferedImage wiki(Path directory, String file) throws IOException
	{
		BufferedImage image = ImageIO.read(directory.resolve(file).toFile());
		if (image == null)
		{
			throw new IOException("Invalid wiki PNG: " + file);
		}
		return image;
	}

	static BufferedImage render(int type, int amount, int width)
	{
		BufferedImage image = new BufferedImage(width, 30, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		try
		{
			g.setFont(FontManager.getRunescapeSmallFont());
			boolean unknown = RetroHitsplatRenderer.style(type, amount) == RetroHitsplatRenderer.Style.UNKNOWN;
			int height = unknown
				? Math.max(30, g.getFontMetrics().stringWidth(Integer.toString(amount)) + 22) : 30;
			int canvasWidth = unknown ? Math.max(width, RetroHitsplatRenderer.unknownWidth(type,
				Math.max(20, g.getFontMetrics().stringWidth(Integer.toString(amount)) + 12)) + 10) : width;
			if (height > image.getHeight() || canvasWidth > image.getWidth())
			{
				g.dispose();
				image = new BufferedImage(canvasWidth, height, BufferedImage.TYPE_INT_ARGB);
				g = image.createGraphics();
				g.setFont(FontManager.getRunescapeSmallFont());
			}
			RetroHitsplatRenderer.draw(g, type, amount, canvasWidth / 2, height / 2 + 5);
		}
		finally
		{
			g.dispose();
		}
		return image;
	}

	private static final class Row
	{
		final String label, file;
		final int type, tinted, max;

		Row(String label, String file, int type)
		{
			this(label, file, type, -1, -1);
		}

		Row(String label, String file, int type, int tinted, int max)
		{
			this.label = label;
			this.file = file;
			this.type = type;
			this.tinted = tinted;
			this.max = max;
		}
	}
}
