package com.kierenboal.npcsnap;

import com.kierenboal.npcsnap.rendering.BillboardDrawGeometry;
import com.kierenboal.npcsnap.state.BillboardPerformanceMetrics;
import com.kierenboal.npcsnap.state.BillboardRedrawReason;
import com.kierenboal.npcsnap.state.BillboardCacheKey;
import com.kierenboal.npcsnap.state.CachedBillboard;
import com.kierenboal.npcsnap.rendering.BillboardRenderRequest;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.GraphicsObject;
import net.runelite.api.TileItem;
import net.runelite.api.TileObject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.FontManager;

@Singleton
@Slf4j
public class NpcSnapDebug
{
	private static final Color BILLBOARD_RED = new Color(255, 0, 0, 220);
	private static final Color READY_TO_REDRAW_MAGENTA = new Color(255, 0, 255, 255);
	private static final Color READY_TO_REDRAW_LOW_PRIORITY = new Color(128, 0, 128, 255);
	private static final Color TEXT_BACKGROUND = new Color(0, 0, 0, 170);
	private static final Color TEXT_FOREGROUND = Color.WHITE;
	private static final Color METRIC_VALUE_FOREGROUND = new Color(220, 220, 220, 255);
	private static final Color METRIC_HOTSPOT_FOREGROUND = new Color(255, 80, 80, 255);
	private static final String[] METRIC_HEADERS = {"Method", "Min ms", "Avg ms", "Max ms", "% plugin"};
	private static final int HOTSPOT_METRIC_COUNT = 3;

	private final Client client;
	private final NpcSnapConfig config;
	private final Map<Actor, FrameState> actorFrames = new IdentityHashMap<>();

	@Inject
	public NpcSnapDebug(Client client, NpcSnapConfig config)
	{
		this.client = client;
		this.config = config;
	}

	void clearFrameStates()
	{
		actorFrames.clear();
	}

	void recordBillboardRedraw(BillboardRenderRequest request, CachedBillboard previous,
		CachedBillboard current, Collection<BillboardRedrawReason> reasons)
	{
		current.markDebugFrameRedrawn(reasons);
		if (!config.debugLogBillboardRedraws() || !log.isDebugEnabled())
		{
			return;
		}
		log.debug("Sprite redraw: cycle={} target={} location={} plane={} reasons=[{}] before=[{}] after=[{}]",
			client.getGameCycle(), redrawTarget(request.renderable), request.localPoint, request.plane,
			BillboardRedrawReason.format(reasons), redrawState(previous), redrawState(current));
	}

	private static String redrawTarget(Object source)
	{
		if (source == null) return "unknown";
		String identity = "@" + Integer.toHexString(System.identityHashCode(source));
		if (source instanceof NPC) return "NPC id=" + ((NPC) source).getId() + " index=" + ((NPC) source).getIndex() + identity;
		if (source instanceof Player) return "Player index=" + ((Player) source).getId() + identity;
		if (source instanceof Projectile) return "Projectile id=" + ((Projectile) source).getId() + identity;
		if (source instanceof GraphicsObject) return "GraphicsObject id=" + ((GraphicsObject) source).getId() + identity;
		if (source instanceof TileItem) return "TileItem id=" + ((TileItem) source).getId() + " quantity=" + ((TileItem) source).getQuantity() + identity;
		if (source instanceof TileObject) return "TileObject id=" + ((TileObject) source).getId() + identity;
		return source.getClass().getSimpleName() + identity;
	}

	private static String redrawState(CachedBillboard sprite)
	{
		if (sprite == null) return "none";
		BillboardCacheKey key = sprite.key;
		return "animation=" + key.animationId + ":" + key.animationFrame
			+ " pose=" + key.poseAnimationId + ":" + key.poseAnimationFrame
			+ " yaw=" + key.relativeYaw + " pitch=" + key.relativePitch
			+ " model=" + key.modelStateHash + " texture=" + key.textureStateHash
			+ " animatedTexture=" + key.animatedTextureOffsetStateHash
			+ " quality=" + key.renderQuality + " bounds=" + sprite.bounds;
	}

	void recordActorFrames(Actor actor, int animationId, int animationFrame, int forcedAnimationFrame, int poseFrame, int forcedPoseFrame)
	{
		if (actor == null)
		{
			return;
		}

		actorFrames.put(actor, new FrameState(animationId, animationFrame, forcedAnimationFrame, poseFrame, forcedPoseFrame));
	}

