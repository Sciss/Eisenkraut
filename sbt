#!/bin/sh
where=`dirname $0`
cd ${where}
if [ ! -f sbt-launch.jar ];
then
  echo ""
  echo "Preparing the Simple Build Tool (this step is only performed the first time)."
  echo "'sbt-launcher.jar' is downloaded from typesafe.artifactoryonline.com..."
  echo ""
  curl -O http://repo.typesafe.com/typesafe/ivy-releases/org.scala-sbt/sbt-launch/0.12.1/sbt-launch.jar
fi

java -Xmx2048m -Xss1m -server -XX:+UseConcMarkSweepGC -XX:MaxPermSize=128m -jar sbt-launch.jar "$@"
