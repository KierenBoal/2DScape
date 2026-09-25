package com.kierenboal.npcsnap.export;

import java.time.LocalDateTime;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import net.runelite.client.util.Filepath;

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
		assertEquals("A_B_C", BillboardExportPaths.sanitizeName("A~B\u0001C", "Object_2"));
		assertEquals("Item_3", BillboardExportPaths.sanitizeName(" <> ", "Item_3"));
	}

	@Test
	public void createsTimestampTargetPathAndCollisionSuffix() throws Exception
	{
		Filepath root = Filepath.Unchecked.getRooted(temporaryFolder.getRoot().toPath());
		LocalDateTime time = LocalDateTime.of(2026, 7, 26, 13, 54, 2);
		Filepath first = BillboardExportPaths.uniqueExportDirectory(root, time, "Warlord_Burn");
		assertEquals("20260726_135402_Warlord_Burn", first.getFileName());
		first.createDirectory();
		assertEquals("20260726_135402_Warlord_Burn_2",
			BillboardExportPaths.uniqueExportDirectory(root, time, "Warlord_Burn").getFileName());
	}

	@Test
	public void keepsPngExtensionAfterFileCollision() throws Exception
	{
		Filepath root = Filepath.Unchecked.getRooted(temporaryFolder.getRoot().toPath());
		Filepath first = root.joinSegment("Goblin_Pitch0_Yaw0.png");
		first.write(new byte[0]);
		assertEquals("Goblin_Pitch0_Yaw0_2.png",
			BillboardExportPaths.uniquePng(first).getFileName());
	}
}
