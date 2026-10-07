package com.kierenboal.npcsnap;

import com.kierenboal.npcsnap.occlusion.BillboardOcclusionQuality;
import com.kierenboal.npcsnap.rendering.AnimationFrameSnapper;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Animation;
import net.runelite.api.Client;
import net.runelite.api.DynamicObject;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Model;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;

import static com.kierenboal.npcsnap.TestProxies.method;
import static com.kierenboal.npcsnap.TestProxies.methodSupplier;
import static com.kierenboal.npcsnap.TestProxies.proxy;
import static org.junit.Assert.*;

/** Runs scheduling, caching and rasterization on synthetic animated scenery. */
public class NpcBillboardObjectCadenceTest
{
	@Test
	public void animatedObjectsHoldTheirCapturedImageUntilTheConfiguredUpdateCycle()
	{
		for (int frameRate : new int[] {2, 4, 10})
		{
			Fixture fixture = new Fixture(true, true, frameRate);
			int interval = (int) Math.ceil(50.0d / frameRate);
			int previous = fixture.frame(0);
			for (int cycle = 1; cycle <= interval * 2; cycle++)
			{
				int current = fixture.frame(cycle);
				if (cycle % interval == 0)
				{
					assertNotEquals("The next captured live pose should be visible", previous, current);
					previous = current;
				}
				else
				{
					assertEquals("Live vertices and frames must not bypass the limit at cycle " + cycle,
						previous, current);
				}
			}
		}
	}

	@Test
	public void disablingTheLimitLetsWorldObjectsUpdateImmediately()
	{
		Fixture fixture = new Fixture(false, true, 4);
		assertNotEquals(fixture.frame(0), fixture.frame(1));
	}

	@Test
	public void objectsWithoutAnAnimationStillRefreshModelChangesImmediately()
	{
		Fixture fixture = new Fixture(true, false, 4);
		assertNotEquals(fixture.frame(0), fixture.frame(1));
	}

	private static final class Fixture
	{
		private final AtomicInteger cycle = new AtomicInteger();
		private final WorldView view;
		private final TileObject object;
		private final NpcBillboardOverlay overlay;

		private Fixture(boolean limited, boolean animated, int frameRate)
		{
			view = proxy(WorldView.class, method("getId", WorldView.TOPLEVEL), method("isTopLevel", true),
				method("contains", true), method("getSizeX", 16), method("getSizeY", 16),
				method("getTileSettings", new byte[4][16][16]), method("getTileHeights", new int[4][17][17]));
			LocalPoint location = new LocalPoint(512, 1024);
			Player localPlayer = proxy(Player.class, method("getWorldView", view), method("getLocalLocation", location));
			Client client = proxy(Client.class, method("getTopLevelWorldView", view), method("getWorldView", view),
				method("getLocalPlayer", localPlayer), method("getGameState", GameState.LOGGED_IN),
				method("isClientThread", true), method("getScale", 512), method("get3dZoom", 512),
				method("getViewportWidth", 800), method("getViewportHeight", 600),
				methodSupplier("getGameCycle", cycle::get));
			Animation animation = proxy(Animation.class, method("getId", 1), method("getNumFrames", 100));
			DynamicObject first = part(animation, animated);
			object = proxy(GameObject.class, method("getWorldView", view),
				method("getId", 1), method("getLocalLocation", location),
				method("getSceneMinLocation", new Point(4, 8)), method("getSceneMaxLocation", new Point(4, 8)),
				method("getRenderable", first));
			NpcSnapConfig config = new NpcSnapConfig()
			{
				@Override public boolean applyToObjects() { return true; }
				@Override public boolean applyToNpcs() { return false; }
				@Override public boolean applyToPlayers() { return false; }
				@Override public boolean applyToGroundItems() { return false; }
				@Override public boolean applyToProjectiles() { return false; }
				@Override public boolean applyToGraphicsObjects() { return false; }
				@Override public boolean useRetroOverheads() { return false; }
				@Override public boolean enableAnimationFrameSnapping() { return limited; }
				@Override public boolean enableRotationSnapping() { return false; }
				@Override public int animationFrameCount() { return frameRate; }
				@Override public BillboardOcclusionQuality billboardOcclusionQuality() { return BillboardOcclusionQuality.OFF; }
			};
			overlay = new NpcBillboardOverlay(client, null, config, new NpcSnapDebug(client, config),
				new AnimationFrameSnapper(client), null);
			overlay.setActive(true);
		}

		private DynamicObject part(Animation animation, boolean animated)
		{
			Model model = proxy(Model.class, method("getVerticesCount", 3), method("getModelHeight", 100),
				methodSupplier("getVerticesX", () -> new float[] {-50 - cycle.get(), 50 + cycle.get(), 0}),
				method("getVerticesY", new float[] {0, 0, -100}), method("getVerticesZ", new float[3]),
				method("getFaceCount", 1), method("getFaceIndices1", new int[] {0}),
				method("getFaceIndices2", new int[] {1}), method("getFaceIndices3", new int[] {2}),
				method("getFaceColors1", new int[] {5000}), method("getFaceColors2", new int[] {5000}),
				method("getFaceColors3", new int[] {5000}));
			return proxy(DynamicObject.class, method("getModel", model), method("getModelHeight", 100),
				method("getAnimation", animated ? animation : null), methodSupplier("getAnimFrame", cycle::get));
		}

		private int frame(int gameCycle)
		{
			cycle.set(gameCycle);
			overlay.observeTileObject(object);
			overlay.beginFrame();
			overlay.prepareFrame(view);
			BufferedImage canvas = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
			Graphics2D graphics = canvas.createGraphics();
			try
			{
				overlay.render(graphics);
			}
			finally
			{
				graphics.dispose();
			}
			int[] pixels = canvas.getRGB(0, 0, 800, 600, null, 0, 800);
			assertTrue("The object must actually draw at cycle " + gameCycle,
				Arrays.stream(pixels).anyMatch(pixel -> pixel != 0));
			return Arrays.hashCode(pixels);
		}
	}
}
