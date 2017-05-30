/*
 *  GraphicsUtil.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.eisenkraut.gui;

import de.sciss.eisenkraut.Main;
import de.sciss.gui.TiledImage;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 *  This is a helper class containing utility static functions
 *  and public objects for common graphics operations
 *
 *  TODO: the tool buttons on non-macos look-and-feel are far to wide
 */
public class GraphicsUtil {
    /**
     *  Tool icon ID: transport play
     */
    public static final int ICON_PLAY       = 0;
    /**
     *  Tool icon ID: transport stop
     */
    public static final int ICON_STOP       = 1;
    /**
     *  Tool icon ID: transport loop
     */
    public static final int ICON_LOOP       = 2;
    /**
     *  Tool icon ID: transport rewind
     */
    public static final int ICON_REWIND     = 3;
    /**
     *  Tool icon ID: transport fast forward
     */
    public static final int ICON_FASTFORWARD = 4;
    /**
     *  Tool icon ID: open file chooser
     */
    public static final int ICON_CHOOSEPATH = 5;
    /**
     *  Tool icon ID: pointer tool
     */
    public static final int ICON_POINTER    = 6;
    /**
     *  Tool icon ID: line tool
     */
    public static final int ICON_LINE       = 7;
    /**
     *  Tool icon ID: bezier tool
     */
    public static final int ICON_CURVE      = 8;
    /**
     *  Tool icon ID: freehand tool
     */
    public static final int ICON_PENCIL     = 9;
    /**
     *  Tool icon ID: arc tool
     */
    public static final int ICON_ARC        = 10;
    /**
     *  Tool icon ID: tuning fork tool
     */
    public static final int ICON_FORK       = 11;
    /**
     *  Tool icon ID: tuning magnifying glass
     */
    public static final int ICON_ZOOM       = 12;
    /**
     *  Tool icon ID: catch (follow) timeline postion
     */
    public static final int ICON_CATCH      = 13;
    /**
     *  Tool icon ID: realtime plug-in (supercollider)
     */
    public static final int ICON_REALTIME   = 15;
    /**
     *  Tool icon ID: solo button for session objects
     */
    public static final int ICON_SOLO       = 16;
    /**
     *  Tool icon ID: mute button for session objects
     */
    public static final int ICON_MUTE       = 18;
    /**
     *  Tool icon ID: trajectory blending
     */
    public static final int ICON_BLENDING   = 19;
    /**
     *  Tool icon ID: preroll blending (not used)
     */
    public static final int ICON_PREEXTRA   = 20;
    /**
     *  Tool icon ID: postroll blending (not used)
     */
    public static final int ICON_POSTEXTRA  = 21;
    /**
     *  Tool icon ID: insert mode
     */
    public static final int ICON_INSERTMODE = 22;
    /**
     *  Tool icon ID: replace mode
     */
    public static final int ICON_OVERWRITEMODE = 23;
    /**
     *  Tool icon ID: mix mode
     */
    public static final int ICON_MIXMODE    = 24;

    /**
     *  Blue translucent colour
     *  for consistent style in selected objects
     */
    public static Color colrSelection() {
        return isDarkSkin() ? colrSelectionDark : colrSelectionLight;
    }
    
    public static Color colrYellow() {
        return isDarkSkin() ? colrYellowDark : colrYellowLight;
    }

    public static Color colrRed() {
        return isDarkSkin() ? colrRedDark : colrRedLight;
    }

    public static Color colrBlue() {
        return isDarkSkin() ? colrBlueDark : colrBlueLight;
    }

