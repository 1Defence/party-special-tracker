package com.partyspecialtracker;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class PartySpecialTrackerPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(PartySpecialTrackerPlugin.class);
		RuneLite.main(args);
	}
}