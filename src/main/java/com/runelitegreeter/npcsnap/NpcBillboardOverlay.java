package com.runelitegreeter.npcsnap;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Model;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.Renderable;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

class NpcBillboardOverlay extends Overlay
{
	private static final int FULL_CIRCLE = 2048;
	private static final int MAX_PITCH = 512;
	private static final int LOCAL_TILE_SIZE = 128;
	private static final int TRANSPARENT = 0;
	private static final int COMBAT_YAW = 512;
	private static final int OPPOSITE_COMBAT_YAW = 1536;
	private static final boolean USE_UNLIT_COLORS = true;
	private static final double MIN_RENDER_QUALITY = 0.01d;
	private static final int MAX_SOURCE_BILLBOARD_SIZE = 4096;
	private static final int MAX_SOURCE_BILLBOARD_COORDINATE = 32768;
	private static final int MAX_DRAW_BILLBOARD_SIZE = 8192;
	private static final int MAX_CANVAS_COORDINATE = 1_000_000;

	private final Client client;
	private final NpcSnapConfig config;
	private final Map<Renderable, CachedBillboard> billboardCache = new HashMap<>();
	private final Map<TileItem, GroundItemBillboard> groundItems = new HashMap<>();
	private final Set<Renderable> activeBillboards = new HashSet<>();
	private int activeBillboardsGameCycle = Integer.MIN_VALUE;

