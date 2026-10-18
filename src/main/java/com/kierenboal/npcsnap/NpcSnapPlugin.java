package com.kierenboal.npcsnap;

import com.kierenboal.npcsnap.features.LoginXpDropGuard;
import com.kierenboal.npcsnap.occlusion.BillboardSceneVisibility;
import com.kierenboal.npcsnap.features.SkillingActivityTracker;
import com.kierenboal.npcsnap.features.SkillingThoughtBubbleOverlay;
import com.kierenboal.npcsnap.export.BillboardExportBatch;
import com.kierenboal.npcsnap.export.BillboardExportPaths;
import com.kierenboal.npcsnap.export.BillboardPngExporter;
import com.kierenboal.npcsnap.export.BillboardObjectExportTarget;
import com.kierenboal.npcsnap.rendering.NpcSnapUiTextureManager;
import com.kierenboal.npcsnap.targeting.BillboardHoverInteractionResolver;
import com.kierenboal.npcsnap.targeting.BillboardSceneDrawCallbacks;

import com.google.inject.Provides;
import java.util.HashSet;
import java.util.Set;
import java.awt.Color;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.GraphicsObject;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.Renderable;
import net.runelite.api.Scene;
import net.runelite.api.SpritePixels;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemQuantityChanged;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.PlayerDespawned;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.client.callback.Hooks;
import net.runelite.client.callback.RenderCallbackManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ColorUtil;

