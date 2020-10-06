# change history

## v1.4.0 (oct 2020)

 - fixes a bug with Wave 64 reading
 - fixes a bug with non-ASCII characters in AIFF marker names
 - improves focus and keyboard navigation for Marker Editor
 - improves default save-as/selection location
 - fixes scsynth crashes when using certain solo/mute combination

## v1.3.2 (may 2017)

 - fixes region list bugs with 'reverse' process
 - fixes region list bugs with empty selection rendering

## v1.3.0 (apr 2016)

 - adds dark-skin option (in preferences)
 - improve reveal-file function (customizable in preferences)
 - improve keyboard shortcuts
 - graphics clean-up
 - various small fixes

## v1.2.0 (nov 2015)

 - improve build scripts, package for Debian
 - improve Linux support
 - add drop-file element to main window

## v1.1.0 (jan 2015)

 - fix incompatibilities with SC versions
 - improve Linux support, keyboard control

## v1.0.0 (oct 2012)

 - move to sbt (simple build tool) for building, instead of Ant
 - move to IntelliJ IDEA for developing, instead of Eclipse
 - move to git and Github, instead of svn and SourceForge
 - add Auto-Play option to the preferences, useful for powering up simple installations

## v0.74 (nov 2010 ; svn rev. 158)

 - Fixes: committing to numberfield with enter key, preferences menu item disappearance on latest os x / java 6 for mac
 - fixing numberfield commit bug in scisslib

## v0.73 (jan 2010 ; svn rev. 153)

 - Fixes: works with new scsynth status-replies

## v0.72 (sep 2009 ; svn rev. 151)

 - Fixes: Recorder working with SC 3.3.1
 - Adding Wave64 icon

## v0.71 (jul 2009 ; svn rev. 147)

 - SuperCollider: added Parametric EQ example, EisKBuffer improvements, added direct scripting example, adding custom menus example
 - Fixes: Sample-Rate-Conversion with recent scsynth
 - Improvements: Support for Wave64, Needlehole Median Filter much faster, Shorter titles for multi-mono documents
 - Interface: output configs and audio boxes sorted alphabetically
 - OSC server: view command, viewStart and viewStop queries, path query, transport commands
 - Now using ScissLib

## v0.70 (jul 2008 ; svn rev. 120)

 - support for multi-mono files
 - support for independant channel editing
 - experimental sonagramme display mode
 - OSC server for basic scripting functionality (e.g. from SuperCollider language). includes SwingOSC bridge for populating windows.
 - updated JCollider and NetUtil with TCP support
 - switchable editing mode (insert, overwrite, mix) for cut and paste operations
 - sample accurate loop playback without gaps
 - processes are marker-aware now
 - new functions: save selection as, play selection
 - many gui beautifications and improvements (pop-up panorama, blend-settings popup and history, higher contrast SPL and wave colours, control room meters, list of switchable audio-interfaces, optional internal-frames-mode ("MDI"), faster waveform display, collapsable waveform view, asynchronous waveform calculation, logarithmic waveform display)
 - many internal architectural changes (simplified and less buggy lock mechanism, unified timebased package, unified application framework, improved level meter management)
 - dozens of bug fixes, fixed some memory leaks
 - using Eclipse 3.2 IDE now with Apache ant task
 - no more jar installer, double-clickable jar application

## v0.63 (dec 2005)

 - added audio recorder
 - minor bugfixes and gui improvements

## v0.62 (nov 2005)

 - minor bugfixes
 - locks for timeline selection, reveal-in-finder (mac-only), new help buttons

## v0.61 (oct 2005)

 - completed transition to new parameter gadgets
 - bugfixes including the corrupted playlist after saving
 - fixes and normalize mode in Needhole process; better progress bar display

## v0.59 (oct 2005)

 - added channel meter display and sample value cursor information
 - waveform is shown in sample-and-hold style when zooming in close enough
 - fixed again non working keyboard shortcuts (cursor keys)
 - worked a bit on the fscape integration

## v0.58 (sep 2005)

 - changed waveform view double buffering, fixes a bug with slow display and memory leak
 - fixed bugs with supercollider boot and audio bus de-allocation
 - fixed non working keyboard shortcuts on PCs
 - minor improvements (zoom tool, keyboard access)

## v0.57 (sep 2005)

 - fixed linux incompatible server options bug
 - introduces new param field with switchable units (foreshadowing the fscape integration ;-)
 - added limiter option to allow volume boosting without distortion, useful when cutting high dynamic material using headphones
 - new-document-from-selection

## v0.55 (sep 2005)

 - improved audio file performance, marker support for WAV files
 - preliminary marker import function, new-empty-document item, mix-clipboard process
 - improved undo manager (lazy redo buffer destruction)
 - small things like: warns you when you open a write-protected file; bug fixes

## v0.5 (aug 2005)

 - converted into a multi-document application. this is not very well tested yet, 0.4 might be more stable.
 - the waveform window now includes the transport (every sound has separate transport).
 - supercollider window has become the main window used for closing the application.
 - supercollider now controlled using basic jcollider classes, a small server stat display in the main window.
 - markers can be moved ;-)
 - more keyboard shortcuts, more help files, bug fixes

## v0.4 (jul 2005)

 - <A HREF="help/IOSetup.html">I/OSetup</A> window and <A HREF="help/ChannelTrack.html">panorama settings</A> for each channel.
 - Automatic SC boot feature. No need to quit and re-launch SC when configuration changes.
 - Automatic sample rate conversion.
 - Basic <A HREF="help/MarkerAxis.html">marker</A> support.
 - A couple of bug fixes

## v0.3 (jul 2005)

 - variable blending curve, blending support in practially all editing steps
 - basic dsp functions (fade in, fade out, adjust gain, invert, reverse, swap channels, crop)
 - waveform overviews are optionally cached
 - view options (time units, vertical ruler, null linie). timeline-insertion-follows-playback
 - lot's of bug fixes. source code more modularized. custom icons and info.plist settings.

## v0.2 (may 2005)

 - fixes problems with clicks between edited regions.
 - duplicates some new features of meloncillo, e.g. improved transport, improved timeline tools, solo+mute. main-volume slider.

## v0.1 (mar 2005)

 - really the first alpha version, beware
