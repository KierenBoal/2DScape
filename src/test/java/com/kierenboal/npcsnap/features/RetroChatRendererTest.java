package com.kierenboal.npcsnap.features;

import java.awt.Color;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class RetroChatRendererTest
{
	@Test
	public void parsesEveryRetroColourAndRemovesPrefix()
	{
		String[] codes = {"red", "dre", "lre", "ora", "or1", "or2", "or3", "yel", "gr1",
			"gre", "gr2", "gr3", "blu", "cya", "mag", "bla", "whi"};
		for (String code : codes)
		{
			RetroChatRenderer.ParsedChat chat = RetroChatRenderer.parse("@" + code.toUpperCase() + "@Hello");
			assertEquals("Hello", chat.text);
			assertEquals(RetroChatRenderer.ColorMode.SOLID, chat.colorMode);
		}
	}

	@Test
	public void malformedAndUnknownPrefixesRemainVisible()
	{
		assertEquals("@wat@Hello", RetroChatRenderer.parse("@wat@Hello").text);
		assertEquals("red Hello", RetroChatRenderer.parse("red Hello").text);
	}

	@Test
	public void parsesBuiltInColourAndMotionInEitherOrder()
	{
		RetroChatRenderer.ParsedChat first = RetroChatRenderer.parse("flash2:wave:Hello");
		assertEquals("Hello", first.text);
		assertEquals(RetroChatRenderer.ColorMode.FLASH2, first.colorMode);
		assertEquals(RetroChatRenderer.Motion.WAVE, first.motion);

		RetroChatRenderer.ParsedChat second = RetroChatRenderer.parse("shake:white:Hello");
		assertEquals("Hello", second.text);
		assertEquals(Color.WHITE, second.color);
		assertEquals(RetroChatRenderer.Motion.SHAKE, second.motion);
	}

	@Test
	public void plainChatDoesNotParseCommands()
	{
		RetroChatRenderer.ParsedChat chat = RetroChatRenderer.plain("@red@Hello");
		assertEquals("@red@Hello", chat.text);
		assertEquals(Color.YELLOW, chat.color);
	}

	@Test
	public void randomColourIsStableWithinBucket()
	{
		RetroChatRenderer.ParsedChat chat = RetroChatRenderer.parse("@ran@Hello");
		Object actor = new Object();
		assertEquals(RetroChatRenderer.color(chat, actor, 400L, 0),
			RetroChatRenderer.color(chat, actor, 599L, 0));
	}

	@Test
	public void inlineRetroColoursCreateIndependentRuns()
	{
		RetroChatRenderer.ParsedChat chat = RetroChatRenderer.parse("@red@hello @blu@world!");
		assertEquals("hello world!", chat.text);
		assertEquals(new Color(0xFF0000), chat.colors[0]);
		assertEquals(new Color(0xFF0000), chat.colors[5]);
		assertEquals(new Color(0x0000FF), chat.colors[6]);
		assertEquals(new Color(0x0000FF), chat.colors[11]);
	}

	@Test
	public void randomModeIsAssignedPerCharacter()
	{
		RetroChatRenderer.ParsedChat chat = RetroChatRenderer.parse("@ran@abcdefghijklmno");
		Object actor = new Object();
		boolean foundDifferentColour = false;
		Color first = RetroChatRenderer.color(chat, actor, 400L, 0);
		for (int i = 1; i < chat.text.length(); i++)
		{
			foundDifferentColour |= !first.equals(RetroChatRenderer.color(chat, actor, 400L, i));
		}
		org.junit.Assert.assertTrue(foundDifferentColour);
	}

	@Test
	public void flashAndGlowAdvanceDeterministically()
	{
		Object actor = new Object();
		RetroChatRenderer.ParsedChat flash = RetroChatRenderer.parse("flash1:Hi");
		assertNotEquals(RetroChatRenderer.color(flash, actor, 0L, 0),
			RetroChatRenderer.color(flash, actor, 250L, 0));
		RetroChatRenderer.ParsedChat glow = RetroChatRenderer.parse("glow1:Hi");
		assertNotEquals(RetroChatRenderer.color(glow, actor, 0L, 0),
			RetroChatRenderer.color(glow, actor, 200L, 0));
	}
}
