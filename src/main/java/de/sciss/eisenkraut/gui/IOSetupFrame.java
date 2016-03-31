/*
 *  IOSetupFrame.java
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

import de.sciss.app.AbstractApplication;
import de.sciss.app.Application;
import de.sciss.app.GraphicsHandler;
import de.sciss.common.AppWindow;
import de.sciss.common.BasicWindowHandler;
import de.sciss.eisenkraut.Main;
import de.sciss.eisenkraut.io.AudioBoxConfig;
import de.sciss.eisenkraut.io.RoutingConfig;
import de.sciss.eisenkraut.util.PrefsUtil;
import de.sciss.gui.CoverGrowBox;
import de.sciss.gui.HelpButton;
import de.sciss.gui.ModificationButton;
import de.sciss.gui.SortedTableModel;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 *  This is the frame that
 *  displays the user adjustable
 *  input/output configuration
 */
public class IOSetupFrame extends AppWindow {

    private static class Tab {
        public Tab() {}

        final List<RoutingConfig>	collConfig		= new ArrayList<RoutingConfig>();
        final Set<String> 			setConfigID		= new HashSet<String>();
        final Set<String> 			setConfigName	= new HashSet<String>();
        final Set<String> 			setDirtyConfig	= new HashSet<String>();
    }

    private static final int NUM_TABS = 2;
    private final Tab[] tabs = new Tab[] { new Tab(), new Tab() };

    private final Preferences		audioPrefs;
    private final int[]			audioHwChannels	= new int[ NUM_TABS ];

    private static final String[]	staticColNames	= { "ioConfig", "ioNumChannels", "ioStartAngle" };
    private static final int[]		staticColWidths	= { 160, 54, 54 };
    private static final int		MAPPING_WIDTH	= 48; // 40; // 36;
//	private static final Font		fnt				= GraphicsUtil.smallGUIFont;

    private static final int[] pntMapNormGradPixLight = {
            0xFFF4F4F4, 0xFFF1F1F1, 0xFFEEEEEE, 0xFFECECEC,
            0xFFECECEC, 0xFFECECEC, 0xFFEDEDED, 0xFFDADADA,
            0xFFDFDFDF, 0xFFE3E3E3, 0xFFE7E7E7, 0xFFEBEBEB,
            0xFFF0F0F0, 0xFFF3F3F3, 0xFFF9F9F9};

    private static final int[] pntMapNormGradPixDark = {
            0xFF747474, 0xFF717171, 0xFF6E6E6E, 0xFF6C6C6C,
            0xFF6C6C6C, 0xFF6C6C6C, 0xFF6D6D6D, 0xFF5A5A5A,
            0xFF5F5F5F, 0xFF636363, 0xFF676767, 0xFF6B6B6B,
            0xFF707070, 0xFF737373, 0xFF797979 };

    private static final int	pntMapSize			= 15;

    private static final int[] pntMapSelGradPixLight = {
            0xFFD8DBE0, 0xFFCAD0D5, 0xFFC2C9CE, 0xFFBEC4CB,
            0xFFBBC2C8, 0xFFB8BEC6, 0xFFB6BCC6, 0xFF9EA8B4,
            0xFFA4ADB9, 0xFFAAB4BF, 0xFFAFB9C6, 0xFFB8C2CE,
            0xFFBBC5D0, 0xFFBFCAD4, 0xFFC7D1DD};

    private static final int[] pntMapSelGradPixDark = {
            0xFF585B70, 0xFF4A5065, 0xFF42495E, 0xFF3E445B,
            0xFF3B4258, 0xFF383E56, 0xFF363C56, 0xFF1E2844,
            0xFF242D49, 0xFF2A344F, 0xFF2F3956, 0xFF38425E,
            0xFF3B4560, 0xFF3F4A64, 0xFF47516D};

    private static final Paint pntMapNormLight, pntMapSelLight, pntMapNormDark, pntMapSelDark;

