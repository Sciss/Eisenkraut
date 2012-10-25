resolvers ++= Seq(
  "less is" at "http://repo.lessis.me",
  "coda" at "http://repo.codahale.com")

// addSbtPlugin( "me.lessis" % "ls-sbt" % "0.1.2" )

addSbtPlugin( "com.eed3si9n" % "sbt-assembly" % "0.8.3" )

addSbtPlugin( "de.sciss" % "sbt-appbundle" % "0.15" )