    public static Color setAlpha(Color in, int alpha) {
        return new Color(in.getRed(), in.getGreen(), in.getBlue(), alpha);
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

    private static final Color colrSelectionDark    = new Color(95, 142, 255, 0x38); // new Color(0x7F, 0x7F, 0xFF, 0x3F);
    private static final Color colrSelectionLight   = new Color(0x00, 0x00, 0xFF, 0x2F);

    private static final Color colrSelectionDarkI   = new Color(0xE0, 0xE0, 0xE0, 0x30);
    private static final Color colrSelectionLightI  = new Color(0x00, 0x00, 0x00, 0x20);

    private static final Color colrYellowDark       = new Color(0xFF, 0xFF, 0x4F);
    private static final Color colrYellowLight      = new Color(0x80, 0x80, 0x00);

    private static final Color colrRedDark          = new Color(0xFF, 0x60, 0x60);
    private static final Color colrRedLight         = Color.red;

    private static final Color colrBlueDark         = new Color(0x5E, 0x97, 0xFF);
    private static final Color colrBlueLight        = new Color(0x3D, 0x3D, 0xB6);

    /**
     *  Yellow translucent colour
     *  for consistent style in optional adjustment objects
     */
    public static final Color colrAdjusting = new Color(0xFF, 0xFF, 0x00, 0x2F);
    /**
     *  Default font for GUI elements.
     *
     *  TODO: this is rather small and could be
     *  user adjustable in a future version.
     */
    public static final Font smallGUIFont = new Font("Helvetica", Font.PLAIN, 10);

    /**
     *  MacOS X Aqua style bar gradient with a size of 15 pixels
     *
     *  TODO:   this should look different on Windows and Linux
     *          depending on their VM's chrome.
     *
     *  @see    de.sciss.eisenkraut.timeline.TimelineAxis
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
     *  @see    #createToolIcons( int )
     */
    protected static final TiledImage imgToolIcons = new TiledImage(GraphicsUtil.class.getResource("toolicons.png"), 16, 16);

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
     *  <li>index 2 - gadget disabled</li>
     *  <li>index 3 - gadget pressed down</li>
     *  </ul>
     *  Usually you'll pass the result directly to
     *  the <code>setToolIcons</code> method.
     *
     *  @param  id  ID corresponding to the index in
     *              the tool icon tiled image, e.g.
     *              <code>ICON_PLAY</code> or <code>ICON_LINE</code>.
     *  @return     four <code>Icon</code> objects for
     *              different gadget states.
     *
     *  @see    #setToolIcons( AbstractButton, Icon[] )
     */
    public static Icon[] createToolIcons(int id) {
        final Shape shp;
        Color gradient  = null;
        Color fg        = null;
        final boolean isDark = isDarkSkin();
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
            case ICON_ZOOM:
                shp = shapeZoom();
                break;
            case ICON_POINTER:
                shp = shapePointer();
                break;
            case ICON_CATCH:
                shp = shapeLocation2();
                break;
            case ICON_INSERTMODE:
                shp         = shapeInsert();
                gradient    = colrYellow();
                fg          = gradient;
                break;
            case ICON_OVERWRITEMODE:
                shp         = shapeOverwrite();
                gradient    = colrRed();
                fg          = gradient;
                break;
            case ICON_MIXMODE:
                shp         = shapeMix();
                gradient    = colrBlue();
                fg          = gradient;
                break;
            case ICON_SOLO:
                shp         = shapeSolo();
                fg          = colrYellow();
                break;
            case ICON_MUTE:
                shp         = shapeMute();
                fg          = colrRed();
                break;
            default:
                final Icon[] icons = new Icon[4];
                for (int i = 0; i < 4; i++) {
                    icons[i] = imgToolIcons.createIcon(id, i);
                }
                return icons;
        }
        final Icon[] icons = new Icon[4];
        icons[0] = new ShapeIcon(shp, isDark ? new Color( 200,  200,  200) : new Color( 32,  32,  32));
        final ShapeIcon iconSel = new ShapeIcon(shp, fg == null ? (isDark ? new Color(0x5E, 0x97, 0xFF) : new Color(0x3D, 0x3D, 0xB6)) : fg);
        icons[1] = iconSel;
        icons[2] = new ShapeIcon(shp, isDark ? new Color( 180,  180,  180, 0x7F) : new Color(40, 40, 40, 0x7F));
        icons[3] = icons[2]; // new ShapeIcon(shp, isDark ? new Color( 147,  175,  227) : new Color(26, 26, 77));

        if (gradient != null) {
            // final Color transparent = new Color(gradient.getRGB() & 0x00FFFFFF, true);
            final Color transparent = isDark ? colrShadowDefaultDark : colrShadowDefaultLight;
            final Paint pnt = new GradientPaint(0f, 0f, gradient, 0f, /* 11f */ 15f, transparent);
            // iconSel.setShadow(Color.black);
            icons[1] = new PaintIcon(icons[1], pnt);
        }
        return icons;
    }

    private static class PaintIcon implements Icon {
        private final Icon peer;
        private final Paint paint;

        PaintIcon(Icon peer, Paint paint) {
            this.peer   = peer;
            this.paint  = paint;
        }

        public void paintIcon(Component c, Graphics g, int x, int y) {
            final Graphics2D g2 = (Graphics2D) g;
            g2.setPaint(paint);
            g2.fillRect(x, y, getIconWidth(), getIconHeight());
            peer.paintIcon(c, g, x, y);
        }

        public int getIconWidth() {
            return peer.getIconWidth();
        }

        public int getIconHeight() {
            return peer.getIconHeight();
        }
    }

    private static Color colrShadowDefaultLight = new Color(0xFF, 0xFF, 0xFF, 0x7F);
    private static Color colrShadowDefaultDark  = new Color(0x00, 0x00, 0x00, 0x7F);

    private static class ShapeIcon implements Icon {

        private final Shape shape;
        private final Paint paint;
        private final int width;
        private final int height;
        private Color colrShadow;

        ShapeIcon(Shape shape, Paint paint) {
            this(shape, paint, 14, 16);
        }

        ShapeIcon(Shape shape, Paint paint, int width, int height) {
            this.shape  = shape;
            this.paint  = paint;
            this.width  = width;
            this.height = height;
            colrShadow  = isDarkSkin() ? colrShadowDefaultDark : colrShadowDefaultLight;
        }

        public void setShadow(Color shadow) {
            this.colrShadow = shadow;
        }

