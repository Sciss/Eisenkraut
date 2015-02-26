![logo](http://sciss.de/eisenkraut/application.png)

# Eisenkraut

[![Flattr this](http://api.flattr.com/button/flattr-badge-large.png)](https://flattr.com/submit/auto?user_id=sciss&url=https%3A%2F%2Fgithub.com%2FSciss%2FEisenkraut&title=Eisenkraut%20Audio%20Editor&language=Java&tags=github&category=software)
[![Build Status](https://travis-ci.org/Sciss/Eisenkraut.svg?branch=master)](https://travis-ci.org/Sciss/Eisenkraut)

## statement

Eisenkraut is a standalone, cross-platform audio file editor using the SuperCollider 3 server application.

Eisenkraut is (C)opyright 2004&ndash;2015 by Hanns Holger Rutz. All rights reserved. It is released under the [GNU General Public License](http://github.com/Sciss/Eisenkraut/blob/master/licenses/Eisenkraut-License.txt).

To contact the author, send an email to `contact at sciss.de`. For project status, API and current version visit [github.com/Sciss/Eisenkraut](http://github.com/Sciss/Eisenkraut).

## downloading

A binary version of Eisenkraut for Linux, OS X and Windows is provided through [Bintray](https://bintray.com/sciss/generic/Eisenkraut).

## setting up and running

Eisenkraut uses the [SuperCollider](http://supercollider.sourceforge.net/) server as the audio playback engine. You need to install a recent SuperCollider 3 version to run this software. The recommended version is SuperCollider 3.6.3 or newer. On Linux Debian, you should be fine with `sudo apt-get install supercollider`.

To launch Eisenkraut, you can use the `Eisenkraut.command` shell script. On a Linux desktop system, you may copy `Eisenkraut.desktop` to `~/.local/share/applications` in order to be able to place Eisenkraut in your desktop's application bar. Make sure you open this file with a text editor and change paths in the `Exec` and `Icon` entries to match your installation location. Future versions will hopefully provide an automatic installation procedure to do that work for you. Installing the `.desktop` file means you can open sound files directly from the desktop or file manager (Nautilus).

After launching Eisenkraut for the first time, make sure that the "SuperCollider Server (scsynth)" field in the audio preferences is pointing to the valid `scsynth` or `scsynth.exe` (on Windows) file! __On OS X, newer SuperCollider versions "hide" the `scsynth` command line tool inside the `SuperCollider.app` bundle.__ So the correct path will be something like `/Applications/SuperCollider/SuperCollider.app/Contents/Resources/scsynth`.

Also note that, by default, the audio server is not automatically booted, so you need to press the "Boot" button in the main window to launch the server and be able to actually _hear_ the sound file when playing it back. There is a preferences switch to enable automatic booting.

## compiling

Eisenkraut can be build from source with [sbt](http://www.scala-sbt.org/#install) version 0.13. It requires Java 1.6, and library dependencies are currently included as jars in the `lib` folder.

If you do not have or wish to install sbt on your system, a shortcut is to use the provided Unix (OS X and Linux) script `sbt` which comes in the main folder. It may be invoked as `./sbt` and will automatically download the correct `sbt-launch.jar` version and place it in the main Eisenkraut directory, the first time it is executed. 

To build on __Linux__ or __Windows__:

    $ sbt assembly

The result will be `Eisenkraut.jar`. This should be double-clickable, and otherwise can be launched with `java -jar Eisenkraut.jar`. The preferred way, however, is to use the `Eisenkraut.command` shell script which will give the application more memory.

To build on __OS X__:

    $ sbt appbundle

The result will be `Eisenkraut.app` in the main directory.

## source code distribution

Eisenkraut's GPL'ed source code is made available through [github.com/Sciss/Eisenkraut](http://github.com/Sciss/Eisenkraut).

As a SuperCollider client it uses the JCollider library, which is also licensed under the GNU General Public License. The compiled library is included, for source code and details visit [sourceforge.net/projects/jcollider/](http://sourceforge.net/projects/jcollider/).

For OSC communication, the NetUtil library is used, which is licensed under the GNU Lesser General Public License (LGPL), for source code and details visit [sourceforge.net/projects/netutil/](http://sourceforge.net/projects/netutil/).

It uses the ScissLib library which is licensed under the GNU General Public License, source code provided through [github.com/Sciss/ScissLib](https://github.com/Sciss/ScissLib), and Steve Roy's MRJAdapter published under the Artistic License, source code provided through [java.net/projects/mrjadapter/sources/svn/show](https://java.net/projects/mrjadapter/sources/svn/show).

ICU4J and Unicode Java code (`Normalizer.jar`) is used which is licensed under a BSD 3-clause type license.

## documentation

Please refer to the `help/index.html` file for a list of manual topics.

A short screencast is available on [Vimeo](https://vimeo.com/26510634).

## to-do's

A lot. Here are some suggestions:

 - Still need to integrate FScape (more a job about FScape than about Eisenkraut)
 - Finding a way to edit with simultaneous video display (might be a separate app synchronized through some kind of OSC transport)
 - Optionally saving meta-information with the sound files (e.g. a separate XML file), like panorama settings, window bounds etc.
 - Finishing sonogram functionality (needs decimation and must be editable)
 - Improving SuperCollider interface and classes
 - etc. etc.
