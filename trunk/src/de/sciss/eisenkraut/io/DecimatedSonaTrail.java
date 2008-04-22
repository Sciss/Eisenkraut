/*
 *  DecimatedSonaTrail.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2008 Hanns Holger Rutz. All rights reserved.
 *
 *	This software is free software; you can redistribute it and/or
 *	modify it under the terms of the GNU General Public License
 *	as published by the Free Software Foundation; either
 *	version 2, june 1991 of the License, or (at your option) any later version.
 *
 *	This software is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *	General Public License for more details.
 *
 *	You should have received a copy of the GNU General Public
 *	License (gpl.txt) along with this software; if not, write to the Free Software
 *	Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 *
 *
 *  Changelog:
 *		18-Feb-08	created
 *		15-Apr-08	extracted back from DecimatedWaveTrail
 */

package de.sciss.eisenkraut.io;

import java.awt.Graphics2D;
//import java.awt.Paint;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
//import java.awt.TexturePaint;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
//import java.awt.image.ColorModel;
//import java.awt.image.MemoryImageSource;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Vector;
import java.util.prefs.Preferences;

import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;

import de.sciss.app.AbstractApplication;
import de.sciss.app.AbstractCompoundEdit;
import de.sciss.eisenkraut.gui.Axis;
import de.sciss.eisenkraut.gui.WaveformView;
import de.sciss.eisenkraut.math.ConstQ;
import de.sciss.eisenkraut.math.MathUtil;
import de.sciss.eisenkraut.util.PrefsUtil;
import de.sciss.gui.VectorSpace;
import de.sciss.io.AudioFile;
import de.sciss.io.CacheManager;
import de.sciss.io.IOUtil;
import de.sciss.io.Span;
import de.sciss.util.MutableInt;

/**
 * 	Sonagram trail with automatic handling of subsampled versions.
 * 	This class is dedicated to the memory of Niklas Werner, a
 * 	former fellow student of mine. The sonagram colour table is
 * 	taken from his work sonasound (http://sonasound.sourceforge.net/)
 * 
 *	@version	0.70, 15-Apr-08
 *	@author		Hanns Holger Rutz
 *
 *	@todo		editing (addAllDep)
 *	@todo		delay compensation (fftSize data seems to be missing,
 *				the necessary pre-delay seems to be fftSize / 2)
 *	@todo		cache management
 *	@todo		cost-calculation of potential sliding DFT
 *	@todo		there's an efficient multi-channel FFT?
 *	@todo		the AsyncEvent should have Span info so DocumentFrame
 *				doesn't always need to repaint
 */