    private final Paint pntMapNormal, pntMapSelected;

    private static final DataFlavor mapFlavor = new DataFlavor(MapTransferable.class, "io_mapping");
    private static final DataFlavor[] mapFlavors = {mapFlavor};

    private static final String[] KEY_INFOTEXT		= { "ioInputInfo", "ioOutputInfo" };
    private static final String[] KEY_DEFAULTNAME	= { "ioDefaultInName", "ioDefaultOutName" };
    private static final String[] KEY_PREFSNODE		= { PrefsUtil.NODE_INPUTCONFIGS, PrefsUtil.NODE_OUTPUTCONFIGS };

//	private final Paint pntMapEmpty;

    static {
        BufferedImage img;

        img = new BufferedImage(1, pntMapSize, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, 1, pntMapSize, pntMapNormGradPixLight, 0, 1);
        pntMapNormLight = new TexturePaint(img, new Rectangle(0, 0, 1, pntMapSize));
        img = new BufferedImage(1, pntMapSize, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, 1, pntMapSize, pntMapSelGradPixLight, 0, 1);
        pntMapSelLight = new TexturePaint(img, new Rectangle(0, 0, 1, pntMapSize));
        img = new BufferedImage(1, pntMapSize, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, 1, pntMapSize, pntMapNormGradPixDark, 0, 1);
        pntMapNormDark = new TexturePaint(img, new Rectangle(0, 0, 1, pntMapSize));
        img = new BufferedImage(1, pntMapSize, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, 1, pntMapSize, pntMapSelGradPixDark, 0, 1);
        pntMapSelDark = new TexturePaint(img, new Rectangle(0, 0, 1, pntMapSize));
    }