        public void paintIcon(Component c, Graphics g, int x, int y) {
            final Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING  , RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE );
            g2.translate(x, y);
            g2.setPaint(colrShadow);
            g2.draw(shape);
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

    private static Shape shapeSolo() {
        return shapeSolo(-1f, 0f, 0.5f);
    }

    private static Shape shapeSolo(float xOff, float yOff, float scale) {
        final Path2D p = new Path2D.Float();
        p.moveTo(21.02539f, 19.7031f);
        p.curveTo(21.124151f, 21.7796f, 19.508724f, 23.559101f, 17.555538f, 24.0495f);
        p.curveTo(15.218763f, 24.7005f, 12.6468525f, 24.677f, 10.346678f, 23.8897f);
        p.curveTo(8.781085f, 23.3209f, 7.5374036f, 21.9438f, 7.2089853f, 20.3008f);
        p.curveTo(7.9316416f, 20.1563f, 8.654298f, 20.0117f, 9.376954f, 19.8672f);
        p.curveTo(9.711152f, 21.6459f, 11.499057f, 22.6103f, 13.176753f, 22.6973f);
        p.curveTo(14.951484f, 22.8091f, 17.180979f, 22.839f, 18.384457f, 21.2908f);
        p.curveTo(19.223303f, 20.1046f, 18.819178f, 18.2158f, 17.404297f, 17.6758f);
        p.curveTo(14.717688f, 16.4685f, 11.3840275f, 16.739601f, 9.075926f, 14.7029f);
        p.curveTo(7.2116766f, 12.8987f, 7.7841997f, 9.310801f, 10.216306f, 8.283899f);
        p.curveTo(12.451131f, 7.2899995f, 15.055282f, 7.320299f, 17.400627f, 7.882499f);
        p.curveTo(19.051216f, 8.268099f, 20.249271f, 9.692399f, 20.638672f, 11.300799f);
        p.curveTo(19.904297f, 11.429699f, 19.169922f, 11.558599f, 18.435547f, 11.687499f);
        p.curveTo(18.095972f, 9.969099f, 16.292114f, 9.184699f, 14.698726f, 9.201699f);
        p.curveTo(13.171225f, 9.168699f, 11.167638f, 9.2427f, 10.361328f, 10.785099f);
        p.curveTo(9.680741f, 12.151099f, 10.651849f, 13.671598f, 12.035823f, 14.030999f);
        p.curveTo(14.608384f, 14.982699f, 17.647264f, 14.917699f, 19.843254f, 16.736f);
        p.curveTo(20.666546f, 17.4812f, 21.046705f, 18.6106f, 21.02539f, 19.7031f);
        p.lineTo(21.02539f, 19.7031f);
        p.moveTo(10.1875f, 1.0128f);
        p.curveTo(5.8650985f, 0.9218f, 1.82291f, 4.3041f, 1.1432713f, 8.5721f);
        p.curveTo(0.8768325f, 10.716f, 1.0492703f, 12.8868f, 1.0f, 15.0436f);
        p.curveTo(1.025343f, 17.607f, 0.9455098f, 20.174f, 1.047356f, 22.7349f);
        p.curveTo(1.4026052f, 27.0404f, 5.160718f, 30.736599f, 9.479701f, 30.9859f);
        p.curveTo(12.413785f, 31.0379f, 15.351056f, 31.012901f, 18.285915f, 31.0009f);
        p.curveTo(22.611284f, 30.8634f, 26.461956f, 27.2619f, 26.926308f, 22.9662f);
        p.curveTo(27.078321f, 20.3577f, 26.964987f, 17.7381f, 27.000008f, 15.124701f);
        p.curveTo(26.958828f, 13.016801f, 27.098648f, 10.899301f, 26.894316f, 8.799601f);
        p.curveTo(26.322966f, 4.5182004f, 22.370419f, 1.0369005f, 18.049965f, 1.0159006f);
        p.curveTo(15.429189f, 1.0059006f, 12.808315f, 1.0159006f, 10.187508f, 1.0159006f);
        p.lineTo(10.1875f, 1.0128f);
        p.moveTo(10.1875f, 3.0128f);
        p.curveTo(12.975908f, 3.0348f, 15.767242f, 2.9678f, 18.553688f, 3.0498f);
        p.curveTo(21.993467f, 3.3112f, 24.903746f, 6.3801003f, 24.990755f, 9.8274f);
        p.curveTo(24.998755f, 14.0019f, 25.021074f, 18.1784f, 24.979284f, 22.3524f);
        p.curveTo(24.802372f, 25.8659f, 21.708311f, 28.9126f, 18.185738f, 29.0038f);
        p.curveTo(15.273218f, 29.0029f, 12.357073f, 29.0528f, 9.446313f, 28.9758f);
        p.curveTo(5.931402f, 28.7044f, 3.001677f, 25.4988f, 3.0023239f, 21.9813f);
        p.curveTo(3.010334f, 17.8685f, 2.9744568f, 13.7544f, 3.020722f, 9.6424f);
        p.curveTo(3.1950474f, 6.1982f, 6.1853743f, 3.2078f, 9.629604f, 3.0335f);
        p.curveTo(9.815227f, 3.0195f, 10.001367f, 3.0125f, 10.187504f, 3.0125f);
        p.lineTo(10.1875f, 3.0128f);
        return scaleAndShift(p, scale, xOff, yOff);
    }

