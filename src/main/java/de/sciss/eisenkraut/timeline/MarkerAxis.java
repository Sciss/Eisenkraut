/*
 *  MarkerAxis.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de\
 */

package de.sciss.eisenkraut.timeline;

import de.sciss.app.AbstractApplication;
import de.sciss.app.AbstractCompoundEdit;
import de.sciss.app.AncestorAdapter;
import de.sciss.app.DynamicListening;
import de.sciss.app.GraphicsHandler;
import de.sciss.common.BasicWindowHandler;
import de.sciss.eisenkraut.edit.BasicCompoundEdit;
import de.sciss.eisenkraut.gui.GraphicsUtil;
import de.sciss.eisenkraut.session.Session;
import de.sciss.gui.ComponentHost;
import de.sciss.gui.DoClickAction;
import de.sciss.gui.MenuAction;
import de.sciss.gui.ParamField;
import de.sciss.gui.SpringPanel;
import de.sciss.io.Span;
import de.sciss.timebased.MarkerStake;
import de.sciss.timebased.Stake;
import de.sciss.timebased.Trail;
import de.sciss.util.DefaultUnitTranslator;
import de.sciss.util.Disposable;
import de.sciss.util.Param;
import de.sciss.util.ParamSpace;

import javax.swing.*;
import javax.swing.event.AncestorEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

/**
 *	TODO:		uses TimelineListener to
 *				not miss document changes. should use 
 *				a document change listener!
 *
 *	TODO:		marker sortierung sollte zentral von session o.ae. vorgenommen
 *				werden sobald neues file geladen wird!
 *
 *	TODO:		had to add 2 pixels to label y coordinate in java 1.5 ; have to check look back in 1.4
 *
 *	TODO:		repaintMarkers : have to provide dirtySpan that accounts for flag width, esp. for dnd!
 *
 *	TODO:		actionEditPrev/NextClass shortcuts funktionieren nicht
 */
