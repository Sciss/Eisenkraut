#!/bin/sh

APPNAME=Eisenkraut
APPVERSION=0.70unstable

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
cd ..

echo "=========================================="
echo "= this script creates html javadoc files ="
echo "= in a subfolder 'doc/api' in the        ="
echo "= main application folder                ="
echo "=========================================="
echo
echo "NOTE : path names MUST NOT contain white space characters"
echo

PACKAGES="de.sciss.app de.sciss.common de.sciss.gui de.sciss.io de.sciss.timebased de.sciss.util de.sciss.eisenkraut de.sciss.eisenkraut.edit de.sciss.eisenkraut.gui de.sciss.eisenkraut.io de.sciss.eisenkraut.math de.sciss.eisenkraut.net de.sciss.eisenkraut.realtime de.sciss.eisenkraut.render de.sciss.eisenkraut.session de.sciss.eisenkraut.timeline de.sciss.eisenkraut.util de.sciss.fscape.gui de.sciss.fscape.render de.sciss.fscape.util"
CLASSPATH="-classpath libraries/JCollider.jar:libraries/MRJAdapter.jar:libraries/NetUtil.jar:libraries/Normalizer.jar:libraries/SwingOSC.jar"
JAVADOC_OPTIONS="-quiet -use -tag synchronization -tag todo -tag warning -source 1.4 -version -author -sourcepath src/ -d doc/api"
WINDOW_TITLE="$APPNAME v$APPVERSION API"

GLOBAL_NETUTIL=http://www.sciss.de/netutil/doc/api/
GLOBAL_JCOLLIDER=http://www.sciss.de/jcollider/doc/api/
# GLOBAL_SWINGOSC=http://www.sciss.de/swingOSC/doc/api/
GLOBAL_JAVA=http://java.sun.com/j2se/1.4.2/docs/api/

LOCAL_NETUTIL="/Users/rutz/Documents/workspace/NetUtil/doc/api"
LOCAL_JCOLLIDER="/Users/rutz/Documents/workspace/JCollider/doc/api"
LOCAL_JAVA="/Developer/Documentation/Java/Reference/1.4.2/doc/api"

REFER_OFFLINE=0
read -er -p "Let javadoc use local API copies when creating docs (y,N)? "
for f in Y y j J; do if [ "$REPLY" = $f ]; then REFER_OFFLINE=1; fi done
LINK_OFFLINE=0
read -er -p "Should the resulting HTML files link to local API copies (y,N)? "
for f in Y y j J; do if [ "$REPLY" = $f ]; then LINK_OFFLINE=1; fi done

if [ $(($REFER_OFFLINE|LINK_OFFLINE)) != 0 ]; then
	read -er -p "Local Java 1.4.2 API folder ('$LOCAL_JAVA')? "
	if [ "$REPLY" != "" ]; then LOCAL_JAVA="$REPLY"; fi
	read -er -p "Local NetUtil API folder ('$LOCAL_NETUTIL')? "
	if [ "$REPLY" != "" ]; then LOCAL_NETUTIL="$REPLY"; fi
	read -er -p "Local JCollider API folder ('$LOCAL_JCOLLIDER')? "
	if [ "$REPLY" != "" ]; then LOCAL_JCOLLIDER="$REPLY"; fi
  
	if [ $LINK_OFFLINE != 0 ]; then
		LINK_OPTIONS="-link file://$LOCAL_JAVA -link file://$LOCAL_NETUTIL -link file://$LOCAL_JCOLLIDER"
	else
		LINK_OPTIONS="-linkoffline $GLOBAL_JAVA file://$LOCAL_JAVA -linkoffline $GLOBAL_NETUTIL file://$LOCAL_NETUTIL -linkoffline $GLOBAL_JCOLLIDER file://$LOCAL_JCOLLIDER"
	fi;
else
	LINK_OPTIONS="-link $GLOBAL_JAVA -link $GLOBAL_NETUTIL -link $GLOBAL_JCOLLIDER"
fi

CMD="javadoc $JAVADOC_OPTIONS $LINK_OPTIONS $CLASSPATH $PACKAGES"
echo $CMD
$CMD

echo "---------- done ----------"
