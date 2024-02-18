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
package com.example;

import com.google.inject.Provides;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PartyChanged;

import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.party.WSClient;
import net.runelite.client.party.events.UserJoin;
import net.runelite.client.party.events.UserPart;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.party.PartyPlugin;
import net.runelite.client.plugins.party.PartyPluginService;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.party.PartyService;
import net.runelite.client.util.Text;

import static com.example.PartySpecialTrackerConfig.TextRenderType;

@PluginDescriptor(
		name = "Party Special Tracker",
		description = "Tracks various special-related stats"
)

@PluginDependency(PartyPlugin.class)
@Slf4j
public class PartySpecialTrackerPlugin extends Plugin
{

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private PartyService partyService;

	@Getter(AccessLevel.PACKAGE)
	@Inject
	private PartyPluginService partyPluginService;

	@Inject
	private PartySpecialTrackerOverlay partySpecialTrackerOverlay;

	@Inject
	private PartySpecialTrackerConfig config;

	@Inject
	private Client client;

	@Inject
	private WSClient wsClient;

	@Getter(AccessLevel.PACKAGE)
	private final Map<String, PartySpecialTrackerMember> members = new ConcurrentHashMap<>();

	@Getter(AccessLevel.PACKAGE)
	@Setter(AccessLevel.PACKAGE)
	private int lastKnownSpecial = -1;

	@Getter(AccessLevel.PACKAGE)
	@Setter(AccessLevel.PACKAGE)
	private boolean queuedUpdate = false;

	/**
	 * Visible players from the configuration (Strings)
	 */
	@Getter(AccessLevel.PACKAGE)
	private List<String> visiblePlayers = new ArrayList<>();

	private final String DEFAULT_MEMBER_NAME = "<unknown>";

	/*<|Cached Configs*/

	int desiredSpecial,
			tickDisplay,
			offSetTextHorizontal,
			offSetTextVertical,
			offSetTextZ,
			offSetStackVertical,
			fontSize;


	Color standardColor,
			lowColor;


	boolean trackMe,
			showAsTracker,
			drawPercentByName,
			drawParentheses,
			boldFont;

	TextRenderType nameRender,
			specRender;

	/*Cached Configs|>*/