    private static Shape shapeMute() {
        return shapeMute(-1f, 0f, 0.5f);
    }

    private static Shape shapeMute(float xOff, float yOff, float scale) {
        final Path2D p = new Path2D.Float();
        p.moveTo(20.134766f, 24.2617f);
        p.curveTo(20.162645f, 19.6085f, 20.067446f, 14.953099f, 20.208006f, 10.3018f);
        p.curveTo(20.329603f, 8.9957f, 19.856266f, 11.2871f, 19.652098f, 11.709499f);
        p.curveTo(18.243673f, 15.7111f, 16.66415f, 19.652f, 15.183624f, 23.6281f);
        p.curveTo(15.127804f, 24.2578f, 14.702459f, 24.352299f, 14.149389f, 24.2617f);
        p.curveTo(13.575208f, 24.3741f, 13.190391f, 24.2107f, 13.116675f, 23.5947f);
        p.curveTo(11.662157f, 19.7638f, 10.143147f, 15.956f, 8.732429f, 12.1094f);
        p.curveTo(8.501633f, 11.52f, 8.133532f, 9.9785f, 8.008979f, 9.8944f);
        p.curveTo(8.154377f, 14.681799f, 8.061826f, 19.472599f, 8.087898f, 24.2617f);
        p.curveTo(7.4238358f, 24.2617f, 6.7597733f, 24.2617f, 6.0957108f, 24.2617f);
        p.curveTo(6.0957108f, 18.7578f, 6.0957108f, 13.2539f, 6.0957108f, 7.75f);
        p.curveTo(7.0761795f, 7.75f, 8.056648f, 7.75f, 9.037117f, 7.75f);
        p.curveTo(10.559278f, 11.7728f, 12.15921f, 15.768f, 13.630867f, 19.808601f);
        p.curveTo(13.949362f, 20.522102f, 14.127401f, 22.701202f, 14.3984375f, 20.9248f);
        p.curveTo(15.465224f, 17.657701f, 16.781324f, 14.476801f, 17.961388f, 11.2489f);
        p.curveTo(18.400692f, 10.082601f, 18.839998f, 8.9163f, 19.279305f, 7.7500005f);
        p.curveTo(20.236336f, 7.7500005f, 21.193367f, 7.7500005f, 22.150398f, 7.7500005f);
        p.curveTo(22.150398f, 13.253901f, 22.150398f, 18.757801f, 22.150398f, 24.2617f);
        p.curveTo(21.478523f, 24.2617f, 20.806648f, 24.2617f, 20.134766f, 24.2617f);
        p.lineTo(20.134766f, 24.2617f);
        p.moveTo(10.1875f, 1.0128f);
        p.curveTo(5.8650985f, 0.9218f, 1.82291f, 4.3041f, 1.1432713f, 8.5721f);
        p.curveTo(0.8768325f, 10.716f, 1.0492703f, 12.8868f, 1.0f, 15.0436f);
        p.curveTo(1.025343f, 17.607f, 0.9455098f, 20.174f, 1.047356f, 22.7349f);
        p.curveTo(1.4026052f, 27.0404f, 5.160718f, 30.736599f, 9.479701f, 30.9859f);
        p.curveTo(12.413785f, 31.0379f, 15.351056f, 31.012901f, 18.285915f, 31.0009f);
        p.curveTo(22.611284f, 30.8634f, 26.461956f, 27.2619f, 26.926308f, 22.9662f);
        p.curveTo(27.078321f, 20.3577f, 26.964987f, 17.7381f, 27.000008f, 15.124701f);
        p.curveTo(26.958828f, 13.016801f, 27.098648f, 10.899301f, 26.894316f, 8.799601f);
        p.curveTo(26.322966f, 4.5182004f, 22.370419f, 1.0369005f, 18.049965f, 1.0159006f);
        p.curveTo(15.429189f, 1.0059006f, 12.808315f, 1.0159006f, 10.187508f, 1.0159006f);
        p.lineTo(10.1875f, 1.0128f);
        p.moveTo(10.1875f, 3.0128f);
        p.curveTo(12.975908f, 3.0348f, 15.767242f, 2.9678f, 18.553688f, 3.0498f);
        p.curveTo(21.993467f, 3.3112f, 24.903746f, 6.3801003f, 24.990755f, 9.8274f);
        p.curveTo(24.998755f, 14.0019f, 25.021074f, 18.1784f, 24.979284f, 22.3524f);
        p.curveTo(24.802372f, 25.8659f, 21.708311f, 28.9126f, 18.185738f, 29.0038f);
        p.curveTo(15.273218f, 29.0029f, 12.357073f, 29.0528f, 9.446313f, 28.9758f);
        p.curveTo(5.931402f, 28.7044f, 3.001677f, 25.4988f, 3.0023239f, 21.9813f);
        p.curveTo(3.010334f, 17.8685f, 2.9744568f, 13.7544f, 3.020722f, 9.6424f);
        p.curveTo(3.1950474f, 6.1982f, 6.1853743f, 3.2078f, 9.629604f, 3.0335f);
        p.curveTo(9.815227f, 3.0195f, 10.001367f, 3.0125f, 10.187504f, 3.0125f);
        p.lineTo(10.1875f, 3.0128f);
        return scaleAndShift(p, scale, xOff, yOff);
    }