public class DecimatedSonaTrail
extends DecimatedTrail
{
	private static final int		UPDATE_PERIOD			= 4000; // millisecs in async overview calculation

	protected final Decimator		decimator;
	
	protected int					fftSize; //				= 1024;
	protected final int				stepSize;
//	protected final float[] 		inpWin;
	
	protected final ConstQ			constQ;
	protected final int				numKernels;
	protected final float[]			filterBuf;
	
	private static final int[] 		colors = {  // from niklas werner's sonasound!
		0x000000, 0x050101, 0x090203, 0x0E0304, 0x120406, 0x160507, 0x1A0608, 0x1D0609,
		0x20070A, 0x23080B, 0x25080C, 0x27090D, 0x290A0D, 0x2B0A0E, 0x2D0B0F, 0x2E0B0F,
		0x300C10, 0x310C10, 0x320C10, 0x320D11, 0x330D11, 0x340D11, 0x340D11, 0x350E12,
		0x350E12, 0x360E12, 0x360E12, 0x360E12, 0x360F13, 0x370F13, 0x370F13, 0x370F13,
		0x381014, 0x381014, 0x381014, 0x391014, 0x391015, 0x3A1115, 0x3A1115, 0x3B1115,
		0x3B1116, 0x3C1216, 0x3D1216, 0x3D1217, 0x3E1217, 0x3E1317, 0x3F1317, 0x3F1317,
		0x401418, 0x401418, 0x401418, 0x401418, 0x411518, 0x411518, 0x411518, 0x411517,
		0x421617, 0x421617, 0x421617, 0x421617, 0x421717, 0x431717, 0x431717, 0x431717,
		0x441818, 0x441818, 0x441818, 0x451818, 0x451818, 0x461919, 0x461919, 0x471919,
		0x471919, 0x481A1A, 0x481A1A, 0x491A1A, 0x4A1A1B, 0x4A1B1B, 0x4B1B1B, 0x4B1B1B,
		0x4C1C1C, 0x4C1C1C, 0x4C1C1C, 0x4D1C1C, 0x4D1D1C, 0x4D1D1C, 0x4D1D1C, 0x4E1D1C,
		0x4E1E1C, 0x4E1E1C, 0x4E1E1C, 0x4F1E1B, 0x4F1F1B, 0x4F1F1B, 0x4F1F1B, 0x4F1F1B,
		0x50201C, 0x50201C, 0x50201C, 0x50201C, 0x50201C, 0x51211C, 0x51211C, 0x51211D,
		0x51211D, 0x52221D, 0x52221E, 0x52221E, 0x52221E, 0x53231F, 0x53231F, 0x53231F,
		0x542420, 0x542420, 0x542420, 0x542420, 0x552521, 0x552521, 0x552521, 0x552522,
		0x562622, 0x562622, 0x562622, 0x562623, 0x572723, 0x572723, 0x572723, 0x572723,
		0x582824, 0x582824, 0x582824, 0x582824, 0x582824, 0x592924, 0x592924, 0x592925,
		0x592925, 0x5A2A25, 0x5A2A25, 0x5A2A25, 0x5B2A26, 0x5B2B26, 0x5B2B27, 0x5B2B27,
		0x5C2C28, 0x5C2C28, 0x5C2C29, 0x5C2C29, 0x5C2D2A, 0x5D2D2B, 0x5D2D2C, 0x5D2D2C,
		0x5D2E2D, 0x5E2E2E, 0x5E2E2F, 0x5E2E30, 0x5E2F30, 0x5F2F31, 0x5F2F32, 0x5F2F33,
		0x603034, 0x603034, 0x603035, 0x603035, 0x613036, 0x613136, 0x613137, 0x613137,
		0x623138, 0x623238, 0x623239, 0x623239, 0x63323A, 0x63333A, 0x63333B, 0x63333B,
		0x64343C, 0x64343C, 0x64343C, 0x64343D, 0x64353D, 0x65353E, 0x65353E, 0x65353F,
		0x65363F, 0x653640, 0x663641, 0x663641, 0x663742, 0x673742, 0x673743, 0x673743,
		0x683844, 0x683844, 0x683844, 0x693845, 0x693845, 0x6A3946, 0x6A3946, 0x6B3947,
		0x6B3947, 0x6C3A48, 0x6C3A48, 0x6D3A49, 0x6D3A49, 0x6E3B4A, 0x6E3B4A, 0x6F3B4B,
		0x703C4C, 0x703C4C, 0x713C4D, 0x713C4E, 0x723D4E, 0x723D4F, 0x733D50, 0x733E51,
		0x743E52, 0x743E52, 0x753E53, 0x753F54, 0x763F55, 0x763F55, 0x773F56, 0x773F57,
		0x784058, 0x784058, 0x784059, 0x784059, 0x79405A, 0x79405A, 0x79405B, 0x79405B,
		0x7A405B, 0x7A405C, 0x7A405C, 0x7A405D, 0x7A405D, 0x7B405E, 0x7B405E, 0x7B405F,
		0x7C4060, 0x7C3F60, 0x7C3F61, 0x7D3F62, 0x7D3F62, 0x7D3F63, 0x7E3F64, 0x7E4065,
		0x7F4066, 0x7F4067, 0x804067, 0x804068, 0x814069, 0x81406A, 0x82406A, 0x82406B,
		0x83406C, 0x833F6C, 0x833F6C, 0x843F6D, 0x843F6D, 0x853F6D, 0x853F6E, 0x863F6E,
		0x863F6E, 0x873F6E, 0x873F6E, 0x883F6E, 0x883F6F, 0x893F6F, 0x893F6F, 0x8A3F6F,
		0x8B4070, 0x8B4070, 0x8C4070, 0x8C4070, 0x8D4071, 0x8D4171, 0x8E4172, 0x8E4172,
		0x8F4173, 0x8F4273, 0x904274, 0x904274, 0x914375, 0x914376, 0x924376, 0x924377,
		0x934478, 0x934478, 0x934479, 0x944479, 0x94447A, 0x94447B, 0x94447B, 0x95447C,
		0x95447D, 0x95447D, 0x95447E, 0x95447F, 0x964480, 0x964480, 0x964481, 0x964482,
		0x974483, 0x974383, 0x974384, 0x974385, 0x974386, 0x984386, 0x984387, 0x984388,
		0x984389, 0x99438A, 0x99438A, 0x99438B, 0x99438C, 0x9A438D, 0x9A438D, 0x9A438E,
		0x9B448F, 0x9B448F, 0x9B4490, 0x9B4490, 0x9C4491, 0x9C4491, 0x9C4492, 0x9C4492,
		0x9D4493, 0x9D4493, 0x9D4493, 0x9D4494, 0x9E4494, 0x9E4495, 0x9E4495, 0x9E4496,
		0x9F4497, 0x9F4397, 0x9F4398, 0x9F4398, 0xA04399, 0xA0439A, 0xA0439B, 0xA0439B,
		0xA1439C, 0xA1439D, 0xA1439E, 0xA1439F, 0xA2439F, 0xA243A0, 0xA243A1, 0xA243A2,
		0xA344A3, 0xA344A3, 0xA344A4, 0xA344A5, 0xA344A6, 0xA444A6, 0xA445A7, 0xA445A8,
		0xA445A9, 0xA545A9, 0xA546AA, 0xA546AB, 0xA546AB, 0xA647AC, 0xA647AD, 0xA647AE,
		0xA748AF, 0xA748AF, 0xA748B0, 0xA748B1, 0xA849B2, 0xA849B2, 0xA849B3, 0xA949B4,
		0xA94AB5, 0xA94AB6, 0xA94AB6, 0xAA4AB7, 0xAA4BB8, 0xAA4BB8, 0xAA4BB9, 0xAA4BBA,
		0xAB4CBB, 0xAB4CBB, 0xAB4CBC, 0xAB4CBC, 0xAB4CBD, 0xAB4DBD, 0xAB4DBE, 0xAB4DBE,
		0xAB4DBF, 0xAB4EBF, 0xAB4EC0, 0xAB4EC0, 0xAB4EC1, 0xAB4FC1, 0xAB4FC2, 0xAB4FC2,
		0xAB50C3, 0xAA50C3, 0xAA50C3, 0xAA50C4, 0xAA51C4, 0xAA51C5, 0xAA51C5, 0xAA51C6,
		0xAA52C6, 0xAA52C7, 0xAA52C7, 0xAA52C8, 0xAA53C8, 0xAA53C9, 0xAA53C9, 0xAA53CA,
		0xAB54CB, 0xAB54CB, 0xAB54CC, 0xAB54CC, 0xAB54CD, 0xAC55CD, 0xAC55CE, 0xAC55CE,
		0xAC55CF, 0xAD56CF, 0xAD56D0, 0xAD56D0, 0xAE56D1, 0xAE57D1, 0xAE57D2, 0xAE57D2,
		0xAF58D3, 0xAF58D3, 0xAF58D3, 0xAF58D4, 0xAF59D4, 0xAF59D4, 0xAF59D5, 0xAF59D5,
		0xAF5AD5, 0xAF5AD5, 0xAF5AD6, 0xAF5AD6, 0xAF5BD6, 0xAF5BD6, 0xAF5BD6, 0xAF5BD6,
		0xAF5CD7, 0xAE5CD7, 0xAE5CD7, 0xAE5CD7, 0xAD5CD7, 0xAD5DD7, 0xAD5DD6, 0xAD5DD6,
		0xAC5DD6, 0xAC5ED6, 0xAC5ED6, 0xAB5ED6, 0xAB5ED6, 0xAB5FD6, 0xAB5FD6, 0xAB5FD6,
		0xAB60D7, 0xAA60D7, 0xAA60D7, 0xAA60D7, 0xAA61D7, 0xAA61D7, 0xAA61D8, 0xAA61D8,
		0xAB62D8, 0xAB62D8, 0xAB62D9, 0xAB62D9, 0xAB63D9, 0xAB63DA, 0xAB63DA, 0xAB63DA,
		0xAB64DB, 0xAA64DB, 0xAA64DB, 0xAA64DB, 0xAA64DC, 0xA965DC, 0xA965DC, 0xA965DC,
		0xA965DC, 0xA865DD, 0xA866DD, 0xA866DD, 0xA766DD, 0xA766DE, 0xA767DE, 0xA767DE,
		0xA768DF, 0xA668DF, 0xA668DF, 0xA669DF, 0xA669E0, 0xA66AE0, 0xA66AE0, 0xA66BE1,
		0xA66BE1, 0xA66CE1, 0xA66CE1, 0xA76DE2, 0xA76DE2, 0xA76EE2, 0xA76EE2, 0xA76FE2,
		0xA770E3, 0xA670E3, 0xA671E3, 0xA671E3, 0xA672E3, 0xA672E3, 0xA573E2, 0xA573E2,
		0xA574E2, 0xA574E2, 0xA475E2, 0xA475E2, 0xA476E2, 0xA376E2, 0xA377E2, 0xA377E2,
		0xA378E3, 0xA278E3, 0xA279E3, 0xA279E3, 0xA17AE3, 0xA17AE4, 0xA17BE4, 0xA17BE4,
		0xA07CE5, 0xA07CE5, 0xA07DE5, 0xA07DE5, 0x9F7EE6, 0x9F7EE6, 0x9F7FE6, 0x9F7FE6,
		0x9F80E7, 0x9E80E7, 0x9E80E7, 0x9E81E7, 0x9E81E7, 0x9D82E7, 0x9D82E7, 0x9D83E7,
		0x9D83E6, 0x9C83E6, 0x9C84E6, 0x9C84E6, 0x9C85E6, 0x9B85E6, 0x9B86E6, 0x9B86E6,
		0x9B87E7, 0x9A87E7, 0x9A87E7, 0x9A88E7, 0x9988E7, 0x9989E8, 0x9989E8, 0x988AE8,
		0x988AE9, 0x988BE9, 0x988BE9, 0x978CEA, 0x978CEA, 0x978DEA, 0x978DEA, 0x978EEA,
		0x978FEB, 0x968FEB, 0x9690EA, 0x9690EA, 0x9691EA, 0x9691EA, 0x9692EA, 0x9692EA,
		0x9693E9, 0x9693E9, 0x9694E9, 0x9694E8, 0x9695E8, 0x9695E8, 0x9696E7, 0x9696E7,
		0x9797E7, 0x9697E6, 0x9697E6, 0x9698E6, 0x9698E5, 0x9699E5, 0x9699E5, 0x969AE5,
		0x969AE4, 0x969BE4, 0x969BE4, 0x969CE4, 0x969CE3, 0x969DE3, 0x969DE3, 0x969EE3,
		0x979FE3, 0x979FE2, 0x97A0E2, 0x97A0E2, 0x97A1E1, 0x98A1E1, 0x98A2E1, 0x98A2E1,
		0x98A3E0, 0x99A3E0, 0x99A4E0, 0x99A4E0, 0x9AA5DF, 0x9AA5DF, 0x9AA6DF, 0x9AA6DF,
		0x9BA7DF, 0x9BA7DE, 0x9BA7DE, 0x9BA7DE, 0x9BA8DE, 0x9BA8DE, 0x9BA8DD, 0x9BA8DD,
		0x9BA8DD, 0x9BA9DD, 0x9BA9DC, 0x9BA9DC, 0x9BA9DC, 0x9BAADC, 0x9BAADB, 0x9BAADB,
		0x9BABDB, 0x9AABDA, 0x9AABDA, 0x9AACD9, 0x9AACD9, 0x99ADD8, 0x99ADD8, 0x99AED7,
		0x99AED7, 0x98AFD6, 0x98AFD5, 0x98B0D5, 0x98B0D4, 0x97B1D4, 0x97B1D3, 0x97B2D3,
		0x97B3D3, 0x96B3D2, 0x96B4D2, 0x96B4D1, 0x95B5D1, 0x95B5D1, 0x95B6D1, 0x95B6D0,
		0x94B7D0, 0x94B7D0, 0x94B7D0, 0x94B8CF, 0x93B8CF, 0x93B9CF, 0x93B9CF, 0x93BACF,
		0x93BBCF, 0x92BBCE, 0x92BCCE, 0x92BCCE, 0x91BDCE, 0x91BDCD, 0x91BECD, 0x91BECD,
		0x90BFCD, 0x90BFCC, 0x90C0CC, 0x90C0CC, 0x8FC1CC, 0x8FC1CB, 0x8FC2CB, 0x8FC2CB,
		0x8FC3CB, 0x8EC3CA, 0x8EC3CA, 0x8EC4CA, 0x8EC4C9, 0x8DC4C9, 0x8DC4C9, 0x8DC5C9,
		0x8DC5C8, 0x8CC5C8, 0x8CC5C8, 0x8CC5C8, 0x8CC6C7, 0x8BC6C7, 0x8BC6C7, 0x8BC6C7,
		0x8BC7C7, 0x8AC7C6, 0x8AC7C6, 0x8AC7C6, 0x89C7C5, 0x89C8C5, 0x89C8C5, 0x88C8C5,
		0x88C8C4, 0x88C9C4, 0x88C9C4, 0x87C9C4, 0x87C9C3, 0x87CAC3, 0x87CAC3, 0x87CAC3,
		0x87CBC3, 0x86CBC2, 0x86CBC2, 0x86CBC2, 0x86CCC2, 0x86CCC1, 0x86CCC1, 0x87CCC1,
		0x87CDC1, 0x87CDC0, 0x87CDC0, 0x87CDC0, 0x87CEC0, 0x87CEBF, 0x87CEBF, 0x87CEBF,
		0x87CFBF, 0x86CFBE, 0x86CFBE, 0x86CFBE, 0x86CFBD, 0x85D0BD, 0x85D0BD, 0x85D0BC,
		0x85D0BC, 0x84D0BC, 0x84D1BC, 0x84D1BB, 0x83D1BB, 0x83D2BB, 0x83D2BB, 0x83D2BB,
		0x83D3BB, 0x82D3BA, 0x82D3BA, 0x82D4BA, 0x82D4BA, 0x82D5BA, 0x82D5BA, 0x82D6BA,
		0x82D6BA, 0x82D7B9, 0x82D7B9, 0x82D8B9, 0x82D8B9, 0x82D9B8, 0x82D9B8, 0x82DAB7,
		0x83DBB7, 0x83DBB6, 0x83DCB5, 0x83DCB4, 0x83DDB3, 0x83DDB2, 0x84DEB1, 0x84DEB0,
		0x84DFAF, 0x84DFAE, 0x84E0AD, 0x85E0AB, 0x85E1AA, 0x85E1A9, 0x86E2A8, 0x86E2A7,
		0x87E3A7, 0x87E3A6, 0x87E3A5, 0x88E3A4, 0x88E4A4, 0x89E4A3, 0x89E4A3, 0x8AE5A2,
		0x8AE5A2, 0x8BE5A1, 0x8BE5A1, 0x8CE5A0, 0x8CE6A0, 0x8DE6A0, 0x8DE69F, 0x8EE69F,
		0x8FE79F, 0x8FE79E, 0x90E79E, 0x90E79D, 0x91E79D, 0x91E89C, 0x92E89C, 0x92E89B,
		0x93E89B, 0x93E99A, 0x94E99A, 0x94E999, 0x94E999, 0x95EA98, 0x95EA98, 0x96EA97,
		0x97EB97, 0x97EB96, 0x98EB95, 0x98EB95, 0x99EC94, 0x99EC94, 0x9AEC93, 0x9AEC93,
		0x9BED92, 0x9BED92, 0x9CED91, 0x9CED91, 0x9DEE90, 0x9DEE90, 0x9EEE8F, 0x9EEE8F,
		0x9FEF8F, 0x9FEF8E, 0x9FEF8E, 0xA0EF8D, 0xA0F08D, 0xA1F08C, 0xA1F08C, 0xA1F08B,
		0xA2F18B, 0xA2F18A, 0xA2F18A, 0xA3F189, 0xA3F288, 0xA3F288, 0xA4F287, 0xA4F287,
		0xA5F387, 0xA5F386, 0xA5F386, 0xA6F385, 0xA6F385, 0xA6F484, 0xA7F484, 0xA7F483,
		0xA7F483, 0xA8F582, 0xA8F582, 0xA9F581, 0xA9F581, 0xA9F681, 0xAAF680, 0xAAF680,
		0xABF780, 0xABF77F, 0xABF77F, 0xACF77F, 0xACF87E, 0xADF87E, 0xADF87E, 0xADF97E,
		0xAEF97D, 0xAEF97D, 0xAFF97D, 0xAFFA7D, 0xB0FA7C, 0xB1FA7C, 0xB1FA7C, 0xB2FA7C,
		0xB3FB7C, 0xB3FB7B, 0xB4FB7B, 0xB5FB7B, 0xB5FB7A, 0xB6FB7A, 0xB7FB7A, 0xB8FB7A,
		0xB9FB79, 0xB9FB79, 0xBAFB79, 0xBBFB79, 0xBCFB78, 0xBCFB78, 0xBDFB78, 0xBEFB78,
		0xBFFB78, 0xBFFA77, 0xC0FA77, 0xC0FA77, 0xC1FA77, 0xC1FA77, 0xC2FA77, 0xC2FA77,
		0xC3FA77, 0xC3FA77, 0xC4FA77, 0xC4FA77, 0xC4FA77, 0xC5FA77, 0xC5FA77, 0xC6FA77,
		0xC7FB78, 0xC7FB78, 0xC8FB78, 0xC8FB78, 0xC9FB78, 0xCAFB79, 0xCAFB79, 0xCBFB79,
		0xCCFB7A, 0xCDFB7A, 0xCEFB7A, 0xCEFB7A, 0xCFFB7B, 0xD0FB7B, 0xD1FB7B, 0xD2FB7B,
		0xD3FB7C, 0xD3FA7C, 0xD4FA7C, 0xD5FA7C, 0xD6FA7C, 0xD7FA7C, 0xD7FA7C, 0xD8FA7C,
		0xD9FA7D, 0xDAFA7D, 0xDAFA7D, 0xDBFA7D, 0xDCFA7E, 0xDDFA7E, 0xDDFA7E, 0xDEFA7F,
		0xDFFB80, 0xDFFB80, 0xE0FB81, 0xE0FB82, 0xE1FB82, 0xE1FB83, 0xE2FB84, 0xE2FB85,
		0xE3FB86, 0xE3FB87, 0xE4FB88, 0xE4FB89, 0xE5FB8A, 0xE5FB8B, 0xE6FB8C, 0xE6FB8E,
		0xE7FB8F, 0xE7FA8F, 0xE8FA90, 0xE8FA91, 0xE9FA92, 0xE9FA93, 0xEAFA94, 0xEAFA95,
		0xEBFA95, 0xEBFA96, 0xECFA97, 0xECFA97, 0xEDFA98, 0xEDFA99, 0xEEFB99, 0xEEFB9A,
		0xEFFB9B, 0xEFFA9B, 0xEFFA9C, 0xF0FA9C, 0xF0FA9D, 0xF0FA9D, 0xF0FA9E, 0xF1FA9E,
		0xF1FA9F, 0xF1FA9F, 0xF1FAA0, 0xF2FAA0, 0xF2FAA1, 0xF2FAA1, 0xF2FAA2, 0xF2FAA2,
		0xF3FBA3, 0xF3FBA3, 0xF3FBA3, 0xF3FBA4, 0xF3FBA5, 0xF4FBA5, 0xF4FBA6, 0xF4FBA6,
		0xF4FBA7, 0xF5FBA8, 0xF5FBA8, 0xF5FBA9, 0xF5FBAA, 0xF6FBAB, 0xF6FBAC, 0xF6FBAD, 
		0xF7FBAF, 0xF7FAB0, 0xF7FAB1, 0xF7FAB3, 0xF8FAB4, 0xF8FAB6, 0xF8FAB7, 0xF9FAB9,
		0xF9FABA, 0xF9FABC, 0xF9FABE, 0xFAFABF, 0xFAFAC1, 0xFAFAC2, 0xFAFAC4, 0xFAFAC5,
		0xFBFBC7, 0xFBFBC8, 0xFBFBC9, 0xFBFBCA, 0xFBFBCB, 0xFBFBCC, 0xFBFBCE, 0xFBFBCF,
		0xFBFBD0, 0xFBFBD1, 0xFBFBD2, 0xFBFBD3, 0xFBFBD5, 0xFBFBD6, 0xFBFBD7, 0xFBFBD9,
		0xFBFBDB, 0xFAFADC, 0xFAFADE, 0xFAFAE0, 0xFAFAE2, 0xFAFAE4, 0xFAFAE6, 0xFAFAE8,
		0xFAFAEA, 0xFAFAED, 0xFAFAEF, 0xFAFAF1, 0xFAFAF3, 0xFAFAF5, 0xFAFAF7, 0xFAFAF9,
		0xFBFBFB, 0xFBFBFC, 0xFBFBFE, 0xFBFBFF, 0xFBFBFF, 0xFBFBFF, 0xFBFBFF, 0xFBFBFF,
		0xFBFBFF, 0xFCFCFF, 0xFCFCFF, 0xFCFCFF, 0xFCFCFF, 0xFDFDFF, 0xFDFDFF, 0xFDFDFF,
		0xFEFEFE
	};

	public DecimatedSonaTrail( AudioTrail fullScale, int model /*, int[] decimations */ )
	throws IOException
	{
		super();

		switch( model ) {
		case MODEL_SONA:
			decimator		= new SonaDecimator();
			break;
		default:
			throw new IllegalArgumentException( "Model " + model );
		}

		this.fullScale	= fullScale;
		this.model		= model;

		constQ			= new ConstQ();
		
		final Preferences cqPrefs = AbstractApplication.getApplication().getUserPrefs().node( PrefsUtil.NODE_VIEW ).node( PrefsUtil.NODE_SONAGRAM );
		constQ.readPrefs( cqPrefs );
		constQ.setSampleRate( fullScale.getRate() );
		System.out.println( "Creating ConstQ Kernels..." );
		constQ.createKernels();
		numKernels		= constQ.getNumKernels();
		filterBuf		= new float[ numKernels ];
		fftSize = constQ.getFFTSize();
		modelChannels = constQ.getNumKernels();
		System.out.println( "...done." );
		
//		numMag			= fftSize >> 1;
//		stepSize		= Math.min( fftSize, 256 );
		// approx. 5 milliseconds resolution (for the high freqs)
//		stepSize		= Math.max( 64, Math.min( fftSize, (int) (0.005 * fullScale.getRate() + 0.5) & ~1 ));
		stepSize		= Math.max( 64, Math.min( fftSize, MathUtil.nextPowerOfTwo( (int) (0.005 * fullScale.getRate() + 0.5) )));

		int decimKorr, j;
		for( decimKorr = 1, j = stepSize; j > 2; decimKorr++, j >>= 1 ) ;

//		inpWin			= Filter.createFullWindow( fftSize, Filter.WIN_HANNING );
		
		fullChannels	= fullScale.getChannelNum();
		decimChannels	= fullChannels * modelChannels;

final int decimations[] = { decimKorr }; // , decimKorr + 8 };
		SUBNUM			= decimations.length; // the first 'subsample' is actually fullrate
		this.decimHelps	= new DecimationHelp[ SUBNUM ];
		for( int i = 0; i < SUBNUM; i++ ) {
			this.decimHelps[ i ] = new DecimationHelp( fullScale.getRate(), decimations[ i ]);
		}
//		MAXSHIFT		= decimations[ SUBNUM - 1 ]; // + decimKorr;
//		MAXCOARSE		= Math.max( fftSize, 1 << MAXSHIFT );
		MAXSHIFT		= decimations[ 0 ]; // + decimKorr;
		MAXCOARSE		= 1 << MAXSHIFT;
		MAXMASK			= -MAXCOARSE;
		MAXCEILADD		= MAXCOARSE - 1;

		tmpBufSize		= fftSize; // Math.max( 4096, MAXCOARSE << 1 );
		// XXX generates OutOfMemoryError ; need to use a different approach in the decimation steps
		// (should "just" use the maximum per-step decimation, which is 256 in the Session defaults)
//		tmpBufSize2		= SUBNUM > 0 ? Math.max( 4096, tmpBufSize >> (decimations[ 0 ] + decimKorr)) : tmpBufSize;
		tmpBufSize2		= 1 << decimations[ 0 ];
		for( int i = 1; i < SUBNUM; i++ ) {
			tmpBufSize2 = Math.max( tmpBufSize2, 1 << (decimations[ i ] - decimations[ i - 1 ]));
		}

		setRate( fullScale.getRate() );
		
		fullScale.addDependant( this );
		
		// ok, the fullScale file might have already been populated
		// final List stakes = fullScale.getAll( true );
		// if( !stakes.isEmpty() ) {
		// XXX TEST
		// addAllDep( null, stakes, null, fullScale.getSpan() );
		addAllDepAsync();
		// addAllDepAsync( null, stakes, null, fullScale.getSpan() );
		// }
	}

	/**
	 * @synchronization must be called in the event thread
	 */
	public void drawWaveform( DecimationInfo info, WaveformView view, Graphics2D g2 )
	{
//if( true ) return;

		final boolean			fromPCM 		= false; // info.idx == -1;
		final int				imgW			= fromPCM ?
				  Math.min( tmpBufSize, tmpBufSize2 * info.getDecimationFactor() )
				: tmpBufSize2; //  << info.shift;
		final int				maxLen			= imgW << info.shift; // * stepSize;

//		final int				imgW			= view.getWidth(); // (int) info.sublength;
		final BufferedImage		bufImg			= new BufferedImage( imgW, modelChannels, BufferedImage.TYPE_INT_RGB );
		final WritableRaster	raster			= bufImg.getRaster();
		final int[]				data			= new int[ imgW * modelChannels ];
		final int				dataStartOff	= imgW * (modelChannels - 1);

//		final AffineTransform	atOrig			= g2.getTransform();
		final Shape				clipOrig		= g2.getClip();
		
//		float[]					chanBuf;
		long					start			= info.span.start;
		long					totalLength		= info.getTotalLength();
		Span					chunkSpan;
		long					fullLen;
//		long					fullStop;
		int						chunkLen; // decimLen;
		float					scaleX;
		Rectangle				r;
		
//		final float				scaleX			= (float) (view.getWidth() * stepSize) / totalLength;
		
final float pixScale = 1072 / (view.getAmpLogMax() - view.getAmpLogMin());
final float pixOff   = -view.getAmpLogMin();

		g2.setRenderingHint( RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR );
		
		try {
			drawBusyList.clear(); // "must be called in the event thread"

			synchronized( bufSync ) {
				createBuffers();

long screenOffX = 0;
				while( totalLength > 0 ) {
					fullLen		= Math.min( maxLen, totalLength );
					chunkLen	= (int) (fromPCM ? fullLen : decimHelps[ info.idx ].fullrateToSubsample( fullLen ));
//					decimLen	= chunkLen / info.inlineDecim;
//					chunkLen	= decimLen * info.inlineDecim;
					fullLen		= (long) chunkLen << info.shift;
//					chunkLen	= (int) (fullLen / stepSize);

//					if( fromPCM ) {
//						fullStop = fullScale.getSpan().stop;
//						if( start + fullLen <= fullStop ) {
//							chunkSpan = new Span( start, start + fullLen );
//							fullScale.readFrames( tmpBuf, 0, chunkSpan );
//						} else {
//							chunkSpan = new Span( start, fullStop );
//							fullScale.readFrames( tmpBuf, 0, chunkSpan );
//							// duplicate last frames
////							for( int i = (int) chunkSpan.getLength(), j = i - 1; i < (int) fullLen; i++ ) {
////								for( int ch = 0; ch < fullChannels; ch++ ) {
////									sPeakP		= tmpBuf[ ch ];
////									sPeakP[ i ]	= sPeakP[ j ];
////								}
////							}
//						}
//						decimator.decimatePCM( tmpBuf, tmpBuf2, fftBuf, 0, decimLen, info.inlineDecim );
//					} else {
////						chunkSpan = new Span( start, start + fullLen );
//						chunkSpan = new Span( start, start + fullLen );
//						readFrames( info.idx, tmpBuf2, 0, drawBusyList, chunkSpan, null);
//						if( info.inlineDecim > 1 ) decimator.decimate( tmpBuf2, tmpBuf2, 0, decimLen, info.inlineDecim );
//					}

					chunkSpan = new Span( start, start + fullLen );
					
//System.out.println( "chunkSpan = " + chunkSpan + "; chunkLen = " + chunkLen + "; fullLen = " + fullLen + "; screenOffX " + screenOffX + "; subLength " + info.sublength + "; shift " + info.shift + "; totalLen " + info.getTotalLength() );
					
					readFrames( info.idx, tmpBuf2, 0, drawBusyList, chunkSpan, null );
//					if( tempFAsync == null || tempFAsync[0] == null ) break;
//					tempFAsync[0].seekFrame( Math.min( start / stepSize, tempFAsync[0].getFrameNum() ));
//					int gaga = (int) Math.min( fullLen, Math.min( tmpBufSize2, tempFAsync[0].getFrameNum() - tempFAsync[0].getFramePosition() ));
//					tempFAsync[0].readFrames( tmpBuf2, 0, gaga);
					
					for( int ch = 0, tmpChReset = 0; ch < fullChannels; ch++, tmpChReset += modelChannels ) {
						r = view.rectForChannel( ch );
						scaleX = (float) r.width / info.getTotalLength();
//System.out.println( " ... for ch = " + ch + "; scaleX = " + scaleX );
						for( int x = 0, off = 0; x < chunkLen; x++ ) {
							for( int y = 0, off2 = x + dataStartOff, tmpCh = tmpChReset; y < modelChannels; y++, tmpCh++, off2 -= imgW, off++ ) {
								data[ off2 ] = colors[ Math.max( 0, Math.min( 1072, (int) ((tmpBuf2[ tmpCh ][ x ] + pixOff) * pixScale) ))];
							}
						}
						raster.setDataElements( 0, 0, imgW, modelChannels, data );
						g2.drawImage( bufImg, r.x + (int) (screenOffX * scaleX + 0.5f), r.y, r.x + (int) ((screenOffX + fullLen) * scaleX + 0.5f), r.y + r.height, 0, 0, chunkLen, modelChannels, view );
					}
					start += fullLen; // chunkLen * stepSize;
					totalLength -= fullLen; // chunkLen * stepSize;
					screenOffX += fullLen;
//					if( gaga == 0 ) totalLength = 0;
				}
			} // synchronized( bufSync )

			// System.err.println( "busyList.size() = "+busyList.size() );

			for( int ch = 0; ch < fullChannels; ch++ ) {
				r = view.rectForChannel( ch );
				g2.clipRect( r.x, r.y, r.width, r.height );
				if( !drawBusyList.isEmpty() ) {
					// g2.setColor( Color.red );
					g2.setPaint( pntBusy );
					for( int i = 0; i < drawBusyList.size(); i++ ) {
						chunkSpan = (Span) drawBusyList.get( i );
						scaleX = (float) r.width / info.getTotalLength();
						g2.fillRect( (int) ((chunkSpan.start - info.span.start) * scaleX) + r.x, r.y,
						             (int) (chunkSpan.getLength() * scaleX), r.height );
					}
				}
				// g2.setTransform(atOrig);
				g2.setClip( clipOrig );
			}
		} catch( IOException e1 ) {
			System.err.println( e1 );
		}
		if( bufImg != null ) bufImg.flush();
	}

	/**
	 * Determines which subsampled version is suitable for a given display range
	 * (the most RAM and CPU economic while maining optimal display resolution).
	 * For a given time span, the lowest resolution is chosen which will produce
	 * at least <code>minLen</code> frames.
	 * 
	 * @param tag the time span the caller is interested in
	 * @param minLen the minimum number of sampled points wanted.
	 * @return an information object describing the best subsample of the track
	 *         editor. note that info.sublength will be smaller than minLen if
	 *         tag.getLength() was smaller than minLen (in this case the
	 *         fullrate version is used).
	 */
	public DecimationInfo getBestSubsample( Span tag, int minLen )
	{
		final DecimationInfo	info;
		final boolean			fromPCM;
		final long				fullLength	= tag.getLength();
		long					subLength, n;
		int						idx, inlineDecim;

		subLength = fullLength;
//		for( idx = 0; idx < SUBNUM; idx++ ) {
//			n = decimHelps[ idx ].fullrateToSubsample( fullLength );
//			if( n < minLen ) break;
//			subLength = n;
//		}
//		idx--;
n = decimHelps[ 0 ].fullrateToSubsample( fullLength );
subLength = n;
idx=0;
		// had to change '>= minLen' to '> minLen' because minLen could be zero!
		switch( model ) {
		case MODEL_SONA:
//			for( inlineDecim = 2; subLength / inlineDecim > minLen; inlineDecim++ ) ;
//			inlineDecim--;
inlineDecim=1;
			break;

		default:
			assert false : model;
			inlineDecim = 1; // never gets here
		}
		subLength /= inlineDecim;
		// System.err.println( "minLen = "+minLen+"; subLength = "+subLength+";
		// inlineDecim = "+inlineDecim+" ; idx = "+idx );
		fromPCM	= idx == -1;
//		toPCM	= fromPCM && inlineDecim == 1;
		info	= new DecimationInfo( tag, subLength, fullChannels, idx,
						fromPCM ? 0 : decimHelps[ idx ].shift,
						inlineDecim, model );
		return info;
	}

	/**
	 * Reads a block of subsampled frames.
	 * 
	 * @param info
	 *            the <code>DecimationInfo</code> as returned by
	 *            <code>getBestSubsample</code>, describing the span to read
	 *            and which resolution to choose.
	 * @param frames
	 *            to buffer to fill, where frames[0][] corresponds to the first
	 *            channel etc. and the buffer length must be at least off +
	 *            info.sublength!
	 * @param off
	 *            offset in frames, such that the first frame will be placed in
	 *            frames[ch][off]
	 * @throws IOException
	 *             if a read error occurs
	 * @see #getBestSubsample( Span, int )
	 * @see DecimationInfo#sublength
	 */
/*
	public boolean readFrame( int sub, long pos, int ch, float[] data )
	throws IOException
	{
		synchronized( bufSync ) {
			createBuffers();

			final int				idx	= indexOf( pos, true );
			final DecimatedWaveStake	ds	= (DecimatedWaveStake) editGetLeftMost( idx, true, null );
			if( ds == null ) return false;

			if( !ds.readFrame( sub, tmpBuf2, 0, pos )) return false;

			for( int i = ch * modelChannels, k = 0; k < modelChannels; i++, k++ ) {
				data[ k ] = tmpBuf2[ i ][ 0 ];
			}

			return true;
		}
	}
*/

	/*
	 * Same as in <code>NondestructiveDecimatedSampledTrack</code> but with
	 * automaic bias adjust.
	 * 
	 * @param tag unbiased fullrate span @param frames buffer to fill. note that
	 * this will not do any interpolation but fill at the decimated rate! @param
	 * framesOff offset in frames for the first frame which is read
	 * 
	 * @synchronization	caller must have bufSync !
	 */
	private void readFrames( int sub, float[][] data, int dataOffset, List busyList,
							 Span readSpan, AbstractCompoundEdit ce )
	throws IOException
	{
		int					idx			= editIndexOf( readSpan.start, true, ce );
		if( idx < 0 ) idx = -(idx + 2);
		final long			startR		= decimHelps[ sub ].roundAdd - readSpan.start;
		final List			coll		= editGetCollByStart( ce );
		final MutableInt	readyLen	= new MutableInt( 0 );
		final MutableInt	busyLen		= new MutableInt( 0 );
		DecimatedStake		stake;
		int					chunkLen, discrepancy;
		Span				subSpan;
		int					readOffset, nextOffset = dataOffset;
		int					len			= (int) (readSpan.getLength() >> decimHelps[ sub ].shift );
		
		while( (len > 0) && (idx < coll.size()) ) {
			stake		= (DecimatedStake) coll.get( idx );
			subSpan		= new Span( Math.max( stake.getSpan().start, readSpan.start ),
									Math.min( stake.getSpan().stop, readSpan.stop ));
			stake.readFrames( sub, data, nextOffset, subSpan, readyLen, busyLen );
			chunkLen	= readyLen.value() + busyLen.value();
			readOffset	= nextOffset + readyLen.value(); // chunkLen;
			nextOffset	= (int) ((subSpan.stop + startR) >> decimHelps[ sub ].shift) + dataOffset;
			discrepancy	= nextOffset - readOffset;
			len 	   -= readyLen.value() + discrepancy;
			if( busyLen.value() == 0 ) {
				if( discrepancy > 0 ) {
					if( readOffset > 0 ) {
						for( int i = readOffset, k = readOffset - 1; i < nextOffset; i++ ) {
							for( int j = 0; j < data.length; j++ ) {
								data[ j ][ i ] = data[ j ][ k ];
							}
						}
					}
				}
			} else {
				final Span busySpan = new Span( subSpan.stop - (subSpan.getLength() * busyLen.value() / chunkLen),
				                                subSpan.stop );
				final int busyLastIdx = busyList.size() - 1;
				if( busyLastIdx >= 0 ) {
					final Span busySpan2 = (Span) busyList.get( busyLastIdx );
					if( busySpan.touches( busySpan2 )) {
						busyList.set( busyLastIdx, busySpan.union( busySpan2 ));
					} else {
						busyList.add( busySpan );
					}
				} else {
					busyList.add( busySpan );
				}
				for( int i = Math.max( 0, readOffset ); i < nextOffset; i++ ) {
					for( int j = 0; j < data.length; j++ ) {
						data[ j ][ i ] = 0f;
					}
				}
			}
			idx++;
		}
	}

	public static void view( float[] data, int off, int length, String descr )
	{
		final float[] dataCopy = new float[ length ];
		
		System.arraycopy( data, off, dataCopy, 0, length );
		
		int width = 256;
		int decimF = Math.max( 1, 2 * length / width );
		int decimLen = length / decimF;
		
		float[] decim = new float[ decimLen ];
		float f1, f2, f3;
		
		f2 = Float.NEGATIVE_INFINITY;
		f3 = Float.POSITIVE_INFINITY;
		for( int i = 0, j = 0; i < decimLen; ) {
			f1 = dataCopy[ j++ ];
			for( int k = 1; k < decimF; k++ ) {
				f1 = Math.max( f1, dataCopy[ j++ ]);
			}
			decim[ i++ ] = f1;
			f2 = Math.max( f2, f1 );
			f3 = Math.min( f3, f1 );
		}
		if( Float.isInfinite( f2 )) f2 = 1f;
		if( Float.isInfinite( f3 )) f3 = 0f;

		VectorDisplay ggVectorDisplay = new VectorDisplay( decim );
		ggVectorDisplay.setMinMax( f3, f2 );
//		ggVectorDisplay.addMouseListener( mia );
//		ggVectorDisplay.addMouseMotionListener( mia );
//		ggVectorDisplay.addTopPainter( tp );
//		ggVectorDisplay.setPreferredSize( new Dimension( width, 256 )); // XXX
		JPanel displayPane = new JPanel( new BorderLayout() );
		displayPane.add( ggVectorDisplay, BorderLayout.CENTER );
		Axis haxis			= new Axis( Axis.HORIZONTAL );
		Axis vaxis			= new Axis( Axis.VERTICAL, Axis.FIXEDBOUNDS );
final VectorSpace spc = VectorSpace.createLinSpace( 0, length - 1, f3, f2, null, null, null, null );
haxis.setSpace( spc );
vaxis.setSpace( spc );
		Box box				= Box.createHorizontalBox();
		box.add( Box.createHorizontalStrut( vaxis.getPreferredSize().width ));
		box.add( haxis );
		displayPane.add( box, BorderLayout.NORTH );
		displayPane.add( vaxis, BorderLayout.WEST );
		
		JFrame f = new JFrame( descr );
		f.setSize( width, 256 );
		f.getContentPane().add( displayPane, BorderLayout.CENTER );
		f.setVisible( true );
	}

	/*
	 * Same as in <code>NondestructiveDecimatedSampledTrack</code> but with
	 * automaic bias adjust.
	 * 
	 * @param tag unbiased fullrate span @param frames buffer to fill. note that
	 * this will not do any interpolation but fill at the decimated rate! @param
	 * framesOff offset in frames for the first frame which is read
	 * 
	 * @see NondestructiveDecimatedSampledTrack#read( Span, float[][], int )
	 */
/*
	private void readFrames( int sub, float[][] data, int dataOffset, List busyList,
							 Span readSpan, AbstractCompoundEdit ce )
	throws IOException
	{
		int					idx			= editIndexOf( readSpan.start, true, ce );
		if( idx < 0 ) idx = -(idx + 2);
		final long			startR		= decimHelps[sub].roundAdd - readSpan.start;
		final List			coll		= editGetCollByStart( ce );
		final MutableInt	readyLen	= new MutableInt( 0 );
		final MutableInt	busyLen		= new MutableInt( 0 );
		DecimatedWaveStake		stake;
		int					chunkLen, discrepancy;
		Span				subSpan;
		int					readOffset, nextOffset = dataOffset;
		int					len			= (int) (readSpan.getLength() >> decimHelps[ sub ].shift);

		while( (len > 0) && (idx < coll.size()) ) {
			stake		= (DecimatedWaveStake) coll.get( idx );
			subSpan		= new Span( Math.max( stake.getSpan().start, readSpan.start ),
									Math.min( stake.getSpan().stop, readSpan.stop ));
			stake.readFrames( sub, data, nextOffset, subSpan, readyLen, busyLen );
			chunkLen	= readyLen.value() + busyLen.value();
			readOffset	= nextOffset + readyLen.value(); // chunkLen;
			nextOffset	= (int) ((subSpan.stop + startR) >> decimHelps[ sub ].shift) + dataOffset;
			discrepancy	= nextOffset - readOffset;
			len 	   -= readyLen.value() + discrepancy;
			if( busyLen.value() == 0 ) {
				if( discrepancy > 0 ) {
					if( readOffset > 0 ) {
						for( int i = readOffset, k = readOffset - 1; i < nextOffset; i++ ) {
							for( int j = 0; j < data.length; j++ ) {
								data[ j ][ i ] = data[ j ][ k ];
							}
						}
					}
				}
			} else {
				busyList.add( new Span( subSpan.stop - (subSpan.getLength() * busyLen.value() / chunkLen),
										subSpan.stop ));
				for( int i = Math.max( 0, readOffset ); i < nextOffset; i++ ) {
					for( int j = 0; j < data.length; j++ ) {
						data[ j ][ i ] = 0f;
					}
				}
			}
			idx++;
		}
	}
*/
	public void debugDump()
	{
		for( int i = 0; i < getNumStakes(); i++ ) {
			((DecimatedStake) get( i, true )).debugDump();
		}
	}

	// ----------- dependant implementation -----------

//	public void dispose()
//	{
//		super.dispose();
//	}

	// private void addAllDepAsync( Object source, List stakes, SyncCompoundEdit
	// ce, Span union )
	private void addAllDepAsync()
	throws IOException
	{
		if( threadAsync != null ) throw new IllegalStateException();

		final List					stakes		= fullScale.getAll( true );
		if( stakes.isEmpty() ) return;

		final DecimatedStake		das;
		final Span					union		= fullScale.getSpan();
		final Span					extSpan;
		final long					fullrateStop, fullrateLen; // , insertLen;
		final int					numFullBuf;
	//	final Object				enc_this	= this;
		// final CacheManager cm = CacheManager.getInstance();
		final AbstractCompoundEdit	ce			= null; // XXX
		final Object				source		= null; // XXX
		final AudioStake			cacheReadAS;
		final AudioStake			cacheWriteAS;
		final DecimatedSonaTrail	enc_this	= this;

		synchronized( fileSync ) {
			das = allocAsync( union );
//das.GOGO = true;
		}
		extSpan			= das.getSpan();
		// insertLen	= extSpan.getLength();
		fullrateStop	= Math.min( extSpan.getStop(), fullScale.editGetSpan( ce ).stop );
		fullrateLen		= fullrateStop - extSpan.getStart();

		cacheReadAS		= null; // openCacheForRead( model );
		if( cacheReadAS == null ) {
			// cacheWriteAS = fullScale.openCacheForWrite( model,
			// decimHelps[ 0 ].fullrateToSubsample( union.getLength() ));
			cacheWriteAS = null; // openCacheForWrite( model, (fullrateLen + MAXCEILADD) & MAXMASK );
//			numFullBuf	= (int) (fullrateLen >> MAXSHIFT);
			numFullBuf	= (int) ((fullrateLen - fftSize + stepSize + stepSize - 1) / stepSize);
		} else {
			// cached files always have integer fullBufs!
			numFullBuf	= (int) ((fullrateLen + MAXCEILADD) >> MAXSHIFT);
			cacheWriteAS = null;
		}

		synchronized( bufSync ) {
			createBuffers();
		}

		editClear( source, das.getSpan(), ce );
		editAdd( source, das, ce );

		threadAsync = new Thread( new Runnable() {
			public void run()
			{
//				final CacheManager	cm					= PrefCacheManager.getInstance();
				long				pos;
				// long framesWritten = 0;
				long				framesWrittenCache	= 0;
				boolean				cacheWriteComplete	= false;
				Span				tag2;
				int					len;
				long				time;
// WARNING: the variant with deferring the continueWrite
// does not work because we loose the bufSync and hence
// any readFrames destroys our buf. For high res analysis
// the speed gain using the outBufOff cache method is
// anyway <3%.
//				int					outBufOff	= 0;
				int					inBufOff = 0, nextLen = fftSize;
				long				nextTime			= System.currentTimeMillis() + UPDATE_PERIOD;

				if( cacheReadAS != null ) {
					pos = decimHelps[ 0 ].fullrateToSubsample( extSpan.getStart() ); // XXX
				} else {
					pos = extSpan.getStart();
				}
//				minCoarse = MAXCOARSE >> decimHelps[ 0 ].shift;

				try {
					for( int i = 0; (i < numFullBuf) && keepAsyncRunning; i++ ) {
						synchronized( bufSync ) {
//							if( (i % 100) == 0 ) System.out.println( "ici " + i + " / " + numFullBuf );
							len = (int) Math.min( nextLen, fullrateStop - pos ); 
//							if( inBufOff + len <= tmpBufSize ) {
								tag2 = new Span( pos, pos + len );
//								fullScale.readFrames( tmpBuf, inBufOff, tag2, null );
								fullScale.readFrames( tmpBuf, fftSize - nextLen, tag2, null );
//							} else { // hitting buffer boundaries
//								tag2 = new Span( pos, pos + (tmpBufSize - inBufOff) );
//								fullScale.readFrames( tmpBuf, inBufOff, tag2, null );
//								tag2 = new Span( tag2.stop, pos + len );
//								fullScale.readFrames( tmpBuf, 0, tag2, null );
//							}
							if( len < nextLen ) {
								for( int ch = 0; ch < fullChannels; ch++ ) {
									for( int j = (inBufOff + len) % tmpBufSize, k = nextLen - len; k >= 0; k-- ) {
										tmpBuf[ ch ][ j ] = 0f;
										if( ++j == tmpBufSize ) j = 0;
									}
								}
							}
//							decimator.decimatePCM( tmpBuf, tmpBuf2, outBufOff, 1, 1 );
							decimator.decimatePCM( tmpBuf, tmpBuf2, 0, 1, 1 );
//							if( ++outBufOff == 1 ) {
//								das.continueWrite( 0, tmpBuf2, 0, outBufOff );
								das.continueWrite( 0, tmpBuf2, 0, 1 );
								if( cacheWriteAS != null ) {
//									cacheWriteAS.writeFrames( tmpBuf2, 0, new Span( framesWrittenCache, framesWrittenCache + outBufOff ));
									cacheWriteAS.writeFrames( tmpBuf2, 0, new Span( framesWrittenCache, framesWrittenCache + 1 ));
								}
//								framesWrittenCache += outBufOff;
								framesWrittenCache++;
//								outBufOff = 0;
//							}
							pos += nextLen;
							inBufOff = (inBufOff + nextLen) % tmpBufSize;
							nextLen = stepSize;
							for( int ch = 0; ch < fullChannels; ch++ ) {
								System.arraycopy( tmpBuf[ ch ], nextLen, tmpBuf[ ch ], 0, fftSize - nextLen );
							}
//							System.out.println( "frame done" );
						}
						time = System.currentTimeMillis();
						if( time >= nextTime ) {
							nextTime = time + UPDATE_PERIOD;
							if( asyncManager != null ) {
								asyncManager.dispatchEvent( new AsyncEvent(
									enc_this, AsyncEvent.UPDATE, time, enc_this ));
							}
						}
					}
					if( keepAsyncRunning ) {
//						if( outBufOff > 0 ) {	// flush rest
//							das.continueWrite( 0, tmpBuf2, 0, outBufOff );
//							if( cacheWriteAS != null ) {
//								cacheWriteAS.writeFrames( tmpBuf2, 0, new Span( framesWrittenCache, framesWrittenCache + outBufOff ));
//							}
//							framesWrittenCache += outBufOff;
//							outBufOff = 0;
//						}
//						cacheWriteComplete = true;
//						if( cacheWriteAS != null ) cacheWriteAS.addToCache( cm );
					}
//					das.flush();
				} catch( IOException e1 ) {
					e1.printStackTrace();
				} finally {
//					System.out.println( "finally" );
					if( cacheReadAS != null ) {
						cacheReadAS.cleanUp();
						cacheReadAS.dispose(); // !!!
					}
					if( cacheWriteAS != null ) {
						cacheWriteAS.cleanUp();
						cacheWriteAS.dispose(); // !!!
						if( !cacheWriteComplete ) { // indicates process was aborted ...
							final File[] f = createCacheFileNames();
							if( f != null ) { // ... therefore delete incomplete cache files!
								for( int i = 0; i < f.length; i++ ) {
									if( !f[ i ].delete() ) f[ i ].deleteOnExit();
									// cm.removeFile( f[ i ]);
								}
							}
						}
					}

					if( asyncManager != null ) {
						asyncManager.dispatchEvent( new AsyncEvent( enc_this,
							AsyncEvent.FINISHED, System.currentTimeMillis(), enc_this ));
					}
					synchronized( threadAsync ) {
						threadAsync.notifyAll();
						// threadAsync = null;
					}
				}
			}
		});

		keepAsyncRunning = true;
		threadAsync.start();
	}

	protected void addAllDep( Object source, List stakes, AbstractCompoundEdit ce, Span union )
	throws IOException
	{
		if( DEBUG ) System.err.println( "addAllDep " + union.toString() );

		final DecimatedStake das;
		final Span extSpan;
		final long fullrateStop, fullrateLen; // , insertLen;
		final int numFullBuf;
		final double progWeight;
		long pos;
		long framesWritten = 0;
		Span tag2;
		float f1;
		int len;

		synchronized( fileSync ) {
			das = alloc( union );
		}
		extSpan = das.getSpan();
		pos = extSpan.getStart();
		// insertLen = extSpan.getLength();
		fullrateStop = Math.min( extSpan.getStop(), fullScale.editGetSpan( ce ).stop );
		fullrateLen = fullrateStop - extSpan.getStart();
		progWeight = 1.0 / fullrateLen;
		numFullBuf = (int) (fullrateLen >> MAXSHIFT);
		pos = extSpan.getStart();

		synchronized( bufSync ) {
			flushProgression();
			createBuffers();

			for( int i = 0; i < numFullBuf; i++ ) {
				tag2 = new Span( pos, pos + MAXCOARSE );
				fullScale.readFrames( tmpBuf, 0, tag2, ce );
				subsampleWrite( tmpBuf, tmpBuf, das, MAXCOARSE, null, 0 );
				pos += MAXCOARSE;
				framesWritten += MAXCOARSE;

				setProgression( framesWritten, progWeight );
			}

			len = (int) (fullrateStop - pos);
			if( len > 0 ) {
				tag2 = new Span( pos, pos + len );
				fullScale.readFrames( tmpBuf, 0, tag2, ce );
				for( int ch = 0; ch < fullChannels; ch++ ) {
					f1 = tmpBuf[ ch ][ len - 1 ];
					for( int i = len; i < MAXCOARSE; i++ ) {
						tmpBuf[ ch ][ i ] = f1;
					}
				}
				subsampleWrite( tmpBuf, tmpBuf, das, MAXCOARSE, null, 0 );
				pos += MAXCOARSE;
				framesWritten += MAXCOARSE;

				setProgression( framesWritten, progWeight );
			}
		} // synchronized( bufSync )

		// editRemove( source, das.getSpan(), ce );
		editClear( source, das.getSpan(), ce );
		// System.err.println( "editRemove "+das.getSpan() );
		editAdd( source, das, ce );
		// System.err.println( "editAdd ..." );
	}

	// ----------- private schnucki -----------

	protected DecimatedStake allocAsync( Span span )
	throws IOException
	{
		if( !Thread.holdsLock( fileSync )) throw new IllegalMonitorStateException();

		final long floorStart	= span.start / stepSize * stepSize; // & MAXMASK;
		final long ceilStop		= (span.stop + stepSize - 1) / stepSize * stepSize; // (span.stop + MAXCEILADD) & MAXMASK;
		final Span extSpan		= (floorStart == span.start) && (ceilStop == span.stop) ?
									span : new Span( floorStart, ceilStop );
		final Span[] fileSpans	= new Span[ SUBNUM ];
		final Span[] biasedSpans = new Span[ SUBNUM ];
		long fileStart;
		long fileStop;

//System.out.println( "allocAsync( " + span + " ) --> " + extSpan );
		
		if( tempFAsync == null ) {
			// XXX THIS IS THE PLACE TO OPEN WAVEFORM CACHE FILE
			tempFAsync = createTempFiles();
		}
		synchronized( tempFAsync ) {
			for( int i = 0; i < SUBNUM; i++ ) {
				fileStart		= tempFAsync[ i ].getFrameNum();
				fileStop		= fileStart + (extSpan.getLength() >> decimHelps[ i ].shift);
				tempFAsync[ i ].setFrameNum( fileStop );
				fileSpans[ i ]	= new Span( fileStart, fileStop );
//System.out.println( "... fileSpan =  " + fileSpans[ i ] + " ( i = " + i + "; shift = " + decimHelps[ i ].shift + " )" );
				biasedSpans[ i ] = extSpan;
			}
		}
		return new DecimatedStake( extSpan, tempFAsync, fileSpans, biasedSpans, decimHelps );
	}

	protected File[] createCacheFileNames()
	{
		final AudioFile[] audioFiles = fullScale.getAudioFiles();
		if( (audioFiles.length == 0) || (audioFiles[0] == null) ) return null;

		final CacheManager cm = PrefCacheManager.getInstance();
		if( !cm.isActive() ) return null;

		final File[] f = new File[ audioFiles.length ];
		for( int i = 0; i < f.length; i++ ) {
			f[i] = cm.createCacheFileName( IOUtil.setFileSuffix( audioFiles[i].getFile(), "fft" ));
		}
		return f;
	}

/*
	private int[][] createCacheChannelMaps()
	{
		final int[][] fullChanMaps	= fullScale.getChannelMaps();
		final int[][] cacheChanMaps	= new int[ fullChanMaps.length ][];

		for( int i = 0; i < fullChanMaps.length; i++ ) {
//			System.out.println( "fullChanMaps[ " + i + " ] = " );
//			for( int k = 0; k < fullChanMaps[ i ].length; k++ ) {
//				System.out.println( "  " + fullChanMaps[ i ][ k ]);
//			}
			cacheChanMaps[ i ] = new int[ fullChanMaps[ i ].length * modelChannels ];
			for( int j = 0; j < cacheChanMaps[ i ].length; j++ ) {
				cacheChanMaps[ i ][ j ] = j;
			}
//			System.out.println( "cacheChanMaps[ " + i + " ] = " );
//			for( int k = 0; k < cacheChanMaps[ i ].length; k++ ) {
//				System.out.println( "  " + cacheChanMaps[ i ][ k ]);
//			}
		}

		return cacheChanMaps;
	}
*/
	/*
	 * @returns the cached stake or null if no cache file is available
	 */
/*
	private AudioStake openCacheForRead( int model )
	throws IOException
	{
		final File[]		f			= createCacheFileNames();
		if( f == null ) return null;

		final AudioFile[]	audioFiles	= fullScale.getAudioFiles();
		final Span[]		fileSpans	= new Span[ audioFiles.length ];
		final AudioFile[]	cacheAFs	= new AudioFile[ audioFiles.length ];
		final String		ourCode		= AbstractApplication.getApplication().getMacOSCreator();
		final int[][]		channelMaps	= createCacheChannelMaps();
		AudioStake			result		= null;
		AudioFileDescr		afd;
		byte[]				appCode;
		AudioFileCacheInfo	infoA, infoB;

		try {
			for( int i = 0; i < cacheAFs.length; i++ ) {
// System.out.println( "openCacheForRead checking '" + f[ i ].getAbsolutePath() + "'" );
				
				if( !f[ i ].isFile() ) return null;
				cacheAFs[ i ] = AudioFile.openAsRead( f[ i ]);
				cacheAFs[ i ].readAppCode();
				afd = cacheAFs[ i ].getDescr();
				final long expected = ((audioFiles[ i ].getFrameNum() + MAXCEILADD) & MAXMASK) >> decimHelps[ 0 ].shift;
				// System.out.println( "expected " + expected+ "; cacheF " +
				// cacheAFs[ i ].getFile().getAbsolutePath() );
				if( expected != afd.length ) {
					// System.err.println( "expected numFrames = "+ expected +
					// ", but got " + afd.length );
					return null;
				}
				appCode = (byte[]) afd.getProperty( AudioFileDescr.KEY_APPCODE );
				// System.err.println( "ourCode = '" + ourCode + "'; afd.appCode
				// = '" + afd.appCode + "'; appCode = '" + appCode + "'" );
				if( ourCode.equals( afd.appCode ) && (appCode != null) ) {
					infoA = AudioFileCacheInfo.decode( appCode );
					if( infoA != null ) {
						infoB = new AudioFileCacheInfo( audioFiles[ i ], model, audioFiles[ i ].getFrameNum() );
						if( !infoA.equals( infoB )) {
							// System.err.println( "info mismatch!" );
							return null;
						}
						// System.err.println( "ok. numChans = " +
						// infoA.getNumChannels() );
					} else {
						return null;
					}
				} else {
					return null;
				}
				fileSpans[ i ] = new Span( 0, cacheAFs[ i ].getFrameNum() );
			}
			// XXX WE NEED A WAY TO CLOSE THE FILES UPON STAKE DISPOSAL XXX
			if( channelMaps.length == 1 ) {
				result = new InterleavedAudioStake( fileSpans[ 0 ], cacheAFs[ 0 ], fileSpans[ 0 ]);
			} else {
				result = new MultiMappedAudioStake( fileSpans[ 0 ], cacheAFs, fileSpans, channelMaps );
			}
			return result;
		} finally {
			if( result == null ) {
				for( int i = 0; i < cacheAFs.length; i++ ) {
					if( cacheAFs[ i ] != null ) {
						cacheAFs[ i ].cleanUp();
						// if( !cacheAFs[ i ].getFile().delete() ) {
						// cacheAFs[ i ].getFile().deleteOnExit();
						// }
					}
				}
			}
		}
	}
*/

/*
	private AudioStake openCacheForWrite( int model, long decimFrameNum )
	throws IOException
	{
		final File[]			f			= createCacheFileNames();
		if( f == null ) return null;

		final AudioFile[]		audioFiles	= fullScale.getAudioFiles();
		final AudioFileDescr	afdProto	= new AudioFileDescr();
		final CacheManager		cm			= PrefCacheManager.getInstance();
		final Span[]			fileSpans	= new Span[ audioFiles.length ];
		final AudioFile[]		cacheAFs	= new AudioFile[ audioFiles.length ];
		final String			ourCode		= AbstractApplication.getApplication().getMacOSCreator();
		final int[][]			channelMaps	= fullScale.getChannelMaps(); // createCacheChannelMaps();
		AudioStake				result		= null;
		AudioFileDescr			afd;
		AudioFileCacheInfo		info;

		afdProto.type			= AudioFileDescr.TYPE_AIFF;
		afdProto.bitsPerSample	= 32;
		afdProto.sampleFormat	= AudioFileDescr.FORMAT_FLOAT;
		afdProto.rate			= decimHelps[ 0 ].rate; // getRate();
		afdProto.appCode		= ourCode;

		try {
			for( int i = 0; i < f.length; i++ ) {
				cm.removeFile( f[ i ]); // in case it existed
// System.out.println( "openCacheForWrite doing '" + f[ i ].getAbsolutePath() + "'" );
				afd				= new AudioFileDescr( afdProto );
				afd.channels	= channelMaps[ i ].length;
				// System.out.println( "channels = " + afd.channels );
				afd.file		= f[ i ];
				info			= new AudioFileCacheInfo( audioFiles[ i ], model, audioFiles[ i ].getFrameNum() );
				afd.setProperty( AudioFileDescr.KEY_APPCODE, info.encode() );
				cacheAFs[ i ]	= AudioFile.openAsWrite( afd );
				fileSpans[ i ]	= new Span( 0, decimFrameNum );
			}
			// XXX WE NEED A WAY TO CLOSE THE FILES UPON STAKE DISPOSAL XXX
			if( channelMaps.length == 1 ) {
				result = new InterleavedAudioStake( fileSpans[ 0 ], cacheAFs[ 0 ], fileSpans[ 0 ]);
			} else {
				result = new MultiMappedAudioStake( fileSpans[ 0 ], cacheAFs, fileSpans, channelMaps);
			}
			// System.err.println( "Cache was written" );
			return result;
		} finally {
			if( result == null ) {
				for( int i = 0; i < cacheAFs.length; i++ ) {
					if( cacheAFs[ i ] != null ) {
						cacheAFs[ i ].cleanUp();
						if( !cacheAFs[ i ].getFile().delete() ) {
							cacheAFs[ i ].getFile().deleteOnExit();
						}
					}
				}
			}
		}
	}
*/

	/*
	 * This is invoked by insert(). it subsamples the given buffer for all
	 * subsample STEs and writes it out using continueWrite; therefore the call
	 * to this method should be bracketed with beginInsert() and finishWrite().
	 * len must be an integer muliple of MAXCOARSE !
	 * 
	 * inBuf == null indicates cache skip
	 */
	// private void subsampleWrite( float[][] inBuf, float[][] outBuf,
	// DecimatedStake das, int len )
	protected void subsampleWrite( float[][] inBuf, float[][] outBuf, DecimatedStake das,
								   int len, AudioStake cacheAS, long cacheOff )
	throws IOException
	{
		int decim;

		if( SUBNUM < 1 ) return;

		decim = decimHelps[ 0 ].shift;
		// calculate first decimation from fullrate PCM
		assert len % fftSize == 0;
//		len >>= decim;
		len = (len / stepSize * modelChannels) >> decim;
		if( inBuf != null ) {
//			System.out.println( "decimator.decimatePCM( inBuf, outBuf, fftBuf, 0, " + len + ", " + (1 << decim) + " )" );
			decimator.decimatePCM( inBuf, outBuf, 0, len, 1 << decim );
//			System.out.println( "doing" );
			das.continueWrite( 0, outBuf, 0, len );
			if( cacheAS != null ) {
				cacheAS.writeFrames( outBuf, 0, new Span( cacheOff, cacheOff + len ));
			}
		}

		subsampleWrite2( outBuf, das, len );
	}

	// same as subsampleWrite but input is already at first decim stage
	private void subsampleWrite2( float[][] buf, DecimatedStake das, int len )
	throws IOException
	{
		int decim;

		// calculate remaining decimations from preceding ones
		for( int i = 1; i < SUBNUM; i++ ) {
			decim = decimHelps[ i ].shift - decimHelps[ i - 1 ].shift;
			len >>= decim;
			// framesWritten >>= decim;
			decimator.decimate( buf, buf, 0, len, 1 << decim );
			// ste[i].continueWrite( ts[i], framesWritten, outBuf, 0, len );
			das.continueWrite( i, buf, 0, len );
		} // for( SUBNUM )
	}

	// ---------------------- decimation subclasses ----------------------

	private abstract class Decimator
	{
		protected Decimator() { /* empty */ }
		
		protected abstract void decimate( float[][] inBuf, float[][] outBuf, int outOff, int len, int decim );
		protected abstract void decimatePCM( float[][] inBuf, float[][] outBuf, int outOff, int len, int decim );
		// protected abstract void decimatePCMFast( float[][] inBuf, float[][]
		// outBuf, int outOff, int len, int decim );
		protected abstract int draw( DecimationInfo info, int ch, int[][] peakPolyX, int[][] peakPolyY,
						  			 int[][] rmsPolyX, int[][] rmsPolyY, int decimLen,
						  			 Rectangle r, float deltaYN, int off );
	}

	private class SonaDecimator
	extends Decimator
	{
		protected SonaDecimator() { /* empty */ }
		
		protected void decimate( float[][] inBuf, float[][] outBuf, int outOff, int len, int decim )
		{
			int 	stop, j, k, m, ch;
			float	f1;
			float[]	inBufCh, outBufCh;

			for( ch = 0; ch < fullChannels; ch++ ) {
				inBufCh		= inBuf[ ch ];
				outBufCh	= outBuf[ ch ];

				for( j = outOff, stop = outOff + len, k = 0; j < stop; j++ ) {
					f1 = inBufCh[ k ];
					for( m = k + decim, k++; k < m; k++ ) {
						f1 += inBufCh[ k ];
					}
					outBufCh[ j ] = f1 / decim;
				}
			}
		}

		protected void decimatePCM( float[][] inBuf, float[][] outBuf, int outOff, int len, int decim )
		{
			final double w = 1.0 / decim;
			
//			for( int inOff = 0, stop = outOff + len, decimCnt = 0; outOff < stop; inOff += stepSize ) {
//				for( int ch = 0; ch < fullChannels; ch++ ) {
//					constQ.transform( inBuf[ ch ], inOff, Math.max( 0, inBuf[ ch ].length - inOff ), outBuf[ ch ], outOff, decimCnt > 0, w );
//				}
//				decimCnt = (decimCnt + 1) % decim;
//				if( decimCnt == 0 ) outOff += modelChannels;
//			}
			
			for( int inOff = 0, stop = outOff + len, decimCnt = 0; outOff < stop; inOff += stepSize ) {
				for( int ch = 0, outChanOff = 0; ch < fullChannels; ch++ ) {
//System.out.println( "calling with " + inBuf[ ch].length + " -- " + inOff + " ; " + fftSize + "; " + filterBuf.length + " -- " + constQ.getNumKernels() );
					constQ.transform( inBuf[ ch ], inOff, fftSize, filterBuf, 0, w );
					if( decimCnt == 0 ) {
						for( int i = 0; i < numKernels; i++ ) {
							outBuf[ outChanOff++ ][ outOff ] = filterBuf[ i ];
						}
					} else {
						for( int i = 0; i < numKernels; i++ ) {
							outBuf[ outChanOff++ ][ outOff ] += filterBuf[ i ];
						}
					}
				}
				decimCnt = (decimCnt + 1) % decim;
				if( decimCnt == 0 ) outOff++;
			}
		}
		
		protected int draw( DecimationInfo info, int ch,
				  			int[][] peakPolyX, int[][] peakPolyY,
				  			int[][] rmsPolyX, int[][] rmsPolyY, int decimLen,
				  			Rectangle r, float deltaYN, int off )
		{
			int			ch2;
			float[]		sPeakP, sPeakN, sRMSP;
			float		offX, scaleX, scaleY;
			
			ch2		= ch * 3;
			sPeakP	= tmpBuf[ ch2++ ];
			sPeakN	= tmpBuf[ ch2++ ];
			sRMSP	= tmpBuf[ ch2 ];
			scaleX	= 4 * r.width / (float) (info.sublength - 1);
			scaleY	= r.height * deltaYN;
			offX	= scaleX * off;
			
			return drawFullWavePeakRMS( sPeakP, sPeakN,
										sRMSP, decimLen, peakPolyX[ ch ],
										peakPolyY[ ch ], rmsPolyX[ ch ],
										rmsPolyY[ ch ], off, offX, scaleX,
										scaleY );
		}
		
		private int drawFullWavePeakRMS( float[] sPeakP, float[] sPeakN,
				float[] sRMS, int len, int[] peakPolyX, int[] peakPolyY,
				int[] rmsPolyX, int[] rmsPolyY, int off, float offX, float scaleX,
				float scaleY )
		{
			// final float scaleYN = -scaleY;
			int		x;
			float	peakP, peakN, rms;

			for( int i = 0, k = peakPolyX.length - 1 - off; i < len; i++, off++, k-- ) {
				x					= (int) (i * scaleX + offX);
				peakPolyX[ off ]	= x;
				peakPolyX[ k ]		= x;
				rmsPolyX[ off ]		= x;
				rmsPolyX[ k ]		= x;
				peakP				= sPeakP[ i ];
				peakN				= sPeakN[ i ];
				peakPolyY[ off ]	= (int) (peakP * scaleY) + 2;
				peakPolyY[ k ]		= (int) (peakN * scaleY) - 2;
				// peakC = (peakP + peakN) / 2;
				rms					= (float) Math.sqrt( sRMS[ i ]); // / 2;
				rmsPolyY[ off ]		= (int) (Math.min( peakP, rms ) * scaleY);
				rmsPolyY[ k ]		= (int) (Math.max( peakN, -rms ) * scaleY);
			}

			return off;
		}
	} // class FullPeakRMSDecimator

	/**
	 *  A <code>VectorDisplay</code> is a two dimensional
	 *  panel which plots a sampled function (32bit float) and allows
	 *  the user to edit this one dimensional data vector
	 *  (or table, simply speaking). It implements
	 *  the <code>EventManager.Processor</code> interface
	 *  to handle custom <code>VectorDisplayEvent</code>s
	 *  and implements the <code>VirtualSurface</code>
	 *  interface to allow transitions between screen
	 *  space and normalized virtual space.
	 *  <p>
	 *  Often it is convenient to attach a popup menu
	 *  created by the static method in <code>VectorTransformer</code>.
	 *  <p>
	 *  Examples of using <code>VectorDisplay</code>s aer
	 *  the <code>SigmaReceiverEditor</code> and the
	 *  <code>SimpleTransmitterEditor</code>.
	 *
	 *  @author		Hanns Holger Rutz
	 *  @version	0.65, 11-Aug-04
	 *
	 *  @see		de.sciss.meloncillo.math.VectorTransformer#createPopupMenu( VectorDisplay )
	 *  @see		VectorDisplayListener
	 *  @see		VectorDisplayEvent
	 *  @see		de.sciss.meloncillo.receiver.SigmaReceiverEditor
	 *  @see		de.sciss.meloncillo.transmitter.SimpleTransmitterEditor
	 *
	 *  @todo		a vertical (y) wrapping mode should be implemented
	 *				similar to x-wrap, useful for circular value spaces like angles
	 *  @todo		due to a bug in horizontal wrapping, the modified span
	 *				information is wrong?
	 */
	public static class VectorDisplay
	extends JComponent  // extends JPanel
	// implements  
	{
		private float[]		vector;

		private final GeneralPath   path		= new GeneralPath();
		private Shape				pathTrns;
		private TextLayout			txtLay		= null;
		private Rectangle2D			txtBounds;
		private Dimension			recentSize;
		private Image				image		= null;

		private static final Stroke	strkLine	= new BasicStroke( 0.5f );
		private static final Paint	pntArea		= new Color( 0x42, 0x5E, 0x9D, 0x7F );
		private static final Paint	pntLine		= Color.black;
		private static final Paint	pntLabel	= new Color( 0x00, 0x00, 0x00, 0x3F );
		
		private final AffineTransform trnsScreenToVirtual  = new AffineTransform();
		private final AffineTransform trnsVirtualToScreen  = new AffineTransform();

		private float		min			= 0.0f;		// minimum vector value
		private float		max			= 1.0f;		// maximum vector value
		private boolean		fillArea	= true;		// fill area under the vector polyline
		private String		label		= null;		// optional text label

		// --- top painter ---

		private final Vector collTopPainters = new Vector();
		
		/**
		 *  Creates a new VectorDisplay with an empty vector.
		 *  The defaults are wrapX = false, wrapY = false,
		 *  min = 0, max = 1.0, fillArea = true, no label
		 */
		public VectorDisplay()
		{
			this( new float[0] );
		}
		
		/**
		 *  Creates a new VectorDisplay with an given initial vector.
		 *  The defaults are wrapX = false, wrapY = false,
		 *  min = 0, max = 1.0, fillArea = true, no label
		 *
		 *  @param  vector  the initial vector data
		 */
		public VectorDisplay( float[] vector )
		{
			setOpaque( false );
			setMinimumSize( new Dimension( 64, 16 ));
//			setPreferredSize( new Dimension( 288, 144 )); // XXX
			recentSize  = getMinimumSize();
			setVector( null, vector );
//			addComponentListener( this );
		}

		/**
		 *  Replaces the existing vector by another one.
		 *  This dispatches a <code>VectorDisplayEvent</code>
		 *  to registered listeners.
		 *
		 *  @param  source  the source in the <code>VectorDisplayEvent</code>.
		 *					use <code>null</code> to prevent event dispatching.
		 *  @param  vector  the new vector data
		 */
		public void setVector( Object source, float[] vector )
		{
			this.vector = vector;
			
			recalcPath();
			repaint();
		}
		
		/**
		 *  Gets the current data array.
		 *
		 *  @return		the current vector data of the editor. valid data
		 *				is from index 0 to the end of the array.
		 *
		 *  @warning			the returned array is not a copy and therefore
		 *						any modifications are forbidden. this also implies
		 *						that relevant data be copied by the listener
		 *						immediately upon receiving the vector.
		 *  @synchronization	should only be called in the event thread
		 */
		public float[] getVector()
		{
			return vector;
		}

		/**
		 *  Changes the allowed range for vector values.
		 *  Influences the graphics display such that
		 *  the top margin of the panel corresponds to max
		 *  and the bottom margin corresponds to min. Also
		 *  user drawings are limited to these values unless
		 *  wrapY is set to true (not yet implemented).
		 *
		 *  @param  min		new minimum y value
		 *  @param  max		new maximum y value
		 *
		 *  @warning	the current vector is left untouched,
		 *				even if values lie outside the new
		 *				allowed range.
		 */
		public void setMinMax( float min, float max )
		{
			if( this.min != min || this.max != max ) {
				this.min	= min;
				this.max	= max;
				repaint();
			}
		}

		/**
		 *  Gets the minimum allowed y value
		 *
		 *  @return		the minimum specified function value
		 */
		public float getMin()
		{
			return min;
		}

		/**
		 *  Gets the maximum allowed y value
		 *
		 *  @return		the maximum specified function value
		 */
		public float getMax()
		{
			return max;
		}

		/**
		 *  Set the graph display mode
		 *
		 *  @param  fillArea	if <code>false</code>, a hairline is
		 *						drawn to connect the sample values. if
		 *						<code>true</code>, the area below the
		 *						curve is additionally filled with a
		 *						translucent colour.
		 */
		public void setFillArea( boolean fillArea )
		{
			if( this.fillArea != fillArea ) {
				this.fillArea   = fillArea;
				repaint();
			}
		}

		/**
		 *  Select the allowed range for vector values.
		 *  Influences the graphics display.
		 */
		public void setLabel( String label )
		{
			if( this.label == null || label == null || !this.label.equals( label )) {
				txtLay		= null;
				this.label  = label;
				repaint();
			}
		}

		public void paintComponent( Graphics g )
		{
			super.paintComponent( g );
			
			Dimension	d	= getSize();

			if( d.width != recentSize.width || d.height != recentSize.height ) {
				recentSize = d;
				recalcTransforms();
				recreateImage();
				redrawImage();
			} else if( pathTrns == null ) {
				recalcTransforms();
				recreateImage();	// XXX since we don't clear the background any more to preserve Aqua LAF
				redrawImage();
			}

			if( image != null ) {
				g.drawImage( image, 0, 0, this );
			}

			// --- invoke top painters ---
			if( !collTopPainters.isEmpty() ) {
				Graphics2D		g2			= (Graphics2D) g;
//				AffineTransform	trnsOrig	= g2.getTransform();

//				g2.transform( trnsVirtualToScreen );
				g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
//				for( int i = 0; i < collTopPainters.size(); i++ ) {
//					((TopPainter) collTopPainters.get( i )).paintOnTop( g2 );
//				}
//				g2.setTransform( trnsOrig );
			}
		}
		
		/**
		 *  Registers a new top painter.
		 *  If the top painter wants to paint
		 *  a specific portion of the surface,
		 *  it must make an appropriate repaint call!
		 *
		 *  @param  p   the painter to be added to the paint queue
		 *
		 *  @synchronization	this method must be called in the event thread
		 */
//		public void addTopPainter( TopPainter p )
//		{
//			if( !collTopPainters.contains( p )) {
//				collTopPainters.add( p );
//			}
//		}

		/**
		 *  Removes a registered top painter.
		 *
		 *  @param  p   the painter to be removed from the paint queue
		 *
		 *  @synchronization	this method must be called in the event thread
		 */
//		public void removeTopPainter( TopPainter p )
//		{
//			collTopPainters.remove( p );
//		}
		
		private void recreateImage()
		{
			if( image != null ) image.flush();
			image = createImage( recentSize.width, recentSize.height );
		}
		
		private void redrawImage()
		{
			if( image == null ) return;

			Graphics2D g2 = (Graphics2D) image.getGraphics();
//			g2.setColor( Color.white );
//			g2.fillRect( 0, 0, recentSize.width, recentSize.height );
			g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
//			g2.setRenderingHint( RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY );
			if( fillArea ) {
				g2.setPaint( pntArea );
				g2.fill( pathTrns );
			}
			g2.setStroke( strkLine );
			g2.setPaint( pntLine );
			g2.draw( pathTrns );
			if( label != null ) {
				g2.setPaint( pntLabel );
				if( txtLay == null ) {
					txtLay		= new TextLayout( label, getFont(), g2.getFontRenderContext() );
					txtBounds   = txtLay.getBounds();
				}
				txtLay.draw( g2, recentSize.width - (float) txtBounds.getWidth() - 4,
								 recentSize.height - (float) txtBounds.getHeight() );
			}
			g2.dispose();
		}

		/*
		 *  Recalculates a Java2D path from the vector
		 *  that will be used for painting operations
		 */
		private void recalcPath()
		{
			int			i;
			float		f1;
			float		f2 = (min - max) / recentSize.height + min;
			
			path.reset();
			if( vector.length > 0 ) {
				f1  = 1.0f / vector.length;
				path.moveTo( -0.01f, f2 );
				path.lineTo( -0.01f, vector[0] );
				for( i = 0; i < vector.length; i++ ) {
					path.lineTo( i * f1, vector[i] );
				}
				path.lineTo( 1.01f, vector[vector.length-1] );
				path.lineTo( 1.01f, f2 );
				path.closePath();
	// System.out.println( "recalced path" );
			}
			pathTrns = null;
		}

	// ---------------- VirtualSurface interface ---------------- 

		/*
		 *  Recalculates the transforms between
		 *  screen and virtual space
		 */
		private void recalcTransforms()
		{
	// System.out.println( "recalc trns for "+recentSize.width+" x "+recentSize.height );

			trnsVirtualToScreen.setToTranslation( 0.0, recentSize.height );
			trnsVirtualToScreen.scale( recentSize.width,recentSize.height / (min - max) );
			trnsVirtualToScreen.translate( 0.0, -min );
			trnsScreenToVirtual.setToTranslation( 0.0, min );
			trnsScreenToVirtual.scale( 1.0 / recentSize.width, (min - max) / recentSize.height );
			trnsScreenToVirtual.translate( 0.0, -recentSize.height );

			pathTrns = path.createTransformedShape( trnsVirtualToScreen );
		}
		
		/**
		 *  Converts a location on the screen
		 *  into a point the virtual space.
		 *  Neither input nor output point need to
		 *  be limited to particular bounds
		 *
		 *  @param  screenPt		point in screen space
		 *  @return the input point transformed to virtual space
		 */
		public Point2D screenToVirtual( Point2D screenPt )
		{
			return trnsScreenToVirtual.transform( screenPt, null );
		}

		/**
		 *  Converts a shape in the screen space
		 *  into a shape in the virtual space.
		 *
		 *  @param  screenShape		arbitrary shape in screen space
		 *  @return the input shape transformed to virtual space
		 */
		public Shape screenToVirtual( Shape screenShape )
		{
			return trnsScreenToVirtual.createTransformedShape( screenShape );
		}

		/**
		 *  Converts a point in the virtual space
		 *  into a location on the screen.
		 *
		 *  @param  virtualPt   point in the virtual space whose
		 *						visible bounds are (0, 0 ... 1, 1)
		 *  @return				point in the display space
		 */
		public Point2D virtualToScreen( Point2D virtualPt )
		{
			return trnsVirtualToScreen.transform( virtualPt, null );
		}

		/**
		 *  Converts a shape in the virtual space
		 *  into a shape on the screen.
		 *
		 *  @param  virtualShape	arbitrary shape in virtual space
		 *  @return the input shape transformed to screen space
		 */
		public Shape virtualToScreen( Shape virtualShape )
		{
			return trnsVirtualToScreen.createTransformedShape( virtualShape );
		}

		/**
		 *  Converts a rectangle in the virtual space
		 *  into a rectangle suitable for Graphics clipping
		 *
		 *  @param  virtualClip		a rectangle in virtual space
		 *  @return the input rectangle transformed to screen space,
		 *			suitable for graphics clipping operations
		 */
		public Rectangle virtualToScreenClip( Rectangle2D virtualClip )
		{
			Point2D screenPt1 = trnsVirtualToScreen.transform( new Point2D.Double( virtualClip.getMinX(),
																				   virtualClip.getMinY() ), null );
			Point2D screenPt2 = trnsVirtualToScreen.transform( new Point2D.Double( virtualClip.getMaxX(),
																				   virtualClip.getMaxY() ), null );
		
			return new Rectangle( (int) Math.floor( screenPt1.getX() ), (int) Math.floor( screenPt1.getY() ),
								  (int) Math.ceil( screenPt2.getX() ), (int) Math.ceil( screenPt2.getY() ));
		}
	}
}