package com.realpaintings;

import javax.annotation.Nullable;
import lombok.AllArgsConstructor;

/**
 * The flat, upright rectangle on the front of a wall decoration that a picture is drawn onto.
 * <p>
 * Coordinates are in local scene units: {@code x} east, {@code y} north and {@code z} height,
 * where (as in the game engine) a smaller {@code z} is higher up.
 */
@AllArgsConstructor
class PaintingQuad
{
	/** Top-left corner, as seen by someone standing in front of the painting. */
	final float x;
	final float y;
	final float z;
	/** Vector from the left edge to the right edge. */
	final float rightX;
	final float rightY;
	/** Height of the quad; it extends downwards (increasing z) from the top edge. */
	final float height;
	/** Unit normal pointing out of the painting, into the room. */
	final int normalX;
	final int normalY;

	float width()
	{
		return (float) Math.hypot(rightX, rightY);
	}

	/**
	 * @return true if a camera at the given local position is in front of the painting
	 */
	boolean isFacing(float cameraX, float cameraY)
	{
		return (cameraX - x) * normalX + (cameraY - y) * normalY > 0;
	}

	/**
	 * Computes the quad for a wall decoration from its model vertices.
	 *
	 * @param vx         model vertex x coordinates
	 * @param vy         model vertex y coordinates (height, negative is up)
	 * @param vz         model vertex z coordinates (north)
	 * @param count      number of vertices to use
	 * @param modelX     local x the model is drawn at
	 * @param modelY     local y (north) the model is drawn at
	 * @param modelZ     height the model is drawn at
	 * @param tileX      local x of the centre of the object's tile
	 * @param tileY      local y of the centre of the object's tile
	 * @param insetRatio fraction of the shorter side to trim from every edge as a frame
	 * @return the quad, or {@code null} if the model isn't a flat wall-mounted shape
	 */
	@Nullable
	static PaintingQuad fromModel(float[] vx, float[] vy, float[] vz, int count,
		int modelX, int modelY, int modelZ, int tileX, int tileY, float insetRatio)
	{
		if (count <= 0)
		{
			return null;
		}

		float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
		float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
		float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
		for (int i = 0; i < count; i++)
		{
			float x = modelX + vx[i];
			float y = modelY + vz[i];
			float z = modelZ + vy[i];
			minX = Math.min(minX, x);
			maxX = Math.max(maxX, x);
			minY = Math.min(minY, y);
			maxY = Math.max(maxY, y);
			minZ = Math.min(minZ, z);
			maxZ = Math.max(maxZ, z);
		}

		float spanX = maxX - minX;
		float spanY = maxY - minY;
		float spanZ = maxZ - minZ;
		if (spanZ <= 0 || Math.max(spanX, spanY) <= 0)
		{
			return null;
		}

		// The painting lies along the wall, so its thinnest horizontal axis is the wall normal.
		// The front of the painting is the face nearest the middle of the tile, which is
		// where the room is; the wall itself runs along the tile's edge.
		boolean alongY = spanX < spanY;
		int normalX = 0, normalY = 0;
		float frontX, frontY, left, length;
		if (alongY)
		{
			normalX = (minX + maxX) / 2 >= tileX ? -1 : 1;
			frontX = normalX < 0 ? minX : maxX;
			length = spanY;
			// Facing west the viewer's left is north; facing east it is south
			left = normalX < 0 ? maxY : minY;
			frontY = left;
		}
		else
		{
			normalY = (minY + maxY) / 2 >= tileY ? -1 : 1;
			frontY = normalY < 0 ? minY : maxY;
			length = spanX;
			// Facing south the viewer's left is west; facing north it is east
			left = normalY < 0 ? minX : maxX;
			frontX = left;
		}

		// The viewer looks along -normal, so their right-hand direction is (-normalY, normalX)
		float dirX = -normalY;
		float dirY = normalX;

		float inset = Math.max(0, insetRatio) * Math.min(length, spanZ);
		float width = length - 2 * inset;
		float height = spanZ - 2 * inset;
		if (width <= 1 || height <= 1)
		{
			return null;
		}

		return new PaintingQuad(
			frontX + dirX * inset,
			frontY + dirY * inset,
			minZ + inset,
			dirX * width,
			dirY * width,
			height,
			normalX,
			normalY);
	}
}
