/*
 *  EditChangeAudioFileDescr.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2020 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.eisenkraut.edit;

import de.sciss.app.BasicUndoableEdit;
import de.sciss.app.PerformableEdit;

import de.sciss.io.AudioFileDescr;

@SuppressWarnings("serial")
public class EditChangeAudioFileDescr extends BasicUndoableEdit {

    private Object                  source;
    private final AudioFileDescr    afd;
    private final String            key;
    private final Object            oldValue, newValue;
    private final String            name;

    public EditChangeAudioFileDescr(Object source, AudioFileDescr afd, String key, Object value, String name) {
        super();
        this.source = source;
        this.afd    = afd;
        this.key    = key;
        newValue    = value;
        oldValue    = afd.getProperty(key);
        this.name   = name;
    }

    public EditChangeAudioFileDescr(Object source, AudioFileDescr afd, String key, Object value) {
        this(source, afd, key, value, null);
    }

    public PerformableEdit perform() {
        afd.setProperty(source, key, newValue);
        source = this;
        return this;
    }

    public void undo() {
        super.undo();
        afd.setProperty(source, key, oldValue);
    }

    public void redo() {
        super.redo();
        perform();
    }

    public String getPresentationName() {
        return name == null ? getResourceString("editChangeAFD") : name;
    }
}