	public FrameDebugInfo actorFrameDebugInfo(Actor actor)
	{
		if (actor == null)
		{
			return null;
		}

		FrameState state = actorFrames.get(actor);
		if (state == null)
		{
			return new FrameDebugInfo(actor.getAnimation(), actor.getAnimationFrame(), actor.getAnimationFrame(), actor.getPoseAnimationFrame(), actor.getPoseAnimationFrame());
		}

		return new FrameDebugInfo(state.animationId, state.animationFrame, state.forcedAnimationFrame, state.poseFrame, state.forcedPoseFrame);
	}

	void drawBillboardDebug(Graphics2D graphics, RenderDebug renderDebug)
	{
	}

	void drawBillboardDebugForeground(Graphics2D graphics, RenderDebug renderDebug)
	{
		Rectangle bounds = renderDebug.bounds;
		if (bounds == null)
		{
			return;
		}
		
		if (config.debugDrawBillboardOutline())
		{
			int size = 1;
			Stroke stroke = graphics.getStroke();
			graphics.setStroke(new BasicStroke(size));
			graphics.setColor(BILLBOARD_RED);
			if (renderDebug.geometry != null && (renderDebug.geometry.isSheared() || renderDebug.geometry.projectedQuad != null))
			{
				graphics.drawPolygon(renderDebug.geometry.polygon());
			}
			else
			{
				graphics.drawRect(bounds.x - (size / 2), bounds.y - (size / 2), bounds.width + size, bounds.height + size);
			}
			graphics.setStroke(stroke);
		}
		
		if (config.debugShowReadyToRedrawFrames() && renderDebug.readyToRedraw)
		{
			int size = 2;
			Stroke stroke = graphics.getStroke();
			graphics.setStroke(new BasicStroke(size));
			graphics.setColor(renderDebug.readyToRedrawColor != null ? renderDebug.readyToRedrawColor : READY_TO_REDRAW_MAGENTA);
			graphics.drawRect(bounds.x - size, bounds.y - size, bounds.width + (size * 2), bounds.height + (size * 2));
			graphics.setStroke(stroke);
		}

		if (config.debugShowFrameRedraws() && renderDebug.redrawOutlineWidth > 0)
		{
			int size = renderDebug.redrawOutlineWidth;
			Stroke stroke = graphics.getStroke();
			graphics.setStroke(new BasicStroke(size));
			graphics.setColor(frameRedrawColor(renderDebug));
			graphics.drawRect(bounds.x + (size / 2), bounds.y + (size / 2), bounds.width - size, bounds.height - size);
			graphics.setStroke(stroke);
			String reasons = BillboardRedrawReason.format(renderDebug.redrawReasons);
			if (!reasons.isEmpty())
			{
				drawRedrawReasons(graphics, reasons, bounds);
			}
		}

		if (config.debugDrawBillboardPaintOrder())
		{
			drawCenteredText(graphics, Integer.toString(renderDebug.paintOrder), bounds.x + (bounds.width / 2), bounds.y + (bounds.height / 2));
		}

		if (config.debugDrawFrameNumber())
		{
			String[] stateLines = stateLines(renderDebug);
			if (stateLines != null && stateLines.length > 0)
			{
				drawCenteredTextBlock(graphics, stateLines, bounds.x + (bounds.width / 2), bounds.y + Math.max(12, bounds.height / 4));
			}
		}

	}

	void drawPerformanceMetrics(Graphics2D graphics, Rectangle viewport, List<BillboardPerformanceMetrics.MetricRow> rows)
	{
		drawPerformanceMetrics(graphics, viewport, rows, java.util.Collections.emptyMap());
	}

	void drawPerformanceMetrics(Graphics2D graphics, Rectangle viewport, List<BillboardPerformanceMetrics.MetricRow> rows, Map<String, Long> counters)
	{
		if (graphics == null || viewport == null)
		{
			return;
		}
		Graphics2D clipped = (Graphics2D) graphics.create();
		try
		{
			clipped.clip(new Rectangle(viewport.x + 8, viewport.y + 8,
				Math.max(0, viewport.width - 16), Math.max(0, viewport.height - 16)));
			drawPerformanceTable(clipped, viewport, rows, performanceContext(counters));
		}
		finally
		{
			clipped.dispose();
		}
	}