	@Inject
	private NpcBillboardOverlay(Client client, NpcSnapConfig config)
	{
		this.client = client;
		this.config = config;
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPosition(OverlayPosition.DYNAMIC);
		setPriority(PRIORITY_HIGHEST);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.enable2dBillboardSprites() || client.getGameState() != GameState.LOGGED_IN)
		{
			activeBillboards.clear();
			return null;
		}

		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			activeBillboards.clear();
			return null;
		}

		List<RenderableBillboard> visibleBillboards = getVisibleBillboards(worldView);
		visibleBillboards.sort(Comparator.comparingDouble(RenderableBillboard::getDepth).reversed());
		for (RenderableBillboard billboard : visibleBillboards)
		{
			if (billboard.renderable instanceof Actor)
			{
				renderActor(graphics, (Actor) billboard.renderable);
			}
			else if (billboard.renderable instanceof Projectile)
			{
				renderProjectile(graphics, (Projectile) billboard.renderable);
			}
			else if (billboard.renderable instanceof TileItem)
			{
				renderGroundItem(graphics, (TileItem) billboard.renderable);
			}
		}

		return null;
	}

	void trackGroundItem(TileItem item, Tile tile)
	{
		if (item == null || tile == null)
		{
			return;
		}

		LocalPoint localPoint = tile.getLocalLocation();
		if (localPoint == null)
		{
			return;
		}

		groundItems.put(item, new GroundItemBillboard(tile.getPlane(), localPoint));
	}

	void untrackGroundItem(TileItem item)
	{
		if (item == null)
		{
			return;
		}

		groundItems.remove(item);
		billboardCache.remove(item);
		activeBillboards.remove(item);
	}

	void clearGroundItems()
	{
		for (TileItem item : groundItems.keySet())
		{
			billboardCache.remove(item);
			activeBillboards.remove(item);
		}

		groundItems.clear();
	}

	void seedGroundItems(WorldView worldView)
	{
		if (worldView == null || worldView.getScene() == null)
		{
			return;
		}

		Tile[][][] tiles = worldView.getScene().getTiles();
		if (tiles == null)
		{
			return;
		}

		for (Tile[][] planeTiles : tiles)
		{
			if (planeTiles == null)
			{
				continue;
			}

			for (Tile[] row : planeTiles)
			{
				if (row == null)
				{
					continue;
				}

				for (Tile tile : row)
				{
					if (tile == null)
					{
						continue;
					}

					Collection<TileItem> tileItems = tile.getGroundItems();
					if (tileItems == null)
					{
						continue;
					}

					for (TileItem item : tileItems)
					{
						trackGroundItem(item, tile);
					}
				}
			}
		}
	}

	boolean shouldBillboardNpc(NPC npc)
	{
		ensureActiveBillboardsCurrent();
		return activeBillboards.contains(npc);
	}

	boolean shouldBillboardPlayer(Player player)
	{
		ensureActiveBillboardsCurrent();
		return activeBillboards.contains(player);
	}

	boolean shouldBillboardProjectile(Projectile projectile)
	{
		ensureActiveBillboardsCurrent();
		return activeBillboards.contains(projectile);
	}

	boolean shouldBillboardGroundItem(TileItem item)
	{
		ensureActiveBillboardsCurrent();
		return activeBillboards.contains(item);
	}

	private void renderActor(Graphics2D graphics, Actor actor)
	{
		Model model = actor.getModel();
		LocalPoint localPoint = actor.getLocalLocation();
		if (model == null || localPoint == null)
		{
			return;
		}

		int vertexCount = model.getVerticesCount();
		if (vertexCount <= 0)
		{
			return;
		}

		float[] verticesX = model.getVerticesX();
		float[] verticesY = model.getVerticesY();
		float[] verticesZ = model.getVerticesZ();
		float[] spriteX = new float[vertexCount];
		float[] spriteY = new float[vertexCount];
		float[] spriteDepth = new float[vertexCount];
		int relativeYaw = relativeYaw(actor);
		int relativePitch = relativePitch(actor);
		for (int i = 0; i < vertexCount; i++)
		{
			double[] yawRotated = rotateYaw(verticesX[i], verticesZ[i], relativeYaw);
			double[] pitchRotated = rotatePitch(verticesY[i], yawRotated[1], relativePitch);
			spriteX[i] = (float) yawRotated[0];
			spriteY[i] = (float) pitchRotated[0];
			spriteDepth[i] = (float) pitchRotated[1];
		}
		List<FaceDraw> faces = buildFaces(model, spriteX, spriteY, spriteDepth);
		if (faces.isEmpty())
		{
			return;
		}

		faces.sort(Comparator.comparingDouble(FaceDraw::getDepth).reversed());
		Rectangle bounds = computeBounds(faces);
		if (!isUsableSourceBounds(bounds))
		{
			return;
		}

		int lightBoost = config.billboardLightBoostPercent();
		BillboardCacheKey cacheKey = new BillboardCacheKey(
			actor instanceof Projectile ? ((Projectile) actor).getId() : actor.getAnimation(),
			actor instanceof Projectile ? ((Projectile) actor).getAnimationFrame() : actor.getAnimationFrame(),
			actor instanceof Projectile ? -1 : actor.getPoseAnimation(),
			actor instanceof Projectile ? -1 : actor.getPoseAnimationFrame(),
			relativeYaw,
			relativePitch,
			config.billboardColorBands(),
			lightBoost,
			qualityKey()
		);
		CachedBillboard cached = billboardCache.get(actor);
		if (shouldRefreshCache(actor, cacheKey, bounds))
		{
			BufferedImage image = renderBillboardImage(faces, bounds);
			if (image == null)
			{
				return;
			}

			cached = new CachedBillboard(cacheKey, bounds, image);
			billboardCache.put(actor, cached);
		}

		int verticalOffset = Math.max(0, actor.getAnimationHeightOffset());
		Point basePoint = Perspective.localToCanvas(client, localPoint, actor.getWorldView().getPlane(), verticalOffset);
		Point topPoint = Perspective.localToCanvas(client, localPoint, actor.getWorldView().getPlane(), verticalOffset + actor.getModelHeight());
		Rectangle hullBounds = actor.getConvexHull() != null ? actor.getConvexHull().getBounds() : null;
		if (basePoint == null)
		{
			return;
		}

		double distance = cameraDistance(actor, localPoint, verticalOffset + (actor.getModelHeight() / 2.0));
		if (!isUsableDistance(distance))
		{
			return;
		}

		double perspectiveScale = client.get3dZoom() / Math.max(1.0, distance);
		int distanceHeight = scaledSize(bounds.height, perspectiveScale);
		int projectedHeight = projectedHeight(basePoint, topPoint);
		int hullHeight = hullBounds != null && isUsableDrawDimension(hullBounds.height) ? hullBounds.height : 0;
		int targetHeight = hullHeight > 0 ? hullHeight : Math.max(distanceHeight, projectedHeight);
		int targetWidth = aspectWidth(bounds, targetHeight);
		int anchorX = hullBounds != null ? hullBounds.x + (hullBounds.width / 2) : basePoint.getX();
		int anchorY = hullBounds != null ? hullBounds.y + hullBounds.height : basePoint.getY();
		if (!isUsableCanvasCoordinate(anchorX) || !isUsableCanvasCoordinate(anchorY))
		{
			return;
		}

		int drawX = anchorX - (targetWidth / 2);
		int drawY = anchorY - targetHeight;
		if (!isUsableDrawSize(targetWidth, targetHeight) || isOutsideViewport(drawX, drawY, targetWidth, targetHeight))
		{
			return;
		}

		graphics.drawImage(cached.image, drawX, drawY, targetWidth, targetHeight, null);
	}

	private void renderGroundItem(Graphics2D graphics, TileItem item)
	{
		GroundItemBillboard groundItem = groundItems.get(item);
		Model model = item.getModel();
		if (groundItem == null || model == null)
		{
			return;
		}

		int vertexCount = model.getVerticesCount();
		if (vertexCount <= 0)
		{
			return;
		}

		float[] verticesX = model.getVerticesX();
		float[] verticesY = model.getVerticesY();
		float[] verticesZ = model.getVerticesZ();
		float[] spriteX = new float[vertexCount];
		float[] spriteY = new float[vertexCount];
		float[] spriteDepth = new float[vertexCount];
		int relativeYaw = relativeYaw(item);
		int relativePitch = relativePitch();
		for (int i = 0; i < vertexCount; i++)
		{
			double[] yawRotated = rotateYaw(verticesX[i], verticesZ[i], relativeYaw);
			double[] pitchRotated = rotatePitch(verticesY[i], yawRotated[1], relativePitch);
			spriteX[i] = (float) yawRotated[0];
			spriteY[i] = (float) pitchRotated[0];
			spriteDepth[i] = (float) pitchRotated[1];
		}

		List<FaceDraw> faces = buildFaces(model, spriteX, spriteY, spriteDepth);
		if (faces.isEmpty())
		{
			return;
		}

		faces.sort(Comparator.comparingDouble(FaceDraw::getDepth).reversed());
		Rectangle bounds = computeBounds(faces);
		if (!isUsableSourceBounds(bounds))
		{
			return;
		}

		BillboardCacheKey cacheKey = new BillboardCacheKey(
			item.getId(),
			item.getQuantity(),
			-1,
			-1,
			relativeYaw,
			relativePitch,
			config.billboardColorBands(),
			config.billboardLightBoostPercent(),
			qualityKey()
		);
		CachedBillboard cached = billboardCache.get(item);
		if (shouldRefreshCache(item, cacheKey, bounds))
		{
			BufferedImage image = renderBillboardImage(faces, bounds);
			if (image == null)
			{
				return;
			}

			cached = new CachedBillboard(cacheKey, bounds, image);
			billboardCache.put(item, cached);
		}

		Point basePoint = Perspective.localToCanvas(client, groundItem.localPoint, groundItem.plane, 0);
		Point topPoint = Perspective.localToCanvas(client, groundItem.localPoint, groundItem.plane, item.getModelHeight());
		if (basePoint == null)
		{
			return;
		}

		double distance = cameraDistance(groundItem.localPoint, groundItem.plane, item.getModelHeight() / 2.0);
		if (!isUsableDistance(distance))
		{
			return;
		}

		double perspectiveScale = client.get3dZoom() / Math.max(1.0, distance);
		int distanceHeight = scaledSize(bounds.height, perspectiveScale);
		int projectedHeight = projectedHeight(basePoint, topPoint);
		int targetHeight = distanceHeight > 0 ? distanceHeight : projectedHeight;
		int targetWidth = aspectWidth(bounds, targetHeight);
		if (!isUsableCanvasCoordinate(basePoint.getX()) || !isUsableCanvasCoordinate(basePoint.getY()))
		{
			return;
		}

		int drawX = basePoint.getX() - (targetWidth / 2);
		int drawY = basePoint.getY() - targetHeight;
		if (!isUsableDrawSize(targetWidth, targetHeight) || isOutsideViewport(drawX, drawY, targetWidth, targetHeight))
		{
			return;
		}

		graphics.drawImage(cached.image, drawX, drawY, targetWidth, targetHeight, null);
	}

	private void renderProjectile(Graphics2D graphics, Projectile projectile)
	{
		Model model = projectile.getModel();
		if (model == null)
		{
			return;
		}

		int vertexCount = model.getVerticesCount();
		if (vertexCount <= 0)
		{
			return;
		}

		float[] verticesX = model.getVerticesX();
		float[] verticesY = model.getVerticesY();
		float[] verticesZ = model.getVerticesZ();
		float[] spriteX = new float[vertexCount];
		float[] spriteY = new float[vertexCount];
		float[] spriteDepth = new float[vertexCount];
		int relativeYaw = relativeYaw(projectile);
		int relativePitch = relativePitch();
		for (int i = 0; i < vertexCount; i++)
		{
			double[] yawRotated = rotateYaw(verticesX[i], verticesZ[i], relativeYaw);
			double[] pitchRotated = rotatePitch(verticesY[i], yawRotated[1], relativePitch);
			spriteX[i] = (float) yawRotated[0];
			spriteY[i] = (float) pitchRotated[0];
			spriteDepth[i] = (float) pitchRotated[1];
		}

		List<FaceDraw> faces = buildFaces(model, spriteX, spriteY, spriteDepth);
		if (faces.isEmpty())
		{
			return;
		}

		faces.sort(Comparator.comparingDouble(FaceDraw::getDepth).reversed());
		Rectangle bounds = computeBounds(faces);
		if (!isUsableSourceBounds(bounds))
		{
			return;
		}

		BillboardCacheKey cacheKey = new BillboardCacheKey(
			projectile.getId(),
			projectile.getAnimationFrame(),
			-1,
			-1,
			relativeYaw,
			relativePitch,
			config.billboardColorBands(),
			config.billboardLightBoostPercent(),
			qualityKey()
		);
		CachedBillboard cached = billboardCache.get(projectile);
		if (shouldRefreshCache(projectile, cacheKey, bounds))
		{
			BufferedImage image = renderBillboardImage(faces, bounds);
			if (image == null)
			{
				return;
			}

			cached = new CachedBillboard(cacheKey, bounds, image);
			billboardCache.put(projectile, cached);
		}

		Point basePoint = Perspective.localToCanvas(client, (int) projectile.getX(), (int) projectile.getY(), projectile.getFloor(), projectile.getHeight());
		Point topPoint = Perspective.localToCanvas(client, (int) projectile.getX(), (int) projectile.getY(), projectile.getFloor(), projectile.getHeight() + projectile.getModelHeight());
		if (basePoint == null)
		{
			return;
		}

		double distance = cameraDistance((int) projectile.getX(), (int) projectile.getY(), projectile.getHeight() + (projectile.getModelHeight() / 2.0));
		if (!isUsableDistance(distance))
		{
			return;
		}

		double perspectiveScale = client.get3dZoom() / Math.max(1.0, distance);
		int distanceHeight = scaledSize(bounds.height, perspectiveScale);
		int projectedHeight = projectedHeight(basePoint, topPoint);
		int targetHeight = Math.max(distanceHeight, projectedHeight);
		int targetWidth = aspectWidth(bounds, targetHeight);
		if (!isUsableCanvasCoordinate(basePoint.getX()) || !isUsableCanvasCoordinate(basePoint.getY()))
		{
			return;
		}

		int drawX = basePoint.getX() - (targetWidth / 2);
		int drawY = basePoint.getY() - targetHeight;
		if (!isUsableDrawSize(targetWidth, targetHeight) || isOutsideViewport(drawX, drawY, targetWidth, targetHeight))
		{
			return;
		}

		graphics.drawImage(cached.image, drawX, drawY, targetWidth, targetHeight, null);
	}

	boolean shouldHideNpc(NPC npc)
	{
		return shouldBillboardNpc(npc);
	}

	boolean shouldHidePlayer(Player player)
	{
		return shouldBillboardPlayer(player);
	}

	boolean shouldHideProjectile(Projectile projectile)
	{
		return shouldBillboardProjectile(projectile);
	}

	boolean shouldHideGroundItem(TileItem item)
	{
		return shouldBillboardGroundItem(item);
	}

	private List<RenderableBillboard> getVisibleBillboards(WorldView worldView)
	{
		ensureActiveBillboardsCurrent(worldView);

		List<RenderableBillboard> visibleBillboards = new ArrayList<>(activeBillboards.size());
		for (Renderable renderable : activeBillboards)
		{
			if (renderable instanceof NPC)
			{
				visibleBillboards.add(new RenderableBillboard(renderable, billboardDepth((NPC) renderable)));
			}
			else if (renderable instanceof Player)
			{
				visibleBillboards.add(new RenderableBillboard(renderable, billboardDepth((Player) renderable)));
			}
			else if (renderable instanceof Projectile)
			{
				visibleBillboards.add(new RenderableBillboard(renderable, billboardDepth((Projectile) renderable)));
			}
			else if (renderable instanceof TileItem)
			{
				visibleBillboards.add(new RenderableBillboard(renderable, billboardDepth((TileItem) renderable)));
			}
		}

		return visibleBillboards;
	}

	private void ensureActiveBillboardsCurrent()
	{
		ensureActiveBillboardsCurrent(client.getTopLevelWorldView());
	}

	private void ensureActiveBillboardsCurrent(WorldView worldView)
	{
		int gameCycle = client.getGameCycle();
		if (activeBillboardsGameCycle == gameCycle)
		{
			return;
		}

		activeBillboards.clear();
		activeBillboardsGameCycle = gameCycle;
		if (!config.enable2dBillboardSprites() || client.getGameState() != GameState.LOGGED_IN || worldView == null)
		{
			return;
		}

		List<RenderableBillboard> candidates = collectCandidates(worldView);
		candidates.sort(Comparator.comparingDouble(RenderableBillboard::getDepth));
		int limit = Math.max(1, config.billboardMaxEntities());
		int count = Math.min(limit, candidates.size());
		for (int i = 0; i < count; i++)
		{
			activeBillboards.add(candidates.get(i).renderable);
		}
	}

	private List<RenderableBillboard> collectCandidates(WorldView worldView)
	{
		Player localPlayer = client.getLocalPlayer();
		LocalPoint localPlayerLocation = localPlayer != null ? localPlayer.getLocalLocation() : null;
		Rectangle viewport = getViewportBounds();
		List<RenderableBillboard> candidates = new ArrayList<>();

		if (config.applyToNpcs())
		{
			for (NPC npc : worldView.npcs())
			{
				if (npc == null || !isEligibleActor(localPlayerLocation, npc.getLocalLocation(), npc, viewport))
				{
					continue;
				}

				candidates.add(new RenderableBillboard(npc, billboardDepth(npc)));
			}
		}

		if (config.applyToPlayers())
		{
			for (Player player : worldView.players())
			{
				if (player == null || !isEligibleActor(localPlayerLocation, player.getLocalLocation(), player, viewport))
				{
					continue;
				}

				candidates.add(new RenderableBillboard(player, billboardDepth(player)));
			}
		}

		if (config.applyToProjectiles())
		{
			for (Projectile projectile : client.getProjectiles())
			{
				if (projectile == null || !isEligibleProjectile(projectile, viewport))
				{
					continue;
				}

				candidates.add(new RenderableBillboard(projectile, billboardDepth(projectile)));
			}
		}

		if (config.applyToGroundItems())
		{
			for (Map.Entry<TileItem, GroundItemBillboard> entry : groundItems.entrySet())
			{
				TileItem item = entry.getKey();
				GroundItemBillboard groundItem = entry.getValue();
				if (item == null || groundItem == null || !isEligibleGroundItem(localPlayerLocation, item, groundItem, viewport))
				{
					continue;
				}

				candidates.add(new RenderableBillboard(item, billboardDepth(item)));
			}
		}

		return candidates;
	}

	private boolean isEligibleActor(LocalPoint localPlayerLocation, LocalPoint actorLocation, Actor actor, Rectangle viewport)
	{
		if (localPlayerLocation == null || actorLocation == null)
		{
			return false;
		}

		if (!isWithinRadius(localPlayerLocation, actorLocation, config.billboardRadiusTiles()))
		{
			return false;
		}

		Shape hull = actor.getConvexHull();
		if (hull != null && hull.getBounds().intersects(viewport))
		{
			return true;
		}

		Polygon tilePoly = actor.getCanvasTilePoly();
		if (tilePoly != null && tilePoly.getBounds().intersects(viewport))
		{
			return true;
		}

		int verticalOffset = Math.max(0, actor.getAnimationHeightOffset());
		Point canvasPoint = Perspective.localToCanvas(client, actorLocation, actor.getWorldView().getPlane(), verticalOffset + (actor.getModelHeight() / 2));
		return canvasPoint != null && viewport.contains(canvasPoint.getX(), canvasPoint.getY());
	}

	private boolean isEligibleProjectile(Projectile projectile, Rectangle viewport)
	{
		Point canvasPoint = Perspective.localToCanvas(client, (int) projectile.getX(), (int) projectile.getY(), projectile.getFloor(), projectile.getHeight());
		return canvasPoint != null && viewport.contains(canvasPoint.getX(), canvasPoint.getY());
	}

	private boolean isEligibleGroundItem(LocalPoint localPlayerLocation, TileItem item, GroundItemBillboard groundItem, Rectangle viewport)
	{
		if (localPlayerLocation == null || groundItem.localPoint == null)
		{
			return false;
		}

		if (!isWithinRadius(localPlayerLocation, groundItem.localPoint, config.billboardRadiusTiles()))
		{
			return false;
		}

		Point canvasPoint = Perspective.localToCanvas(client, groundItem.localPoint, groundItem.plane, Math.max(0, item.getModelHeight() / 2));
		return canvasPoint != null && viewport.contains(canvasPoint.getX(), canvasPoint.getY());
	}

	private boolean shouldRefreshCache(Renderable renderable, BillboardCacheKey cacheKey, Rectangle bounds)
	{
		CachedBillboard cached = billboardCache.get(renderable);
		if (cached == null)
		{
			return true;
		}

		boolean sizeChanged = cached.bounds.width != bounds.width || cached.bounds.height != bounds.height;
		if (sizeChanged)
		{
			return true;
		}

		if (cached.key.equals(cacheKey))
		{
			return false;
		}

		return isScheduledRedrawFrame(renderable);
	}

	private boolean isScheduledRedrawFrame(Renderable renderable)
	{
		int interval = Math.max(1, 50 / Math.max(1, config.animationFrameCount()));
		int bucket = Math.floorMod(System.identityHashCode(renderable), interval);
		return Math.floorMod(client.getGameCycle(), interval) == bucket;
	}

	private BufferedImage renderBillboardImage(List<FaceDraw> faces, Rectangle bounds)
	{
		double qualityScale = renderQualityScale();
		int imageWidth = Math.max(1, (int) Math.round(bounds.width * qualityScale));
		int imageHeight = Math.max(1, (int) Math.round(bounds.height * qualityScale));
		if (!isUsableDrawSize(imageWidth, imageHeight))
		{
			return null;
		}

		BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D imageGraphics = image.createGraphics();
		try
		{
			imageGraphics.scale(qualityScale, qualityScale);
			imageGraphics.translate(-bounds.x, -bounds.y);
			for (FaceDraw face : faces)
			{
				//imageGraphics.setColor(face.getColor());
				imageGraphics.setColor(snapToRamp(face.getColor(), config.billboardColorBands()));
				imageGraphics.fillPolygon(face.getPolygon());
			}
		}
		finally
		{
			imageGraphics.dispose();
		}

		//disabled quantize for now
		//quantize(image, config.billboardPaletteSize());
		return image;
	}

	private static boolean isUsableSourceBounds(Rectangle bounds)
	{
		return bounds.width > 0
			&& bounds.height > 0
			&& bounds.width <= MAX_SOURCE_BILLBOARD_SIZE
			&& bounds.height <= MAX_SOURCE_BILLBOARD_SIZE
			&& Math.abs(bounds.x) <= MAX_SOURCE_BILLBOARD_COORDINATE
			&& Math.abs(bounds.y) <= MAX_SOURCE_BILLBOARD_COORDINATE;
	}

	private static boolean isUsableDistance(double distance)
	{
		return Double.isFinite(distance) && distance >= 1.0d;
	}

	private static int scaledSize(int sourceSize, double scale)
	{
		if (!Double.isFinite(scale) || scale <= 0.0d)
		{
			return -1;
		}

		double scaled = sourceSize * scale;
		if (!Double.isFinite(scaled) || scaled <= 0.0d || scaled > MAX_DRAW_BILLBOARD_SIZE)
		{
			return -1;
		}

		return Math.max(1, (int) Math.round(scaled));
	}

	private static int projectedHeight(Point basePoint, Point topPoint)
	{
		if (basePoint == null || topPoint == null)
		{
			return 0;
		}

		long height = Math.abs((long) basePoint.getY() - topPoint.getY());
		if (height > MAX_DRAW_BILLBOARD_SIZE)
		{
			return -1;
		}

		return (int) height;
	}

	private static int aspectWidth(Rectangle bounds, int targetHeight)
	{
		if (targetHeight <= 0 || bounds.height <= 0)
		{
			return -1;
		}

		double width = bounds.width * (targetHeight / (double) bounds.height);
		if (!Double.isFinite(width) || width <= 0.0d || width > MAX_DRAW_BILLBOARD_SIZE)
		{
			return -1;
		}

		return Math.max(1, (int) Math.round(width));
	}

	private static boolean isUsableDrawSize(int width, int height)
	{
		return isUsableDrawDimension(width) && isUsableDrawDimension(height);
	}

	private static boolean isUsableDrawDimension(int dimension)
	{
		return dimension > 0 && dimension <= MAX_DRAW_BILLBOARD_SIZE;
	}

	private static boolean isUsableCanvasCoordinate(int coordinate)
	{
		return Math.abs(coordinate) <= MAX_CANVAS_COORDINATE;
	}
	
	private Color snapToRamp(Color color, int colorBands)
	{
		int alpha = color.getAlpha();

		if (alpha == 0)
		{
			return color;
		}

		float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);

		float h = hsb[0];
		float s = hsb[1];
		float b = hsb[2];
		
		// Optional: make colours a bit more punchy before banding
		s = clamp01(s * 1.15f);
		//b = clamp01((b - 0.5f) * 1.25f + 0.5f);
		b = snapBrightness(b);

		int bands = Math.max(1, colorBands);
		if (bands == 1)
		{
			b = 0.5f;
		}
		else
		{
			int band = Math.min(bands - 1, (int) (b * bands));
			b = band / (float) (bands - 1);
		}

		int rgb = Color.HSBtoRGB(h, s, b);

		return new Color(
			(rgb >> 16) & 0xFF,
			(rgb >> 8) & 0xFF,
			rgb & 0xFF,
			alpha
		);
		
	}
			
	private float snapBrightness(float b)
	{
		
		int bands = config.billboardColorBands();
		float minBrightness = 0.1f;
		float maxBrightness = 0.9f;
		
		bands = Math.max(1, bands);

		minBrightness = clamp01(minBrightness);
		maxBrightness = clamp01(maxBrightness);

		if (maxBrightness < minBrightness)
		{
			float temp = minBrightness;
			minBrightness = maxBrightness;
			maxBrightness = temp;
		}

		// Compress incoming brightness into the configured output range.
		float compressed = minBrightness + b * (maxBrightness - minBrightness);

		if (bands == 1)
		{
			return (minBrightness + maxBrightness) * 0.5f;
		}

		// Snap to nearest band.
		float normalized = (compressed - minBrightness) / (maxBrightness - minBrightness);
		float snapped = Math.round(normalized * (bands - 1)) / (float) (bands - 1);

		return minBrightness + snapped * (maxBrightness - minBrightness);
	}

	private static float clamp01(float value)
	{
		return Math.max(0.0f, Math.min(1.0f, value));
	}
	

	private double renderQualityScale()
	{
		return Math.max(MIN_RENDER_QUALITY, Math.min(1.0d, config.renderBillboardQuality() / 100.0d));
	}

	private int qualityKey()
	{
		return (int) Math.round(renderQualityScale() * 10_000.0d);
	}

	private double cameraDistance(Actor actor, LocalPoint localPoint, double verticalOffset)
	{
		double dx = localPoint.getX() - client.getCameraFpX();
		double dy = localPoint.getY() - client.getCameraFpY();
		double groundHeight = Perspective.getTileHeight(client, localPoint, actor.getWorldView().getPlane());
		double dz = (groundHeight + verticalOffset) - client.getCameraFpZ();
		return Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
	}

	private double cameraDistance(int x, int y, double z)
	{
		double dx = x - client.getCameraFpX();
		double dy = y - client.getCameraFpY();
		double dz = z - client.getCameraFpZ();
		return Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
	}

	private double cameraDistance(LocalPoint localPoint, int plane, double verticalOffset)
	{
		double dx = localPoint.getX() - client.getCameraFpX();
		double dy = localPoint.getY() - client.getCameraFpY();
		double groundHeight = Perspective.getTileHeight(client, localPoint, plane);
		double dz = (groundHeight + verticalOffset) - client.getCameraFpZ();
		return Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
	}

	private double billboardDepth(NPC npc)
	{
		LocalPoint localPoint = npc.getLocalLocation();
		if (localPoint == null)
		{
			return Double.NEGATIVE_INFINITY;
		}

		return cameraDistance(npc, localPoint, Math.max(0, npc.getAnimationHeightOffset()) + (npc.getModelHeight() / 2.0));
	}

	private double billboardDepth(Player player)
	{
		LocalPoint localPoint = player.getLocalLocation();
		if (localPoint == null)
		{
			return Double.NEGATIVE_INFINITY;
		}

		return cameraDistance(player, localPoint, Math.max(0, player.getAnimationHeightOffset()) + (player.getModelHeight() / 2.0));
	}

	private double billboardDepth(Projectile projectile)
	{
		return cameraDistance((int) projectile.getX(), (int) projectile.getY(), projectile.getHeight() + (projectile.getModelHeight() / 2.0));
	}

	private double billboardDepth(TileItem item)
	{
		GroundItemBillboard groundItem = groundItems.get(item);
		if (groundItem == null)
		{
			return Double.NEGATIVE_INFINITY;
		}

		return cameraDistance(groundItem.localPoint, groundItem.plane, item.getModelHeight() / 2.0);
	}

	private boolean isWithinRadius(LocalPoint source, LocalPoint target, int radiusTiles)
	{
		int dx = source.getX() - target.getX();
		int dy = source.getY() - target.getY();
		int radius = radiusTiles * LOCAL_TILE_SIZE;
		return (dx * dx) + (dy * dy) <= (radius * radius);
	}

	private boolean isInsideViewport(int x, int y)
	{
		int viewportX = client.getViewportXOffset();
		int viewportY = client.getViewportYOffset();
		int viewportWidth = client.getViewportWidth();
		int viewportHeight = client.getViewportHeight();
		return x >= viewportX && x < viewportX + viewportWidth && y >= viewportY && y < viewportY + viewportHeight;
	}

	private boolean isOutsideViewport(int x, int y, int width, int height)
	{
		int viewportX = client.getViewportXOffset();
		int viewportY = client.getViewportYOffset();
		int viewportWidth = client.getViewportWidth();
		int viewportHeight = client.getViewportHeight();
		return (long) x + width < viewportX
			|| x > (long) viewportX + viewportWidth
			|| (long) y + height < viewportY
			|| y > (long) viewportY + viewportHeight;
	}

	private Rectangle getViewportBounds()
	{
		return new Rectangle(
			client.getViewportXOffset(),
			client.getViewportYOffset(),
			client.getViewportWidth(),
			client.getViewportHeight()
		);
	}

	private List<FaceDraw> buildFaces(
		Model model,
		float[] spriteX,
		float[] spriteY,
		float[] spriteDepth
	)
	{
		List<FaceDraw> faces = new ArrayList<>(model.getFaceCount());
		int[] faceIndices1 = model.getFaceIndices1();
		int[] faceIndices2 = model.getFaceIndices2();
		int[] faceIndices3 = model.getFaceIndices3();
		int[] faceColors1 = model.getFaceColors1();
		int[] faceColors2 = model.getFaceColors2();
		int[] faceColors3 = model.getFaceColors3();
		short[] unlitFaceColors = model.getUnlitFaceColors();
		byte[] transparencies = model.getFaceTransparencies();
		short[] textures = model.getFaceTextures();

		for (int face = 0; face < model.getFaceCount(); face++)
		{
			if (textures != null && face < textures.length && textures[face] != -1)
			{
				continue;
			}

			int a = faceIndices1[face];
			int b = faceIndices2[face];
			int c = faceIndices3[face];

			Polygon polygon = new Polygon(
				new int[]{Math.round(spriteX[a]), Math.round(spriteX[b]), Math.round(spriteX[c])},
				new int[]{Math.round(spriteY[a]), Math.round(spriteY[b]), Math.round(spriteY[c])},
				3
			);

			int alpha = transparencies == null || face >= transparencies.length ? 255 : 255 - (transparencies[face] & 0xFF);
			Color color = applyLightBoost(hslToColor(faceColor(face, faceColors1, faceColors2, faceColors3, unlitFaceColors), alpha));

			double depth = (
				spriteDepth[a] +
				spriteDepth[b] +
				spriteDepth[c]
			) / 3.0;

			faces.add(new FaceDraw(polygon, color, depth));
		}

		return faces;
	}

	private static int faceColor(int face, int[] faceColors1, int[] faceColors2, int[] faceColors3, short[] unlitFaceColors)
	{
		if (USE_UNLIT_COLORS && unlitFaceColors != null && face < unlitFaceColors.length && unlitFaceColors[face] != -1)
		{
			return Short.toUnsignedInt(unlitFaceColors[face]);
		}

		return (faceColors1[face] + faceColors2[face] + faceColors3[face]) / 3;
	}

	private static Rectangle computeBounds(List<FaceDraw> faces)
	{
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;

		for (FaceDraw face : faces)
		{
			Polygon polygon = face.getPolygon();
			for (int i = 0; i < polygon.npoints; i++)
			{
				minX = Math.min(minX, polygon.xpoints[i]);
				minY = Math.min(minY, polygon.ypoints[i]);
				maxX = Math.max(maxX, polygon.xpoints[i]);
				maxY = Math.max(maxY, polygon.ypoints[i]);
			}
		}

		if (minX == Integer.MAX_VALUE)
		{
			return new Rectangle();
		}

		return new Rectangle(minX, minY, Math.max(1, (maxX - minX) + 1), Math.max(1, (maxY - minY) + 1));
	}

	private static void quantize(BufferedImage image, int paletteSize)
	{
		int width = image.getWidth();
		int height = image.getHeight();
		int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
		int[] palette = buildPalette(pixels, Math.max(2, paletteSize));
		if (palette.length == 0)
		{
			return;
		}

		float[] redError = new float[pixels.length];
		float[] greenError = new float[pixels.length];
		float[] blueError = new float[pixels.length];

		for (int y = 0; y < height; y++)
		{
			for (int x = 0; x < width; x++)
			{
				int index = (y * width) + x;
				int argb = pixels[index];
				int alpha = (argb >>> 24) & 0xFF;
				if (alpha == 0)
				{
					pixels[index] = TRANSPARENT;
					continue;
				}

				float sourceR = clampChannel(((argb >> 16) & 0xFF) + redError[index]);
				float sourceG = clampChannel(((argb >> 8) & 0xFF) + greenError[index]);
				float sourceB = clampChannel((argb & 0xFF) + blueError[index]);
				int nearest = nearestColor(palette, sourceR, sourceG, sourceB);
				pixels[index] = (alpha << 24) | nearest;

				float errorR = sourceR - ((nearest >> 16) & 0xFF);
				float errorG = sourceG - ((nearest >> 8) & 0xFF);
				float errorB = sourceB - (nearest & 0xFF);

				diffuse(width, height, x, y, redError, greenError, blueError, errorR, errorG, errorB);
			}
		}

		image.setRGB(0, 0, width, height, pixels, 0, width);
	}

	private static int[] buildPalette(int[] pixels, int paletteSize)
	{
		Map<Integer, Integer> histogram = new HashMap<>();
		for (int pixel : pixels)
		{
			int alpha = (pixel >>> 24) & 0xFF;
			if (alpha == 0)
			{
				continue;
			}

			int bucket = quantizeBucket(pixel);
			histogram.merge(bucket, 1, Integer::sum);
		}

		if (histogram.isEmpty())
		{
			return new int[0];
		}

		return histogram.entrySet().stream()
			.sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
			.limit(paletteSize)
			.mapToInt(entry -> bucketToColor(entry.getKey()))
			.toArray();
	}

	private static int quantizeBucket(int argb)
	{
		int red = (argb >> 16) & 0xFF;
		int green = (argb >> 8) & 0xFF;
		int blue = argb & 0xFF;
		int r = red >> 3;
		int g = green >> 3;
		int b = blue >> 3;
		return (r << 10) | (g << 5) | b;
	}

	private static int bucketToColor(int bucket)
	{
		int red = ((bucket >> 10) & 0x1F) * 255 / 31;
		int green = ((bucket >> 5) & 0x1F) * 255 / 31;
		int blue = (bucket & 0x1F) * 255 / 31;
		return (red << 16) | (green << 8) | blue;
	}

	private static int nearestColor(int[] palette, float red, float green, float blue)
	{
		int nearest = palette[0];
		float bestDistance = Float.MAX_VALUE;

		for (int color : palette)
		{
			float dr = red - ((color >> 16) & 0xFF);
			float dg = green - ((color >> 8) & 0xFF);
			float db = blue - (color & 0xFF);
			float distance = (dr * dr) + (dg * dg) + (db * db);
			if (distance < bestDistance)
			{
				bestDistance = distance;
				nearest = color;
			}
		}

		return nearest;
	}

	private static void diffuse(
		int width,
		int height,
		int x,
		int y,
		float[] redError,
		float[] greenError,
		float[] blueError,
		float errorR,
		float errorG,
		float errorB
	)
	{
		applyError(width, height, x + 1, y, redError, greenError, blueError, errorR, errorG, errorB, 7.0f / 16.0f);
		applyError(width, height, x - 1, y + 1, redError, greenError, blueError, errorR, errorG, errorB, 3.0f / 16.0f);
		applyError(width, height, x, y + 1, redError, greenError, blueError, errorR, errorG, errorB, 5.0f / 16.0f);
		applyError(width, height, x + 1, y + 1, redError, greenError, blueError, errorR, errorG, errorB, 1.0f / 16.0f);
	}

	private static void applyError(
		int width,
		int height,
		int x,
		int y,
		float[] redError,
		float[] greenError,
		float[] blueError,
		float errorR,
		float errorG,
		float errorB,
		float weight
	)
	{
		if (x < 0 || y < 0 || x >= width || y >= height)
		{
			return;
		}

		int index = (y * width) + x;
		redError[index] += errorR * weight;
		greenError[index] += errorG * weight;
		blueError[index] += errorB * weight;
	}

	private static float clampChannel(float channel)
	{
		return Math.max(0.0f, Math.min(255.0f, channel));
	}

	private int relativeYaw(Actor actor)
	{
		//int rawRelativeYaw = actor.getCurrentOrientation() - client.getCameraYaw();
		int rawRelativeYaw = client.getCameraYaw() + actor.getCurrentOrientation();
		if (shouldCombatSnap(actor))
		{
			return combatYaw(rawRelativeYaw);
		}

		if (!config.enableRotationSnapping())
		{
			return Math.floorMod(rawRelativeYaw, FULL_CIRCLE);
		}

		return snapJauByAngles(rawRelativeYaw, config.numberOfYawRotationAngles());
	}

	private int relativeYaw(Projectile projectile)
	{
		//int rawRelativeYaw = projectile.getOrientation() - client.getCameraYaw();
		int rawRelativeYaw = client.getCameraYaw() - projectile.getOrientation();
		if (!config.enableRotationSnapping())
		{
			return Math.floorMod(rawRelativeYaw, FULL_CIRCLE);
		}

		return snapJauByAngles(rawRelativeYaw, config.numberOfYawRotationAngles());
	}

	private int relativeYaw(TileItem item)
	{
		int rawRelativeYaw = client.getCameraYaw();
		if (!config.enableRotationSnapping())
		{
			return Math.floorMod(rawRelativeYaw, FULL_CIRCLE);
		}

		return snapJauByAngles(rawRelativeYaw, config.numberOfYawRotationAngles());
	}

	private boolean shouldCombatSnap(Actor actor)
	{
		return config.enableBillboardCombatSnapping() && isMutuallyInteracting(actor);
	}

	private boolean isMutuallyInteracting(Actor actor)
	{
		if (actor == null)
		{
			return false;
		}

		Actor interacting = actor.getInteracting();
		return interacting != null && interacting.getInteracting() == actor;
	}

	private static int combatYaw(int rawRelativeYaw)
	{
		int normalized = Math.floorMod(rawRelativeYaw, FULL_CIRCLE);
		int combatDistance = jauDistance(normalized, COMBAT_YAW);
		int oppositeDistance = jauDistance(normalized, OPPOSITE_COMBAT_YAW);
		return combatDistance <= oppositeDistance ? COMBAT_YAW : OPPOSITE_COMBAT_YAW;
	}

	private static int jauDistance(int a, int b)
	{
		int distance = Math.abs(Math.floorMod(a, FULL_CIRCLE) - Math.floorMod(b, FULL_CIRCLE));
		return Math.min(distance, FULL_CIRCLE - distance);
	}

	private int relativePitch()
	{
		int rawPitch = Math.max(0, Math.min(MAX_PITCH, client.getCameraPitch()));
		if (!config.enableRotationSnapping())
		{
			return rawPitch;
		}

		int res = snapPitchByAngles(rawPitch, 0, config.numberOfPitchRotationAngles());
		//invert as NPCs rotate negatively to look upwards
		return -res;
	}

	private int relativePitch(Actor actor)
	{
		if (shouldCombatSnap(actor))
		{
			return 0;
		}

		return relativePitch();
	}

	private static int snapJauByAngles(int jau, int angleCount)
	{
		int clampedAngleCount = Math.max(1, angleCount);
		int step = Math.max(1, FULL_CIRCLE / clampedAngleCount);
		int normalized = Math.floorMod(jau, FULL_CIRCLE);
		return Math.floorMod(((normalized + (step / 2)) / step) * step, FULL_CIRCLE);
	}

	private static int snapPitchByAngles(int pitch, int minPitch, int angleCount)
	{
		int clampedMinPitch = Math.max(0, Math.min(minPitch, MAX_PITCH));
		int clampedPitch = Math.max(clampedMinPitch, Math.min(pitch, MAX_PITCH));
		int clampedAngleCount = Math.max(1, angleCount);
		int pitchRange = Math.max(1, MAX_PITCH - clampedMinPitch);
		if (clampedAngleCount == 1)
		{
			return clampedMinPitch;
		}

		int snappedIndex = Math.max(0, Math.min(clampedAngleCount - 1, ((clampedPitch - clampedMinPitch) * clampedAngleCount) / pitchRange));
		if (snappedIndex == 0)
		{
			return clampedMinPitch;
		}
		if (snappedIndex == clampedAngleCount - 1)
		{
			return MAX_PITCH;
		}

		int snappedPitch = clampedMinPitch + (int) Math.round(((snappedIndex + 0.5d) * pitchRange) / clampedAngleCount);
		return Math.max(clampedMinPitch, Math.min(MAX_PITCH, snappedPitch));
	}

	private Color applyLightBoost(Color color)
	{
		float boost = config.billboardLightBoostPercent() / 100.0f;
		int red = Math.min(255, Math.round(color.getRed() * boost));
		int green = Math.min(255, Math.round(color.getGreen() * boost));
		int blue = Math.min(255, Math.round(color.getBlue() * boost));
		return new Color(red, green, blue, color.getAlpha());
	}

	private static double[] rotateYaw(float x, float z, int orientation)
	{
		double sin = Perspective.SINE[orientation] / 65536.0;
		double cos = Perspective.COSINE[orientation] / 65536.0;
		double rotatedX = (x * cos) + (z * sin);
		double rotatedZ = (z * cos) - (x * sin);
		return new double[]{rotatedX, rotatedZ};
	}

	private static double[] rotatePitch(float y, double depth, int pitch)
	{
		int inversePitch = Math.floorMod(-pitch, FULL_CIRCLE);
		double sin = Perspective.SINE[inversePitch] / 65536.0;
		double cos = Perspective.COSINE[inversePitch] / 65536.0;
		double rotatedY = (y * cos) - (depth * sin);
		double rotatedDepth = (depth * cos) + (y * sin);
		return new double[]{rotatedY, rotatedDepth};
	}

	private static Color hslToColor(int packedHsl, int alpha)
	{
		int hue = (packedHsl >> 10) & 0x3F;
		int saturation = (packedHsl >> 7) & 0x07;
		int lightness = packedHsl & 0x7F;
		float h = hue / 64.0f;
		float s = saturation / 8.0f;
		float l = lightness / 128.0f;

		float r;
		float g;
		float b;

		if (s == 0.0f)
		{
			r = l;
			g = l;
			b = l;
		}
		else
		{
			float q = l < 0.5f ? l * (1.0f + s) : l + s - (l * s);
			float p = 2.0f * l - q;
			r = hueToRgb(p, q, h + (1.0f / 3.0f));
			g = hueToRgb(p, q, h);
			b = hueToRgb(p, q, h - (1.0f / 3.0f));
		}

		return new Color(clamp(r), clamp(g), clamp(b), Math.max(0, Math.min(255, alpha)));
	}

	private static float hueToRgb(float p, float q, float t)
	{
		if (t < 0)
		{
			t += 1.0f;
		}
		if (t > 1)
		{
			t -= 1.0f;
		}
		if (t < (1.0f / 6.0f))
		{
			return p + ((q - p) * 6.0f * t);
		}
		if (t < 0.5f)
		{
			return q;
		}
		if (t < (2.0f / 3.0f))
		{
			return p + ((q - p) * ((2.0f / 3.0f) - t) * 6.0f);
		}
		return p;
	}

	private static int clamp(float component)
	{
		return Math.max(0, Math.min(255, Math.round(component * 255.0f)));
	}

	private static final class FaceDraw
	{
		private final Polygon polygon;
		private final Color color;
		private final double depth;

		private FaceDraw(Polygon polygon, Color color, double depth)
		{
			this.polygon = polygon;
			this.color = color;
			this.depth = depth;
		}

		private Polygon getPolygon()
		{
			return polygon;
		}

		private Color getColor()
		{
			return color;
		}

		private double getDepth()
		{
			return depth;
		}
	}

	private static final class BillboardCacheKey
	{
		private final int animationId;
		private final int animationFrame;
		private final int poseAnimationId;
		private final int poseAnimationFrame;
		private final int relativeYaw;
		private final int relativePitch;
		private final int colorBands;
		private final int lightBoost;
		private final int renderQuality;

		private BillboardCacheKey(int animationId, int animationFrame, int poseAnimationId, int poseAnimationFrame, int relativeYaw, int relativePitch, int colorBands, int lightBoost, int renderQuality)
		{
			this.animationId = animationId;
			this.animationFrame = animationFrame;
			this.poseAnimationId = poseAnimationId;
			this.poseAnimationFrame = poseAnimationFrame;
			this.relativeYaw = relativeYaw;
			this.relativePitch = relativePitch;
			this.colorBands = colorBands;
			this.lightBoost = lightBoost;
			this.renderQuality = renderQuality;
		}

		@Override
		public boolean equals(Object o)
		{
			if (this == o)
			{
				return true;
			}
			if (!(o instanceof BillboardCacheKey))
			{
				return false;
			}
			BillboardCacheKey that = (BillboardCacheKey) o;
			return animationId == that.animationId
				&& animationFrame == that.animationFrame
				&& poseAnimationId == that.poseAnimationId
				&& poseAnimationFrame == that.poseAnimationFrame
				&& relativeYaw == that.relativeYaw
				&& relativePitch == that.relativePitch
				&& colorBands == that.colorBands
				&& lightBoost == that.lightBoost
				&& renderQuality == that.renderQuality;
		}

		@Override
		public int hashCode()
		{
			int result = animationId;
			result = 31 * result + animationFrame;
			result = 31 * result + poseAnimationId;
			result = 31 * result + poseAnimationFrame;
			result = 31 * result + relativeYaw;
			result = 31 * result + relativePitch;
			result = 31 * result + colorBands;
			result = 31 * result + lightBoost;
			result = 31 * result + renderQuality;
			return result;
		}
	}

	private static final class CachedBillboard
	{
		private final BillboardCacheKey key;
		private final Rectangle bounds;
		private final BufferedImage image;

		private CachedBillboard(BillboardCacheKey key, Rectangle bounds, BufferedImage image)
		{
			this.key = key;
			this.bounds = new Rectangle(bounds);
			this.image = image;
		}
	}

	private static final class GroundItemBillboard
	{
		private final int plane;
		private final LocalPoint localPoint;

		private GroundItemBillboard(int plane, LocalPoint localPoint)
		{
			this.plane = plane;
			this.localPoint = localPoint;
		}
	}

	private static final class RenderableBillboard
	{
		private final Renderable renderable;
		private final double depth;

		private RenderableBillboard(Renderable renderable, double depth)
		{
			this.renderable = renderable;
			this.depth = depth;
		}

		private double getDepth()
		{
			return depth;
		}
	}
}