    /**
     *  Creates a new i/o setup frame
     */
	public IOSetupFrame() {
		super(SUPPORT);

		setTitle(getResourceString("frameIOSetup"));

        final Container					cp			= getContentPane();
        final Application				app			= AbstractApplication.getApplication();
        final Box						buttonPanel;
        final JTabbedPane				ggTabPane;
        final String					abCfgID;
        final AudioBoxConfig			abCfg;

        final boolean isDark = UIManager.getBoolean("dark-skin");
        pntMapNormal    = isDark ? pntMapNormDark : pntMapNormLight;
        pntMapSelected  = isDark ? pntMapSelDark  : pntMapSelLight;

		audioPrefs 	= app.getUserPrefs().node(PrefsUtil.NODE_AUDIO);
		abCfgID 	= audioPrefs.get(PrefsUtil.KEY_AUDIOBOX, AudioBoxConfig.ID_DEFAULT);
		abCfg 		= new AudioBoxConfig(audioPrefs.node(PrefsUtil.NODE_AUDIOBOXES).node(abCfgID));

        audioHwChannels[0]	= abCfg.numInputChannels;
        audioHwChannels[1]	= abCfg.numOutputChannels;

        ggTabPane			= new JTabbedPane();
        ggTabPane.putClientProperty("styleId", "attached");

        // ---------- tabs ----------

		for (int i = 0; i < NUM_TABS; i++) {    // input + output tabs
			fromPrefs(i);
			ggTabPane.addTab(app.getResourceString(i == 0 ? "labelInputs" : "labelOutputs"), null,
					createTab(i), null);
		}

        // ---------- generic gadgets ----------

        buttonPanel = Box.createHorizontalBox(); // new JPanel( new FlowLayout( FlowLayout.RIGHT, 4, 4 ));
//        pntMapEmpty = buttonPanel.getBackground();
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
        buttonPanel.add(new HelpButton("IOSetup"));
        buttonPanel.add(Box.createHorizontalGlue());
        final JButton ggOk = new JButton(app.getResourceString("buttonOk"));
        ggOk.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                final ControlRoomFrame f;

				for (int i = 0; i < NUM_TABS; i++) {
					if (!toPrefs(i)) return;
				}
				disposeAndClose();
// XXX ControlRoomFrame cannot rely on prefs since childAdded is
// never fired (probably bug in java or spi)
				f = (ControlRoomFrame) app.getComponent(Main.COMP_CTRLROOM);
				if (f != null) f.refillIOConfigs();
			}
		});
        buttonPanel.add(ggOk);
        final JButton ggCancel = new JButton(app.getResourceString("buttonCancel"));
        buttonPanel.add(ggCancel);
        ggCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                disposeAndClose();
            }
        });
        buttonPanel.add(CoverGrowBox.create());
        final Dimension d1 = ggOk    .getPreferredSize();
        final Dimension d2 = ggCancel.getPreferredSize();
        d1.width  = Math.max(d1.width , d2.width );
        d1.height = Math.max(d1.height, d2.height);
        ggOk    .setPreferredSize(d1);
        ggCancel.setPreferredSize(d1);

        cp.add(ggTabPane  , BorderLayout.CENTER);
        cp.add(buttonPanel, BorderLayout.SOUTH );

        // ---------- ----------

        ggTabPane.setSelectedIndex(NUM_TABS - 1);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        init();
        app.addComponent(Main.COMP_IOSETUP, this);
    }

    protected boolean autoUpdatePrefs() {
        return true;
    }

    private JComponent createTab(final int id) {
        final JPanel				tab;
        final LayoutManager			lay;
        final JTable				table;
        final AbstractTableModel	tm;
        final SortedTableModel		stm;
        final JTableHeader			th;
        final TableCellRenderer		tcr;
        final JScrollPane			scroll;
        final JTextArea				lbTextArea;
        final Box					b;
        final AbstractButton		ggPlus, ggMinus;

        tab			= new JPanel();
        lay			= new BorderLayout();
        tab.setLayout(lay);

        lbTextArea = new JTextArea(getResourceString(KEY_INFOTEXT[id]));
        lbTextArea.setEditable(false);
        lbTextArea.setBackground(null);
        lbTextArea.setColumns(32);
        lbTextArea.setLineWrap(true);
        lbTextArea.setWrapStyleWord(true);
        tab.add(lbTextArea, BorderLayout.NORTH);
        lbTextArea.setBorder(BorderFactory.createEmptyBorder(8, 2, 8, 2));

        tm      = new TableModel(id);
        stm     = new SortedTableModel(tm);
        table   = new JTable(stm);
        th      = table.getTableHeader();
        stm.setTableHeader(th);
        th.setReorderingAllowed(false);
        th.setResizingAllowed(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setCellSelectionEnabled(true);
        table.setColumnSelectionAllowed(false);
        table.setDragEnabled(true);
        table.setShowGrid(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        table.setTransferHandler(new MapTransferHandler(id));

        stm.setSortedColumn(0, SortedTableModel.ASCENDING);

        tcr = new MappingRenderer();
        setColumnRenderersAndWidths(table, stm, tcr);

        scroll = new JScrollPane(table, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                                        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);

        tab.add(scroll, BorderLayout.CENTER);

        final Tab t = tabs[id];

        b       = Box.createHorizontalBox();
        ggPlus  = new ModificationButton(ModificationButton.SHAPE_PLUS );
        ggMinus = new ModificationButton(ModificationButton.SHAPE_MINUS);
        ggMinus.setEnabled(false);
        ggPlus.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
//				int row = table.getSelectedRow() + table.getSelectedRowCount();
//				if( row <= 0 ) row = collConfigs[ ID ].size();
                final int modelIndex = t.collConfig.size();
                final int viewIndex;
                final RoutingConfig cfg = createUniqueConfig(id);
//				collConfigs[ ID ].add( row, cfg );
                t.collConfig.add(cfg);
                t.setConfigID.add(cfg.id);
                t.setConfigName.add(cfg.name);
                t.setDirtyConfig.add(cfg.id);
                tm.fireTableRowsInserted(modelIndex, modelIndex);
                viewIndex = stm.getViewIndex(modelIndex);
                table.setRowSelectionInterval(viewIndex, viewIndex);
            }
        });
        ggMinus.addActionListener( new ActionListener() {
            public void actionPerformed( ActionEvent e )
            {
                final int firstRow	= Math.max( 0, table.getSelectedRow() );
                final int lastRow	= Math.min( table.getRowCount(), firstRow + table.getSelectedRowCount() ) - 1;
                RoutingConfig cfg;
                final int[] modelIndices;

                if( firstRow <= lastRow ) {
                    modelIndices = new int[ lastRow - firstRow + 1 ];
                    for( int i = 0, viewIndex = firstRow; viewIndex <= lastRow; i++, viewIndex++ ) {
                        modelIndices[ i ] = stm.getModelIndex( viewIndex );
                    }
                    Arrays.sort( modelIndices );

                    for (int i = modelIndices.length - 1; i >= 0; i--) {
                        cfg = t.collConfig.remove(modelIndices[i]);
                        t.setConfigName.remove(cfg.name);
                        // never remove the id during one editing session,
                        // because that will confuse the prefs listeners
                        // and the setDirtyConfigs approach
//						setConfigIDs[ id ].remove( cfg.id );
                        t.setDirtyConfig.add(cfg.id);
                    }
//					tm.fireTableRowsDeleted( firstRow, lastRow );
                    tm.fireTableDataChanged();
                }
            }
        });
        b.add( ggPlus );
        b.add( ggMinus );
        b.add( Box.createHorizontalGlue() );

        table.getSelectionModel().addListSelectionListener( new ListSelectionListener() {
            public void valueChanged( ListSelectionEvent e )
            {
                ggMinus.setEnabled( table.getSelectedRowCount() > 0 );
            }
        });

        tab.add( b, BorderLayout.SOUTH );

        return tab;
    }

    private void disposeAndClose() {
        AbstractApplication.getApplication().removeComponent(Main.COMP_IOSETUP);    // needs to re-created each time!
        setVisible(false);
        dispose();
    }

    private void fromPrefs(int id) {
        final Tab t = tabs[id];

        t.collConfig.clear();
        t.setConfigName.clear();
        t.setConfigID.clear();

        final Preferences	ocPrefs		= audioPrefs.node( KEY_PREFSNODE[ id ]);
        final String[]		arrayNames;
        RoutingConfig		cfg;
        Preferences			cfgPrefs;

        try {
            arrayNames = ocPrefs.childrenNames();
//System.err.println( "Got "+arrayNames.length+" children . " );
        }
        catch( BackingStoreException e1 ) {
            BasicWindowHandler.showErrorDialog( getWindow(), e1, getResourceString( "errLoadPrefs" ));
            return;
        }

        for (String arrayName : arrayNames) {
            cfgPrefs = ocPrefs.node(arrayName);
            try {
                cfg = new RoutingConfig(cfgPrefs);
                t.collConfig   .add(cfg);
                t.setConfigID  .add(arrayName);
                t.setConfigName.add(cfg.name);
            } catch (NumberFormatException e1) {
                System.err.println("IOSetupFrame - fromPrefs:");
                e1.printStackTrace();
            }
        }
    }

    private boolean toPrefs(int id) {
        final Preferences	ocPrefs = audioPrefs.node(KEY_PREFSNODE[id]);
        RoutingConfig		cfg;
        Preferences			cfgPrefs;
        String				cfgID;

        final Tab t = tabs[id];

        try {
            for( int i = 0; i < t.collConfig.size(); i++ ) {
                cfg = t.collConfig.get(i);
                if( t.setDirtyConfig.remove(cfg.id)) {
                    cfgPrefs	= ocPrefs.node( cfg.id );
                    cfg.toPrefs( cfgPrefs );
                }
            }

            for (String aSetDirtyConfig : t.setDirtyConfig) {
                cfgID = aSetDirtyConfig;
                cfgPrefs = ocPrefs.node(cfgID);
                cfgPrefs.removeNode();
            }

            ocPrefs.flush();
        }
        catch( BackingStoreException e1 ) {
            BasicWindowHandler.showErrorDialog( getWindow(), e1, getResourceString( "errSavePrefs" ));
            return false;
        }

        return true;
    }

    private RoutingConfig createUniqueConfig(int id) {
        final Tab t = tabs[id];

        final String test = getResourceString(KEY_DEFAULTNAME[id]);
        String name = test;
        for (int i = 1; t.setConfigName.contains(name); i++) {
            name = test + " " + i;
        }
        String cfgID = "user1";
        for (int i = 2; t.setConfigID.contains(cfgID); i++) {
            cfgID = "user" + i;
        }

        return new RoutingConfig(cfgID, name);
    }

    private void setColumnRenderersAndWidths(JTable table, SortedTableModel stm, TableCellRenderer tcr) {
        final TableColumnModel	tcm	= table.getColumnModel();
        TableColumn				col;
        int						i;

        for (i = 0; i < staticColNames.length; i++) {
            col = tcm.getColumn(i);
            col.setMinWidth(staticColWidths[i]);
        }
        for (; i < table.getColumnCount(); i++) {
            stm.setSortingAllowed(i, false);
            col = tcm.getColumn(i);
            col.setPreferredWidth(MAPPING_WIDTH);
            col.setMinWidth(MAPPING_WIDTH);
            col.setMaxWidth(MAPPING_WIDTH);
            col.setCellRenderer(tcr);
        }
    }

    protected static String getResourceString(String key) {
        return AbstractApplication.getApplication().getResourceString(key);
    }

