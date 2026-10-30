package com.realpaintings;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

/**
 * Loads the user's pictures from the plugin's data folder and keeps them in sync with it.
 * <p>
 * A picture for a painting is any supported image file whose base name matches
 * {@link Painting#getFileName()}, e.g. {@code lumbridge.jpg}.
 */
@Slf4j
class PaintingImageStore
{
	static final String[] EXTENSIONS = {"png", "jpg", "jpeg", "gif", "bmp"};

	/**
	 * Pictures are downscaled to this size on load; paintings never cover enough of the
	 * screen to need more, and smaller images keep the per-frame drawing cheap.
	 */
	private static final int MAX_IMAGE_SIZE = 1024;

	@AllArgsConstructor
	private static class LoadedImage
	{
		final String fileName;
		final long lastModified;
		final BufferedImage image;
	}

	private final Filepath directory;
	private final Map<Painting, LoadedImage> images = new EnumMap<>(Painting.class);
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

	PaintingImageStore(Filepath directory)
	{
		this.directory = directory;
	}

	Filepath getDirectory()
	{
		return directory;
	}

	void addListener(Runnable listener)
	{
		listeners.add(listener);
	}

	@Nullable
	synchronized BufferedImage getImage(Painting painting)
	{
		LoadedImage loaded = images.get(painting);
		return loaded == null ? null : loaded.image;
	}

	@Nullable
	synchronized String getFileName(Painting painting)
	{
		LoadedImage loaded = images.get(painting);
		return loaded == null ? null : loaded.fileName;
	}

	/**
	 * Re-reads the image folder, loading new or modified pictures and dropping deleted ones.
	 * Performs disk IO, so must not be called on the client thread.
	 */
	void scan()
	{
		boolean changed = false;
		List<Filepath> files = listImageFiles();

		for (Painting painting : Painting.values())
		{
			Filepath file = findFile(files, painting);
			LoadedImage current;
			synchronized (this)
			{
				current = images.get(painting);
			}

			if (file == null)
			{
				if (current != null)
				{
					synchronized (this)
					{
						images.remove(painting);
					}
					changed = true;
				}
				continue;
			}

			long lastModified;
			try
			{
				lastModified = file.getLastModifiedTime().toMillis();
			}
			catch (IOException e)
			{
				continue;
			}

			if (current != null && current.fileName.equals(file.getFileName()) && current.lastModified == lastModified)
			{
				continue;
			}

			BufferedImage image = readImage(file);
			synchronized (this)
			{
				if (image == null)
				{
					images.remove(painting);
				}
				else
				{
					images.put(painting, new LoadedImage(file.getFileName(), lastModified, image));
				}
			}
			changed = true;
		}

		if (changed)
		{
			listeners.forEach(Runnable::run);
		}
	}

	/**
	 * Copies {@code source} into the image folder as the picture for {@code painting},
	 * replacing any picture it had before.
	 *
	 * @throws IOException if the file can't be read as an image or copied
	 */
	void setImage(Painting painting, Filepath source) throws IOException
	{
		if (readImage(source) == null)
		{
			throw new IOException("Not a supported image file");
		}

		String extension = extensionOf(source.getFileName());
		Filepath target = directory.joinSegment(painting.getFileName() + "." + extension);

		for (Filepath existing : filesFor(painting))
		{
			if (!existing.equals(target))
			{
				existing.deleteIfExists();
			}
		}

		if (!source.equals(target))
		{
			source.copyTo(target, StandardCopyOption.REPLACE_EXISTING);
		}
		scan();
	}

	void clearImage(Painting painting) throws IOException
	{
		for (Filepath existing : filesFor(painting))
		{
			existing.deleteIfExists();
		}
		scan();
	}

	private List<Filepath> filesFor(Painting painting)
	{
		return listImageFiles().stream()
			.filter(f -> baseNameOf(f.getFileName()).equalsIgnoreCase(painting.getFileName()))
			.collect(Collectors.toList());
	}

	@Nullable
	private static Filepath findFile(List<Filepath> files, Painting painting)
	{
		// Prefer extensions in EXTENSIONS order if several files exist for one painting
		for (String extension : EXTENSIONS)
		{
			for (Filepath file : files)
			{
				String name = file.getFileName();
				if (baseNameOf(name).equalsIgnoreCase(painting.getFileName())
					&& extensionOf(name).equals(extension))
				{
					return file;
				}
			}
		}
		return null;
	}

	private List<Filepath> listImageFiles()
	{
		try (Stream<Filepath> stream = directory.walk(1))
		{
			return stream
				.filter(Filepath::isFile)
				.filter(f -> isSupported(f.getFileName()))
				.collect(Collectors.toList());
		}
		catch (IOException e)
		{
			log.debug("Unable to list image folder", e);
			return new ArrayList<>();
		}
	}

	@Nullable
	private static BufferedImage readImage(Filepath file)
	{
		try (InputStream in = file.openInputStream())
		{
			BufferedImage image = ImageIO.read(in);
			return image == null ? null : downscale(image);
		}
		catch (IOException e)
		{
			log.debug("Unable to read image {}", file, e);
			return null;
		}
	}

	private static BufferedImage downscale(BufferedImage image)
	{
		int width = image.getWidth();
		int height = image.getHeight();
		double scale = Math.min(1.0, (double) MAX_IMAGE_SIZE / Math.max(width, height));
		int newWidth = Math.max(1, (int) Math.round(width * scale));
		int newHeight = Math.max(1, (int) Math.round(height * scale));

		BufferedImage out = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.drawImage(image, 0, 0, newWidth, newHeight, null);
		g.dispose();
		return out;
	}

	private static boolean isSupported(String fileName)
	{
		String extension = extensionOf(fileName);
		for (String supported : EXTENSIONS)
		{
			if (supported.equals(extension))
			{
				return true;
			}
		}
		return false;
	}

	private static String baseNameOf(String fileName)
	{
		int dot = fileName.lastIndexOf('.');
		return dot == -1 ? fileName : fileName.substring(0, dot);
	}

	private static String extensionOf(String fileName)
	{
		int dot = fileName.lastIndexOf('.');
		return dot == -1 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
	}
}
