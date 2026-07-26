package com.kierenboal.npcsnap.export;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;

@Slf4j
@Singleton
public class BillboardPngExporter
{
	private final Client client;
	private final ClientThread clientThread;
	private ExecutorService executor;
	private volatile Path latestExportDirectory;

	@Inject
	public BillboardPngExporter(Client client, ClientThread clientThread)
	{
		this.client = client;
		this.clientThread = clientThread;
	}

	public synchronized void start()
	{
		if (executor == null || executor.isShutdown())
		{
			executor = Executors.newSingleThreadExecutor(runnable ->
			{
				Thread thread = new Thread(runnable, "2dscape-png-export");
				thread.setDaemon(true);
				return thread;
			});
		}
	}

	public void export(BillboardExportBatch batch)
	{
		ExecutorService current;
		synchronized (this)
		{
			start();
			current = executor;
		}
		current.execute(() -> write(batch));
	}

	public synchronized void shutDown()
	{
		if (executor != null)
		{
			executor.shutdownNow();
			executor = null;
		}
	}

	public Path getLatestExportDirectory()
	{
		return latestExportDirectory;
	}

	private void write(BillboardExportBatch batch)
	{
		Path directory = null;
		try
		{
			Path root = RuneLite.RUNELITE_DIR.toPath().resolve("2dscape");
			Files.createDirectories(root);
			directory = BillboardExportPaths.uniqueExportDirectory(root, LocalDateTime.now(), batch.name);
			Files.createDirectory(directory);
			for (BillboardExportFrame frame : batch.frames)
			{
				if (Thread.currentThread().isInterrupted())
				{
					throw new IOException("Export cancelled");
				}
				String filename = batch.name + "_Pitch" + frame.pitch + "_Yaw" + frame.yaw
					+ "_frame" + frame.animationFrame + ".png";
				Path output = BillboardExportPaths.uniquePng(directory.resolve(filename));
				if (!ImageIO.write(frame.image, "PNG", output.toFile()))
				{
					throw new IOException("No PNG writer is available");
				}
			}
			Path completedDirectory = directory;
			latestExportDirectory = completedDirectory;
			boolean copied = copyToClipboard(completedDirectory.toString());
			log.debug("Exported {} billboard PNGs to {}", batch.frames.size(), completedDirectory);
			notifyPlayer("Exported " + batch.frames.size() + " sprites to <u=ffffff>"
				+ completedDirectory + "</u>" + (copied ? " (path copied to clipboard)" : ""));
		}
		catch (Exception ex)
		{
			log.debug("Unable to export billboard PNGs to {}", directory, ex);
			notifyPlayer("2DScape sprite export failed: " + ex.getMessage());
		}
	}

	private boolean copyToClipboard(String value)
	{
		try
		{
			StringSelection selection = new StringSelection(value);
			Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			clipboard.setContents(selection, selection);
			return true;
		}
		catch (RuntimeException ex)
		{
			log.debug("Unable to copy the export directory to the clipboard", ex);
			return false;
		}
	}

	private void notifyPlayer(String message)
	{
		clientThread.invokeLater(() ->
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "2DScape", message, null));
	}
}
