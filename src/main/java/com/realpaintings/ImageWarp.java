package com.realpaintings;

import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

/**
 * Draws an image onto an arbitrary screen-space quadrilateral.
 * <p>
 * The quad is given as a {@code (grid + 1) x (grid + 1)} lattice of projected points,
 * row-major from the top-left. Each lattice cell is drawn as two triangles with their own
 * affine transform, which approximates a true perspective warp closely enough to be
 * indistinguishable at the size paintings appear on screen.
 */
final class ImageWarp
{
	private ImageWarp()
	{
	}

	static void draw(Graphics2D graphics, BufferedImage image, int[] screenX, int[] screenY, int grid)
	{
		Shape originalClip = graphics.getClip();
		float cellWidth = (float) image.getWidth() / grid;
		float cellHeight = (float) image.getHeight() / grid;

		for (int row = 0; row < grid; row++)
		{
			for (int col = 0; col < grid; col++)
			{
				int i00 = row * (grid + 1) + col;
				int i10 = i00 + 1;
				int i01 = i00 + grid + 1;
				int i11 = i01 + 1;
				float sx0 = col * cellWidth;
				float sy0 = row * cellHeight;

				drawTriangle(graphics, image, screenX, screenY, sx0, sy0, cellWidth, cellHeight, i00, i10, i01);
				graphics.setClip(originalClip);
				drawTriangle(graphics, image, screenX, screenY, sx0 + cellWidth, sy0 + cellHeight, -cellWidth, -cellHeight, i11, i01, i10);
				graphics.setClip(originalClip);
			}
		}
	}

	/**
	 * Draws the right-angled triangle of {@code image} with its corner at (sx, sy) and legs
	 * of length dx (horizontal) and dy (vertical) onto the screen triangle formed by the
	 * points {@code corner}, {@code alongX} and {@code alongY}.
	 */
	private static void drawTriangle(Graphics2D graphics, BufferedImage image, int[] screenX, int[] screenY,
		float sx, float sy, float dx, float dy, int corner, int alongX, int alongY)
	{
		float cx = screenX[corner], cy = screenY[corner];
		float m00 = (screenX[alongX] - cx) / dx;
		float m10 = (screenY[alongX] - cy) / dx;
		float m01 = (screenX[alongY] - cx) / dy;
		float m11 = (screenY[alongY] - cy) / dy;
		if (m00 * m11 - m01 * m10 == 0)
		{
			return;
		}

		AffineTransform transform = new AffineTransform(m00, m10, m01, m11,
			cx - m00 * sx - m01 * sy,
			cy - m10 * sx - m11 * sy);

		Path2D.Float triangle = new Path2D.Float();
		triangle.moveTo(cx, cy);
		triangle.lineTo(screenX[alongX], screenY[alongX]);
		triangle.lineTo(screenX[alongY], screenY[alongY]);
		triangle.closePath();

		graphics.clip(triangle);
		graphics.drawImage(image, transform, null);
	}
}