@SuppressWarnings("serial")
public class MarkerAxis
        extends JComponent
        implements TimelineListener, MouseListener, MouseMotionListener, KeyListener,
        DynamicListening, Trail.Listener, Disposable {

    protected final Session		doc;

    private final Font			fntLabel; //		= new Font( "Helvetica", Font.ITALIC, 10 );

    private String[]			markLabels		= new String[0];
    private int[]				markFlagPos		= new int[0];
    private int					numMarkers		= 0;
//	private int					numRegions		= 0;	// XXX not yet used
    private final GeneralPath   shpFlags		= new GeneralPath();
    private int					recentWidth		= -1;
    private boolean				doRecalc		= true;
    private Span				visibleSpan		= new Span();
    private double				scale			= 1.0;

//	private static final int[] pntBarGradientPixels = { 0xFFB8B8B8, 0xFFC0C0C0, 0xFFC8C8C8, 0xFFD3D3D3,
//														0xFFDBDBDB, 0xFFE4E4E4, 0xFFEBEBEB, 0xFFF1F1F1,
//														0xFFF6F6F6, 0xFFFAFAFA, 0xFFFBFBFB, 0xFFFCFCFC,
//														0xFFF9F9F9, 0xFFF4F4F4, 0xFFEFEFEF };
    private static final int barExtent = 15; // pntBarGradientPixels.length;

    private static final int[] pntMarkGradPix= {
            0xFF5B8581, 0xFF618A86, 0xFF5D8682, 0xFF59827E,
            0xFF537D79, 0xFF4F7975, 0xFF4B7470, 0xFF47716D,
            0xFF446E6A, 0xFF426B67, 0xFF406965, 0xFF3F6965,
            0xFF3F6864};    // , 0xFF5B8581

    private static final int[] pntMarkDragPix; // Light, pntMarkDragPixDark;

    private static final Color	colrLabelLight      = Color.white;
    private static final Color	colrLabelDragLight  = new Color( 0xFF, 0xFF, 0xFF, 0x7F );

    private static final Color	colrLabelDark       = Color.black;
    private static final Color	colrLabelDragDark   = new Color( 0x00, 0x00, 0x00, 0x7F );

    private final Color	colrLabel, colrLabelDrag;

    //	private static final Paint	pntMarkStick= new Color( 0x31, 0x50, 0x4D, 0xC0 );
    private static final Paint	pntMarkStick= new Color( 0x31, 0x50, 0x4D, 0x7F );
    private static final Paint	pntMarkStickDrag = new Color( 0x31, 0x50, 0x4D, 0x5F );
    private static final Stroke	strkStick	= new BasicStroke( 1.0f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_BEVEL,
        1.0f, new float[] { 4.0f, 4.0f }, 0.0f );

    private static final int	markExtent = pntMarkGradPix.length;
    private final Paint			pntBackground;
    private final Paint			pntMarkFlag, pntMarkFlagDrag;
    private final BufferedImage img2, img3;

    private final ComponentHost	host;
    private boolean				isListening	= false;

    // ----- Edit-Marker Dialog -----
    private JPanel					editMarkerPane	= null;
    private Object[]				editOptions		= null;
    private ParamField				ggMarkPos;
    protected JTextField			ggMarkName;
    private JButton					ggEditPrev, ggEditNext;
    protected int					editIdx			= -1;
    private DefaultUnitTranslator	timeTrans;

    // ---- dnd ----
    private MarkerStake			dragMark		= null;
    private MarkerStake			dragLastMark	= null;
    private boolean				dragStarted		= false;
    private int					dragStartX		= 0;

    private boolean				adjustCatchBypass	= false;

    static {
        pntMarkDragPix  = new int[markExtent];
//        pntMarkDragPixDark  = new int[markExtent];
        for (int i = 0; i < markExtent; i++) {
            pntMarkDragPix[i] = pntMarkGradPix[i] & 0xBFFFFFFF;    // = 50% alpha
//            pntMarkDragPixDark [i] = pntMarkGradPixDark [i] & 0xBFFFFFFF;    // = 50% alpha
        }
    }

    public MarkerAxis(Session doc) {
        this(doc, null);
    }

    /**
     * Constructs a new object for
     * displaying the timeline ruler
     *
     * @param doc session Session
     */
    public MarkerAxis(Session doc, ComponentHost host) {
        super();

        this.doc    = doc;
        this.host	= host;

        final boolean isDark = GraphicsUtil.isDarkSkin();
        colrLabel       = isDark ? colrLabelDark     : colrLabelLight;
        colrLabelDrag   = isDark ? colrLabelDragDark : colrLabelDragLight;

        fntLabel = AbstractApplication.getApplication().getGraphicsHandler().getFont(GraphicsHandler.FONT_LABEL | GraphicsHandler.FONT_MINI).deriveFont(Font.ITALIC);

        setMaximumSize(new Dimension(getMaximumSize().width, barExtent));
        setMinimumSize(new Dimension(getMinimumSize().width, barExtent));
        setPreferredSize(new Dimension(getPreferredSize().width, barExtent));

        pntBackground = GraphicsUtil.pntBarGradient(); // new TexturePaint( img1, new Rectangle( 0, 0, 1, barExtent ));
        img2 = new BufferedImage(1, markExtent, BufferedImage.TYPE_INT_ARGB);
        img2.setRGB(0, 0, 1, markExtent, pntMarkGradPix, 0, 1);
        pntMarkFlag = new TexturePaint(img2, new Rectangle(0, 1, 1, markExtent));
        img3 = new BufferedImage(1, markExtent, BufferedImage.TYPE_INT_ARGB);
        img3.setRGB(0, 0, 1, markExtent, pntMarkDragPix, 0, 1);
        pntMarkFlagDrag = new TexturePaint(img3, new Rectangle(0, 1, 1, markExtent));

        setOpaque(true);

        // not necessary; it also kills the VK_TAB response of DocumentFrame!
        //		setFocusable( true );

        // --- Listener ---

        this.addMouseListener(this);
        this.addMouseMotionListener(this);
        this.addKeyListener(this);
    }

    private String getResourceString(String key) {
        return (AbstractApplication.getApplication().getResourceString(key));
    }

    // sync: attempts shared on timeline
    private void recalculateDisplay(FontMetrics fm) {
        List<Stake> markers;
        long			start, stop;
        MarkerStake		mark;

//long t1 = System.currentTimeMillis();

        shpFlags.reset();
        numMarkers	= 0;
//		numRegions	= 0;

//		if( !doc.bird.attemptShared( Session.DOOR_TIME, 250 )) return;
//		try {
            visibleSpan = doc.timeline.getVisibleSpan();	// so we don't have to do that after startListening
            start		= visibleSpan.start;
            stop		= visibleSpan.stop;
            scale		= (double) recentWidth / (stop - start);

            markers		= doc.markers.getRange( visibleSpan, true );	// XXX plus a bit before
//long t3 = System.currentTimeMillis();
            numMarkers	= markers.size();
//System.err.println( "numMarkers = "+numMarkers );
            if( (numMarkers > markLabels.length) || (numMarkers < (markLabels.length >> 1)) ) {
                markLabels		= new String[ numMarkers * 3 / 2 ];		// 'decent growing and shrinking'
                markFlagPos		= new int[ markLabels.length ];
            }

            for( int i = 0; i < numMarkers; i++ ) {
                mark				= (MarkerStake) markers.get( i );
                markLabels[ i ]		= mark.name;
                markFlagPos[ i ]	= (int) (((mark.pos - start) * scale) + 0.5);
                shpFlags.append( new Rectangle( markFlagPos[ i ], 1, fm.stringWidth( mark.name ) + 8, markExtent ), false );
            }

//			coll	= (java.util.List) afd.getProperty( AudioFileDescr.KEY_REGIONS );
//			if( coll != regions ) {
//				regions		= coll;
//				regionIdx	= 0;
//			}
//			if( (regions != null) && !regions.isEmpty() ) {
//			
//			}
            doRecalc	= false;
//		}
//		finally {
//			doc.bird.releaseShared( Session.DOOR_TIME );
//		}
//long t2 = System.currentTimeMillis();
//System.out.println( "recalculateDisplay " + (t3-t1) + " / " + (t2-t3) );
    }

    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        final Graphics2D	g2	= (Graphics2D) g;

        g2.setFont(fntLabel);

        final FontMetrics	fm	= g2.getFontMetrics();
        final int			y	= fm.getAscent() + 2;

        if (doRecalc || (recentWidth != getWidth())) {
            recentWidth = getWidth();
            recalculateDisplay(fm);
        }

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        g2.setPaint(pntBackground);
        g2.fillRect(0, 0, recentWidth, barExtent);

        g2.setPaint(pntMarkFlag);
        g2.fill(shpFlags);

        g2.setColor(colrLabel);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (int i = 0; i < numMarkers; i++) {
            g2.drawString(markLabels[i], markFlagPos[i] + 4, y);
        }

        // handle dnd graphics
        if (dragLastMark != null) {
            final int dragMarkFlagPos = (int) (((dragLastMark.pos - visibleSpan.start) * (double) recentWidth / visibleSpan.getLength()) + 0.5);
            g2.setPaint(pntMarkFlagDrag);
            g2.fillRect(dragMarkFlagPos, 1, fm.stringWidth(dragLastMark.name) + 8, markExtent);
            g2.setColor(colrLabelDrag);
            g2.drawString(dragLastMark.name, dragMarkFlagPos + 4, y);
        }

//long t2 = System.currentTimeMillis();
//System.out.println( "paintComponent " + (t2 - t1) );
//System.out.println( numMarkers );
    }

    public void paintFlagSticks( Graphics2D g2, Rectangle bounds )
    {
        if( doRecalc ) {
            recalculateDisplay( g2.getFontMetrics() );	// XXX nicht ganz sauber (anderer graphics-context!)
        }

        final Stroke	strkOrig	= g2.getStroke();
//		int				x;

        g2.setPaint( pntMarkStick );
        g2.setStroke( strkStick );
        for( int i = 0; i < numMarkers; i++ ) {
            g2.drawLine( markFlagPos[i], bounds.y, markFlagPos[i], bounds.y + bounds.height );
        }
        if( dragLastMark != null ) {
            final int dragMarkFlagPos = (int) (((dragLastMark.pos - visibleSpan.start) * (double) recentWidth / visibleSpan.getLength()) + 0.5);
            g2.setPaint( pntMarkStickDrag );
            g2.drawLine( dragMarkFlagPos, bounds.y, dragMarkFlagPos, bounds.y + bounds.height );
        }
        g2.setStroke( strkOrig );
    }

    private void triggerRedisplay()
    {
        doRecalc	= true;
        if( host != null ) {
            host.update( this );
        } else if( isVisible() ) {
            repaint();
        }
    }
  
    public void addMarker( long pos )
    {
        final AbstractCompoundEdit	ce;

        pos		= Math.max( 0, Math.min( doc.timeline.getLength(), pos ));
        ce		= new BasicCompoundEdit( getResourceString( "editAddMarker" ));
        doc.markers.editBegin( ce );
        try {
            doc.markers.editAdd( this, new MarkerStake( pos, "Mark" ), ce );
        }
        catch( IOException e1 ) {	// should never happen
            System.err.println("addMarker:");
            e1.printStackTrace();
            ce.cancel();
            return;
        }
        finally {
            doc.markers.editEnd( ce );
        }
        ce.perform();
        ce.end();
        doc.getUndoManager().addEdit( ce );
    }

    private void removeMarkerLeftTo(long pos) {
        final AbstractCompoundEdit ce;
        final MarkerStake mark;

        mark 	= getMarkerLeftTo(pos);
        // pos 	= Math.max(0, Math.min(doc.timeline.getLength(), pos));
        if (mark == null) return;

        ce = new BasicCompoundEdit(getResourceString("editDeleteMarker"));
        doc.markers.editBegin(ce);
        try {
            doc.markers.editRemove(this, mark, ce);
        } catch (IOException e1) {    // should never happen
            System.err.println("removeMarkerLeftTo:");
            e1.printStackTrace();
            ce.cancel();
            return;
        } finally {
            doc.markers.editEnd(ce);
        }
        ce.perform();
        ce.end();
        doc.getUndoManager().addEdit(ce);
    }

    private void editMarkerLeftTo( long pos )
    {
        final int				result;
//		final MarkerStake		mark; //	= getMarkerLeftTo( pos );

//		if( mark == null ) return;

        editIdx		= doc.markers.indexOf( pos );
        if( editIdx < 0 ) {
            editIdx = -(editIdx + 2);
            if( editIdx == -1 ) return;
        }

        if( editMarkerPane == null ) {
            final SpringPanel		spring;
            final ActionMap			amap;
            final InputMap			imap;
            JLabel					lb;
            KeyStroke				ks;
            Action					a;

            spring			= new SpringPanel( 4, 2, 4, 2 );
            ggMarkName		= new JTextField( 24 );
//			GUIUtil.setInitialDialogFocus( ggMarkName );	// removes itself automatically
            ggMarkName.addAncestorListener( new AncestorAdapter() {
                public void ancestorAdded( AncestorEvent e ) {
                    ggMarkName.requestFocusInWindow();
                    ggMarkName.selectAll();
//					c.removeAncestorListener( this );
                }
            });

            // XXX sync
            timeTrans		= new DefaultUnitTranslator();
            ggMarkPos		= new ParamField( timeTrans );
            ggMarkPos.addSpace( ParamSpace.spcTimeHHMMSS );
            ggMarkPos.addSpace( ParamSpace.spcTimeSmps );
            ggMarkPos.addSpace( ParamSpace.spcTimeMillis );
            ggMarkPos.addSpace( ParamSpace.spcTimePercentF );

            lb				= new JLabel( getResourceString( "labelName" ));
//			lb.setLabelFor( ggMarkName );
            spring.gridAdd( lb, 0, 0 );
            spring.gridAdd( ggMarkName, 1, 0 );
            lb				= new JLabel( getResourceString( "labelPosition" ));
//			lb.setLabelFor( ggMarkPos );
            spring.gridAdd( lb, 0, 1 );
            spring.gridAdd( ggMarkPos, 1, 1, -1, 1 );
//			GUIUtil.setDeepFont( spring, null );
            spring.makeCompactGrid();
            editMarkerPane	= new JPanel( new BorderLayout() );
            editMarkerPane.add( spring, BorderLayout.NORTH );

            amap			= spring.getActionMap();
            imap			= spring.getInputMap( JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT );
            ks				= KeyStroke.getKeyStroke( KeyEvent.VK_LEFT, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask() );
            // XXX DOESN'T WORK ;-(
            //			ggMarkName.getInputMap().remove( ks );
            imap.put( ks, "prev" );
            a				= new ActionEditPrev();
//			amap.put( "prev", a );
            ggEditPrev		= new JButton( a );
            amap.put( "prev", new DoClickAction( ggEditPrev ));
            ks				= KeyStroke.getKeyStroke( KeyEvent.VK_RIGHT, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask() );
            // XXX DOESN'T WORK ;-(
            //			ggMarkName.getInputMap().remove( ks );
            imap.put( ks, "next" );
            a				= new ActionEditNext();
//			amap.put( "next", a );
            ggEditNext		= new JButton( a );
            amap.put( "next", new DoClickAction( ggEditNext ));

            editOptions		= new Object[] { ggEditNext, ggEditPrev, getResourceString( "buttonOk" ), getResourceString( "buttonCancel" )};
//			editOptions		= new Object[] { ggEditNext, ggEditPrev, JOptionPane.OK_OPTION, JOptionPane.CANCEL_OPTION };
        }

        // XXX sync
        timeTrans.setLengthAndRate( doc.timeline.getLength(), doc.timeline.getRate() );

        updateEditMarker();

        final JOptionPane op = new JOptionPane( editMarkerPane, JOptionPane.QUESTION_MESSAGE, JOptionPane.OK_CANCEL_OPTION,
                                                null, editOptions, editOptions[ 2 ]);
//		result = JOptionPane.showOptionDialog( BasicWindowHandler.getWindowAncestor( this ),
//											   editMarkerPane, getResourceString( "inputDlgEditMarker" ),
//			JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, editOptions, editOptions[ 2 ]);
        result = BasicWindowHandler.showDialog( op, BasicWindowHandler.getWindowAncestor( this ), getResourceString( "inputDlgEditMarker" ));

//		if( result == JOptionPane.OK_OPTION ) {
        if( result == 2 ) {
            commitEditMarker();
        }
    }

    protected void updateEditMarker()
    {
        final MarkerStake mark = doc.markers.get( editIdx );
        if( mark == null ) return;

        ggMarkPos.setValue( new Param( mark.pos, ParamSpace.TIME | ParamSpace.SMPS ));
        ggMarkName.setText( mark.name );

        ggEditPrev.setEnabled( editIdx > 0 );
        ggEditNext.setEnabled( (editIdx + 1) < doc.markers.getNumStakes() );

        ggMarkName.requestFocusInWindow();
        ggMarkName.selectAll();
    }

    protected void commitEditMarker() {
        final MarkerStake mark = doc.markers.get(editIdx);
        if (mark == null) return;

        final long				positionSmps;
        final AbstractCompoundEdit	ce;

        positionSmps	= (long) timeTrans.translate( ggMarkPos.getValue(), ParamSpace.spcTimeSmps ).val;
        if( (positionSmps == mark.pos) && (ggMarkName.getText().equals( mark.name ))) return; // no change

        ce		= new BasicCompoundEdit( getResourceString( "editEditMarker" ));
        doc.markers.editBegin( ce );
        try {
            doc.markers.editRemove( this, mark, ce );
            doc.markers.editAdd( this, new MarkerStake( positionSmps, ggMarkName.getText() ), ce );
        }
        catch( IOException e1 ) {	// should never happen
            System.err.println("Edit marker:");
            e1.printStackTrace();
            ce.cancel();
            return;
        }
        finally {
            doc.markers.editEnd( ce );
        }
        ce.perform();
        ce.end();
        doc.getUndoManager().addEdit( ce );
    }

    private MarkerStake getMarkerLeftTo( long pos )
    {
        int idx;

//		if( !doc.bird.attemptShared( Session.DOOR_TIME, 250 )) return null;
//		try {
            idx		= doc.markers.indexOf( pos );
            if( idx < 0 ) {
                idx = -(idx + 2);
                if( idx == -1 ) return null;
            }
            return doc.markers.get( idx );
//		}
//		finally {
//			doc.bird.releaseShared( Session.DOOR_TIME );
//		}
    }

    // -------------- Disposable interface --------------

    public void dispose()
    {
        markLabels	= null;
        markFlagPos	= null;
        shpFlags.reset();
        // img1.flush();
        img2.flush();
        img3.flush();
    }