	List<String> performanceContext(Map<String, Long> counters)
	{
		List<String> lines = new ArrayList<>(4);
		net.runelite.api.Player player = client.getLocalPlayer();
		net.runelite.api.WorldView top = client.getTopLevelWorldView();
		com.kierenboal.npcsnap.targeting.WorldViewLocationResolver.ResolvedLocation location =
			player != null && top != null ? com.kierenboal.npcsnap.targeting.WorldViewLocationResolver.resolve(client, player) : null;
		String source = location != null && !location.source.isTopLevel()
			? String.format(Locale.ROOT, ", Source: %d/%d, View: %d", location.sourcePoint.getX(), location.sourcePoint.getY(), location.source.getId()) : "";
		lines.add(location != null
			? String.format(Locale.ROOT, "Player (X: %d, Y: %d, Z: %.1f, Plane: %d%s)",
				top.getBaseX() + location.mainPoint.getSceneX(), top.getBaseY() + location.mainPoint.getSceneY(), location.height, top.getPlane(), source)
			: "Player (unavailable)");
		lines.add(String.format(Locale.ROOT, "Camera (X: %.1f, Y: %.1f, Z: %.1f, Pitch: %d, Yaw: %d, Zoom: %d, %s %dx%d)",
			client.getCameraFpX(), client.getCameraFpY(), client.getCameraFpZ(), client.getCameraPitch(), client.getCameraYaw(), client.get3dZoom(),
			client.isGpu() ? "GPU" : "CPU", client.getViewportWidth(), client.getViewportHeight()));
		lines.add(String.format(Locale.ROOT, "Renderables (Actors: %s, Items: %s, Effects: %s, Boats: %s, Hidden: %s, Objects: %s/%s)",
			compactCount(counters.getOrDefault("Actors", 0L)), compactCount(counters.getOrDefault("Items", 0L)),
			compactCount(counters.getOrDefault("Effects", 0L)), compactCount(counters.getOrDefault("Boats", 0L)),
			compactCount(counters.getOrDefault("Boats skipped", 0L)), compactCount(counters.getOrDefault("Objects accepted", 0L)),
			compactCount(counters.getOrDefault("Objects checked", 0L))));
		lines.add(String.format(Locale.ROOT, "Geometry (Faces: %s, Inspected: %s, Accepted: %s, Terrain: %s/%s)",
			compactCount(counters.getOrDefault("Faces available", 0L)), compactCount(counters.getOrDefault("Faces inspected", 0L)),
			compactCount(counters.getOrDefault("Faces accepted", 0L)), compactCount(counters.getOrDefault("Terrain tris accepted", 0L)),
			compactCount(counters.getOrDefault("Terrain tris checked", 0L))));
		return lines;
	}

	static String compactCount(long value)
	{
		return value >= 1_000_000L ? String.format(Locale.ROOT, "%.1fm", value / 1_000_000d)
			: value >= 1_000L ? String.format(Locale.ROOT, "%.1fk", value / 1_000d) : Long.toString(value);
	}

	static int performanceRowCapacity(int viewportHeight, int lineHeight, int contextLines)
	{
		return performanceRowCapacityForContextHeight(viewportHeight, lineHeight, contextLines * lineHeight);
	}

	static int performanceRowCapacityForContextHeight(int viewportHeight, int lineHeight, int contextHeight)
	{
		return Math.max(0, (viewportHeight - 16 - 10 - contextHeight) / Math.max(1, lineHeight) - 1);
	}

