package com.kierenboal.npcsnap;

import com.kierenboal.npcsnap.features.LoginXpDropGuard;
import com.kierenboal.npcsnap.features.SkillingActivityTracker;
import com.kierenboal.npcsnap.features.SkillingThoughtBubbleOverlay;
import com.kierenboal.npcsnap.rendering.AnimationFrameSnapper;
import com.kierenboal.npcsnap.rendering.NpcSnapTextureBandingManager;
import com.kierenboal.npcsnap.rendering.NpcSnapRendererRefresher;
import com.kierenboal.npcsnap.rendering.NpcSnapUiTextureManager;
import com.kierenboal.npcsnap.targeting.BillboardHoverInteractionResolver;
import com.kierenboal.npcsnap.targeting.BillboardSceneDrawCallbacks;

import com.google.inject.Provides;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GraphicsObject;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.Renderable;
import net.runelite.api.Scene;
import net.runelite.api.SpritePixels;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemQuantityChanged;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.PlayerDespawned;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.client.callback.Hooks;
import net.runelite.client.callback.RenderCallbackManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "2DScape",
	description = "Janky RuneScape Classic inspired graphics"
)
public class NpcSnapPlugin extends Plugin
	implements Hooks.RenderableDrawListener
{
	private final Map<Actor, RenderState> mutatedActors = new HashMap<>();
	private final Runnable restoreFrameListener = this::restoreNpcState;
	private final LoginXpDropGuard loginXpDropGuard = new LoginXpDropGuard();
	private boolean pendingSkillXpSeed;
	private NpcSnapUiTextureManager uiTextureManager;
	private NpcSnapTextureBandingManager textureBandingManager;
	private NpcSnapRendererRefresher rendererRefresher;
	private BillboardSceneDrawCallbacks sceneDrawCallbacks;
	private NpcSnapConfigChangeHandler configChangeHandler;

	@Inject
	private Client client;

	@Inject
	private DrawManager drawManager;

	@Inject
	private NpcSnapConfig config;

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
	private AnimationFrameSnapper animationFrameSnapper;

	@Inject
	private RenderCallbackManager renderCallbackManager;

	@Inject
	private PluginManager pluginManager;

	@Override
	protected void startUp()
	{
		ensureTextureBandingManager().markDirty();
		ensureUiTextureManager().markDirty();
		pendingSkillXpSeed = client.getGameState() == GameState.LOGGED_IN;
		drawManager.registerEveryFrameListener(restoreFrameListener);
		overlayManager.add(billboardOverlay);
		overlayManager.add(skillingThoughtBubbleOverlay);
		renderCallbackManager.register(this);
		log.debug("2DScape started");
	}

	@Override
	protected void shutDown()
	{
		renderCallbackManager.unregister(this);
		overlayManager.remove(skillingThoughtBubbleOverlay);
		overlayManager.remove(billboardOverlay);
		drawManager.unregisterEveryFrameListener(restoreFrameListener);
		restoreNpcState();
		billboardOverlay.clearGroundItems();
		billboardOverlay.clearTileObjects();
		billboardOverlay.clearBillboardCache();
		billboardOverlay.clearTextureCache();
		skillingActivityTracker.clear();
		pendingSkillXpSeed = false;
		animationFrameSnapper.clear();
		debug.clearFrameStates();
		if (ensureTextureBandingManager().restore())
		{
			billboardOverlay.clearTextureCache();
			ensureRendererRefresher().requestRefresh();
		}
		ensureUiTextureManager().restore();
		log.debug("2DScape stopped");
	}

	@Subscribe
	public void onBeforeRender(BeforeRender beforeRender)
	{
		restoreNpcState();
		debug.clearFrameStates();
		billboardOverlay.beginFrame();
		syncUiTextureQuality();

		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		syncGlobalTextureQuality();

		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			return;
		}

		if (config.applyToGroundItems())
		{
			billboardOverlay.syncGroundItems(worldView);
		}

		if (config.applyToNpcs())
		{
			for (NPC npc : worldView.npcs())
			{
				if (npc == null)
				{
					continue;
				}

				applyAnimationFrameSnap(npc);
			}
		}

		if (config.applyToPlayers())
		{
			for (Player player : worldView.players())
			{
				if (player == null)
				{
					continue;
				}

				applyAnimationFrameSnap(player);
			}
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
		loginXpDropGuard.advanceTick();
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
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
		{
			skillingActivityTracker.clear();
			pendingSkillXpSeed = true;
			loginXpDropGuard.onLoggedIn();
			ensureUiTextureManager().markDirty();
			ensureTextureBandingManager().markDirty();
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
	public void onWidgetLoaded(WidgetLoaded widgetLoaded)
	{
		ensureUiTextureManager().onWidgetLoaded(config.enableUiTextureBanding(), config.uiTextureColorBands(), config.uiSpriteQuality());
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked menuOptionClicked)
	{
		if (BillboardHoverInteractionResolver.isActorInteractionAction(menuOptionClicked.getMenuAction()))
		{
			billboardOverlay.noteActorInteraction(menuOptionClicked.getMenuEntry().getActor(), client.getTickCount());
			return;
		}
		if (BillboardHoverInteractionResolver.isGroundItemAction(menuOptionClicked.getMenuAction()))
		{
			billboardOverlay.noteGroundItemInteraction(menuOptionClicked.getMenuEntry());
			return;
		}

		billboardOverlay.clearInteractionState();
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
	public void onPlayerDespawned(PlayerDespawned playerDespawned)
	{
		billboardOverlay.clearInteractionIfMatches(playerDespawned.getPlayer());
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged configChanged)
	{
		ensureConfigChangeHandler().handle(configChanged.getGroup(), configChanged.getKey());
	}

	private NpcSnapConfigChangeHandler ensureConfigChangeHandler()
	{
		if (configChangeHandler == null)
		{
			configChangeHandler = new NpcSnapConfigChangeHandler(
				() -> ensureTextureBandingManager().markDirty(),
				billboardOverlay::clearTextureCache,
				() -> ensureUiTextureManager().markDirty(),
				billboardOverlay::clearBillboardCache);
		}
		return configChangeHandler;
	}

	@Override
	public boolean addEntity(Renderable renderable, boolean drawingUi)
	{
		return ensureSceneDrawCallbacks().addEntity(renderable, drawingUi);
	}

	@Override
	public boolean draw(Renderable renderable, boolean drawingUi)
	{
		return ensureSceneDrawCallbacks().draw(renderable, drawingUi);
	}

	@Override
	public boolean drawObject(Scene scene, TileObject tileObject)
	{
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
				public boolean shouldHideTileObject(TileObject tileObject)
				{
					return billboardOverlay.shouldHideTileObject(tileObject);
				}
			});
		}
		return sceneDrawCallbacks;
	}

	private void applyAnimationFrameSnap(Actor actor)
	{
		if (!config.enableAnimationFrameSnapping())
		{
			return;
		}

		int actionFrameCount = config.animationFrameCount();
		if (actionFrameCount <= 0)
		{
			return;
		}

		int originalAnimationFrame = actor.getAnimationFrame();
		int snappedAnimationFrame = animationFrameSnapper.snapActorAnimationFrame(
			actor, actor.getAnimation(), originalAnimationFrame, actionFrameCount,
			config.deterministicAnimationLooping(), false, config.logBillboardAnimationData());
		int originalPoseFrame = actor.getPoseAnimationFrame();
		int snappedPoseFrame = animationFrameSnapper.snapActorAnimationFrame(
			actor, actor.getPoseAnimation(), originalPoseFrame, actionFrameCount,
			config.deterministicAnimationLooping(), true, config.logBillboardAnimationData());
		debug.recordActorFrames(actor, actor.getAnimation(), originalAnimationFrame, snappedAnimationFrame, originalPoseFrame, snappedPoseFrame);

		if (snappedAnimationFrame == originalAnimationFrame && snappedPoseFrame == originalPoseFrame)
		{
			return;
		}

		mutatedActors.put(actor, new RenderState(originalAnimationFrame, originalPoseFrame));

		if (snappedAnimationFrame != originalAnimationFrame)
		{
			actor.setAnimationFrame(snappedAnimationFrame);
		}

		if (snappedPoseFrame != originalPoseFrame)
		{
			actor.setPoseAnimationFrame(snappedPoseFrame);
		}

		// RuneLite exposes Actor#getOrientation()/getCurrentOrientation(), but not a public setter.
		// The rotation config is kept so the supported API can be wired in immediately if that changes.
	}

	private void syncGlobalTextureQuality()
	{
		boolean changed = ensureTextureBandingManager().sync(
			config.enableGlobalTextureBanding(),
			config.globalTextureColorBands(),
			config.globalTextureSpriteQuality());
		if (changed)
		{
			billboardOverlay.clearTextureCache();
			ensureRendererRefresher().requestRefresh();
		}
	}

	private void syncUiTextureQuality()
	{
		ensureUiTextureManager().sync(config.enableUiTextureBanding(), config.uiTextureColorBands(), config.uiSpriteQuality());
	}

	private NpcSnapUiTextureManager ensureUiTextureManager()
	{
		if (uiTextureManager == null)
		{
			uiTextureManager = new NpcSnapUiTextureManager(client, this::loadSpriteSnapshot);
		}

		return uiTextureManager;
	}

	private NpcSnapTextureBandingManager ensureTextureBandingManager()
	{
		if (textureBandingManager == null)
		{
			textureBandingManager = new NpcSnapTextureBandingManager(client);
		}

		return textureBandingManager;
	}

	private NpcSnapRendererRefresher ensureRendererRefresher()
	{
		if (rendererRefresher == null)
		{
			rendererRefresher = new NpcSnapRendererRefresher(client, pluginManager);
		}

		return rendererRefresher;
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

	private void restoreNpcState()
	{
		if (mutatedActors.isEmpty())
		{
			return;
		}

		for (Map.Entry<Actor, RenderState> entry : mutatedActors.entrySet())
		{
			Actor actor = entry.getKey();
			RenderState state = entry.getValue();
			actor.setAnimationFrame(state.getAnimationFrame());
			actor.setPoseAnimationFrame(state.getPoseAnimationFrame());
		}

		mutatedActors.clear();
	}

	private void seedCurrentSkillXp()
	{
		for (net.runelite.api.Skill skill : skillingActivityTracker.getTrackedSkills())
		{
			skillingActivityTracker.seedXp(skill, client.getSkillExperience(skill));
		}
	}

	@Value
	private static class RenderState
	{
		int animationFrame;
		int poseAnimationFrame;
	}
}
