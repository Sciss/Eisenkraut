/*
 *  GraphicsUtil.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 */

package de.sciss.eisenkraut.gui;

import de.sciss.eisenkraut.Main;
import de.sciss.gui.TiledImage;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 *  This is a helper class containing utility static functions
 *  and public objects for common graphics operations
 *
 *	TODO: the tool buttons on non-macos look-and-feel are far to wide
 */
public class GraphicsUtil
{
    /**
     *  Tool icon ID: transport play
     */
    public static final int ICON_PLAY		= 0;
    /**
     *  Tool icon ID: transport stop
     */
    public static final int ICON_STOP		= 1;
    /**
     *  Tool icon ID: transport loop
     */
    public static final int ICON_LOOP		= 2;
    /**
     *  Tool icon ID: transport rewind
     */
    public static final int ICON_REWIND		= 3;
    /**
     *  Tool icon ID: transport fast forward
     */
    public static final int ICON_FASTFORWARD= 4;
    /**
     *  Tool icon ID: open file chooser
     */
    public static final int ICON_CHOOSEPATH = 5;
    /**
     *  Tool icon ID: pointer tool
     */
    public static final int ICON_POINTER	= 6;
    /**
     *  Tool icon ID: line tool
     */
    public static final int ICON_LINE		= 7;
    /**
     *  Tool icon ID: bezier tool
     */
    public static final int ICON_CURVE		= 8;
    /**
     *  Tool icon ID: freehand tool
     */
    public static final int ICON_PENCIL		= 9;
    /**
     *  Tool icon ID: arc tool
     */
    public static final int ICON_ARC		= 10;
    /**
     *  Tool icon ID: tuning fork tool
     */
    public static final int ICON_FORK		= 11;
    /**
     *  Tool icon ID: tuning magnifying glass
     */
    public static final int ICON_ZOOM		= 12;
    /**
     *  Tool icon ID: catch (follow) timeline postion
     */
    public static final int ICON_CATCH		= 13;
    /**
     *  Tool icon ID: realtime plug-in (supercollider)
     */
    public static final int ICON_REALTIME	= 15;
    /**
     *  Tool icon ID: solo button for session objects
     */
    public static final int ICON_SOLO		= 16;
    /**
     *  Tool icon ID: mute button for session objects
     */
    public static final int ICON_MUTE		= 18;
    /**
     *  Tool icon ID: trajectory blending
     */
    public static final int ICON_BLENDING	= 19;
    /**
     *  Tool icon ID: preroll blending (not used)
     */
    public static final int ICON_PREEXTRA	= 20;
    /**
     *  Tool icon ID: postroll blending (not used)
     */
    public static final int ICON_POSTEXTRA	= 21;
    /**
     *  Tool icon ID: insert mode
     */
    public static final int ICON_INSERTMODE	= 22;
    /**
     *  Tool icon ID: replace mode
     */
    public static final int ICON_OVERWRITEMODE	= 23;
    /**
     *  Tool icon ID: mix mode
     */
    public static final int ICON_MIXMODE	= 24;

    /**
     *  Blue translucent colour
     *  for consistent style in selected objects
     */
    public static Color colrSelection() {
        return isDarkSkin() ? colrSelectionDark : colrSelectionLight;
    }

    /** <tt>true</tt> for bug: https://stackoverflow.com/questions/19480076 */
    public static final boolean animationNeedsSync =
            Main.isLinux && System.getProperty("java.version").startsWith("1.8.");

    public static void sync() {
        if (animationNeedsSync) Toolkit.getDefaultToolkit().sync();
    }

    public static Color colrInactiveSelection() {
        return isDarkSkin() ? colrSelectionDarkI : colrSelectionLightI;
    }

    private static final Color colrSelectionDark    = new Color(0x7F, 0x7F, 0xFF, 0x3F);
    private static final Color colrSelectionLight   = new Color(0x00, 0x00, 0xFF, 0x2F);

