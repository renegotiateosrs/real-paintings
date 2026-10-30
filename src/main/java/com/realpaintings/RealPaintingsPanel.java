package com.realpaintings;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;

class RealPaintingsPanel extends PluginPanel
{
	private static final int THUMBNAIL_SIZE = 40;

	private final RealPaintingsPlugin plugin;
	private final Map<Painting, Row> rows = new EnumMap<>(Painting.class);

	RealPaintingsPanel(RealPaintingsPlugin plugin)
	{
		this.plugin = plugin;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		JLabel title = new JLabel("Real Paintings");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setAlignmentX(LEFT_ALIGNMENT);
		add(title);

		JLabel help = new JLabel("<html>Choose a picture for any painting in your house's quest hall. "
			+ "You can also copy images straight into the image folder, named after the painting "
			+ "(e.g. <i>lumbridge.png</i>).</html>");
		help.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		help.setBorder(BorderFactory.createEmptyBorder(6, 0, 8, 0));
		help.setAlignmentX(LEFT_ALIGNMENT);
		add(help);

		JButton openFolder = new JButton("Open image folder");
		openFolder.setAlignmentX(LEFT_ALIGNMENT);
		openFolder.addActionListener(e -> plugin.openImageFolder());
		add(openFolder);

		for (Painting painting : Painting.values())
		{
			Row row = new Row(painting);
			rows.put(painting, row);
			add(row);
		}

		refresh();
	}

	/**
	 * Updates the thumbnails and file names. Safe to call from any thread.
	 */
	void refresh()
	{
		SwingUtilities.invokeLater(() -> rows.values().forEach(Row::update));
	}

	private class Row extends JPanel
	{
		private final Painting painting;
		private final JLabel thumbnail = new JLabel();
		private final JLabel status = new JLabel();
		private final JButton clear = new JButton("Clear");
		private BufferedImage shownImage;

		Row(Painting painting)
		{
			this.painting = painting;

			setLayout(new BorderLayout(8, 0));
			setBackground(ColorScheme.DARKER_GRAY_COLOR);
			setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(8, 0, 0, 0, ColorScheme.DARK_GRAY_COLOR),
				BorderFactory.createEmptyBorder(6, 6, 6, 6)));
			setAlignmentX(LEFT_ALIGNMENT);

			thumbnail.setPreferredSize(new Dimension(THUMBNAIL_SIZE, THUMBNAIL_SIZE));
			thumbnail.setHorizontalAlignment(SwingConstants.CENTER);
			thumbnail.setBackground(ColorScheme.DARK_GRAY_COLOR);
			thumbnail.setOpaque(true);
			add(thumbnail, BorderLayout.WEST);

			JPanel text = new JPanel(new GridLayout(2, 1));
			text.setOpaque(false);
			JLabel name = new JLabel(painting.getDisplayName());
			name.setForeground(ColorScheme.TEXT_COLOR);
			text.add(name);
			status.setFont(FontManager.getRunescapeSmallFont());
			text.add(status);
			add(text, BorderLayout.CENTER);

			JPanel buttons = new JPanel(new GridLayout(1, 2, 4, 0));
			buttons.setOpaque(false);
			buttons.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
			JButton choose = new JButton("Choose...");
			choose.addActionListener(e -> plugin.choosePicture(painting));
			clear.addActionListener(e -> plugin.clearPicture(painting));
			buttons.add(choose);
			buttons.add(clear);
			add(buttons, BorderLayout.SOUTH);
		}

		void update()
		{
			PaintingImageStore store = plugin.getImageStore();
			BufferedImage image = store.getImage(painting);
			String fileName = store.getFileName(painting);

			if (image != shownImage)
			{
				shownImage = image;
				thumbnail.setIcon(image == null ? null
					: new ImageIcon(ImageUtil.resizeImage(image, THUMBNAIL_SIZE, THUMBNAIL_SIZE, true)));
			}

			if (fileName == null)
			{
				status.setText("Original painting");
				status.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
				status.setToolTipText("Looking for " + painting.getFileName() + ".png / .jpg");
			}
			else
			{
				status.setText(fileName);
				status.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
				status.setToolTipText(fileName);
			}
			clear.setEnabled(fileName != null);
		}
	}
}
