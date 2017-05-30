/*
 *  EditDeleteFileWhenDying.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.eisenkraut.edit;

import java.io.File;
import java.io.IOException;

import de.sciss.app.BasicUndoableEdit;
import de.sciss.app.PerformableEdit;

import de.sciss.io.InterleavedStreamFile;

/**
 *  An <code>UndoableEdit</code> that
 *  closes and deletes an <code>InterleavedStreamFile</code> (or <code>AudioFile</code>)
 *  when it's <code>die()</code> method is called.
 *  Alternatively, using the constructor with a plain <code>File</code>
 *  object will simply delete that file.
 *  Useful for undoing recordings for example.
 */
@SuppressWarnings("serial")
public class EditDeleteFileWhenDying extends BasicUndoableEdit {

    private final InterleavedStreamFile isf;
    private final File                  f;

    /**
     * @param isf the file to close and delete when <code>die()</code> is called
     */
    public EditDeleteFileWhenDying(InterleavedStreamFile isf) {
        super();
        this.isf = isf;
        f = isf.getFile();
    }

    /**
     * @param f the file to delete when <code>die()</code> is called
     */
    public EditDeleteFileWhenDying(File f) {
        super();
        isf = null;
        this.f = f;
    }

    public PerformableEdit perform() {
        return this;
    }

    public void die() {
        if (isf != null) {
            try {
                isf.close();
            } catch (IOException e1) {
                System.err.println("File not closed : " + f.getAbsolutePath());
            }
        }
        if (f != null) {
            if (!f.delete()) {
                System.err.println("File not deleted : " + f.getAbsolutePath());
            }
        }
        super.die();
    }
}