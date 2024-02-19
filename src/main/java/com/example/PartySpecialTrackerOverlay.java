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

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;

public class PartySpecialTrackerOverlay extends Overlay
{
    private final Client client;
    private final PartySpecialTrackerPlugin plugin;

    @Inject
    PartySpecialTrackerOverlay(Client client, PartySpecialTrackerPlugin plugin)
    {
        this.client = client;
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.UNDER_WIDGETS);
    }

    /**
     * Renders tracked party members special amount.<br>
     * Additionally, displays ticks since special has drained if present
     */
    @Override
    public Dimension render(Graphics2D graphics)
    {

        if(plugin.trackMe && !plugin.showAsTracker)
            return null;

        graphics.setFont(new Font(FontManager.getRunescapeFont().toString(), plugin.boldFont ? Font.BOLD : Font.PLAIN, plugin.fontSize));

        //track player locations for vertical-offsetting purposes, when players are stacked their names/hp(if rendered) should stack instead of overlapping
        List<WorldPoint> trackedLocations = new ArrayList<>();


        for(Player player : client.getPlayers())
        {

            if (player == null || player.getName() == null)
            {
                continue;
            }

            String name = plugin.SanitizeName(player.getName());
            if(!plugin.RenderPlayer(name))
            {
                continue;
            }

            PartySpecialTrackerMember member = plugin.getMembers().get(name);

            int currentSpecial = member.getCurrentSpecial();
            int ticksSinceDrain = member.getTicksSinceDrain();

            boolean hasDesiredSpecial = plugin.HasDesiredSpecial(currentSpecial);
            boolean nameRendered = plugin.RenderText(plugin.nameRender,hasDesiredSpecial) || plugin.RenderText(plugin.specRender,hasDesiredSpecial);

            if(nameRendered)
            {
                int playersTracked = 0;
                WorldPoint currentLoc = player.getWorldLocation();
                for(int i=0; i<trackedLocations.size(); i++)
                {
                    WorldPoint compareLoc = trackedLocations.get(i);
                    if(compareLoc.getX() == currentLoc.getX() && compareLoc.getY() == currentLoc.getY())
                    {
                        playersTracked++;
                    }
                }
                trackedLocations.add(player.getWorldLocation());
                renderPlayerOverlay(graphics, player, playersTracked,currentSpecial,ticksSinceDrain,hasDesiredSpecial);
            }

        }

        return null;
    }

    /**
     * Calculates the display of a given party member based on current data and config values.
     * @param graphics
     * @param actor The party member to draw visuals for
     * @param playersTracked The number of players on the same tile of actor
     * @param currentSpecial The current tracked special amount
     * @param ticksSinceDrain The ticks since special drained
     * @param hasDesiredSpecial Indicates currentSpecial Surpasses config
     */
    private void renderPlayerOverlay(Graphics2D graphics, Player actor, int playersTracked, int currentSpecial, int ticksSinceDrain, boolean hasDesiredSpecial)
    {
        Color color = hasDesiredSpecial ? plugin.standardColor : plugin.lowColor;

        String playerName = plugin.RenderText(plugin.nameRender,hasDesiredSpecial) ? Text.removeTags(actor.getName()) : "";
        String endingPercentString = plugin.drawPercentByName ? "%" : "";
        String startingParenthesesString = plugin.drawParentheses ? "(" : "";
        String endingParenthesesString = plugin.drawParentheses ? ")" : "";

        playerName += plugin.RenderText(plugin.specRender,hasDesiredSpecial) ? " "+(startingParenthesesString+currentSpecial+endingPercentString+endingParenthesesString) : "";
        boolean lingeringTick = (ticksSinceDrain == (plugin.tickDisplay+1));
        String tickDisplayString = ticksSinceDrain > -1 ? " 🗲"+Math.min(ticksSinceDrain,plugin.tickDisplay)  : "";

        Point textLocation = actor.getCanvasTextLocation(graphics, playerName, plugin.offSetTextZ);

        float verticalOffSetMultiplier = 1f + (playersTracked * (((float)plugin.offSetStackVertical)/100f));

        if(textLocation != null)
        {
            textLocation = new Point(textLocation.getX()+ plugin.offSetTextHorizontal, (-plugin.offSetTextVertical)+(int) (textLocation.getY() * verticalOffSetMultiplier));
            RenderSpecialText(graphics, textLocation, playerName, tickDisplayString, color,lingeringTick);
        }

    }

    /**
     * Modified version of {@link net.runelite.client.ui.overlay.OverlayUtil#renderTextLocation(Graphics2D, Point, String, Color)} <br>
     * Draws an initial string with the special value, followed by drawing a string of the ticks since special drain.<br>
     * Used to draw left to right rather than being centered, maintains value location whether or not ticks are currently displayed.
     */
    public void RenderSpecialText(Graphics2D graphics, Point txtLoc, String specialValueText, String ticksSinceSpecialText, Color color, boolean lingeringTick) {

        int x = txtLoc.getX();
        int y = txtLoc.getY();

        graphics.setColor(Color.BLACK);
        graphics.drawString(specialValueText, x  + 1, y + 1);

        graphics.setColor(ColorUtil.colorWithAlpha(color, 0xFF));
        graphics.drawString(specialValueText, x , y);


        FontMetrics fm = graphics.getFontMetrics();
        int textWidth = fm.stringWidth(specialValueText);

        int alpha = 0xFF;
        if(lingeringTick)
        {
            //following the last tick the text will linger and fade out
            float tickPercentage = (client.getGameCycle() - plugin.getLastKnownGameCycle()) / 30f;
            alpha = (int) (255 - (255 * tickPercentage));
        }

        graphics.setColor(ColorUtil.colorWithAlpha(Color.BLACK, alpha));
        graphics.drawString(ticksSinceSpecialText, x+textWidth+1, y+1);

        graphics.setColor(ColorUtil.colorWithAlpha(Color.yellow, alpha));
        graphics.drawString(ticksSinceSpecialText, x+textWidth, y);

    }

}