    private static Shape shapeInsert() {
        return shapeInsert(-1f, 0f, 0.5f);
    }

    private static Shape shapeInsert(float xOff, float yOff, float scale) {
        final Path2D p = new Path2D.Float();
        p.moveTo(30.601023f, 17.183632f);
        p.curveTo(30.2153f, 15.91982f, 29.276339f, 14.946398f, 28.019863f, 14.425948f);
        p.curveTo(25.931894f, 13.561084f, 23.942749f, 14.339098f, 23.284533f, 15.928166f);
        p.curveTo(23.009f, 16.593359f, 22.907965f, 17.568958f, 23.577961f, 18.669096f);
        p.curveTo(24.320553f, 19.907545f, 25.82281f, 20.984406f, 26.461647f, 21.898455f);
        p.curveTo(27.027885f, 22.674194f, 26.978569f, 23.368145f, 26.764267f, 23.885519f);
        p.curveTo(26.35097f, 24.883308f, 25.049433f, 25.621418f, 23.38645f, 24.932589f);
        p.curveTo(22.129974f, 24.412138f, 21.410885f, 23.378256f, 21.161392f, 22.517239f);
        p.curveTo(21.136572f, 22.420368f, 21.151892f, 22.383413f, 21.182491f, 22.309502f);
        p.curveTo(21.197802f, 22.272552f, 21.243721f, 22.161682f, 21.185091f, 22.094112f);
        p.lineTo(19.857967f, 23.103045f);
        p.curveTo(20.357725f, 24.457386f, 21.444506f, 25.492039f, 22.867281f, 26.081371f);
        p.curveTo(25.675875f, 27.24473f, 27.491295f, 25.78862f, 28.08828f, 24.347368f);
        p.curveTo(28.34085f, 23.737608f, 28.485182f, 22.866535f, 28.047739f, 21.88437f);
        p.curveTo(27.440287f, 20.528715f, 25.952024f, 19.522596f, 25.151371f, 18.476576f);
        p.curveTo(24.4278f, 17.50578f, 24.507732f, 16.737919f, 24.699074f, 16.275978f);
        p.curveTo(25.06645f, 15.389054f, 26.15314f, 14.908318f, 27.465048f, 15.451728f);
        p.curveTo(28.536749f, 15.895641f, 29.169245f, 16.720474f, 29.34483f, 17.550877f);
        p.curveTo(29.36965f, 17.647747f, 29.335829f, 17.67705f, 29.30525f, 17.750961f);
        p.curveTo(29.25933f, 17.861826f, 29.29945f, 17.921741f, 29.33641f, 17.93705f);
        p.lineTo(30.60099f, 17.183632f);
        p.moveTo(10.07259f, 20.543509f);
        p.lineTo(11.310588f, 21.056305f);
        p.lineTo(15.060886f, 12.002285f);
        p.lineTo(16.365795f, 23.171888f);
        p.lineTo(17.40054f, 23.600492f);
        p.lineTo(21.977432f, 12.550894f);
        p.curveTo(22.031012f, 12.421551f, 22.064793f, 12.392249f, 22.158495f, 12.322822f);
        p.curveTo(22.192286f, 12.293522f, 22.267504f, 12.216439f, 22.287294f, 12.116398f);
        p.lineTo(20.919985f, 11.550026f);
        p.lineTo(17.38399f, 20.086674f);
        p.lineTo(16.097334f, 9.552419f);
        p.lineTo(14.840857f, 9.031969f);
        p.lineTo(10.072622f, 20.543509f);
        p.moveTo(6.30421f, 5.495974f);
        p.lineTo(5.852644f, 6.586152f);
        p.lineTo(7.9775667f, 7.466324f);
        p.lineTo(4.097157f, 16.834461f);
        p.lineTo(1.8428907f, 15.900715f);
        p.lineTo(1.3989779f, 16.972416f);
        p.lineTo(7.348762f, 19.436895f);
        p.lineTo(7.7850213f, 18.383675f);
        p.lineTo(5.3829346f, 17.388697f);
        p.lineTo(9.270998f, 8.002081f);
        p.lineTo(11.543742f, 8.943482f);
        p.lineTo(11.995308f, 7.853304f);
        p.lineTo(6.30421f, 5.495974f);
        return scaleAndShift(p, scale, xOff, yOff);
    }