// ----------- internal classes  -----------

    @SuppressWarnings("serial")
    private class MapTransferHandler extends TransferHandler {
        private final int id;

        MapTransferHandler(int id)
        {
            this.id	= id;
        }

        /**
         * Overridden to import a MapTransferable if it is available.
         */
        public boolean importData(JComponent c, Transferable trans) {
            MapTransferable			mt;
            final JTable			table	= (JTable) c;
            final SortedTableModel	stm		= (SortedTableModel) table.getModel();
            final int				row		= table.getSelectedRow();
            final int				mapCh	= table.getSelectedColumn() - staticColNames.length;
            final int				modelIndex;
            RoutingConfig			cfg;
            int						temp;

            final Tab t = tabs[id];

            try {
                if( mapCh >= 0 && (row < table.getRowCount()) && trans.isDataFlavorSupported( mapFlavor )) {
                    modelIndex	= stm.getModelIndex( row );
                    cfg			= t.collConfig.get(modelIndex);
                    mt			= (MapTransferable) trans.getTransferData( mapFlavor );
                    // only allowed within same config
                    if( mt.cfg == cfg ) {
//System.err.println( "original mapping : "+(mt.idx+1)+"->"+(mt.cfg.mapping[ mt.idx ]+1)+"; new target " +(mapCh+1));
                        for( int i = 0; i < cfg.numChannels; i++ ) {
                            // dragged onto already mapped spot
                            if (cfg.mapping[i] == mapCh) {
                                if( i == mt.idx ) return false; // source == target, no action
                                temp					= cfg.mapping[ mt.idx ];
                                cfg.mapping[ mt.idx ]	= mapCh;
                                cfg.mapping[ i ]		= temp;	// simply swapped for now
                                ((AbstractTableModel) stm.getTableModel()).fireTableRowsUpdated( modelIndex, modelIndex );
                                return true;
                            }
                        }
                        // dragged onto empty spot
                        cfg.mapping[ mt.idx ] = mapCh;
                        t.setDirtyConfig.add(cfg.id);
                        ((AbstractTableModel) stm.getTableModel()).fireTableRowsUpdated( modelIndex, modelIndex );
                        return true;
                    }
                }
            }
            catch( UnsupportedFlavorException e1 ) { e1.printStackTrace(); }
            catch( IOException e2 ) { e2.printStackTrace(); }

            return false;
        }

        public int getSourceActions( JComponent c )
        {
            return MOVE;
        }

        protected Transferable createTransferable( JComponent c )
        {
            final JTable			table	= (JTable) c;
            final SortedTableModel	stm		= (SortedTableModel) table.getModel();
            final int				row		= table.getSelectedRow();
            final int				mapCh	= table.getSelectedColumn() - staticColNames.length;
            final int				modelIndex;
            RoutingConfig			cfg;

            final Tab t = tabs[id];

            if( mapCh >= 0 && (row < table.getRowCount()) ) {
                modelIndex	= stm.getModelIndex( row );
                cfg			= t.collConfig.get(modelIndex);
                for( int i = 0; i < cfg.numChannels; i++ ) {
                    if( cfg.mapping[ i ] == mapCh ) {
                        return new MapTransferable( cfg, i );
                    }
                }
            }
            return null;
        }

        protected void exportDone(JComponent source, Transferable data, int action) {
            //			System.err.println( "exportDone. Action == "+action );
        }

        public boolean canImport(JComponent c, DataFlavor[] flavors) {
            for (DataFlavor flavor : flavors) {
                for (DataFlavor mapFlavor1 : mapFlavors) {
                    if (flavor.equals(mapFlavor1)) return true;
                }
            }
            return false;
        }
    } // class MapTransferHandler

    private static class MapTransferable
            implements Transferable {

        final RoutingConfig cfg;
        protected final int idx;

        MapTransferable(RoutingConfig cfg, int idx) {
            this.cfg = cfg;
            this.idx = idx;
        }

        public DataFlavor[] getTransferDataFlavors()
        {
            return mapFlavors;
        }

        public boolean isDataFlavorSupported(DataFlavor flavor) {
            for (DataFlavor mapFlavor1 : mapFlavors) {
                if (mapFlavor1.equals(flavor)) return true;
            }
            return false;
        }

        public Object getTransferData( DataFlavor flavor )
        throws UnsupportedFlavorException, IOException
        {
            if( flavor.equals( mapFlavor )) {
                return this;
            }
            throw new UnsupportedFlavorException( flavor );
        }
    }

    @SuppressWarnings("serial")
    private class MappingRenderer
            extends JComponent
            implements TableCellRenderer {

        private Paint pntFilled = pntMapNormal;
        private String value    = null;

        MappingRenderer() {
            super();
            setOpaque(true);
            setFont(AbstractApplication.getApplication().getGraphicsHandler().getFont(GraphicsHandler.FONT_SMALL));
        }

        public Component getTableCellRendererComponent(JTable table, Object v,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            pntFilled = hasFocus ? pntMapSelected : pntMapNormal;
            value = v == null ? null : v.toString();
            return this;
        }

        public void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g;

            if (value == null) {
                g2.setPaint(getBackground());
                g2.fillRect(0, 0, getWidth(), getHeight());
            } else {
                final FontMetrics fm = g2.getFontMetrics(g2.getFont());
                g2.setPaint(pntFilled);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(getForeground());
                g2.drawString(value, (getWidth() - fm.stringWidth(value)) * 0.5f, fm.getAscent());
            }
        }
    }

    @SuppressWarnings("serial")
    private class TableModel
            extends AbstractTableModel {

        private final int id;

        TableModel(int id) {
            this.id = id;
        }

        public String getColumnName(int col) {
            if (col < staticColNames.length) {
                return getResourceString(staticColNames[col]);
            } else {
                return String.valueOf(col - staticColNames.length + 1);
            }
        }

        public int getRowCount()
        {
            return tabs[id].collConfig.size();
        }

        public int getColumnCount()
        {
            return audioHwChannels[ id ] + staticColNames.length;
        }

        public Object getValueAt( int row, int col )
        {
            final Tab t = tabs[id];

            if( row > t.collConfig.size() ) return null;

            final RoutingConfig c = t.collConfig.get(row);

            switch( col ) {
            case 0:
                return c.name;
            case 1:
                return c.numChannels;
            case 2:
                return c.startAngle;
            default:
                col -= staticColNames.length;
                for( int i = 0; i < c.mapping.length; i++ ) {
                    if( c.mapping[ i ] == col ) return i + 1;
                }
                return null;
            }
        }

        public Class<?> getColumnClass( int col )
        {
            switch( col ) {
            case 0:
                return String.class;
            case 1:
                return Integer.class;
            case 2:
                return Float.class;
            default:
                return Integer.class;
            }
        }

        public boolean isCellEditable( int row, int col )
        {
            return col < staticColNames.length;
        }

        public void setValueAt(Object value, int row, int col) {
            final Tab t = tabs[id];

            if( (row > t.collConfig.size()) || (value == null) ) return;

            final RoutingConfig cfg				= t.collConfig.get(row);
            final int			oldChannels		= cfg.numChannels;
            int[]				newMapping;
            String				name;
            RoutingConfig		newCfg			= null;
            int					newChannels;
            float				newStartAngle;

            switch( col ) {
            case 0:
                name = value.toString();
                if( (name.length() > 0) &&
                    !t.setConfigName.contains(name)) {

                    newCfg = new RoutingConfig( cfg.id, name, cfg.mapping, cfg.startAngle );
                }
                break;

            case 1:
                if( value instanceof Number ) {
                    newChannels = Math.max( 0, ((Number) value).intValue() );
                } else if( value instanceof String ) {
                    try {
                        newChannels = Math.max( 0, Integer.parseInt( value.toString() ));
                    }
                    catch( NumberFormatException e1 ) {
                        break;
                    }
                } else {
                    assert false : value;
                    break;
                }
                if( newChannels < oldChannels ) {
                    newMapping = new int[ newChannels ];
                    System.arraycopy( cfg.mapping, 0, newMapping, 0, newChannels );
                } else if( newChannels > oldChannels ) {
                    newMapping = new int[ newChannels ];
                    System.arraycopy( cfg.mapping, 0, newMapping, 0, oldChannels );
                    for( int i = oldChannels, minCh = 0; i < newChannels; i++ ) {
                        chanLp:
                        for( int ch = minCh; true; ch++ ) {
                            for( int j = 0; j < i; j++ ) {
                                if( newMapping[ j ] == ch ) continue chanLp;
                            }
                            newMapping[ i ] = ch;
                            minCh = ch + 1;
                            break;
                        }
                    }
                } else break;

                newCfg = new RoutingConfig( cfg.id, cfg.name, newMapping, cfg.startAngle );
                break;

            case 2:
                if( value instanceof Number ) {
                    newStartAngle = Math.max( -360f, Math.min( 360f, ((Number) value).floatValue() ));
                } else if( value instanceof String ) {
                    try {
                        newStartAngle = Math.max( -360f, Math.min( 360f, Float.parseFloat( value.toString() )));
                    }
                    catch( NumberFormatException e1 ) {
                        break;
                    }
                } else {
                    assert false : value;
                    break;
                }
                if( newStartAngle != cfg.startAngle ) {
                    newCfg = new RoutingConfig( cfg.id, cfg.name, cfg.mapping, newStartAngle );
                }
                break;

            default:
                // set by changing numChannels and drag+drop
                break;
            }

            if (newCfg != null) {
                t.collConfig.set(row, newCfg);
                t.setConfigName .remove(cfg.name);
                t.setConfigName .add(newCfg.name);
                t.setDirtyConfig.add(newCfg.id);
            }
            if (col <= 2) fireTableRowsUpdated(row, row);    // updates sorting!
        }
    }
}