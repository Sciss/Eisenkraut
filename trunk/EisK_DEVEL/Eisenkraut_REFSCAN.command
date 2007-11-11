#!/bin/sh

followlink()
{
	prg="$1"
	while [ -h "$prg" ] ; do
		ls=`ls -ld "$prg"`
		link=`expr "$ls" : '.*-> \(.*\)$'`
		if expr "$link" : '.*/.*' > /dev/null; then
			prg="$link"
		else
			prg=`dirname "$prg"`/"$link"
		fi
	done
	echo $prg
}

absdir() 
{ 
	[ -n "$1" ] && ( cd "$1" 2> /dev/null && pwd ; ) 
}

where=`followlink $0`
where=`dirname ${where}`
where=`absdir ${where}`
cd ${where}
java -Dapple.laf.useScreenMenuBar=true -Xdock:icon=Eisenkraut.app/Contents/Resources/application.icns -Xdock:name=Eisenkraut -ea -cp "/Users/rutz/Documents/devel/ReferenceScanner/jb2refscan-2.89.jar:Eisenkraut.jar:libraries/JCollider.jar:libraries/MRJAdapter.jar:libraries/NetUtil.jar:libraries/Normalizer.jar:libraries/SwingOSC.jar" com.jb2works.reference.Launcher de.sciss.eisenkraut.Main
