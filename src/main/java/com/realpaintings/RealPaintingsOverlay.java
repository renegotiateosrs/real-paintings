package com.realpaintings;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.DecorativeObject;
import net.runelite.api.Model;
import net.runelite.api.Perspective;
import net.runelite.api.Renderable;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

/**
 * Draws the user's pictures over the paintings in the scene, warped to match the
 * painting's perspective on screen.
 */
class RealPaintingsOverlay extends Overlay
{
	/**
	 * Number of rows and columns the picture is split into for {@link ImageWarp}.
	 */
	private static final int GRID = 6;
	private static final int POINTS = (GRID + 1) * (GRID + 1);

	private final Client client;
	private final RealPaintingsPlugin plugin;
	private final RealPaintingsConfig config;

	private final float[] pointsX = new float[POINTS];
	private final float[] pointsY = new float[POINTS];
	private final float[] pointsZ = new float[POINTS];
	private final int[] screenX = new int[POINTS];
	private final int[] screenY = new int[POINTS];

	private final Map<Painting, PreparedImage> prepared = new EnumMap<>(Painting.class);

	@Inject
	RealPaintingsOverlay(Client client, RealPaintingsPlugin plugin, RealPaintingsConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(OverlayPriority.LOW);
	}

	void clearCache()
	{
		prepared.clear();
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Map<DecorativeObject, Painting> paintings = plugin.getPaintings();
		if (paintings.isEmpty())
		{
			return null;
		}

		Composite originalComposite = graphics.getComposite();
		Shape originalClip = graphics.getClip();
		Object originalInterpolation = graphics.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		if (config.opacity() < 100)
		{
			graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, config.opacity() / 100f));
		}

		for (Map.Entry<DecorativeObject, Painting> entry : paintings.entrySet())
		{
			BufferedImage source = plugin.getImageStore().getImage(entry.getValue());
			if (source != null)
			{
				renderPainting(graphics, entry.getKey(), entry.getValue(), source);
			}
		}

		graphics.setComposite(originalComposite);
		graphics.setClip(originalClip);
		if (originalInterpolation != null)
		{
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, originalInterpolation);
		}
		return null;
	}

	private void renderPainting(Graphics2D graphics, DecorativeObject object, Painting painting, BufferedImage source)
	{
		if (object.getPlane() != client.getTopLevelWorldView().getPlane())
		{
			return;
		}

		Renderable renderable = object.getRenderable();
		if (renderable == null)
		{
			return;
		}

		Model model = renderable instanceof Model ? (Model) renderable : renderable.getModel();
		if (model == null)
		{
			return;
		}

		PaintingQuad quad = PaintingQuad.fromModel(
			model.getVerticesX(), model.getVerticesY(), model.getVerticesZ(), model.getVerticesCount(),
			object.getX() + object.getXOffset(),
			object.getY() + object.getYOffset(),
			object.getZ() - renderable.getAnimationHeightOffset(),
			object.getX(), object.getY(),
			config.frameInset() / 100f);
		if (quad == null || !quad.isFacing(client.getCameraX(), client.getCameraY()))
		{
			return;
		}

		for (int row = 0; row <= GRID; row++)
		{
			float v = (float) row / GRID;
			for (int col = 0; col <= GRID; col++)
			{
				float u = (float) col / GRID;
				int i = row * (GRID + 1) + col;
				pointsX[i] = quad.x + quad.rightX * u;
				pointsY[i] = quad.y + quad.rightY * u;
				pointsZ[i] = quad.z + quad.height * v;
			}
		}

		Perspective.modelToCanvas(client, object.getWorldView(), POINTS, 0, 0, 0, 0,
			pointsX, pointsY, pointsZ, screenX, screenY);
		for (int i = 0; i < POINTS; i++)
		{
			if (screenX[i] == Integer.MIN_VALUE)
			{
				// Part of the painting is behind the camera
				return;
			}
		}

		BufferedImage image = prepare(painting, source, quad.width() / quad.height);
		ImageWarp.draw(graphics, image, screenX, screenY, GRID);
	}

	/**
	 * Crops/fits the picture to the painting's aspect ratio and applies the brightness
	 * setting, caching the result since it only changes when the settings or picture do.
	 */
	private BufferedImage prepare(Painting painting, BufferedImage source, float aspect)
	{
		FitMode mode = config.fitMode();
		int brightness = config.brightness();
		// Round so float noise in the model doesn't defeat the cache
		float roundedAspect = Math.round(aspect * 50) / 50f;

		PreparedImage cached = prepared.get(painting);
		if (cached != null && cached.matches(source, mode, brightness, roundedAspect))
		{
			return cached.image;
		}

		BufferedImage image = PreparedImage.render(source, mode, brightness, roundedAspect);
		prepared.put(painting, new PreparedImage(source, mode, brightness, roundedAspect, image));
		return image;
	}

	private static class PreparedImage
	{
		final BufferedImage source;
		final FitMode mode;
		final int brightness;
		final float aspect;
		final BufferedImage image;

		PreparedImage(BufferedImage source, FitMode mode, int brightness, float aspect, BufferedImage image)
		{
			this.source = source;
			this.mode = mode;
			this.brightness = brightness;
			this.aspect = aspect;
			this.image = image;
		}

		boolean matches(BufferedImage source, FitMode mode, int brightness, float aspect)
		{
			return this.source == source && this.mode == mode && this.brightness == brightness
				&& this.aspect == aspect;
		}

		static BufferedImage render(BufferedImage source, FitMode mode, int brightness, float aspect)
		{
			int srcW = source.getWidth();
			int srcH = source.getHeight();
			float srcAspect = (float) srcW / srcH;

			int outW, outH;
			int drawX = 0, drawY = 0, drawW, drawH;
			int cropX = 0, cropY = 0, cropW = srcW, cropH = srcH;
			switch (mode)
			{
				case CROP:
					if (srcAspect > aspect)
					{
						cropW = Math.max(1, Math.round(srcH * aspect));
						cropX = (srcW - cropW) / 2;
					}
					else
					{
						cropH = Math.max(1, Math.round(srcW / aspect));
						cropY = (srcH - cropH) / 2;
					}
					outW = drawW = cropW;
					outH = drawH = cropH;
					break;
				case FIT:
					if (srcAspect > aspect)
					{
						outW = srcW;
						outH = Math.max(1, Math.round(srcW / aspect));
					}
					else
					{
						outH = srcH;
						outW = Math.max(1, Math.round(srcH * aspect));
					}
					drawW = srcW;
					drawH = srcH;
					drawX = (outW - srcW) / 2;
					drawY = (outH - srcH) / 2;
					break;
				default:
					outW = drawW = srcW;
					outH = drawH = srcH;
					break;
			}

			BufferedImage out = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = out.createGraphics();
			if (mode == FitMode.FIT)
			{
				g.setColor(Color.BLACK);
				g.fillRect(0, 0, outW, outH);
			}
			g.drawImage(source,
				drawX, drawY, drawX + drawW, drawY + drawH,
				cropX, cropY, cropX + cropW, cropY + cropH,
				null);
			if (brightness < 100)
			{
				g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 1 - brightness / 100f));
				g.setColor(Color.BLACK);
				g.fillRect(0, 0, outW, outH);
			}
			g.dispose();
			return out;
		}
	}
}
