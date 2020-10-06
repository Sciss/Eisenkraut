/*
 *  RenderPlugIn.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2020 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.eisenkraut.render;

import java.io.IOException;
import java.util.prefs.Preferences;

import javax.swing.JComponent;

import de.sciss.util.Disposable;

public interface RenderPlugIn extends Disposable {
    public static final int POLICY_BYPASS	= 0;
    public static final int POLICY_CLEAR	= 1;
    public static final int POLICY_MODIFY	= 2;

    public String getName();
    public boolean hasUserParameters();
    public boolean shouldDisplayParameters();
    /**
     * @return Either of POLICY_BYPASS, POLICY_CLEAR, or POLICY_MODIFY
     */
    public int getAudioPolicy();
    /**
     *	@return	Either of POLICY_BYPASS, or POLICY_MODIFY
     */
    public int getUnselectedAudioPolicy();
    /**
     *	@return Either of POLICY_BYPASS, POLICY_CLEAR, or POLICY_MODIFY
     */
    public int getMarkerPolicy();
    /**
     *	@return Either of POLICY_BYPASS or POLICY_MODIFY
     */
    public int getLengthPolicy();

    /**
     *  This gets called right after the
     *  instantiation of a new render module
     *
     *  @param	prefs	the plug-in's preferences
     */
    public void init(Preferences prefs);

    public void dispose();

    /**
     *  Asks the plugin to return a component
     *  suitable for presenting to the user
     *  in order to allow parameter adjustments
     *  The provided PlugInContext can be used
     *  to query the number of receivers, transmitters
     *  etc. Since the user might change the
     *  transmitter or receiver collection etc.
     *  before the actual start of the plugin processing,
     *  this method might be called several times,
     *  asking the plugin to re-adapt to the new
     *  values in the context.
     *
     *	@param	context		the context which may serve as
     *						a hint on how to display the GUI.
     *	@return	a component containing the plug-in specific
     *			GUI elements which will be attached to the
     *			host frame.
     */
    public JComponent getSettingsView( RenderContext context );

    /**
     *  Begins the rendering. If the parameters are
     *  not workable for the module, it should throw
     *  or set an Exception or warnings and return false.
     *  It shall return true on success. It can make
     *  adjustments to the RenderContext by setting options
     *  like KEY_TARGET_RATE, KEY_MIN_BLOCK_SIZE etc. which
     *  will be read out by the host. Though access to
     *  Session is provided through the init() method,
     *  the render module should only use the fields provided
     *  by the context, such as getSourceRate(), getReceivers()
     *  etc. It needn't deal with door locking which is
     *  provided by the host.
     *
     *	@param	source	render source. the plug-in should
     *					check the requests related to the data
     *					it wishes to receive.
     *	@return	<code>false</code> if an error occurs
     *			and rendering should be aborted
     *
     *	@throws	IOException	if a read/write error occurs
     */
    public boolean producerBegin( RenderSource source )
    throws IOException;

    /**
     *  Renders some output from the provided block of
     *  sense data. Options like target sample-rate or
     *  block size are considered to be set in beginRender()
     *  and thus it's not guaranteed that the host check
     *  a modification of these values. The module should
     *  invoke host.setProgression() if possible allowing
     *  the user to predict the duration of the rendering.
     *
     *	@param	source	render source containing the current
     *					data  block
     *	@return	<code>false</code> if an error occurs
     *			and rendering should be aborted
     *
     *	@throws	IOException	if a read/write error occurs
     */
    public boolean producerRender( RenderSource source )
    throws IOException;

    /**
     *  Allows the render module to perform any necessary
     *  finishing activities like closing files or
     *  normalizing output.
     *
     *	@param	source	render source
     *	@return	<code>false</code> if an error occurs
     *			and rendering should be aborted
     *
     *	@throws	IOException	if a read/write error occurs
     */
    public boolean producerFinish( RenderSource source )
    throws IOException;

    /**
     *  Tells the module that the rendering was aborted.
     *  The module should perform any necessary cleanups
     *  and return as soon as possible.
     *
     *	@param	source	render source
     *
     *	@throws	IOException	if a read/write error occurs
     */
    public void producerCancel( RenderSource source )
    throws IOException;
}