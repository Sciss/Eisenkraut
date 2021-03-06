/*
 *  RenderConsumer.java
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

/**
 *	Classes implementing this interface
 *	state that they can consume rendered
 *	and transformed streaming data. Its
 *	just like <code>RenderPlugIn</code> but
 *	from the reverse perspective. However
 *	unlike <code>RenderPlugIn</code>, the
 *	consumer cannot specify itself which
 *	data it wishes to receive (pull)
 *	but is provided with a pre-configured
 *	source object and requests (push).
 */
public interface RenderConsumer
{
    /**
     *	Initiates the consumption.
     *	The consumer should check the source's
     *	request fields to find out which
     *	data is to be written out.
     *
     *	@param	source	render source featuring
     *					the target requests
     *	@return	<code>false</code> if an error occurs
     *			and consumption should be aborted
     *
     *	@throws	IOException	if a read/write error occurs
     */
    public boolean consumerBegin( RenderSource source )
    throws IOException;

    /**
     *	Requests the consumer to consume a block of rendered data.
     *
     *	@param	source	render source featuring
     *					the target requests and the rendered data block.
     *	@return	<code>false</code> if an error occurs
     *			and consumption should be aborted
     *
     *	@throws	IOException	if a read/write error occurs
     */
    public boolean consumerRender( RenderSource source )
    throws IOException;

    /**
     *	Tells the consumer to finish consumption.
     *	i.e. close files, end compound edits etc.
     *
     *	@param	source	render source
     *	@return	<code>false</code> if an error occurs
     *			and consumption should be aborted
     *
     *	@throws	IOException	if a read/write error occurs
     */
    public boolean consumerFinish( RenderSource source )
    throws IOException;

    /**
     *	Tells the consumer that the rendering was
     *	aborted and it should cancel any unfinished edits.
     *
     *	@param	source	render source
     *
     *	@throws	IOException	if a read/write error occurs
     */
    public void consumerCancel( RenderSource source )
    throws IOException;
}
