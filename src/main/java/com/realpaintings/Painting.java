package com.realpaintings;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.ObjectID;

/**
 * The wall decorations in a player-owned house that can be given a custom picture.
 * <p>
 * {@link #fileName} is the base name (without extension) the plugin looks for in its
 * image folder, e.g. {@code lumbridge.png} or {@code lumbridge.jpg}.
 */
@Getter
@RequiredArgsConstructor
public enum Painting
{
	// Quest hall - portrait space
	KING_ARTHUR(ObjectID.POH_PORTRAIT_KINGARTHUR_1, "King Arthur portrait", "arthur"),
	ELENA(ObjectID.POH_PORTRAIT_ELENA_1, "Elena portrait", "elena"),
	GIANT_DWARF(ObjectID.POH_PORTRAIT_GIANTDWARF_1, "Giant dwarf portrait", "giant-dwarf"),
	MISCELLANIANS(ObjectID.POH_PORTRAIT_PRINCE_PRINCESS_1, "Miscellanians portrait", "miscellanians"),

	// Quest hall - landscape space
	LUMBRIDGE(ObjectID.POH_LANDSCAPE_LUMBRIDGE_1, "Lumbridge landscape", "lumbridge"),
	DESERT(ObjectID.POH_LANDSCAPE_DESERT_1, "The Desert landscape", "desert"),
	MORYTANIA(ObjectID.POH_LANDSCAPE_MORYTANIA_1, "Morytania landscape", "morytania"),
	KARAMJA(ObjectID.POH_LANDSCAPE_KARAMJA_1, "Karamja landscape", "karamja"),
	ISAFDAR(ObjectID.POH_LANDSCAPE_ISTAFAR_1, "Isafdar landscape", "isafdar"),

	// Quest hall - map space
	SMALL_MAP(ObjectID.POH_WALL_MAP_FREEAREA, "Small map", "small-map"),
	MEDIUM_MAP(ObjectID.POH_WALL_MAP_WORLD, "Medium map", "medium-map"),
	LARGE_MAP(ObjectID.POH_WALL_MAP_WORLD_UNDERGROUND, "Large map", "large-map");

	private static final Map<Integer, Painting> BY_OBJECT_ID = new HashMap<>();

	static
	{
		for (Painting painting : values())
		{
			BY_OBJECT_ID.put(painting.objectId, painting);
		}
	}

	private final int objectId;
	private final String displayName;
	private final String fileName;

	@Nullable
	public static Painting forObjectId(int objectId)
	{
		return BY_OBJECT_ID.get(objectId);
	}
}