    private static final Color colrSelectionDarkI   = new Color(0xE0, 0xE0, 0xE0, 0x30);
    private static final Color colrSelectionLightI  = new Color(0x00, 0x00, 0x00, 0x20);

    /**
     *  Yellow translucent colour
     *  for consistent style in optional adjustment objects
     */
    public static final Color		colrAdjusting   = new Color( 0xFF, 0xFF, 0x00, 0x2F );
    /**
     *  Default font for GUI elements.
     *
     *  TODO: this is rather small and could be
     *			user adjustable in a future version.
     */
    public static final Font		smallGUIFont	= new Font( "Helvetica", Font.PLAIN, 10 );

    /**
     *  MacOS X Aqua style bar gradient with a size of 15 pixels
     *
     *  TODO:   this should look different on Windows and Linux
     *			depending on their VM's chrome.
     *
     *  @see	de.sciss.eisenkraut.timeline.TimelineAxis
     */
    public static Paint pntBarGradient() {
        return isDarkSkin() ? pntBarGradientDark : pntBarGradientLight;
    }

    public static boolean isDarkSkin() {
        return UIManager.getBoolean("dark-skin");
    }

    /**
     *  Collection of toolbar icons. The corresponding IDs are those
     *  named ICON_... (e.g. <code>ICON_PLAY</code> for the transport
     *  play icon).
     *
     *  @see	#createToolIcons( int )
     */
    protected static final TiledImage  imgToolIcons	= new TiledImage( GraphicsUtil.class.getResource( "toolicons.png" ), 16, 16 );

    private static final int[] pntBarGradPixLight = {
            0xFFB8B8B8, 0xFFC0C0C0, 0xFFC8C8C8, 0xFFD3D3D3,
            0xFFDBDBDB, 0xFFE4E4E4, 0xFFEBEBEB, 0xFFF1F1F1,
            0xFFF6F6F6, 0xFFFAFAFA, 0xFFFBFBFB, 0xFFFCFCFC,
            0xFFF9F9F9, 0xFFF4F4F4, 0xFFEFEFEF};

    private static final int[] pntBarGradPixDark = {
            0xFF080808, 0xFF101010, 0xFF141414, 0xFF1B1B1B,
            0xFF1D1D1D, 0xFF222222, 0xFF252525, 0xFF282828,
            0xFF2B2B2B, 0xFF2D2D2D, 0xFF2D2D2D, 0xFF2E2E2E,
            0xFF2C2C2C, 0xFF2A2A2A, 0xFF272727};

    private static final Paint pntBarGradientDark;
    private static final Paint pntBarGradientLight;

    static {
        final BufferedImage imgDark = new BufferedImage(1, 15, BufferedImage.TYPE_INT_ARGB);
        imgDark.setRGB(0, 0, 1, 15, pntBarGradPixDark, 0, 1);
        pntBarGradientDark = new TexturePaint(imgDark, new Rectangle(0, 0, 1, 15));
        final BufferedImage imgLight = new BufferedImage(1, 15, BufferedImage.TYPE_INT_ARGB);
        imgLight.setRGB(0, 0, 1, 15, pntBarGradPixLight, 0, 1);
        pntBarGradientLight = new TexturePaint(imgLight, new Rectangle(0, 0, 1, 15));
    }

    private GraphicsUtil() { /* empty */ }

