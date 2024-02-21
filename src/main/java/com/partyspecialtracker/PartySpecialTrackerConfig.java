/*
 * Copyright (c) 2022, Jamal <http://github.com/1Defence>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.partyspecialtracker;

import net.runelite.client.config.*;

import java.awt.*;

@ConfigGroup("partyspecialtracker")
public interface PartySpecialTrackerConfig extends Config
{
	enum TextRenderType { ALWAYS,NEVER,WHEN_MISSING_SPEC}
	@ConfigSection(name="Visual Overlay", description="visual overlay settings", position=1, closedByDefault=false)
	String visualOverlay = "visualOverlay";
	@ConfigSection(name="Text Overlay", description="text overlay settings", position=2, closedByDefault=true)
	String textOverlay = "textOverlay";

	/*Visual Overlay*/
	@ConfigItem(
			position = 0,
			keyName = "trackMe",
			name = "Track Me",
			description = "Track and send local players special information to party",
			section = visualOverlay
	)
	default boolean getTrackMe()
	{
		return true;
	}

	@ConfigItem(
			position = 1,
			keyName = "showAsTracker",
			name = "Show As Tracker",
			description = "Default behaviour hides visuals with trackMe on, enable to override that",
			section = visualOverlay)
	default boolean getShowAsTracker()
	{
		return false;
	}

	@ConfigItem(
			position = 2,
			keyName = "visiblePlayers",
			name = "Visible Players",
			description = "Only names listed will have visuals shown, if list is empty all connected party members will show up",
			section = visualOverlay
	)
	default String getVisiblePlayers()
	{
		return "";
	}

	@ConfigItem(
			position = 3,
			keyName = "desiredSpecial",
			name = "Desired Special",
			description = "The desired special energy amount",
			section = visualOverlay
	)
	default int getDesiredSpecial() { return 50; }


	@ConfigItem(
			position = 4,
			keyName = "standardColor",
			name = "Standard Color",
			description = "The Color when party member has desired special energy",
			section = visualOverlay
	)
	default Color getStandardColor()
	{
		return new Color(0,255,255);
	}

	@ConfigItem(
			position = 5,
			keyName = "lowColor",
			name = "Low Color",
			description = "The Color when party member has less than desired special energy",
			section = visualOverlay
	)
	default Color getLowColor()
	{
		return new Color(255,0,0);
	}

	@ConfigItem(
			position = 6,
			keyName = "tickDisplay",
			name = "Tick Display",
			description = "How many ticks to display after special has been used, 0 to disable",
			section = visualOverlay
	)
	default int getTickDisplay() { return 0; }


	/*Text Overlay*/
	@ConfigItem(
			position = 0,
			keyName = "nameRender",
			name = "Name Render",
			description = "Configures how player names should render",
			section = textOverlay
	)
	default TextRenderType nameRender()
	{
		return TextRenderType.NEVER;
	}
	@ConfigItem(
			position = 1,
			keyName = "specRender",
			name = "Spec Render",
			description = "Configures how player special should render",
			section = textOverlay
	)
	default TextRenderType specRender()
	{
		return TextRenderType.ALWAYS;
	}

	@ConfigItem(
			position = 2,
			keyName = "drawPercentByName",
			name = "Draw Percent By Name",
			description = "Draw a % beside the numeral value of remaining special energy",
			section = textOverlay)
	default boolean drawPercentByName() { return true; }

	@ConfigItem(
			position = 3,
			keyName = "drawParentheses",
			name = "Draw Parentheses By Name",
			description = "Draw parentheses surrounding special number",
			section = textOverlay)
	default boolean drawParentheses() { return false; }

	@ConfigItem(
			position = 4,
			keyName = "offSetTextHorizontal",
			name = "OffSet Text Horizontal",
			description = "OffSet the text horizontally",
			section = textOverlay)
	default int offSetTextHorizontal() { return 0; }

	@ConfigItem(
			position = 5,
			keyName = "offSetTextVertical",
			name = "OffSet Text Vertical",
			description = "OffSet the text vertically",
			section = textOverlay)
	default int offSetTextVertial() { return 20; }

	@ConfigItem(
			position = 6,
			keyName = "offSetTextZ",
			name = "OffSet Text Z",
			description = "OffSet the text Z",
			section = textOverlay)
	default int offSetTextZ() { return 80; }

	@Range(min = 1,max=20)
	@ConfigItem(
			position = 7,
			keyName = "offSetStackVertical",
			name = "OffSet Stack Vertical",
			description = "OffSet the text vertically when multiple accounts are stacked",
			section = textOverlay)
	default int offSetStackVertical() { return 10; }

	@Range(max=16, min=8)
	@ConfigItem(
			position=8,
			keyName="fontSize",
			name="Font Size",
			description="font size",
			section = textOverlay)
	default int fontSize() {
		return 12;
	}

	@ConfigItem(
			keyName = "boldFont",
			name = "Bold Font",
			description = "Configures whether font is bold or not",
			position = 9,
			section = textOverlay
	)
	default boolean boldFont()
	{
		return true;
	}

}
