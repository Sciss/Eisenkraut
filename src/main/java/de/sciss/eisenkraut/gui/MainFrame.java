/*
 *  MainFrame.java
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

package de.sciss.eisenkraut.gui;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Locale;
import javax.swing.*;
import javax.swing.border.AbstractBorder;

import de.sciss.eisenkraut.Main;
import de.sciss.eisenkraut.net.SuperColliderClient;

import de.sciss.app.AbstractApplication;
import de.sciss.app.AbstractWindow;
import de.sciss.app.Application;
import de.sciss.common.AppWindow;
import de.sciss.common.BasicApplication;
import de.sciss.gui.AbstractWindowHandler;
import de.sciss.gui.CoverGrowBox;
import de.sciss.gui.LogTextArea;
import de.sciss.gui.MultiStateButton;
import de.sciss.gui.TreeExpanderButton;
import de.sciss.jcollider.Server;
import de.sciss.jcollider.ServerEvent;
import de.sciss.jcollider.ServerListener;

/**
 *  @author		Hanns Holger Rutz
 *  @version	0.70, 07-Dec-07
 */
public class MainFrame
extends AppWindow
implements de.sciss.jcollider.Constants, ServerListener
//implements	RunnableProcessing, ProgressComponent, TransportListener,
//			RealtimeConsumer, OSCListener, de.sciss.app.DocumentListener
{
//	private final PrintStream			logStream;
	
//	private boolean						keepScRunning;
//	private ProcessingThread			pt				= null;
	protected boolean					booted			= false;
	
	private final ActionBoot			actionBoot;

//	private final JCheckBox				ggDumpOSC;
	protected final MultiStateButton	ggDumpOSC;
	private final JLabel				lbStatus1;
	private final JLabel				lbStatus2;

//	private ServerOptions				serverOptions;

	private final MessageFormat			msgStatus1;
	private final MessageFormat			msgStatus2;
	private final Object[]				argsStatus		= new Object[ 5 ];
	private final String				unknownStatus;
	protected final Box					boxStatus2;

	private final LogTextArea			lta;
	protected final MultiStateButton	ggBoot;

	private final Font					fntMonoSpaced;
	
	protected final SuperColliderClient	superCollider;

	@SuppressWarnings("serial")
	private class TH extends TransferHandler {
		public TH() {
			super();
		}

		@Override
		public boolean canImport(TransferSupport support) {
			return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
		}

		@Override
		public boolean importData(TransferSupport support) {
			if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
				try {
					java.util.List fs = (java.util.List)
							support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
					for (Object f1 : fs) {
						File f = (File) f1;
						// System.out.println(f);
						final Application	app	= AbstractApplication.getApplication();
						((BasicApplication) app).getMenuFactory().openDocument(f);
					}
					return true;
				} catch (IOException e) {
					return false;
				} catch (UnsupportedFlavorException e) {
					return false;
				}
			} else {
				return false;
			}
		}
	}

	@SuppressWarnings("serial")
	private static class DropBorder extends AbstractBorder {
		public DropBorder() {
			super();
		}

		@Override public Insets getBorderInsets(Component c, Insets insets) {
			insets.top  = insets.bottom = 5;
			insets.left = insets.right  = 8;
			return insets;
		}

		private final Stroke strk = new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER,
				10.0f, new float[] { 3f, 3f }, 0.0f);

		@Override
		public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
			Graphics2D g2 = (Graphics2D) g;
			g2.setColor(Color.darkGray);
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			// g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
			g2.setStroke(strk);
			g2.drawRoundRect(x + 1, y + 1, width - 3, height - 3, 8, 8);
		}
	}

	public MainFrame() {
		super( REGULAR );
		
		final Application	app	= AbstractApplication.getApplication();

		superCollider	= SuperColliderClient.getInstance();
		
		if( app.getWindowHandler().usesInternalFrames() ) {
			setTitle( app.getResourceString( "frameMain" ));
			((JInternalFrame) getWindow()).setClosable( false );
		} else {
			setTitle( app.getName() + " : " + app.getResourceString( "frameMain" ));
		}
		
		final Container					cp				= getContentPane();
		final Box						boxStatus1		= Box.createHorizontalBox();
		final JPanel					bottomPane		= new JPanel( new BorderLayout( 4, 2 ));
		final JScrollPane				ggScroll;
//		final JButton					ggBoot;
		final AbstractWindow.Listener	winListener;
		final TreeExpanderButton		ggStatusExp;
//		String[]						cfgNames		= null;
		final String[]					fntNames;
	
		lta				= new LogTextArea( 16, 40, false, null );
		ggScroll		= lta.placeMeInAPane();
		lta.makeSystemOutput();
		
		actionBoot		= new ActionBoot();
//		ggBoot			= new JButton( actionBoot );
		ggBoot			= new MultiStateButton();
		ggBoot.setFocusable( false );	// prevent user from accidentally starting/stopping server
		ggBoot.setAutoStep( false );
		ggBoot.addActionListener( actionBoot );
		
//		ggDumpOSC		= new JCheckBox( getResourceString( "labelDumpOSC" ));
//		ggDumpOSC.addItemListener( new ItemListener() {
//			public void itemStateChanged( ItemEvent e ) {
//				root.superCollider.dumpOSC( ggDumpOSC.isSelected() ? kDumpText : kDumpOff );
//			}
//		});
		ggDumpOSC		= new MultiStateButton();
		ggDumpOSC.setFocusable( false );
		ggDumpOSC.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent e ) {
				superCollider.dumpOSC( ggDumpOSC.getSelectedIndex() );
			}
		});