	@Provides
	PartySpecialTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PartySpecialTrackerConfig.class);
	}

	@Override
	protected void startUp()
	{
		CacheConfigs();
		overlayManager.add(partySpecialTrackerOverlay);
		lastKnownSpecial = client.getLocalPlayer() != null ? (client.getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT) / 10) : -1;
		queuedUpdate = true;
		wsClient.registerMessage(PartySpecialTrackerUpdate.class);
	}

	@Override
	protected void shutDown()
	{
		wsClient.unregisterMessage(PartySpecialTrackerUpdate.class);
		overlayManager.remove(partySpecialTrackerOverlay);
		members.clear();
	}

	@Subscribe
	public void onPartyChanged(PartyChanged partyChanged)
	{
		members.clear();
	}

	@Subscribe
	public void onUserJoin(final UserJoin message)
	{
		//when a user joins, request an update for the next registered game tick
		queuedUpdate = true;
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged runeScapeProfileChanged)
	{
		queuedUpdate = true;
	}

	@Subscribe
	public void onUserPart(final UserPart message)
	{
		//name not always present, find by id
		String name = "";
		for (Map.Entry<String, PartySpecialTrackerMember> entry: members.entrySet()) {
			if(entry.getValue().getMemberID() == message.getMemberId()){
				name = entry.getKey();
			}
		}
		if(!name.isEmpty()) {
			members.remove(name);
		}
	}


	void RegisterMember(long memberID, String memberName, int currentSpecial)
	{
		if(memberName.equals(DEFAULT_MEMBER_NAME))
		{
			return;
		}

		if(currentSpecial == -1){
			//stop tracking on impossible -1, rather than sending a separate packet.
			members.remove(memberName);
			return;
		}

		if(members.containsKey(memberName))
		{
			PartySpecialTrackerMember member = members.get(memberName);
			if(currentSpecial < member.getCurrentSpecial())
			{
				members.get(memberName).setTicksSinceDrain(0);
			}
			member.setMemberID(memberID);
			member.setCurrentSpecial(currentSpecial);
		}else{
			members.put(memberName, new PartySpecialTrackerMember(memberName, memberID, currentSpecial));
		}
	}


	public List<String> parsePlayerList(String playerList)
	{
		final String configPlayers = playerList.toLowerCase();

		if (configPlayers.isEmpty())
		{
			return Collections.emptyList();
		}

		return Text.fromCSV(configPlayers);
	}


	public List<String> parseVisiblePlayers()
	{
		return parsePlayerList(config.getVisiblePlayers());
	}


	@Subscribe
	public void onConfigChanged(ConfigChanged configChanged)
	{
		if (!configChanged.getGroup().equals("partyspecialtracker"))
		{
			return;
		}

		CacheConfigs();

		if(configChanged.getKey().equals("trackMe")){
			if(trackMe){
				queuedUpdate = true;
			}
			else if (client.getLocalPlayer() != null && partyService.isInParty() && partyService.getLocalMember() != null)
			{
				//player has turned off tracking, tell other party members to remove them rather than rendering a non-updating party member.
				String currentLocalUsername = SanitizeName(client.getLocalPlayer().getName());
				if(members.containsKey(currentLocalUsername))
				{
					SendUpdate(currentLocalUsername, -1);
				}
			}
		}

	}

	public void CacheConfigs()
	{

		desiredSpecial = config.getDesiredSpecial();
		tickDisplay = config.getTickDisplay();
		offSetTextHorizontal = config.offSetTextHorizontal();
		offSetTextVertical = config.offSetTextVertial();
		offSetTextZ = config.offSetTextZ();
		offSetStackVertical = config.offSetStackVertical();
		fontSize = config.fontSize();

		standardColor = config.getStandardColor();
		lowColor = config.getLowColor();

		trackMe = config.getTrackMe();
		showAsTracker = config.getShowAsTracker();
		drawPercentByName = config.drawPercentByName();
		drawParentheses = config.drawParentheses();
		boldFont = config.boldFont();

		nameRender = config.nameRender();
		specRender = config.specRender();

		visiblePlayers = parseVisiblePlayers();
	}


	@Subscribe
	public void onPartySpecialTrackerUpdate(PartySpecialTrackerUpdate update)
	{

		if (partyService.getLocalMember().getMemberId() == update.getMemberId())
		{
			return;
		}

		String name = partyService.getMemberById(update.getMemberId()).getDisplayName();
		if (name == null)
		{
			return;
		}

		RegisterMember(update.getMemberId(),name,update.getCurrentSpecial());
	}


	@Subscribe
	public void onGameTick(GameTick event)
	{
		//an update has been requested, resync special
		if (trackMe && queuedUpdate && lastKnownSpecial > -1 && client.getLocalPlayer() != null && partyService.isInParty() && partyService.getLocalMember() != null)
		{
			String currentLocalUsername = SanitizeName(client.getLocalPlayer().getName());
			String partyName = partyService.getMemberById(partyService.getLocalMember().getMemberId()).getDisplayName();
			//dont send unless the partyname has updated to the local name
			if (currentLocalUsername != null && currentLocalUsername.equals(partyName))
			{
				queuedUpdate = false;
				SendUpdate(currentLocalUsername, lastKnownSpecial);
			}
		}

		for (PartySpecialTrackerMember member : members.values())
		{
			if(member.getTicksSinceDrain() == -1)
				continue;
			if(member.IncrementTicksSinceDrain() > tickDisplay)
			{
				member.setTicksSinceDrain(-1);
			}
		}

	}
	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (event.getVarpId() != VarPlayer.SPECIAL_ATTACK_PERCENT)
		{
			return;
		}
		lastKnownSpecial = event.getValue()/10;
		queuedUpdate = true;
	}

	public void SendUpdate(String name, int currentSpecial)
	{
		if(partyService.getLocalMember() != null)
		{
			partyService.send(new PartySpecialTrackerUpdate(currentSpecial));
			//handle self locally.
			RegisterMember(partyService.getLocalMember().getMemberId(),name,currentSpecial);
		}
	}

	String SanitizeName(String name)
	{
		return Text.removeTags(Text.toJagexName(name));
	}

	public boolean RenderText(TextRenderType textRenderType, boolean healthy)
	{
		if(textRenderType == TextRenderType.NEVER)
			return false;
		return textRenderType == TextRenderType.ALWAYS
				|| (textRenderType == TextRenderType.WHEN_MISSING_SPEC && !healthy);
	}

	public boolean HasDesiredSpecial(int currentSpecial){ return currentSpecial >= desiredSpecial; }

	boolean RenderPlayer(String sanitizedName)
	{
		if(!members.containsKey(sanitizedName))
			return false;
		return visiblePlayers.isEmpty() || visiblePlayers.contains(sanitizedName.toLowerCase());
	}


}
