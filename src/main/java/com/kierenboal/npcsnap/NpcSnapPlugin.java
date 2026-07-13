package com.kierenboal.npcsnap;

import com.google.inject.Provides;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameState;
import net.runelite.api.GameObject;
import net.runelite.api.GraphicsObject;
import net.runelite.api.GroundObject;
import net.runelite.api.ItemLayer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.Renderable;
import net.runelite.api.Scene;
import net.runelite.api.SpritePixels;
import net.runelite.api.TileItem;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
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
	private static final int LOGIN_XP_DROP_GRACE_TICKS = 10;

	private final Map<Actor, RenderState> mutatedActors = new HashMap<>();
	private final Runnable restoreFrameListener = this::restoreNpcState;
	private boolean pendingSkillXpSeed;
	private int gameTickCounter;
	private int loginXpDropGraceUntilTick = Integer.MIN_VALUE;
	private int ignoredLoginXpDropTick = Integer.MIN_VALUE;
	private NpcSnapUiTextureManager uiTextureManager;
	private NpcSnapTextureBandingManager textureBandingManager;

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
		ensureTextureBandingManager().restore();
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

		for (NPC npc : worldView.npcs())
		{
			if (npc == null || !config.applyToNpcs())
			{
				continue;
			}

			applyAnimationFrameSnap(npc);
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
		if (shouldIgnoreLoginXpDrop(statChanged))
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
		gameTickCounter++;
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
			loginXpDropGraceUntilTick = gameTickCounter + LOGIN_XP_DROP_GRACE_TICKS;
			ignoredLoginXpDropTick = Integer.MIN_VALUE;
			ensureUiTextureManager().markDirty();
		}
		else
		{
			skillingActivityTracker.clear();
			pendingSkillXpSeed = false;
			loginXpDropGraceUntilTick = Integer.MIN_VALUE;
			ignoredLoginXpDropTick = Integer.MIN_VALUE;
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
		if (!"npc-snap".equals(configChanged.getGroup()))
		{
			return;
		}

		if ("enableGlobalTextureBanding".equals(configChanged.getKey())
			|| "globalTextureColorBands".equals(configChanged.getKey()))
		{
			ensureTextureBandingManager().markDirty();
			billboardOverlay.clearTextureCache();
		}

		if ("enableUiTextureBanding".equals(configChanged.getKey())
			|| "uiTextureColorBands".equals(configChanged.getKey())
			|| "uiSpriteQuality".equals(configChanged.getKey()))
		{
			ensureUiTextureManager().markDirty();
		}

		if ("useInventorySpritesForGroundItems".equals(configChanged.getKey()))
		{
			billboardOverlay.clearBillboardCache();
		}
	}

	@Override
	public boolean addEntity(Renderable renderable, boolean drawingUi)
	{
		if (!config.enable2dBillboardSprites() || drawingUi)
		{
			return true;
		}

		// IMPORTANT: addEntity is the actor interaction path, not the actor hiding path.
		// NPCs and players must continue through addEntity even when a billboard is active,
		// because RuneLite builds their hover/right-click targeting from this callback chain.
		// If an actor starts becoming unclickable after changes around billboard hiding,
		// the first thing to check is whether addEntity was changed to return false for
		// NPCs/players with billboards. That will remove the clickbox/menu state entirely.
		//
		// Visual suppression for billboarded actors is handled indirectly elsewhere by the
		// renderer; do not "optimize" actor hiding here unless you have verified in-game
		// that NPC/player clickboxes and targeting still work correctly.
		billboardOverlay.noteSceneRenderable(renderable);

		if (ObjectClassifier.keepsActorInteraction(renderable))
		{
			return true;
		}

		return !billboardOverlay.shouldHideRenderable(renderable);
	}

	@Override
	public boolean draw(Renderable renderable, boolean drawingUi)
	{
		if (!config.enable2dBillboardSprites() || drawingUi)
		{
			return true;
		}

		billboardOverlay.noteSceneRenderable(renderable);
		return !billboardOverlay.shouldHideRenderable(renderable);
	}

	@Override
	public boolean drawObject(Scene scene, TileObject tileObject)
	{
		if (!config.enable2dBillboardSprites())
		{
			return true;
		}

		if (tileObject instanceof GameObject)
		{
			// IMPORTANT: drawObject is the visual suppression path for TileObjects and their
			// backing renderables. This method is easy to regress because addEntity must keep
			// actors alive for clickboxes, while drawObject must aggressively return false for
			// billboarded TileObject renderables so the original 3D model does not render
			// underneath the sprite.
			//
			// The common failure modes here are:
			// 1. Returning true unconditionally after observeTileObject()/shouldHideTileObject(),
			//    which causes the original model to render under the billboard.
			// 2. Removing the per-renderable shouldHideRenderable() checks below, which breaks
			//    suppression for GameObject/GroundObject/DecorativeObject/WallObject parts.
			// 3. Moving actor clickbox logic into drawObject(), even though NPCs/players do not
			//    use this callback path for their interaction state.
			if (observeAndShouldHideTileObject(tileObject))
			{
				return false;
			}

			return shouldDrawRenderable(((GameObject) tileObject).getRenderable());
		}

		if (tileObject instanceof GroundObject)
		{
			if (observeAndShouldHideTileObject(tileObject))
			{
				return false;
			}

			return shouldDrawRenderable(((GroundObject) tileObject).getRenderable());
		}

		if (tileObject instanceof DecorativeObject)
		{
			if (observeAndShouldHideTileObject(tileObject))
			{
				return false;
			}

			DecorativeObject decorativeObject = (DecorativeObject) tileObject;
			return shouldDrawAllRenderables(decorativeObject.getRenderable(), decorativeObject.getRenderable2());
		}

		if (tileObject instanceof WallObject)
		{
			if (observeAndShouldHideTileObject(tileObject))
			{
				return false;
			}

			WallObject wallObject = (WallObject) tileObject;
			return shouldDrawAllRenderables(wallObject.getRenderable1(), wallObject.getRenderable2());
		}

		if (tileObject instanceof ItemLayer)
		{
			ItemLayer itemLayer = (ItemLayer) tileObject;
			billboardOverlay.noteSceneRenderable(itemLayer.getBottom());
			billboardOverlay.noteSceneRenderable(itemLayer.getMiddle());
			billboardOverlay.noteSceneRenderable(itemLayer.getTop());
			return shouldDrawAllRenderables(itemLayer.getBottom(), itemLayer.getMiddle(), itemLayer.getTop());
		}

		return true;
	}

	private boolean observeAndShouldHideTileObject(TileObject tileObject)
	{
		if (!config.applyToObjects() && !config.applyToGraphicsObjects())
		{
			return false;
		}

		billboardOverlay.observeTileObject(tileObject);
		return billboardOverlay.shouldHideTileObject(tileObject);
	}

	private boolean shouldDrawAllRenderables(Renderable... renderables)
	{
		for (Renderable renderable : renderables)
		{
			if (!shouldDrawRenderable(renderable))
			{
				return false;
			}
		}

		return true;
	}

	private boolean shouldDrawRenderable(Renderable renderable)
	{
		return !billboardOverlay.shouldHideRenderable(renderable);
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
		int snappedAnimationFrame = animationFrameSnapper.snapAnimationFrame(actor.getAnimation(), originalAnimationFrame, actionFrameCount);
		int originalPoseFrame = actor.getPoseAnimationFrame();
		int snappedPoseFrame = animationFrameSnapper.snapAnimationFrame(actor.getPoseAnimation(), originalPoseFrame, actionFrameCount);
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
		ensureTextureBandingManager().sync(config.enableGlobalTextureBanding(), config.globalTextureColorBands());
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

	private boolean shouldIgnoreLoginXpDrop(StatChanged statChanged)
	{
		if (statChanged == null
			|| client.getGameState() != GameState.LOGGED_IN
			|| gameTickCounter > loginXpDropGraceUntilTick
			|| !skillingActivityTracker.isTrackedXpIncrease(statChanged.getSkill(), statChanged.getXp()))
		{
			return false;
		}

		if (ignoredLoginXpDropTick == Integer.MIN_VALUE)
		{
			ignoredLoginXpDropTick = gameTickCounter;
			return true;
		}

		return ignoredLoginXpDropTick == gameTickCounter;
	}

	@Value
	private static class RenderState
	{
		int animationFrame;
		int poseAnimationFrame;
	}
}
