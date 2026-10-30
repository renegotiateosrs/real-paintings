package com.realpaintings;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.events.DecorativeObjectDespawned;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;

@Slf4j
@PluginDescriptor(
	name = "Real Paintings",
	description = "Replace the paintings and maps hanging in your house with your own pictures",
	tags = {"poh", "house", "painting", "portrait", "landscape", "image", "photo"},
	internalName = "real-paintings"
)
public class RealPaintingsPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private RealPaintingsOverlay overlay;

	@Inject
	private RealPaintingsConfig config;

	@Inject
	private ScheduledExecutorService executor;

	/**
	 * Painting objects currently in the scene. Only accessed from the client thread.
	 */
	@Getter(AccessLevel.PACKAGE)
	private final Map<DecorativeObject, Painting> paintings = new HashMap<>();

	@Getter(AccessLevel.PACKAGE)
	private PaintingImageStore imageStore;

	private NavigationButton navButton;
	private ScheduledFuture<?> scanFuture;

	@Override
	protected void startUp() throws Exception
	{
		imageStore = new PaintingImageStore(getPluginDirectory());

		RealPaintingsPanel panel = new RealPaintingsPanel(this);
		imageStore.addListener(panel::refresh);

		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Real Paintings")
			.icon(icon)
			.priority(8)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		overlayManager.add(overlay);

		// Poll the folder so pictures dropped into it in a file manager show up by themselves
		scanFuture = executor.scheduleWithFixedDelay(this::scanImages, 0, 3, TimeUnit.SECONDS);

		clientThread.invoke(this::findPaintingsInScene);
	}

	@Override
	protected void shutDown() throws Exception
	{
		if (scanFuture != null)
		{
			scanFuture.cancel(true);
			scanFuture = null;
		}
		overlayManager.remove(overlay);
		overlay.clearCache();
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		clientThread.invoke(paintings::clear);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOADING)
		{
			paintings.clear();
		}
	}

	@Subscribe
	public void onDecorativeObjectSpawned(DecorativeObjectSpawned event)
	{
		DecorativeObject object = event.getDecorativeObject();
		Painting painting = Painting.forObjectId(object.getId());
		if (painting != null)
		{
			paintings.put(object, painting);
		}
	}

	@Subscribe
	public void onDecorativeObjectDespawned(DecorativeObjectDespawned event)
	{
		paintings.remove(event.getDecorativeObject());
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!config.showMenuOptions())
		{
			return;
		}

		MenuEntry entry = event.getMenuEntry();
		if (entry.getType() != MenuAction.EXAMINE_OBJECT)
		{
			return;
		}

		Painting painting = Painting.forObjectId(entry.getIdentifier());
		if (painting == null)
		{
			return;
		}

		if (imageStore.getImage(painting) != null)
		{
			client.getMenu().createMenuEntry(1)
				.setOption("Clear picture")
				.setTarget(entry.getTarget())
				.setType(MenuAction.RUNELITE)
				.onClick(e -> clearPicture(painting));
		}

		client.getMenu().createMenuEntry(1)
			.setOption("Set picture")
			.setTarget(entry.getTarget())
			.setType(MenuAction.RUNELITE)
			.onClick(e -> choosePicture(painting));
	}

	/**
	 * Asks the user for an image file and uses it for {@code painting}.
	 */
	void choosePicture(Painting painting)
	{
		SwingUtilities.invokeLater(() ->
		{
			List<Filepath> files = new Filepath.Chooser()
				.setIsOpen()
				.setAcceptsFiles()
				.setDialogTitle("Choose a picture for the " + painting.getDisplayName())
				.addExtensionFilter("Images", PaintingImageStore.EXTENSIONS)
				.showDialog(client);
			if (files == null || files.isEmpty())
			{
				return;
			}

			Filepath file = files.get(0);
			executor.execute(() ->
			{
				try
				{
					imageStore.setImage(painting, file);
				}
				catch (IOException | IllegalArgumentException e)
				{
					log.debug("Unable to set picture for {}", painting, e);
					showError("Couldn't use " + file.getFileName() + ": " + e.getMessage());
				}
			});
		});
	}

	void clearPicture(Painting painting)
	{
		executor.execute(() ->
		{
			try
			{
				imageStore.clearImage(painting);
			}
			catch (IOException e)
			{
				log.debug("Unable to clear picture for {}", painting, e);
				showError("Couldn't remove the picture: " + e.getMessage());
			}
		});
	}

	void openImageFolder()
	{
		LinkBrowser.open(imageStore.getDirectory().toString());
	}

	void scanImages()
	{
		try
		{
			imageStore.scan();
		}
		catch (RuntimeException e)
		{
			// Don't let an exception cancel the scheduled rescans
			log.debug("Error scanning image folder", e);
		}
	}

	private void findPaintingsInScene()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		Scene scene = client.getTopLevelWorldView().getScene();
		for (Tile[][] plane : scene.getTiles())
		{
			for (Tile[] column : plane)
			{
				for (Tile tile : column)
				{
					if (tile == null || tile.getDecorativeObject() == null)
					{
						continue;
					}

					DecorativeObject object = tile.getDecorativeObject();
					Painting painting = Painting.forObjectId(object.getId());
					if (painting != null)
					{
						paintings.put(object, painting);
					}
				}
			}
		}
	}

	private void showError(String message)
	{
		SwingUtilities.invokeLater(() ->
			JOptionPane.showMessageDialog(client.getCanvas(), message, "Real Paintings", JOptionPane.WARNING_MESSAGE));
	}

	@Provides
	RealPaintingsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RealPaintingsConfig.class);
	}
}