    /**
     *  Creates an array of icons which display
     *  a particular icon in different shades
     *  which correspond to a gadget's states.
     *  <ul>
     *  <li>index 0 - gadget normal</li>
     *  <li>index 1 - gadget selected</li>
     *  <li>index 2 - gadget pressed down</li>
     *  <li>index 3 - gadget disabled</li>
     *  </ul>
     *  Usually you'll pass the result directly to
     *  the <code>setToolIcons</code> method.
     *
     *  @param  id  ID corresponding to the index in
     *				the tool icon tiled image, e.g.
     *				<code>ICON_PLAY</code> or <code>ICON_LINE</code>.
     *  @return		four <code>Icon</code> objects for
     *				different gadget states.
     *
     *  @see	#setToolIcons( AbstractButton, Icon[] )
     */
    public static Icon[] createToolIcons(int id) {
        final Shape shp;
        switch(id) {
            case ICON_PLAY:
                shp = shapePlay();
                break;
            case ICON_STOP:
                shp = shapeStop();
                break;
            case ICON_FASTFORWARD:
                shp = shapeFFwd();
                break;
            case ICON_REWIND:
                shp = shapeRwd();
                break;
            case ICON_LOOP:
                shp = shapeLoop();
                break;
            default:
                final Icon[] icons = new Icon[4];
                for (int i = 0; i < 4; i++) {
                    icons[i] = imgToolIcons.createIcon(id, i);
                }
                return icons;
        }
        final Icon[] icons = new Icon[4];
        final boolean isDark = isDarkSkin();
        icons[0] = new ShapeIcon(shp, isDark ? new Color( 200,  200,  200) : Color.black);
        icons[1] = new ShapeIcon(shp, isDark ? new Color(0x5E, 0x97, 0xFF) : new Color(0x3D, 0x3D, 0xB6));
        icons[2] = new ShapeIcon(shp, isDark ? new Color( 147,  175,  227) : new Color(26, 26, 77));
        icons[3] = new ShapeIcon(shp, isDark ? new Color( 200,  200,  200, 0x7F) : new Color(0, 0, 0, 0x7F));
        return icons;
    }

    private static class ShapeIcon implements Icon {
        private final Shape shape;
        private final Paint paint;
        private final int width;
        private final int height;

        ShapeIcon(Shape shape, Paint paint) {
            this(shape, paint, 14, 16);
        }

        ShapeIcon(Shape shape, Paint paint, int width, int height) {
            this.shape  = shape;
            this.paint  = paint;
            this.width  = width;
            this.height = height;
        }

        public void paintIcon(Component c, Graphics g, int x, int y) {
            final Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING  , RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE );
            g2.translate(x, y);
            g2.setPaint(paint);
            g2.fill(shape);
            g2.translate(-x, -y);
        }

        public int getIconWidth() {
            return width;
        }

