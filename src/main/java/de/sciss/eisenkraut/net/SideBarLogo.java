/*
 *  SideBarLogo.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2015 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 */

package de.sciss.eisenkraut.net;

import java.awt.Dimension;
import java.awt.Graphics;

import javax.swing.ImageIcon;
import javax.swing.JComponent;

@SuppressWarnings("serial")
public class SideBarLogo extends JComponent {
	private static int			width;
	private static int			height;
	private static ImageIcon	icon	= null;

	public SideBarLogo() {
		super();

		if (icon == null) {
			icon   = new ImageIcon(getClass().getResource("SCcubeLogo.png"));
			width  = icon.getIconWidth();
			height = icon.getIconHeight();
		}

		setMinimumSize  (new Dimension(width, height));
		setPreferredSize(new Dimension(width, getPreferredSize().height));
		setMaximumSize  (new Dimension(width, getMaximumSize  ().height));
	}

	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		icon.paintIcon(this, g, 0, 0);
	}
}