// ---------------- LaterInvocationManager.Listener interface ---------------- 

        // called by DynamicPrefChangeManager ; o = PreferenceChangeEvent
//		public void laterInvocation( Object o )
//		{
//			final PreferenceChangeEvent	pce = (PreferenceChangeEvent) o;
//			final String				key = pce.getKey();
//			
//			if( key.equals( PrefsUtil.KEY_TIMEUNITS )) {
//				int timeUnits = pce.getNode().getInt( key, 0 );
//				setFlags( timeUnits == 0 ? 0 : TIMEFORMAT );
//				recalculateDisplay();
//			}
//		}

// ---------------- DynamicListening interface ---------------- 

    public void startListening()
    {
        if( !isListening ) {
            doc.timeline.addTimelineListener( this );
    //		doc.tracks.addListener( this );
            doc.markers.addListener( this );
            triggerRedisplay();
            isListening = true;
        }
    }

    public void stopListening()
    {
        if( isListening ) {
            doc.markers.removeListener( this );
    // 		doc.tracks.removeListener( this );
            doc.timeline.removeTimelineListener( this );
            isListening = false;
        }
    }

// ---------------- MarkerManager.Listener interface ---------------- 

    public void trailModified( Trail.Event e )
    {
        if( e.getAffectedSpan().touches( visibleSpan )) {
            triggerRedisplay();
        }
    }

