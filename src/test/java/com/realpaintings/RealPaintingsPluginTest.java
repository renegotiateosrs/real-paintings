package com.realpaintings;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class RealPaintingsPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(RealPaintingsPlugin.class);
		RuneLite.main(args);
	}
}