//		ggDumpOSC.addItem( getResourceString( "labelDumpOff" ), null, null );
//		ggDumpOSC.addItem( getResourceString( "labelDumpText" ), null, new Color( 0xFF, 0xFA, 0x9D ), new Color( 0xFA, 0xE7, 0x9D ));
//		ggDumpOSC.addItem( getResourceString( "labelDumpHex" ), null, new Color( 0xFA, 0x9D, 0xFF ), new Color( 0xE7, 0x9D, 0xFA ));

        JLabel ggImport = new JLabel("<html><body><i>Drop</i></body>", null, SwingConstants.CENTER);
        ggImport.setForeground(Color.darkGray);
        ggImport.setTransferHandler(new TH());
        ggImport.setToolTipText("Drop audio files here to open them");
        ggImport.setBorder(new DropBorder());
        Dimension importPref = ggImport.getPreferredSize();
        // importPref.width = 100;
        ggImport.setPreferredSize(importPref);
        ggImport.setMaximumSize  (importPref);

		boxStatus2		= Box.createHorizontalBox();
		msgStatus1		= new MessageFormat( getResourceString( "ptrnServerStatus1" ), Locale.US );
		msgStatus2		= new MessageFormat( getResourceString( "ptrnServerStatus2" ), Locale.US );
		unknownStatus	= getResourceString( "labelServerNotRunning" );
		lbStatus1		= new JLabel( unknownStatus );
		lbStatus2		= new JLabel();

		ggStatusExp	= new TreeExpanderButton();
		ggStatusExp.setExpandedToolTip( getResourceString( "buttonExpStatsTT" ));
		ggStatusExp.setCollapsedToolTip( getResourceString( "buttonCollStatsTT" ));
		ggStatusExp.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent e )
			{
				final Dimension	d	= getSize();

				if( ggStatusExp.isExpanded() ) {
					boxStatus2.setVisible( true );
					setSize( new Dimension( d.width, d.height + boxStatus2.getPreferredSize().height + 2 ));
				} else {
					boxStatus2.setVisible( false );
					setSize( new Dimension( d.width, d.height - (boxStatus2.getPreferredSize().height + 2) ));
				}
			}
		});
		
		lbStatus1.setPreferredSize( new Dimension( 192, lbStatus1.getPreferredSize().height ));
		lbStatus2.setPreferredSize( new Dimension( 226, lbStatus1.getPreferredSize().height ));
		
		boxStatus1.add( new JLabel( new ImageIcon( getClass().getResource( "sc-icon.png" ))));
		boxStatus1.add( Box.createHorizontalStrut( 2 ));
		boxStatus1.add( ggStatusExp );
		boxStatus1.add( lbStatus1 );
		boxStatus1.add( ggBoot );
        boxStatus1.add( Box.createHorizontalStrut(4));
        boxStatus1.add(ggImport);
		boxStatus1.add( Box.createHorizontalGlue() );

//		boxStatus2.add( Box.createHorizontalStrut( 32 ));
		boxStatus2.add( lbStatus2 );
		boxStatus2.add( ggDumpOSC );
		boxStatus2.add( Box.createHorizontalGlue() );
		boxStatus2.setVisible( false );

		boxStatus1.add( CoverGrowBox.create() );
		boxStatus2.add( CoverGrowBox.create() );
//        if( AbstractApplication.getApplication().getUserPrefs().getBoolean( PrefsUtil.KEY_INTRUDINGSIZE, false )) {
//    		boxStatus1.add( Box.createHorizontalStrut( 16 ));
//    		boxStatus2.add( Box.createHorizontalStrut( 16 ));
//        }
		
		bottomPane.add( boxStatus1, BorderLayout.NORTH );
		bottomPane.add( boxStatus2, BorderLayout.SOUTH );
//		bottomPane.add( ggBoot, BorderLayout.EAST );
		bottomPane.setBorder( BorderFactory.createEmptyBorder( 0, 4, 0, 4 ));
		cp.add( ggScroll, BorderLayout.CENTER );
		cp.add( bottomPane, BorderLayout.SOUTH );

		fntNames = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
		if( contains( fntNames, "Monaco" )) {							// Mac OS
			fntMonoSpaced = new Font( "Monaco", Font.PLAIN, 9 );		// looks bigger than it is
		} else if( contains( fntNames, "Lucida Sans Unicode" )) {		// Windows XP
			fntMonoSpaced = new Font( "Lucida Sans Unicode", Font.PLAIN, 9 );
		} else {
			fntMonoSpaced = new Font( "Monospaced", Font.PLAIN, 10 );
		}
		
		AbstractWindowHandler.setDeepFont( cp );
		
		ggBoot.setNumColumns( 9 );
		ggBoot.addItem( getResourceString( "buttonBoot" ), null, null );