    private static Shape shapeOverwrite() {
        return shapeOverwrite(-1f, 0f, 0.5f);
    }

    private static Shape shapeOverwrite(float xOff, float yOff, float scale) {
        final Path2D p = new Path2D.Float();
        p.moveTo(23.98087f, 11.956031f);
        p.lineTo(19.212635f, 23.467567f);
        p.lineTo(20.506065f, 24.003325f);
        p.lineTo(22.610825f, 18.921988f);
        p.lineTo(24.846613f, 19.848082f);
        p.lineTo(25.291761f, 25.985624f);
        p.lineTo(26.751492f, 26.590263f);
        p.lineTo(26.210785f, 20.369843f);
        p.curveTo(27.757332f, 20.555838f, 29.261578f, 19.64192f, 29.896832f, 18.10828f);
        p.curveTo(30.371359f, 16.96267f, 30.25012f, 15.635228f, 29.400377f, 14.655465f);
        p.curveTo(28.832825f, 13.98742f, 28.119852f, 13.670449f, 27.36227f, 13.356649f);
        p.lineTo(23.98087f, 11.956028f);
        p.moveTo(24.76916f, 13.711305f);
        p.lineTo(26.857126f, 14.57617f);
        p.curveTo(27.337543f, 14.775165f, 27.79183f, 14.984985f, 28.180403f, 15.405711f);
        p.curveTo(28.747955f, 16.073755f, 28.811361f, 16.965933f, 28.505215f, 17.705036f);
        p.curveTo(28.199068f, 18.44414f, 27.564808f, 18.98239f, 26.761848f, 19.039455f);
        p.curveTo(26.226543f, 19.077496f, 25.775427f, 18.912287f, 25.313486f, 18.720947f);
        p.lineTo(23.077698f, 17.794853f);
        p.lineTo(24.76916f, 13.711306f);
        p.moveTo(14.169269f, 7.8919325f);
        p.lineTo(13.280014f, 21.118437f);
        p.lineTo(13.871297f, 21.363356f);
        p.lineTo(22.539618f, 11.359044f);
        p.lineTo(21.264664f, 10.830941f);
        p.lineTo(14.80844f, 18.526003f);
        p.lineTo(15.518133f, 8.45065f);
        p.lineTo(14.169269f, 7.8919325f);
        p.moveTo(10.775881f, 13.26212f);
        p.curveTo(11.219794f, 12.19042f, 11.735216f, 10.737031f, 11.619773f, 9.238808f);
        p.curveTo(11.474486f, 7.4468f, 10.445762f, 6.2197185f, 9.189286f, 5.6992693f);
        p.curveTo(7.4708705f, 4.987478f, 4.499242f, 5.3152337f, 2.624093f, 9.842244f);
        p.curveTo(0.74894804f, 14.369244f, 2.543221f, 16.77935f, 4.3170695f, 17.514103f);
        p.curveTo(5.555068f, 18.0269f, 7.1762943f, 17.875814f, 8.56913f, 16.655975f);
        p.curveTo(9.691687f, 15.670548f, 10.354929f, 14.278388f, 10.775881f, 13.26212f);
        p.moveTo(8.636365f, 6.8773518f);
        p.curveTo(9.449379f, 7.214113f, 10.248174f, 8.107828f, 10.225306f, 9.678649f);
        p.curveTo(10.215305f, 10.800211f, 9.811557f, 11.931828f, 9.413566f, 12.892662f);
        p.curveTo(9.099766f, 13.650243f, 8.572976f, 14.817501f, 7.6869144f, 15.597818f);
        p.curveTo(6.6841903f, 16.502995f, 5.613578f, 16.57908f, 4.837519f, 16.257627f);
        p.curveTo(3.5440876f, 15.72187f, 2.4525034f, 13.862611f, 3.9679298f, 10.204048f);
        p.curveTo(5.391512f, 6.7672167f, 7.435322f, 6.379863f, 8.636365f, 6.8773518f);
        return scaleAndShift(p, scale, xOff, yOff);
    }

    private static Shape shapeMix() {
        return shapeMix(-1f, 0f, 0.5f);
    }