	private void drawPerformanceTable(Graphics2D graphics, Rectangle viewport, List<BillboardPerformanceMetrics.MetricRow> rows, List<String> context)
	{
		if (graphics == null || viewport == null || rows == null || rows.isEmpty())
		{
			return;
		}

		Font font = metricsFont();
		Font oldFont = graphics.getFont();
		Object oldTextAntialiasing = graphics.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING);
		Object oldFractionalMetrics = graphics.getRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS);
		graphics.setFont(font);
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
		FontMetrics metrics = graphics.getFontMetrics();
		Font contextFont = font.deriveFont(Math.max(1f, font.getSize2D() - 2f));
		FontMetrics contextMetrics = graphics.getFontMetrics(contextFont);
		int padding = 5;
		int gap = 12;
		int lineHeight = metrics.getHeight();
		int contextHeight = context.size() * contextMetrics.getHeight();
		int capacity = performanceRowCapacityForContextHeight(viewport.height, lineHeight, contextHeight);
		rows = rows.subList(0, Math.min(rows.size(), capacity));
		int[] columnWidths = new int[METRIC_HEADERS.length];
		for (int column = 0; column < METRIC_HEADERS.length; column++)
		{
			columnWidths[column] = metrics.stringWidth(METRIC_HEADERS[column]);
		}
		for (BillboardPerformanceMetrics.MetricRow row : rows)
		{
			String[] values = metricValues(row);
			for (int column = 0; column < values.length; column++)
			{
				columnWidths[column] = Math.max(columnWidths[column], metrics.stringWidth(values[column]));
			}
		}

		int x = viewport.x + 8;
		int y = viewport.y + 8;
		int width = (padding * 2) + (gap * (columnWidths.length - 1));
		for (int columnWidth : columnWidths)
		{
			width += columnWidth;
		}
		for (String line : context)
		{
			width = Math.max(width, contextMetrics.stringWidth(line) + padding * 2);
		}
		int height = (lineHeight * (rows.size() + 1)) + contextHeight + (padding * 2);
		graphics.setColor(TEXT_BACKGROUND);
		graphics.fillRect(x, y, width, height);
		int[] columnX = metricColumnPositions(x + padding, columnWidths, gap);
		int contextY = y + padding + contextMetrics.getAscent();
		graphics.setColor(METRIC_VALUE_FOREGROUND);
		graphics.setFont(contextFont);
		for (String line : context)
		{
			graphics.drawString(line, x + padding, contextY);
			contextY += contextMetrics.getHeight();
		}
		graphics.setFont(font);
		int headerY = y + padding + contextHeight + metrics.getAscent();
		for (int column = 0; column < METRIC_HEADERS.length; column++)
		{
			graphics.drawString(METRIC_HEADERS[column], columnX[column], headerY);
		}
		boolean[] hotspotRows = highestTotalTimeRows(rows);
		for (int i = 0; i < rows.size(); i++)
		{
			BillboardPerformanceMetrics.MetricRow row = rows.get(i);
			if (row == null)
			{
				continue;
			}

			int lineY = headerY + ((i + 1) * lineHeight);
			graphics.setColor(row.color);
			String[] values = metricValues(row);
			graphics.drawString(values[0], columnX[0], lineY);
			for (int column = 1; column < values.length; column++)
			{
				graphics.setColor(column == values.length - 1 && hotspotRows[i] ? METRIC_HOTSPOT_FOREGROUND : METRIC_VALUE_FOREGROUND);
				graphics.drawString(values[column], columnX[column], lineY);
			}
		}
		graphics.setFont(oldFont);
		restoreRenderingHint(graphics, RenderingHints.KEY_TEXT_ANTIALIASING, oldTextAntialiasing, RenderingHints.VALUE_TEXT_ANTIALIAS_DEFAULT);
		restoreRenderingHint(graphics, RenderingHints.KEY_FRACTIONALMETRICS, oldFractionalMetrics, RenderingHints.VALUE_FRACTIONALMETRICS_DEFAULT);
	}

	private void restoreRenderingHint(Graphics2D graphics, RenderingHints.Key key, Object value, Object fallback)
	{
		graphics.setRenderingHint(key, value != null ? value : fallback);
	}

	private Font metricsFont()
	{
		Font font = FontManager.getDefaultBoldFont();
		if (font == null)
		{
			font = FontManager.getDefaultFont();
		}
		if (font == null)
		{
			font = new Font(Font.SANS_SERIF, Font.BOLD, 12);
		}
		return font.deriveFont(Font.BOLD, 12.0f);
	}

	private String indentedMetricName(BillboardPerformanceMetrics.MetricRow row)
	{
		int spaces = Math.max(0, row.depth) * 2;
		StringBuilder builder = new StringBuilder(spaces + row.name.length());
		for (int i = 0; i < spaces; i++)
		{
			builder.append(' ');
		}
		builder.append(row.name);
		return builder.toString();
	}

	private int[] metricColumnPositions(int firstColumnX, int[] columnWidths, int gap)
	{
		int[] columnX = new int[columnWidths.length];
		int x = firstColumnX;
		for (int column = 0; column < columnWidths.length; column++)
		{
			columnX[column] = x;
			x += columnWidths[column] + gap;
		}
		return columnX;
	}

	private boolean[] highestTotalTimeRows(List<BillboardPerformanceMetrics.MetricRow> rows)
	{
		boolean[] hotspots = new boolean[rows.size()];
		List<Integer> candidates = new ArrayList<>();
		for (int index = 0; index < rows.size(); index++)
		{
			BillboardPerformanceMetrics.MetricRow row = rows.get(index);
			if (row != null && row.depth > 0)
			{
				candidates.add(index);
			}
		}
		candidates.sort(Comparator.comparingDouble((Integer index) -> rows.get(index).percentOfOverall).reversed());
		for (int index = 0; index < Math.min(HOTSPOT_METRIC_COUNT, candidates.size()); index++)
		{
			hotspots[candidates.get(index)] = true;
		}
		return hotspots;
	}

	private String[] metricValues(BillboardPerformanceMetrics.MetricRow row)
	{
		return new String[]
		{
			indentedMetricName(row),
			String.format(Locale.ROOT, "%.1f", row.minMillis),
			String.format(Locale.ROOT, "%.1f", row.avgMillis),
			String.format(Locale.ROOT, "%.1f", row.maxMillis),
			String.format(Locale.ROOT, "%.1f%%", row.percentOfOverall)
		};
	}

	private String[] stateLines(RenderDebug renderDebug)
	{
		StateDebugInfo stateDebugInfo = renderDebug.stateDebugInfo;
		if (stateDebugInfo == null)
		{
			return null;
		}

		String displayedFrame = Integer.toString(stateDebugInfo.displayedFrame);
		String currentFrame = Integer.toString(stateDebugInfo.currentFrame);
		String queuePosition = stateDebugInfo.queuePosition >= 0 ? Integer.toString(stateDebugInfo.queuePosition) : "-";
		return new String[] {
			"#" + Integer.toHexString(stateDebugInfo.stateHash).toUpperCase(),
			"P: " + stateDebugInfo.pitchDegrees + " / Y: " + stateDebugInfo.yawDegrees,
			"A: " + stateDebugInfo.animationId + " / DF: " + displayedFrame + " / CF: " + currentFrame,
			"Q: " + queuePosition
		};
	}

	private Color frameRedrawColor(RenderDebug renderDebug)
	{
		int seed = renderDebug.redrawColorSeed;
		
		int pattern = Math.floorMod(seed, 7);
		
		int red = 0;
		int green = 0;
		int blue = 0;
		
		if (pattern == 0)
		{
			red = brightColorChannel(seed, 0);
		}
		
		if (pattern == 1)
		{
			green = brightColorChannel(seed, 8);
		}
		
		if (pattern == 2)
		{
			blue = brightColorChannel(seed, 16);
		}
		
		if (pattern == 3)
		{
			red = brightColorChannel(seed, 0);
			green = brightColorChannel(seed, 8);
		}
		
		if (pattern == 4)
		{
			red = brightColorChannel(seed, 0);
			blue = brightColorChannel(seed, 8);
		}
		
		if (pattern == 5)
		{
			green = brightColorChannel(seed, 0);
			blue = brightColorChannel(seed, 8);
		}
		
		if (pattern == 6)
		{
			red = brightColorChannel(seed, 0);
			blue = brightColorChannel(seed, 8);
		}

		return new Color(red, green, blue, 255);
	}

	private static int brightColorChannel(int seed, int shift)
	{
		return 192 + Math.floorMod(seed >> shift, 64);
	}

	static Color readyToRedrawColor(double score, double minScore, double maxScore)
	{
		if (!Double.isFinite(score) || !Double.isFinite(minScore) || !Double.isFinite(maxScore))
		{
			return READY_TO_REDRAW_MAGENTA;
		}

		double ratio = maxScore <= minScore ? 1.0d : (score - minScore) / (maxScore - minScore);
		ratio = Math.max(0.0d, Math.min(1.0d, ratio));
		int red = interpolate(READY_TO_REDRAW_LOW_PRIORITY.getRed(), READY_TO_REDRAW_MAGENTA.getRed(), ratio);
		int blue = interpolate(READY_TO_REDRAW_LOW_PRIORITY.getBlue(), READY_TO_REDRAW_MAGENTA.getBlue(), ratio);
		return new Color(red, 0, blue, 255);
	}

	private static int interpolate(int low, int high, double ratio)
	{
		return low + (int) Math.round((high - low) * ratio);
	}

	private void drawCenteredText(Graphics2D graphics, String text, int centerX, int centerY)
	{
		Font font = graphics.getFont().deriveFont(Font.BOLD, 12.0f);
		Font oldFont = graphics.getFont();
		graphics.setFont(font);
		FontMetrics metrics = graphics.getFontMetrics();
		int padding = 3;
		int width = metrics.stringWidth(text);
		int height = metrics.getHeight();
		int x = centerX - (width / 2);
		int y = centerY - (height / 2);

		graphics.setColor(TEXT_BACKGROUND);
		graphics.fillRect(x - padding, y - padding, width + (padding * 2), height + (padding * 2));
		graphics.setColor(TEXT_FOREGROUND);
		graphics.drawString(text, x, y + metrics.getAscent());
		graphics.setFont(oldFont);
	}

	private void drawRedrawReasons(Graphics2D graphics, String reasons, Rectangle bounds)
	{
		if (bounds.width <= 8 || bounds.height <= 8)
		{
			return;
		}
		Font font = graphics.getFont().deriveFont(Font.BOLD, 12.0f);
		String[] lines;
		int textHeight;
		do
		{
			FontMetrics metrics = graphics.getFontMetrics(font);
			lines = wrapReasonText(reasons, metrics, bounds.width - 8);
			textHeight = lines.length * metrics.getHeight();
			if (textHeight <= bounds.height - 8 || font.getSize() <= 7)
			{
				break;
			}
			font = font.deriveFont(font.getSize2D() - 1.0f);
		}
		while (true);
		Graphics2D labelGraphics = (Graphics2D) graphics.create();
		try
		{
			labelGraphics.clipRect(bounds.x + 2, bounds.y + 2, bounds.width - 4, bounds.height - 4);
			drawCenteredTextBlock(labelGraphics, lines, bounds.x + bounds.width / 2,
				bounds.y + Math.max(4, (bounds.height - textHeight) / 2), font);
		}
		finally
		{
			labelGraphics.dispose();
		}
	}

	static String[] wrapReasonText(String text, FontMetrics metrics, int maxWidth)
	{
		List<String> lines = new ArrayList<>();
		String line = "";
		for (String word : text.split(" "))
		{
			String candidate = line.isEmpty() ? word : line + " " + word;
			if (!line.isEmpty() && metrics.stringWidth(candidate) > maxWidth)
			{
				lines.add(line);
				line = "";
			}
			while (metrics.stringWidth(word) > maxWidth && word.length() > 1)
			{
				int end = word.length() - 1;
				while (end > 1 && metrics.stringWidth(word.substring(0, end)) > maxWidth)
				{
					end--;
				}
				lines.add(word.substring(0, end));
				word = word.substring(end);
			}
			line = line.isEmpty() ? word : line + " " + word;
		}
		if (!line.isEmpty())
		{
			lines.add(line);
		}
		return lines.toArray(new String[0]);
	}

	private void drawCenteredTextBlock(Graphics2D graphics, String[] lines, int centerX, int topY)
	{
		drawCenteredTextBlock(graphics, lines, centerX, topY, graphics.getFont().deriveFont(Font.BOLD, 12.0f));
	}

	private void drawCenteredTextBlock(Graphics2D graphics, String[] lines, int centerX, int topY, Font font)
	{
		if (lines == null || lines.length == 0)
		{
			return;
		}

		Font oldFont = graphics.getFont();
		graphics.setFont(font);
		FontMetrics metrics = graphics.getFontMetrics();
		int padding = 3;
		int lineHeight = metrics.getHeight();
		int maxWidth = 0;
		for (String line : lines)
		{
			if (line != null)
			{
				maxWidth = Math.max(maxWidth, metrics.stringWidth(line));
			}
		}

		int totalHeight = lineHeight * lines.length;
		int x = centerX - (maxWidth / 2);
		int y = topY;
		graphics.setColor(TEXT_BACKGROUND);
		graphics.fillRect(x - padding, y - padding, maxWidth + (padding * 2), totalHeight + (padding * 2));
		graphics.setColor(TEXT_FOREGROUND);
		for (int i = 0; i < lines.length; i++)
		{
			String line = lines[i];
			if (line == null)
			{
				continue;
			}

			int lineX = centerX - (metrics.stringWidth(line) / 2);
			int lineY = y + (i * lineHeight) + metrics.getAscent();
			graphics.drawString(line, lineX, lineY);
		}
		graphics.setFont(oldFont);
	}

	static final class RenderDebug
	{
		private final Rectangle bounds;
		private final BillboardDrawGeometry geometry;
		private final int paintOrder;
		private final int redrawOutlineWidth;
		private final int redrawColorSeed;
		private final boolean readyToRedraw;
		private final Color readyToRedrawColor;
		private final StateDebugInfo stateDebugInfo;
		private final Collection<BillboardRedrawReason> redrawReasons;

		private RenderDebug(
			BillboardDrawGeometry geometry,
			int paintOrder,
			int redrawOutlineWidth,
			int redrawColorSeed,
			boolean readyToRedraw,
			Color readyToRedrawColor,
			StateDebugInfo stateDebugInfo,
			Collection<BillboardRedrawReason> redrawReasons
		)
		{
			this.geometry = geometry;
			this.bounds = geometry != null ? geometry.bounds : null;
			this.paintOrder = paintOrder;
			this.redrawOutlineWidth = redrawOutlineWidth;
			this.redrawColorSeed = redrawColorSeed;
			this.readyToRedraw = readyToRedraw;
			this.readyToRedrawColor = readyToRedrawColor;
			this.stateDebugInfo = stateDebugInfo;
			this.redrawReasons = redrawReasons;
		}

		static RenderDebug forGeometry(
			BillboardDrawGeometry geometry,
			int paintOrder,
			int redrawOutlineWidth,
			int redrawColorSeed,
			boolean readyToRedraw,
			Color readyToRedrawColor,
			StateDebugInfo stateDebugInfo,
			Collection<BillboardRedrawReason> redrawReasons)
		{
			return new RenderDebug(geometry, paintOrder, redrawOutlineWidth, redrawColorSeed, readyToRedraw, readyToRedrawColor, stateDebugInfo, redrawReasons);
		}
	}

	public static final class FrameDebugInfo
	{
		final int animationId;
		final int animationFrame;
		final int forcedAnimationFrame;
		final int poseFrame;
		final int forcedPoseFrame;

		private FrameDebugInfo(int animationId, int animationFrame, int forcedAnimationFrame, int poseFrame, int forcedPoseFrame)
		{
			this.animationId = animationId;
			this.animationFrame = animationFrame;
			this.forcedAnimationFrame = forcedAnimationFrame;
			this.poseFrame = poseFrame;
			this.forcedPoseFrame = forcedPoseFrame;
		}

		public static FrameDebugInfo of(int animationId, int animationFrame, int forcedAnimationFrame)
		{
			return new FrameDebugInfo(animationId, animationFrame, forcedAnimationFrame, -1, -1);
		}
	}

	public static final class StateDebugInfo
	{
		private final int stateHash;
		private final int pitchDegrees;
		private final int yawDegrees;
		private final int animationId;
		private final int displayedFrame;
		private final int currentFrame;
		private final int queuePosition;

		private StateDebugInfo(int stateHash, int pitchDegrees, int yawDegrees, int animationId, int displayedFrame, int currentFrame, int queuePosition)
		{
			this.stateHash = stateHash;
			this.pitchDegrees = pitchDegrees;
			this.yawDegrees = yawDegrees;
			this.animationId = animationId;
			this.displayedFrame = displayedFrame;
			this.currentFrame = currentFrame;
			this.queuePosition = queuePosition;
		}

		static StateDebugInfo of(int stateHash, int pitchDegrees, int yawDegrees, int animationId, int displayedFrame, int currentFrame, int queuePosition)
		{
			return new StateDebugInfo(stateHash, pitchDegrees, yawDegrees, animationId, displayedFrame, currentFrame, queuePosition);
		}
	}

	private static final class FrameState
	{
		private final int animationId;
		private final int animationFrame;
		private final int forcedAnimationFrame;
		private final int poseFrame;
		private final int forcedPoseFrame;

		private FrameState(int animationId, int animationFrame, int forcedAnimationFrame, int poseFrame, int forcedPoseFrame)
		{
			this.animationId = animationId;
			this.animationFrame = animationFrame;
			this.forcedAnimationFrame = forcedAnimationFrame;
			this.poseFrame = poseFrame;
			this.forcedPoseFrame = forcedPoseFrame;
		}
	}
}