        public int getIconHeight() {
            return height;
        }
    }

    private static Shape shapeStop() {
        return shapeStop(1f, 2f, 0.75f);
    }

    private static Shape shapeStop(float xOff, float yOff, float scale) {
        return new Rectangle2D.Float(xOff, yOff, scale * 16f, scale * 16f);
    }

    private static Shape shapePlay() {
        return shapePlay(2f, 0f, 0.75f);
    }

    private static Shape shapePlay(float xOff, float yOff, float scale) {
        final GeneralPath gp = new GeneralPath();
        gp.moveTo(xOff, yOff);
        gp.lineTo(xOff + scale * 15f, yOff + scale * 10f);
        gp.lineTo(xOff, yOff + scale * 20f);
        gp.closePath();
        return gp;
    }

    private static Shape shapeFFwd() {
        return shapeFFwd(0f, 3f, 0.75f);
    }

    private static Shape shapeFFwd(float xOff, float yOff, float scale) {
        final Shape play = shapePlay(xOff, yOff, scale * 0.7f);
        final Shape p2   = AffineTransform.getTranslateInstance(scale * 10.667f, 0).createTransformedShape(play);
        final Area res = new Area(play);
        res.add(new Area(p2));
        return res;
    }

    private static Shape shapeRwd() {
        return shapeRwd(-1f, 3f, 0.75f);
    }

    private static Shape shapeRwd(float xOff, float yOff, float scale) {
        final Shape s1 = shapeFFwd(0f, yOff, scale);
        final AffineTransform at = AffineTransform.getScaleInstance(-1.0, 1.0);
        at.translate(-s1.getBounds2D().getWidth() - xOff, 0);
        return at.createTransformedShape(s1);
    }

    private static Shape shapeLoop() {
        return shapeLoop(-1f, 4f, 0.75f, true);
    }

    private static Shape shapeLoop(float xOff, float yOff, float scale, boolean doRotate) {
        final Area res = new Area(new RoundRectangle2D.Float(0f, scale * 4f, scale * 22f, scale * 14f, scale * 10f, scale * 10f));
        res.subtract(new Area(new RoundRectangle2D.Float(0f + scale * 3f, scale * 7f, scale * 16f, scale * 8f, scale * 8f, scale * 8f)));

        final GeneralPath gp = new GeneralPath();
        gp.moveTo(0f, scale * 18f);
        gp.lineTo(scale * 11f, scale * 9f);
        gp.lineTo(scale * 11f, 0f);
        gp.lineTo(scale * 22f, 0f);
        gp.lineTo(scale * 22f, scale * 18f);

        gp.closePath();
        res.subtract(new Area(gp));
        // final Shape play = shapePlay(9f, 0.5f, scale * 0.5f);
        final Shape play = shapePlay(8f, 0.5f, scale * 0.5f);
        res.add(new Area(play));
        final Shape rot = AffineTransform.getRotateInstance(Math.PI, scale * 11f, scale * 12f).createTransformedShape(res);
        res.add(new Area(rot));
        final AffineTransform at = AffineTransform.getScaleInstance(1f, 0.8f);
        if (doRotate) {
            at.rotate(Math.PI * -0.2, scale * 11f, scale * 12f);
            at.preConcatenate(AffineTransform.getTranslateInstance(xOff, yOff - 3f));
        } else {
            at.translate(xOff, yOff);
        }
        return at.createTransformedShape(res);
    }

    /**
     *  Change the <code>Icon</code>s of an
     *  <code>AbstractButton</code> (<code>JButton</code>
     *  or <code>JToggleButton</code>).
     *
     *  @param  b		the button whose icons are to be set
     *  @param  icons   four <code>Icon</code> objects for
     *					different gadget states, such as
     *					created by the <code>createToolIcons</code> method.
     *
     *  @see	#createToolIcons( int )
     */
    public static void setToolIcons( AbstractButton b, Icon[] icons )
    {
        b.setIcon( icons[0] );
        b.setSelectedIcon( icons[1] );
        b.setPressedIcon( icons[3] );
        b.setDisabledIcon( icons[2] );
//        Insets defInsets = b.getInsets();
    }

    /**
     *  Calculates the length of line.
     *
     *  @param  ln  the <code>Line2D</code> shape whose
     *				length is to be calculated
     *  @return		the length as given by the distance
     *				of the start point to the end point*
     */
    public static double getLineLength(Line2D ln) {
        return (Point2D.distance(ln.getX1(), ln.getY1(), ln.getX2(), ln.getY2()));
    }

    /**
     *  Calculates the one point on a line which is
     *  nearest to a given point, such that a line
     *  between the given and the returned point will
     *  be orthogonal to the line.
     *
     *  @param  pt  point lying somewhere next to or on the line.
     *  @param  ln  line onto which the pt should be projected
     *  @return		the given point projected onto the line
     */
    public static Point2D projectPointOntoLine( Point2D pt, Line2D ln )
    {
        double  dx			= ln.getX2() - ln.getX1();
        double  dy			= ln.getY2() - ln.getY1();
        double  lineLenSq   = dx*dx + dy*dy;
        double  d1;

        if( lineLenSq == 0.0 ) {
            return( new Point2D.Double( ln.getX1(), ln.getY1() ));
        } else {
            d1 = ( (pt.getX() - ln.getX1()) * dx +
                   (pt.getY() - ln.getY1()) * dy ) / lineLenSq;
            return( new Point2D.Double( ln.getX1() + d1 * dx, ln.getY1() + d1 * dy ));
        }
    }
}