    private static Shape shapeMix(float xOff, float yOff, float scale) {
        final Path2D p = new Path2D.Float();
        p.moveTo(30.102737f, 15.023929f);
        p.lineTo(25.93134f, 18.40497f);
        p.lineTo(25.31704f, 13.041628f);
        p.lineTo(23.931221f, 12.467603f);
        p.lineTo(24.656479f, 19.392254f);
        p.lineTo(19.089077f, 23.948526f);
        p.lineTo(20.51185f, 24.537859f);
        p.lineTo(24.852173f, 21.01031f);
        p.lineTo(25.537756f, 26.619656f);
        p.lineTo(26.979008f, 27.216642f);
        p.lineTo(26.168472f, 19.975246f);
        p.lineTo(31.39617f, 15.559685f);
        p.lineTo(30.10274f, 15.023928f);
        p.moveTo(15.50544f, 8.977531f);
        p.lineTo(15.053874f, 10.067709f);
        p.lineTo(17.178797f, 10.947881f);
        p.lineTo(13.298387f, 20.31602f);
        p.lineTo(11.044121f, 19.382273f);
        p.lineTo(10.600207f, 20.453972f);
        p.lineTo(16.549992f, 22.918453f);
        p.lineTo(16.98625f, 21.86523f);
        p.lineTo(14.584164f, 20.870255f);
        p.lineTo(18.472227f, 11.483639f);
        p.lineTo(20.74497f, 12.425039f);
        p.lineTo(21.196537f, 11.334862f);
        p.lineTo(15.505439f, 8.977531f);
        p.moveTo(5.3797207f, 4.7833204f);
        p.lineTo(0.6038316f, 16.313337f);
        p.lineTo(1.8233526f, 16.81848f);
        p.lineTo(5.397616f, 8.189445f);
        p.lineTo(5.835336f, 13.717772f);
        p.lineTo(6.3157535f, 13.916767f);
        p.lineTo(10.731299f, 10.312137f);
        p.lineTo(7.126421f, 19.015081f);
        p.lineTo(8.3459425f, 19.520224f);
        p.lineTo(13.121831f, 7.9902077f);
        p.lineTo(12.160997f, 7.592217f);
        p.lineTo(6.925646f, 12.026256f);
        p.lineTo(6.3959885f, 5.2042723f);
        p.lineTo(5.3797207f, 4.7833204f);
        return scaleAndShift(p, scale, xOff, yOff);
    }

    private static Shape shapeLocation2() {
        return shapeLocation2(-1f, -1f, 0.5f);
    }

    private static Shape shapeLocation2(float xOff, float yOff, float scale) {
        final Path2D gp = new Path2D.Float();
        gp.moveTo(15.833999633789062D, 29.083999633789062D);
        gp.lineTo(15.833999633789062D, 16.166000366210938D);
        gp.lineTo(2.9170000553131104D, 16.166000366210938D);
        gp.lineTo(29.08300018310547D, 2.9170000553131104D);
//        gp.moveTo(16.0D, 3.5D);
//        gp.curveTo(11.857999801635742D, 3.5D, 8.5D, 6.857999801635742D, 8.5D, 11.0D);
//        gp.curveTo(8.5D, 15.142999649047852D, 16.0D, 29.121000289916992D, 16.0D, 29.121000289916992D);
//        gp.curveTo(16.0D, 29.121000289916992D, 23.5D, 15.142999649047852D, 23.5D, 11.0D);
//        gp.curveTo(23.5D, 6.857999801635742D, 20.14299964904785D, 3.5D, 16.0D, 3.5D);
//        gp.moveTo(16.0D, 14.583999633789062D);
//        gp.curveTo(14.020999908447266D, 14.583999633789062D, 12.416000366210938D, 12.979999542236328D, 12.416000366210938D, 11.0D);
//        gp.curveTo(12.416000366210938D, 9.020000457763672D, 14.020999908447266D, 7.415999889373779D, 16.0D, 7.415999889373779D);
//        gp.curveTo(17.979000091552734D, 7.415999889373779D, 19.583999633789062D, 9.020999908447266D, 19.583999633789062D, 11.0D);
//        gp.curveTo(19.583999633789062D, 12.979000091552734D, 17.979000091552734D, 14.583999633789062D, 16.0D, 14.583999633789062D);
        return scaleAndShift(gp, scale, xOff, yOff);
    }

    private static Shape shapePointer() {
        return shapePointer(-1f, -1f, 0.5f);
    }

    private static Shape shapePointer(float xOff, float yOff, float scale) {
        final Path2D p = new Path2D.Float();
        p.moveTo(15.0f, 24.9999f);
        p.lineTo(21.0f, 24.9999f);
        p.lineTo(21.0f, 27.9999f);
        p.lineTo(15.0f, 27.9999f);
        p.lineTo(15.0f, 24.9999f);
        p.moveTo(7.0f, 24.9999f);
        p.lineTo(13.0f, 24.9999f);
        p.lineTo(13.0f, 27.9999f);
        p.lineTo(7.0f, 27.9999f);
        p.lineTo(7.0f, 24.9999f);
        p.moveTo(15.0f, 2.9999008f);
        p.lineTo(21.0f, 2.9999008f);
        p.lineTo(21.0f, 5.999901f);
        p.lineTo(15.0f, 5.999901f);
        p.lineTo(15.0f, 2.9999008f);
        p.moveTo(15.347826f, 6.0f);
        p.lineTo(15.347826f, 25.0f);
        p.lineTo(12.347826f, 25.0f);
        p.lineTo(12.347826f, 6.0f);
        p.lineTo(15.347826f, 6.0f);
        p.moveTo(7.0f, 2.9999f);
        p.lineTo(13.0f, 2.9999f);
        p.lineTo(13.0f, 5.9999f);
        p.lineTo(7.0f, 5.9999f);
        p.lineTo(7.0f, 2.9999f);
        return scaleAndShift(p, scale, xOff, yOff);
    }

