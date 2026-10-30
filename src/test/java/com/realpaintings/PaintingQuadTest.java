package com.realpaintings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class PaintingQuadTest
{
	private static final int TILE_X = 6464;
	private static final int TILE_Y = 6464;

	/**
	 * A box-shaped painting 4 units deep against the east edge of the tile: x 56..60,
	 * y -40..40 (north), height -200..-100 (y is negative upwards).
	 */
	private static PaintingQuad eastWallPainting(float inset)
	{
		float[] vx = new float[8], vy = new float[8], vz = new float[8];
		int i = 0;
		for (int x : new int[]{56, 60})
		{
			for (int z : new int[]{-40, 40})
			{
				for (int y : new int[]{-200, -100})
				{
					vx[i] = x;
					vz[i] = z;
					vy[i] = y;
					i++;
				}
			}
		}
		return PaintingQuad.fromModel(vx, vy, vz, 8, TILE_X, TILE_Y, 0, TILE_X, TILE_Y, inset);
	}

	@Test
	public void eastWallFacesWest()
	{
		PaintingQuad quad = eastWallPainting(0);
		assertNotNull(quad);
		assertEquals(-1, quad.normalX);
		assertEquals(0, quad.normalY);
		// Front face is the one nearer the middle of the tile
		assertEquals(TILE_X + 56, quad.x, 0.01);
		// Facing east, the viewer's left is north, so the quad runs north -> south
		assertEquals(TILE_Y + 40, quad.y, 0.01);
		assertEquals(0, quad.rightX, 0.01);
		assertEquals(-80, quad.rightY, 0.01);
		assertEquals(-200, quad.z, 0.01);
		assertEquals(100, quad.height, 0.01);
	}

	@Test
	public void insetTrimsEveryEdgeEqually()
	{
		PaintingQuad quad = eastWallPainting(0.1f);
		assertNotNull(quad);
		// 10% of the shorter side (80) = 8 units per edge
		assertEquals(64, quad.width(), 0.01);
		assertEquals(84, quad.height, 0.01);
		assertEquals(-192, quad.z, 0.01);
		assertEquals(TILE_Y + 32, quad.y, 0.01);
	}

	@Test
	public void onlyVisibleFromTheRoom()
	{
		PaintingQuad quad = eastWallPainting(0);
		assertNotNull(quad);
		assertTrue(quad.isFacing(TILE_X - 300, TILE_Y));
		assertFalse(quad.isFacing(TILE_X + 300, TILE_Y));
	}

	@Test
	public void southWallFacesNorth()
	{
		float[] vx = {-50, 50, -50, 50};
		float[] vz = {-60, -60, -58, -58};
		float[] vy = {-150, -150, -50, -50};
		PaintingQuad quad = PaintingQuad.fromModel(vx, vy, vz, 4, TILE_X, TILE_Y, 0, TILE_X, TILE_Y, 0);
		assertNotNull(quad);
		assertEquals(1, quad.normalY);
		// Facing south, the viewer's left is east
		assertEquals(TILE_X + 50, quad.x, 0.01);
		assertEquals(-100, quad.rightX, 0.01);
		assertEquals(TILE_Y - 58, quad.y, 0.01);
	}

	@Test
	public void degenerateModelIsRejected()
	{
		assertNull(PaintingQuad.fromModel(new float[0], new float[0], new float[0], 0, 0, 0, 0, 0, 0, 0));
		float[] flat = {0, 10, 20};
		float[] zero = {0, 0, 0};
		assertNull(PaintingQuad.fromModel(flat, zero, zero, 3, 0, 0, 0, 0, 0, 0));
	}
}