// ---------------- MouseListener interface ---------------- 
// we're listening to the ourselves

    public void mouseEntered( MouseEvent e )
    {
//		if( isEnabled() ) dispatchMouseMove( e );
    }

    public void mouseExited( MouseEvent e ) { /* ignore */ }

    public void mousePressed( MouseEvent e )
    {
        final long pos = (long) (e.getX() / scale + visibleSpan.getStart() + 0.5);

        if( shpFlags.contains( e.getPoint() )) {
            if( e.isAltDown() ) {					// delete marker
                removeMarkerLeftTo( pos + 1 );
            } else if( e.getClickCount() == 2 ) {	// rename
                editMarkerLeftTo( pos + 1 );
            } else {								// start drag
                dragMark			= getMarkerLeftTo( pos + 1 );
//				dragLastMark		= dragMark;
                dragStarted			= false;
                dragStartX			= e.getX();
                adjustCatchBypass	= true;
                doc.getFrame().addCatchBypass();
                requestFocus();
            }

        } else if( !e.isAltDown() && (e.getClickCount() == 2) ) {		// insert marker
            addMarker( pos );
        }
    }

    public void mouseReleased( MouseEvent e )
    {
        AbstractCompoundEdit ce;

        if( adjustCatchBypass ) {
            adjustCatchBypass = false;
            doc.getFrame().removeCatchBypass();
        }

        try {
            if( dragLastMark != null ) {
//				if( !doc.bird.attemptExclusive( Session.DOOR_TIME, 250 )) return;
//				try {
                    // ok this is tricky and totally stupid, have to replace it some day XXX
//					doc.markers.remove( this, dragLastMark );	// remove temporary marker
//					doc.markers.add( this, dragMark );			// restore original marker for undoable edit!
                    ce	= new BasicCompoundEdit( getResourceString( "editMoveMarker" ));
                    doc.markers.editBegin( ce );
                    try {
                        doc.markers.editRemove( this, dragMark, ce );
                        doc.markers.editAdd( this, dragLastMark, ce );
                    }
                    catch( IOException e1 ) {	// should never happen
                        System.err.println("Move marker:");
                        e1.printStackTrace();
                        ce.cancel();
                        return;
                    }
                    doc.markers.editEnd( ce );
                    ce.perform();
                    ce.end();
                    doc.getUndoManager().addEdit( ce );
//				}
//				catch( IOException e1 ) {	// should never happen
//					System.err.println( e1 );
//					return;
//				}
//				finally {
//					doc.bird.releaseExclusive( Session.DOOR_TIME );
//				}
            }
        }
        finally {
            dragStarted		= false;
            dragMark		= null;
            dragLastMark	= null;
        }
    }

    public void mouseClicked( MouseEvent e ) { /* ignored */ }