@Slf4j
@PluginDescriptor(
	name = "2DScape",
	description = "Janky RuneScape Classic inspired graphics",
	internalName = "2dscape"
)
public class NpcSnapPlugin extends Plugin
	implements Hooks.RenderableDrawListener
{
	private static final String CONFIG_GROUP = "npc-snap";
	private static final String RETRO_OVERHEADS_KEY = "useRetroOverheads";
	private final LoginXpDropGuard loginXpDropGuard = new LoginXpDropGuard();
	private final RendererAvailabilityWarning rendererAvailabilityWarning = new RendererAvailabilityWarning();
	private final NpcSnapChangelog changelog = new NpcSnapChangelog();
	private boolean pendingSkillXpSeed;
	private Scene groundItemScene;
	private NpcSnapUiTextureManager uiTextureManager;
	private BillboardSceneDrawCallbacks sceneDrawCallbacks;
	private NpcSnapConfigChangeHandler configChangeHandler;
	private final Set<String> exportMenuTargetsThisTick = new HashSet<>();
	private volatile boolean active;

	@Inject
	private Client client;

	@Inject
	private BillboardSceneVisibility sceneVisibility;

	@Inject
	private NpcSnapConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private NpcBillboardOverlay billboardOverlay;

	@Inject
	private SkillingThoughtBubbleOverlay skillingThoughtBubbleOverlay;

	@Inject
	private SkillingActivityTracker skillingActivityTracker;

	@Inject
	private NpcSnapDebug debug;

	@Inject
	private RenderCallbackManager renderCallbackManager;

	@Inject
	private BillboardPngExporter billboardPngExporter;

	@Override
	protected void startUp()
	{
		migrateOcclusionQuality();
		rendererAvailabilityWarning.reset();
		active = true;
		changelog.requestCheck();
		billboardOverlay.setActive(true);
		skillingThoughtBubbleOverlay.setActive(true);
		migrateLegacyRetroOverheadConfig();
		ensureUiTextureManager().markDirty();
		pendingSkillXpSeed = client.getGameState() == GameState.LOGGED_IN;
		overlayManager.add(billboardOverlay);
		overlayManager.add(skillingThoughtBubbleOverlay);
		renderCallbackManager.register(this);
		billboardPngExporter.start();
		log.debug("2DScape started");
	}

	private void migrateOcclusionQuality()
	{
		String value = configManager.getConfiguration(CONFIG_GROUP, "billboardOcclusionQuality");
		if (com.kierenboal.npcsnap.occlusion.BillboardOcclusionQuality.isRetiredValue(value))
		{
			configManager.setConfiguration(CONFIG_GROUP, "billboardOcclusionQuality", "HIGH");
		}
	}

	private void migrateLegacyRetroOverheadConfig()
	{
		if (configManager.getConfiguration(CONFIG_GROUP, RETRO_OVERHEADS_KEY) != null)
		{
			return;
		}

		String legacyHpBar = configManager.getConfiguration(CONFIG_GROUP, "useRetroHpBar");
		String legacyHitsplats = configManager.getConfiguration(CONFIG_GROUP, "useRetroHitsplats");
		String legacyChatEffects = configManager.getConfiguration(CONFIG_GROUP, "useRetroChatEffects");
		if (legacyHpBar == null && legacyHitsplats == null && legacyChatEffects == null)
		{
			return;
		}

		boolean enabled = !isFalse(legacyHpBar) && !isFalse(legacyHitsplats) && !isFalse(legacyChatEffects);
		configManager.setConfiguration(CONFIG_GROUP, RETRO_OVERHEADS_KEY, enabled);
	}

	private static boolean isFalse(String value)
	{
		return "false".equalsIgnoreCase(value);
	}

	@Override
	protected void shutDown()
	{
		// PluginManager removes us from the event bus before calling this method. Keep
		// the direct render hooks harmless even if one cleanup step below fails.
		active = false;
		cleanupStep("billboard overlay activity", () -> billboardOverlay.setActive(false));
		cleanupStep("thought bubble overlay activity", () -> skillingThoughtBubbleOverlay.setActive(false));
		cleanupStep("scene visibility", sceneVisibility::stop);
		cleanupStep("render callback", () -> renderCallbackManager.unregister(this));
		cleanupStep("thought bubble overlay", () -> overlayManager.remove(skillingThoughtBubbleOverlay));
		cleanupStep("billboard overlay", () -> overlayManager.remove(billboardOverlay));
		cleanupStep("ground item state", this::clearGroundItemState);
		cleanupStep("tile object state", billboardOverlay::clearTileObjects);
		cleanupStep("billboard cache", billboardOverlay::clearBillboardCache);
		cleanupStep("texture cache", billboardOverlay::clearTextureCache);
		cleanupStep("interaction state", billboardOverlay::clearInteractionState);
		cleanupStep("skilling state", skillingActivityTracker::clear);
		pendingSkillXpSeed = false;
		rendererAvailabilityWarning.reset();
		cleanupStep("debug frame state", debug::clearFrameStates);
		cleanupStep("PNG exporter", billboardPngExporter::shutDown);
		cleanupStep("UI texture restoration", () -> ensureUiTextureManager().restore());
		log.debug("2DScape stopped");
	}

	private void cleanupStep(String name, Runnable cleanup)
	{
		try
		{
			cleanup.run();
		}
		catch (RuntimeException ex)
		{
			// A broken client-side object should not prevent later hooks from being
			// removed. RuneLite has already marked the plugin inactive at this point.
			log.debug("2DScape cleanup step failed: " + name, ex);
		}
	}

	@Subscribe
	public void onBeforeRender(BeforeRender beforeRender)
	{
		if (!active)
		{
			return;
		}

		sceneVisibility.beginFrame();
		debug.clearFrameStates();
		billboardOverlay.beginFrame();
		syncUiTextureQuality();

		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		WorldView worldView = client.getTopLevelWorldView();
		ensureGroundItemsSeeded(worldView);
		if (worldView == null)
		{
			return;
		}

		billboardOverlay.prepareFrame(worldView);
	}

	@Provides
	NpcSnapConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(NpcSnapConfig.class);
	}

	@Subscribe
	public void onItemSpawned(ItemSpawned itemSpawned)
	{
		billboardOverlay.trackGroundItem(itemSpawned.getItem(), itemSpawned.getTile());
	}

	@Subscribe
	public void onItemDespawned(ItemDespawned itemDespawned)
	{
		billboardOverlay.untrackGroundItem(itemDespawned.getItem());
	}

	@Subscribe
	public void onItemQuantityChanged(ItemQuantityChanged itemQuantityChanged)
	{
		billboardOverlay.trackGroundItem(itemQuantityChanged.getItem(), itemQuantityChanged.getTile());
	}

	@Subscribe
	public void onStatChanged(StatChanged statChanged)
	{
		if (loginXpDropGuard.shouldIgnore(statChanged, client.getGameState(), skillingActivityTracker))
		{
			skillingActivityTracker.seedXp(statChanged.getSkill(), statChanged.getXp());
			return;
		}

		long timeoutMillis = Math.max(1, config.skillingTimeoutSeconds()) * 1000L;
		skillingActivityTracker.recordXp(statChanged.getSkill(), statChanged.getXp(), System.currentTimeMillis(), timeoutMillis);
	}

	@Subscribe
	public void onGameTick(GameTick gameTick)
	{
		changelog.check(active && client.getGameState() == GameState.LOGGED_IN,
			() -> configManager.getConfiguration(CONFIG_GROUP, NpcSnapChangelog.LAST_SEEN_VERSION_KEY),
			message -> client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "2DScape", message, null),
			version -> configManager.setConfiguration(CONFIG_GROUP, NpcSnapChangelog.LAST_SEEN_VERSION_KEY, version));
		loginXpDropGuard.advanceTick();
		if (rendererAvailabilityWarning.shouldWarn(
			client.getGameState() == GameState.LOGGED_IN && config.enable2dBillboardSprites(),
			client.getDrawCallbacks() != null))
		{
			client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "2DScape",
				"ERROR: Enable RuneLite's GPU plugin or 117 HD. 2DScape needs a GPU renderer to hide the original 3D models.", null);
		}
		billboardOverlay.clearStaleInteraction(client.getLocalPlayer(), client.getTickCount());
		if (!pendingSkillXpSeed || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		seedCurrentSkillXp();
		pendingSkillXpSeed = false;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		clearGroundItemState();
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
		{
			changelog.requestCheck();
			skillingActivityTracker.clear();
			pendingSkillXpSeed = true;
			loginXpDropGuard.onLoggedIn();
			ensureUiTextureManager().markDirty();
			billboardOverlay.clearTextureCache();
		}
		else
		{
			skillingActivityTracker.clear();
			pendingSkillXpSeed = false;
			loginXpDropGuard.onLoggedOut();
			billboardOverlay.clearInteractionState();
			ensureUiTextureManager().restore();
			ensureUiTextureManager().markDirty();
		}
	}

	@Subscribe
	public void onProfileChanged(ProfileChanged profileChanged)
	{
		changelog.requestCheck();
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded widgetLoaded)
	{
		ensureUiTextureManager().onWidgetLoaded(config.enableUiTextureBanding(), config.uiTextureColorBands(),
			config.uiSpriteQuality(), config.applyToCustomUiTextures());
	}

	@Subscribe
	public void onClientTick(ClientTick clientTick)
	{
		exportMenuTargetsThisTick.clear();
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!config.enableShiftRightClickExportPng())
		{
			return;
		}

		MenuEntry source = event.getMenuEntry();
		if (!client.isKeyPressed(KeyCode.KC_SHIFT))
		{
			return;
		}
		if (isEquipmentTab(source))
		{
			addLocalPlayerExportMenuEntry();
			return;
		}
		if (!isExportableMenuEntry(source))
		{
			return;
		}

		String key = exportMenuKey(source);
		if (!exportMenuTargetsThisTick.add(key))
		{
			return;
		}
		BillboardObjectExportTarget objectTarget = source.getActor() == null
			&& !BillboardHoverInteractionResolver.isGroundItemAction(source.getType())
			? BillboardObjectExportTarget.snapshot(client, source) : null;
		client.createMenuEntry(-1)
			.setOption("Export sprite")
			.setTarget(source.getTarget())
			.setType(MenuAction.RUNELITE)
			.onClick(ignored -> exportBillboard(source, objectTarget));
	}

	private void addLocalPlayerExportMenuEntry()
	{
		Player player = client.getLocalPlayer();
		if (player == null || !exportMenuTargetsThisTick.add("local-player-equipment"))
		{
			return;
		}
		String target = ColorUtil.wrapWithColorTag(
			BillboardExportPaths.sanitizeName(player.getName(), "Player").replace('_', ' '),
			Color.WHITE);
		client.createMenuEntry(-1)
			.setOption("Export sprite")
			.setTarget(target)
			.setType(MenuAction.RUNELITE)
			.onClick(ignored -> exportLocalPlayer());
	}

	private void exportLocalPlayer()
	{
		BillboardExportBatch batch = billboardOverlay.captureExport(client.getLocalPlayer());
		if (batch == null)
		{
			client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "2DScape",
				"The local player is no longer available for export.", null);
			return;
		}
		billboardPngExporter.export(batch, this::getPluginDirectory);
	}

	private static boolean isEquipmentTab(MenuEntry entry)
	{
		if (entry == null || entry.getWidget() == null)
		{
			return false;
		}
		int widgetId = entry.getWidget().getId();
		return widgetId == InterfaceID.Toplevel.STONE4
			|| widgetId == InterfaceID.ToplevelOsrsStretch.STONE4
			|| widgetId == InterfaceID.ToplevelPreEoc.STONE4;
	}

	private static String exportMenuKey(MenuEntry entry)
	{
		if (entry.getActor() != null)
		{
			return "actor:" + System.identityHashCode(entry.getActor());
		}
		return entry.getWorldViewId() + ":" + entry.getIdentifier() + ":" + entry.getParam0() + ":" + entry.getParam1()
			+ ":" + (BillboardHoverInteractionResolver.isGroundItemAction(entry.getType()) ? "item" : "object");
	}

	private void exportBillboard(MenuEntry source, BillboardObjectExportTarget objectTarget)
	{
		BillboardExportBatch batch = objectTarget != null
			? billboardOverlay.captureExport(objectTarget) : billboardOverlay.captureExport(source);
		if (batch == null)
		{
			client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "2DScape",
				"That render target is no longer available for export.", null);
			return;
		}
		billboardPngExporter.export(batch, this::getPluginDirectory);
	}

	private static boolean isExportableMenuEntry(MenuEntry entry)
	{
		if (entry == null)
		{
			return false;
		}
		if (entry.getActor() != null || BillboardHoverInteractionResolver.isGroundItemAction(entry.getType()))
		{
			return true;
		}
		switch (entry.getType())
		{
			case ITEM_USE_ON_GAME_OBJECT:
			case WIDGET_TARGET_ON_GAME_OBJECT:
			case GAME_OBJECT_FIRST_OPTION:
			case GAME_OBJECT_SECOND_OPTION:
			case GAME_OBJECT_THIRD_OPTION:
			case GAME_OBJECT_FOURTH_OPTION:
			case GAME_OBJECT_FIFTH_OPTION:
			case EXAMINE_OBJECT:
				return true;
			default:
				return false;
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked menuOptionClicked)
	{
		MenuEntry entry = menuOptionClicked.getMenuEntry();
		if (BillboardHoverInteractionResolver.isActorInteractionAction(menuOptionClicked.getMenuAction()))
		{
			billboardOverlay.noteActorInteraction(entry.getActor(), client.getTickCount());
			return;
		}
		if (BillboardHoverInteractionResolver.isGroundItemAction(menuOptionClicked.getMenuAction()))
		{
			billboardOverlay.noteGroundItemInteraction(entry);
			return;
		}

		billboardOverlay.clearInteractionState();
	}

	@Subscribe
	public void onMenuOpened(MenuOpened menuOpened)
	{
		client.setMenuEntries(java.util.Arrays.stream(menuOpened.getMenuEntries())
			.filter(entry -> !"Copy to clipboard".equals(entry.getOption())
				|| !"2DScape".equals(BillboardExportPaths.stripTags(entry.getTarget())))
			.toArray(MenuEntry[]::new));
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged interactingChanged)
	{
		if (interactingChanged.getSource() != client.getLocalPlayer())
		{
			return;
		}

		billboardOverlay.onLocalPlayerInteractionChanged(interactingChanged.getTarget(), client.getTickCount());
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned npcDespawned)
	{
		billboardOverlay.clearInteractionIfMatches(npcDespawned.getNpc());
	}

	@Subscribe
	public void onWorldViewUnloaded(net.runelite.api.events.WorldViewUnloaded event)
	{
		billboardOverlay.unloadOcclusionWorldView(event.getWorldView());
	}

	@Subscribe
	public void onGameObjectSpawned(net.runelite.api.events.GameObjectSpawned event)
	{
		billboardOverlay.observeOcclusionTile(event.getTile(), event.getGameObject());
	}

	@Subscribe
	public void onGameObjectDespawned(net.runelite.api.events.GameObjectDespawned event)
	{
		billboardOverlay.observeOcclusionTile(event.getTile(), event.getGameObject());
	}

	@Subscribe
	public void onGroundObjectSpawned(net.runelite.api.events.GroundObjectSpawned event)
	{
		billboardOverlay.observeOcclusionTile(event.getTile(), event.getGroundObject());
	}

	@Subscribe
	public void onGroundObjectDespawned(net.runelite.api.events.GroundObjectDespawned event)
	{
		billboardOverlay.observeOcclusionTile(event.getTile(), event.getGroundObject());
	}

	@Subscribe
	public void onWallObjectSpawned(net.runelite.api.events.WallObjectSpawned event)
	{
		billboardOverlay.observeOcclusionTile(event.getTile(), event.getWallObject());
	}

	@Subscribe
	public void onWallObjectDespawned(net.runelite.api.events.WallObjectDespawned event)
	{
		billboardOverlay.observeOcclusionTile(event.getTile(), event.getWallObject());
	}

	@Subscribe
	public void onDecorativeObjectSpawned(net.runelite.api.events.DecorativeObjectSpawned event)
	{
		billboardOverlay.observeOcclusionTile(event.getTile(), event.getDecorativeObject());
	}

	@Subscribe
	public void onDecorativeObjectDespawned(net.runelite.api.events.DecorativeObjectDespawned event)
	{
		billboardOverlay.observeOcclusionTile(event.getTile(), event.getDecorativeObject());
	}


	@Subscribe
	public void onPlayerDespawned(PlayerDespawned playerDespawned)
	{
		billboardOverlay.clearInteractionIfMatches(playerDespawned.getPlayer());
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged configChanged)
	{
		if (CONFIG_GROUP.equals(configChanged.getGroup()) && "enableSkillingBubbles".equals(configChanged.getKey()) && !config.enableSkillingBubbles())
		{
			skillingActivityTracker.clear();
			pendingSkillXpSeed = true;
		}
		if (CONFIG_GROUP.equals(configChanged.getGroup()) && "billboardOcclusionQuality".equals(configChanged.getKey()))
		{
			migrateOcclusionQuality();
		}
		ensureConfigChangeHandler().handle(configChanged.getGroup(), configChanged.getKey());
	}

	private NpcSnapConfigChangeHandler ensureConfigChangeHandler()
	{
		if (configChangeHandler == null)
		{
			configChangeHandler = new NpcSnapConfigChangeHandler(
				() -> ensureUiTextureManager().markDirty(),
				billboardOverlay::clearBillboardCache);
		}
		return configChangeHandler;
	}

	@Override
	public boolean addEntity(Renderable renderable, boolean drawingUi)
	{
		if (!active)
		{
			return true;
		}

		return ensureSceneDrawCallbacks().addEntity(renderable, drawingUi);
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied hitsplatApplied)
	{
		billboardOverlay.recordHitsplat(hitsplatApplied.getActor(), hitsplatApplied.getHitsplat());
	}

	@Override
	public boolean draw(Renderable renderable, boolean drawingUi)
	{
		if (!active)
		{
			return true;
		}

		return ensureSceneDrawCallbacks().draw(renderable, drawingUi);
	}

	@Override
	public boolean drawObject(Scene scene, TileObject tileObject)
	{
		if (!active)
		{
			return true;
		}

		return ensureSceneDrawCallbacks().drawObject(tileObject);
	}

	private BillboardSceneDrawCallbacks ensureSceneDrawCallbacks()
	{
		if (sceneDrawCallbacks == null)
		{
			sceneDrawCallbacks = new BillboardSceneDrawCallbacks(config, new BillboardSceneDrawCallbacks.OverlayAccess()
			{
				@Override
				public void noteSceneRenderable(Renderable renderable)
				{
					billboardOverlay.noteSceneRenderable(renderable);
				}

				@Override
				public void noteSceneActorDraw(Actor actor)
				{
					billboardOverlay.noteSceneActorDraw(actor);
				}

				@Override
				public void observeTileObject(TileObject tileObject)
				{
					billboardOverlay.observeTileObject(tileObject);
				}

				@Override
				public boolean shouldHideRenderable(Renderable renderable)
				{
					return billboardOverlay.shouldHideRenderable(renderable);
				}

				@Override
				public boolean shouldHideActor2d(Renderable renderable)
				{
					return billboardOverlay.shouldHideActor2d(renderable);
				}

				@Override
				public boolean shouldHideTileObject(TileObject tileObject)
				{
					return billboardOverlay.shouldHideTileObject(tileObject);
				}
			});
		}
		return sceneDrawCallbacks;
	}

	private void syncUiTextureQuality()
	{
		ensureUiTextureManager().sync(config.enableUiTextureBanding(), config.uiTextureColorBands(),
			config.uiSpriteQuality(), config.applyToCustomUiTextures());
	}

	private void ensureGroundItemsSeeded(WorldView worldView)
	{
		Scene scene = worldView == null ? null : worldView.getScene();
		if (scene == groundItemScene)
		{
			return;
		}

		clearGroundItemState();
		groundItemScene = scene;
		if (scene == null)
		{
			return;
		}

		billboardOverlay.seedGroundItems(worldView);
	}

	private void clearGroundItemState()
	{
		try
		{
			billboardOverlay.clearGroundItems();
		}
		finally
		{
			groundItemScene = null;
		}
	}

	private NpcSnapUiTextureManager ensureUiTextureManager()
	{
		if (uiTextureManager == null)
		{
			uiTextureManager = new NpcSnapUiTextureManager(client, this::loadSpriteSnapshot);
		}

		return uiTextureManager;
	}

	private NpcSnapUiTextureManager.SpriteSnapshot loadSpriteSnapshot(int spriteId)
	{
		SpritePixels[] sprites = client.getSprites(client.getIndexSprites(), spriteId, 0);
		if (sprites == null || sprites.length == 0 || sprites[0] == null)
		{
			return null;
		}

		return NpcSnapUiTextureManager.SpriteSnapshot.of(sprites[0]);
	}

	private void seedCurrentSkillXp()
	{
		for (net.runelite.api.Skill skill : skillingActivityTracker.getTrackedSkills())
		{
			skillingActivityTracker.seedXp(skill, client.getSkillExperience(skill));
		}
	}

}
