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

import net.runelite.client.party.WSClient;
import net.runelite.client.party.events.UserPart;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.party.PartyPlugin;
import net.runelite.client.plugins.party.PartyPluginService;
import net.runelite.client.plugins.party.messages.StatusUpdate;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.party.PartyService;
import net.runelite.client.util.Text;

import static com.partyspecialtracker.PartySpecialTrackerConfig.TextRenderType;

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
	private boolean usedSpecial = false;

	/**
	 * Visible players from the configuration (Strings)
	 */
	@Getter(AccessLevel.PACKAGE)
	private List<String> visiblePlayers = new ArrayList<>();

	private final String DEFAULT_MEMBER_NAME = "<unknown>";

	@Getter(AccessLevel.PACKAGE)
	@Setter(AccessLevel.PACKAGE)
	private int lastKnownGameCycle;

	@Getter(AccessLevel.PACKAGE)
	@Setter(AccessLevel.PACKAGE)
	private int lastSpecialVarbitThisTick = -1;

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
		lastSpecialVarbitThisTick = -1;
		usedSpecial = false;
		wsClient.registerMessage(PartySpecialTrackerFringeConditionUpdate.class);
	}

	@Override
	protected void shutDown()
	{
		wsClient.unregisterMessage(PartySpecialTrackerFringeConditionUpdate.class);
		overlayManager.remove(partySpecialTrackerOverlay);
		members.clear();
	}

	/**
	 * Local player has left party or joined a new one, flush existing list of members.
	 */
	@Subscribe
	public void onPartyChanged(PartyChanged partyChanged)
	{
		members.clear();
	}

	/**
	 * A member has left the party, remove them from the list of tracked players
	 */
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

	/**
	 * Parse config list of player names and convert into a list of strings.<br>
	 * Used to determine which tracked players you want to see.
	 */
	public List<String> parseVisiblePlayers()
	{
		final String configPlayers = config.getVisiblePlayers().toLowerCase();

		if (configPlayers.isEmpty())
		{
			return Collections.emptyList();
		}

		return Text.fromCSV(configPlayers);
	}

	/**
	 * Update Cache
	 */
	@Subscribe
	public void onConfigChanged(ConfigChanged configChanged)
	{
		if (!configChanged.getGroup().equals("partyspecialtracker"))
		{
			return;
		}

		CacheConfigs();

	}

	/**
	 * Cache config values to reduce extensive checking
	 */
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

	/**
	 * Received status update packet from party member.<br>
	 * Verify packet isn't bad and update the party member in question
	 */
	@Subscribe
	public void onStatusUpdate(final StatusUpdate event){

		//two packets send on login, the first has false spec data and assumes the name is set when it can have changed.
		//ignore the first invalid packet.
		if(event.getCharacterName() == null && !partyService.getMemberById(event.getMemberId()).isLoggedIn())
			return;

		String name;
		if((name = event.getCharacterName()) == null){
			if((name = partyService.getMemberById(event.getMemberId()).getDisplayName()) == null)
			{
				return;
			}
		}

		if(name.isEmpty()){
			return;
		}

		if(event.getSpecEnergy() == null)
			return;

		UpdateMember(name,event);
	}

	/**
	 * Received packet from party member.<br>
	 * Party members special has drained in fringe case, start tracking time<br>
	 */
	@Subscribe
	public void onPartySpecialTrackerFringeConditionUpdate(PartySpecialTrackerFringeConditionUpdate packet)
	{
		String name = partyService.getMemberById(packet.getMemberId()).getDisplayName();
		if (name == null)
		{
			return;
		}

		members.get(name).StartTrackingDrain();
	}

	/**
	 * Increment active tick timers and send additional update packet if fringe condition is met.
	 */
	@Subscribe
	public void onGameTick(GameTick event)
	{
		//save current cycle to determine duration into the current tick
		lastKnownGameCycle = client.getGameCycle();
		lastSpecialVarbitThisTick = -1;

		//increment members with active ticks
		for (PartySpecialTrackerMember member : members.values())
		{
			if(!member.IsTrackingDrain())
				continue;
			member.IncrementTicksSinceDrain(tickDisplay);
		}

		//Check for fringe case, occurs when xfer is recieved the same tick it's used
		if(usedSpecial)
		{
			usedSpecial = false;
			if (trackMe && IsValidAndInParty())
			{
				String currentLocalUsername = GetLocalPlayerName();
				String partyName = partyService.getMemberById(partyService.getLocalMember().getMemberId()).getDisplayName();
				//dont send unless the partyname has updated to the local name
				if (currentLocalUsername != null && currentLocalUsername.equals(partyName))
				{
					SendFringeConditionUpdate();
				}
			}
		}

	}

	/**
	 * Watch special change events for double varbit change to request an update packet in the game tick
	 */
	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		//special is set to 0 on these states, disregard this information
		if(client.getGameState() == GameState.LOGGING_IN || client.getGameState() == GameState.HOPPING)
			return;

		if (event.getVarpId() != VarPlayer.SPECIAL_ATTACK_PERCENT)
			return;

		/*
		*fringe case where special changes numerous times in the same game tick
		*occurs when player receives energy transfer on the same tick that they use their special attack.
		*use of spec needs to be calculated locally as opposed to checking spec diff on update
		*/

		int currentSpecial = event.getValue()/10;
		if(currentSpecial < lastSpecialVarbitThisTick)
		{
			usedSpecial = true;
		}
		lastSpecialVarbitThisTick = currentSpecial;
	}

	/**
	 * Send a packet informing party members a drain has occured in situation where it won't be picked up by status update<br>
	 * Occurs when varbit changes twice in the same tick.
	 */
	public void SendFringeConditionUpdate()
	{
		if(partyService.getLocalMember() != null)
		{
			PartySpecialTrackerFringeConditionUpdate packet = new PartySpecialTrackerFringeConditionUpdate();
			partyService.send(packet);
		}
	}

	/**
	 * Updates or adds player to the map of tracked party members.
	 * @param memberName Party member name, this is a sanitized Jagex name.
	 * @param update Standard status packet
	 */
	void UpdateMember(String memberName, StatusUpdate update)
	{
		if(memberName.equals(DEFAULT_MEMBER_NAME))
		{
			return;
		}

		int updatedSpecial = update.getSpecEnergy();
		long memberID = update.getMemberId();
		boolean memberUsedSpecial = false;

		if(members.containsKey(memberName))
		{
			PartySpecialTrackerMember member = members.get(memberName);
			member.setMemberID(memberID);
			if(member.getCurrentSpecial() > updatedSpecial){
				memberUsedSpecial = true;
			}
			member.setCurrentSpecial(updatedSpecial);
		}else{
			members.put(memberName, new PartySpecialTrackerMember(memberName, memberID, updatedSpecial));
		}

		if(memberUsedSpecial)
		{
			members.get(memberName).StartTrackingDrain();
		}

	}

	/**
	 * Remove tags and convert to Jagex name
	 * @param name Local players raw name
	 */
	String SanitizeName(String name)
	{
		return Text.removeTags(Text.toJagexName(name));
	}

	/**
	 * Get sanitized name of the local player
	 */
	String GetLocalPlayerName(){
		return SanitizeName(client.getLocalPlayer().getName());
	}

	/**
	 * Check if text should be rendered based on player-chosen configs
	 * @param textRenderType The chosen style of text rendering
	 * @param hasDesiredSpecial Flag indicating the current special value surpasses player-chosen config
	 */
	public boolean RenderText(TextRenderType textRenderType, boolean hasDesiredSpecial)
	{
		if(textRenderType == TextRenderType.NEVER)
			return false;
		return textRenderType == TextRenderType.ALWAYS
				|| (textRenderType == TextRenderType.WHEN_MISSING_SPEC && !hasDesiredSpecial);
	}

	/**
	 * Check if a special value surpasses player-chosen config
	 * @param specialValue Current special of a given party member
	 */
	public boolean HasDesiredSpecial(int specialValue){ return specialValue >= desiredSpecial; }

	/**
	 * Check if a given player should be rendered
	 * @param sanitizedName see {@link #SanitizeName(String)}
	 */
	boolean RenderPlayer(String sanitizedName)
	{
		if(!members.containsKey(sanitizedName))
			return false;
		return visiblePlayers.isEmpty() || visiblePlayers.contains(sanitizedName.toLowerCase());
	}

	/**
	 * Ensure local player is valid and currently in a party
	 */
	Boolean IsValidAndInParty(){ return (client.getLocalPlayer() != null && partyService.isInParty() && partyService.getLocalMember() != null);}


}