// ---------------- MouseMotionListener interface ---------------- 
// we're listening to ourselves

    public void mouseMoved( MouseEvent e ) { /* ignore */ }

    public void mouseDragged( MouseEvent e )
    {
        if( dragMark == null ) return;

        if( !dragStarted ) {
            if( Math.abs( e.getX() - dragStartX ) < 5 ) return;
            dragStarted = true;
        }

        final Span			dirtySpan;
        final long			oldPos	= dragLastMark != null ? dragLastMark.pos : dragMark.pos;
        final long			newPos	= Math.max( 0, Math.min( doc.timeline.getLength(), (long) ((e.getX() - dragStartX) / scale + dragMark.pos + 0.5) ));

        if( oldPos == newPos ) return;

        dirtySpan		= new Span( Math.min( oldPos, newPos ), Math.max( oldPos, newPos ));
        dragLastMark	= new MarkerStake( newPos, dragMark.name );
        doc.getFrame().repaintMarkers( dirtySpan );
    }

// ---------------- KeyListener interface ---------------- 
// we're listening to ourselves

    public void keyPressed( KeyEvent e )
    {
        if( e.getKeyCode() == KeyEvent.VK_ESCAPE ) {
            dragMark		= null;
            dragLastMark	= null;
            if( dragStarted ) {
                dragStarted	= false;
                doc.getFrame().repaintMarkers( visibleSpan );
            }
        }
    }

    public void keyReleased( KeyEvent e ) { /* ignored */ }
    public void keyTyped( KeyEvent e ) { /* ignored */ }

