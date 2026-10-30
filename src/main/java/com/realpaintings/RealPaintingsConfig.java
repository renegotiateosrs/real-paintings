package com.realpaintings;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(RealPaintingsConfig.GROUP)
public interface RealPaintingsConfig extends Config
{
	String GROUP = "realpaintings";

	@ConfigItem(
		keyName = "fitMode",
		name = "Image fit",
		description = "How your picture is fitted to the painting's canvas",
		position = 0
	)
	default FitMode fitMode()
	{
		return FitMode.CROP;
	}

	@Range(max = 40)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "frameInset",
		name = "Frame size",
		description = "How much of the painting's edge is left visible as a frame around your picture",
		position = 1
	)
	default int frameInset()
	{
		return 10;
	}

	@Range(min = 10, max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "brightness",
		name = "Brightness",
		description = "Darken the picture so it blends with the room's lighting",
		position = 2
	)
	default int brightness()
	{
		return 85;
	}

	@Range(min = 10, max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "opacity",
		name = "Opacity",
		description = "How opaque the picture is drawn over the original painting",
		position = 3
	)
	default int opacity()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "showMenuOptions",
		name = "Right-click options",
		description = "Add 'Set picture' / 'Clear picture' options when right-clicking a painting",
		position = 4
	)
	default boolean showMenuOptions()
	{
		return true;
	}
}
