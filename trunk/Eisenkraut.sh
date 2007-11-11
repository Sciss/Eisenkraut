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
java -Dswing.defaultlaf=com.sun.java.swing.plaf.gtk.GTKLookAndFeel -ea -cp "Eisenkraut.jar:libraries/JCollider.jar:libraries/MRJAdapter.jar:libraries/NetUtil.jar:libraries/Normalizer.jar:libraries/SwingOSC.jar" de.sciss.eisenkraut.Main $@