// ---------------- TimelineListener interface ---------------- 
  
    public void timelineSelected( TimelineEvent e ) { /* ignored */ }
    public void timelinePositioned( TimelineEvent e ) { /* ignored */ }

    public void timelineChanged( TimelineEvent e )
    {
        triggerRedisplay();
    }

    public void timelineScrolled( TimelineEvent e )
    {
//		if( !doc.bird.attemptShared( Session.DOOR_TIME, 250 )) return;
//		try {
            visibleSpan = doc.timeline.getVisibleSpan();
            scale		= (double) getWidth() / visibleSpan.getLength();

            triggerRedisplay();
//		}
//		finally {
//			doc.bird.releaseShared( Session.DOOR_TIME );
//		}
    }

// ---------------- internal classes ----------------

    @SuppressWarnings("serial")
    private class ActionEditPrev
            extends MenuAction {
        protected ActionEditPrev()
        {
//			super( "\u21E0", KeyStroke.getKeyStroke( KeyEvent.VK_LEFT, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask() ));
            super( "\u21E0" );
        }

        public void actionPerformed( ActionEvent e )
        {
            commitEditMarker();
            if( editIdx > 0 ) {
                editIdx--;
                updateEditMarker();
            }
        }
    }

    @SuppressWarnings("serial")
    private class ActionEditNext
            extends MenuAction {

        protected ActionEditNext() {
            super("\u21E2", KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
        }

        public void actionPerformed(ActionEvent e) {
            commitEditMarker();
            if ((editIdx + 1) < doc.markers.getNumStakes()) {
                editIdx++;
                updateEditMarker();
            }
        }
    }
}