    private static Shape shapeZoom() {
        return shapeZoom(-1f, -1f, 0.5f);
    }

    private static Shape shapeZoom(float xOff, float yOff, float scale) {
        final Path2D p = new Path2D.Float();
        p.moveTo(22.645999908447266D, 19.30699920654297D);
        p.curveTo(23.605998992919922D, 17.7239990234375D, 24.16900062561035D, 15.871999740600586D, 24.170000076293945D, 13.88599967956543D);
        p.curveTo(24.16900062561035D, 8.093000411987305D, 19.47800064086914D, 3.4010000228881836D, 13.687999725341797D, 3.3989999294281006D);
        p.curveTo(7.896999835968018D, 3.4010000228881836D, 3.2039999961853027D, 8.093000411987305D, 3.2039999961853027D, 13.885000228881836D);
        p.curveTo(3.2039999961853027D, 19.673999786376953D, 7.896999835968018D, 24.36600112915039D, 13.687999725341797D, 24.36600112915039D);
        p.curveTo(15.674999237060547D, 24.36600112915039D, 17.527000427246094D, 23.803001403808594D, 19.110000610351562D, 22.843000411987305D);
        p.lineTo(26.238000869750977D, 29.970001220703125D);
        p.lineTo(29.773000717163086D, 26.433000564575195D);
        p.lineTo(22.645999908447266D, 19.30699920654297D);
        p.moveTo(13.687999725341797D, 20.368999481201172D);
        p.curveTo(10.105999946594238D, 20.361000061035156D, 7.2099995613098145D, 17.46500015258789D, 7.2039995193481445D, 13.88499927520752D);
        p.curveTo(7.2099995613098145D, 10.302999496459961D, 10.106999397277832D, 7.406999111175537D, 13.687999725341797D, 7.398999214172363D);
        p.curveTo(17.267000198364258D, 7.406999111175537D, 20.166000366210938D, 10.302999496459961D, 20.172000885009766D, 13.88499927520752D);
        p.curveTo(20.165000915527344D, 17.46500015258789D, 17.267000198364258D, 20.361000061035156D, 13.687999725341797D, 20.368999481201172D);
        p.moveTo(15.687000274658203D, 9.050999641418457D);
        p.lineTo(11.687000274658203D, 9.050999641418457D);
        p.lineTo(11.687000274658203D, 11.883999824523926D);
        p.lineTo(8.854000091552734D, 11.883999824523926D);
        p.lineTo(8.854000091552734D, 15.885000228881836D);
        p.lineTo(11.687000274658203D, 15.885000228881836D);
        p.lineTo(11.687000274658203D, 18.718000411987305D);
        p.lineTo(15.687000274658203D, 18.718000411987305D);
        p.lineTo(15.687000274658203D, 15.884000778198242D);
        p.lineTo(18.519001007080078D, 15.884000778198242D);
        p.lineTo(18.519001007080078D, 11.885000228881836D);
        p.lineTo(15.68600082397461D, 11.885000228881836D);
        p.lineTo(15.68600082397461D, 9.050999641418457D);
        return scaleAndShift(p, scale, xOff, yOff);
    }

    private static Shape scaleAndShift(Shape in, float scale, float xOff, float yOff) {
        if (scale == 1f && xOff == 0f && yOff == 0f) {
            return in;
        } else {
            final AffineTransform at = AffineTransform.getScaleInstance(scale, scale);
            at.translate(xOff, yOff);
            return at.createTransformedShape(in);
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
        final Path2D p = new Path2D.Float();
        p.moveTo(xOff, yOff);
        p.lineTo(xOff + scale * 15f, yOff + scale * 10f);
        p.lineTo(xOff, yOff + scale * 20f);
        p.closePath();
        return p;
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

        final Path2D gp = new Path2D.Float();
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
     *  @param  b       the button whose icons are to be set
     *  @param  icons   four <code>Icon</code> objects for
     *                  different gadget states, such as
     *                  created by the <code>createToolIcons</code> method.
     *
     *  @see    #createToolIcons( int )
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
     *              length is to be calculated
     *  @return     the length as given by the distance
     *              of the start point to the end point*
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
     *  @return     the given point projected onto the line
     */
    public static Point2D projectPointOntoLine(Point2D pt, Line2D ln) {

        double dx = ln.getX2() - ln.getX1();
        double dy = ln.getY2() - ln.getY1();
        double lineLenSq = dx * dx + dy * dy;
        double d1;

        if (lineLenSq == 0.0) {
            return (new Point2D.Double(ln.getX1(), ln.getY1()));
        } else {
            d1 = ((pt.getX() - ln.getX1()) * dx +
                    (pt.getY() - ln.getY1()) * dy) / lineLenSq;
            return (new Point2D.Double(ln.getX1() + d1 * dx, ln.getY1() + d1 * dy));
        }
    }
}