// NOTE: BUG WITH CUSTOM COMPOSITE ON WIN-XP!!!
//		ggBoot.addItem( getResourceString( "buttonTerminate" ), null, new Color( 0xFF, 0xFA, 0x9D ), new Color( 0xFA, 0xE7, 0x9D ));
ggBoot.addItem( getResourceString( "buttonTerminate" ), null, new Color( 0xFF, 0xFA, 0x9D ));
		ggDumpOSC.setNumColumns( 9 );
		ggDumpOSC.addItem( getResourceString( "labelDumpOff" ), null, null );
//		ggDumpOSC.addItem( getResourceString( "labelDumpText" ), null, new Color( 0xFF, 0xFA, 0x9D ), new Color( 0xFA, 0xE7, 0x9D ));
ggDumpOSC.addItem( getResourceString( "labelDumpText" ), null, new Color( 0xFF, 0xFA, 0x9D ));
//		ggDumpOSC.addItem( getResourceString( "labelDumpHex" ), null, new Color( 0xFF, 0x9D, 0x9D ), new Color( 0xFA, 0x8D, 0x9D ));
ggDumpOSC.addItem( getResourceString( "labelDumpHex" ), null, new Color( 0xFF, 0x9D, 0x9D ));
		final Dimension d = new Dimension( ggDumpOSC.getPreferredSize().width, ggDumpOSC.getMaximumSize().height );
		ggBoot.setMaximumSize( d );
		ggDumpOSC.setMaximumSize( d );
		
		lbStatus1.setFont( fntMonoSpaced );
		lbStatus2.setFont( fntMonoSpaced );
		lta.setFont( fntMonoSpaced );

		// ---- menus and actions ----
		
		((BasicApplication) app).getMenuBarRoot().putMimic( "edit.clear", this, lta.getClearAction() );

		// ---- listeners -----
		
//		root.getDocumentHandler().addDocumentListener( this );

		winListener = new AbstractWindow.Adapter() {
			public void windowClosing( AbstractWindow.Event e ) {
				app.quit();
			}
		};
		addListener( winListener );
		
		superCollider.addServerListener( this );
		
//        HelpGlassPane.setHelp( getRootPane(), "MainFrame" );

		setDefaultCloseOperation( WindowConstants.DO_NOTHING_ON_CLOSE );
		
		init();
		app.addComponent( Main.COMP_MAIN, this );
		setVisible( true );
//		toFront();
	}
	
	protected boolean autoUpdatePrefs()
	{
		return true;
	}

	protected Point2D getPreferredLocation()
	{
		return new Point2D.Float( 0f, 0f );
	}

	public void dispose() {
		AbstractApplication.getApplication().removeComponent(Main.COMP_MAIN);
		super.dispose();
	}

	private static boolean contains(String[] array, String name) {
		for (String anArray : array) {
			if (anArray.equals(name)) return true;
		}
		return false;
	}

	private String getResourceString(String key) {
		return AbstractApplication.getApplication().getResourceString(key);
	}
	
	private void updateStatus()
	{
		final Server.Status s = superCollider.getStatus();
		if( s != null ) {
			argsStatus[0] = (float) s.sampleRate;
			argsStatus[1] = s.avgCPU;
			lbStatus1.setText( msgStatus1.format( argsStatus ));
			if( boxStatus2.isVisible() ) {
				argsStatus[ 0 ]	= s.numUGens;
				argsStatus[ 1 ]	= s.numSynths;
				argsStatus[ 2 ]	= s.numGroups;
				argsStatus[ 3 ]	= s.numSynthDefs;
				lbStatus2.setText( msgStatus2.format( argsStatus ));
			}
		} else {
			lbStatus1.setText( unknownStatus );
		}
	}
	
// ------------- ServerListener interface -------------

	public void serverAction( ServerEvent e )
	{
		switch( e.getID() ) {
		case ServerEvent.RUNNING:
			actionBoot.booted();
			break;

		case ServerEvent.STOPPED:
			actionBoot.terminated();
			updateStatus();
			break;

		case ServerEvent.COUNTS:
			updateStatus();
			break;
			
		default:
			break;
		}
	}

// ------------- interne klassen -------------

	@SuppressWarnings("serial")
	private class ActionBoot
	extends AbstractAction
	{
		protected ActionBoot()
		{
			super();
//			super( getResourceString( "buttonBoot" ));
//			putValue( SMALL_ICON, new ImageIcon( getClass().getResource( "sc-icon.png" )));
		}
		
		public void actionPerformed( ActionEvent e )
		{
			if( booted ) {
				superCollider.stop();
			} else {
				superCollider.boot();
			}
		}
		
		protected void terminated()
		{
			booted = false;
//			putValue( NAME, getResourceString( "buttonBoot" ));
			ggBoot.setSelectedIndex( 0 );
		}

		protected void booted()
		{
			booted = true;
//			putValue( NAME, getResourceString( "buttonTerminate" ));
			ggBoot.setSelectedIndex( 1 );
		}
	} // class actionBootClass
}