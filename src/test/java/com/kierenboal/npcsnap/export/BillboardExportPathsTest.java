package com.kierenboal.npcsnap.export;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;

public class BillboardExportPathsTest
{
	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void sanitizesNamesAndUsesFallbacks()
	{
		assertEquals("Warlord_Burn", BillboardExportPaths.sanitizeName("<col=ff0000>Warlord Burn</col>", "NPC_1"));
		assertEquals("A_B_C", BillboardExportPaths.sanitizeName(" A:B/C ", "Object_2"));
		assertEquals("Item_3", BillboardExportPaths.sanitizeName(" <> ", "Item_3"));
	}

	@Test
	public void createsTimestampTargetPathAndCollisionSuffix() throws Exception
	{
		Path root = temporaryFolder.getRoot().toPath();
		LocalDateTime time = LocalDateTime.of(2026, 7, 26, 13, 54, 2);
		Path first = BillboardExportPaths.uniqueExportDirectory(root, time, "Warlord_Burn");
		assertEquals("20260726_135402_Warlord_Burn", first.getFileName().toString());
		Files.createDirectory(first);
		assertEquals("20260726_135402_Warlord_Burn_2",
			BillboardExportPaths.uniqueExportDirectory(root, time, "Warlord_Burn").getFileName().toString());
	}

	@Test
	public void keepsPngExtensionAfterFileCollision() throws Exception
	{
		Path first = temporaryFolder.getRoot().toPath().resolve("Goblin_Pitch0_Yaw0.png");
		Files.createFile(first);
		assertEquals("Goblin_Pitch0_Yaw0_2.png",
			BillboardExportPaths.uniquePng(first).getFileName().toString());
	}
}
