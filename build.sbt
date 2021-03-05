import com.typesafe.sbt.packager.linux.LinuxPackageMapping

lazy val baseName     = "Eisenkraut"
lazy val baseNameL    = baseName.toLowerCase

lazy val authorName   = "Hanns Holger Rutz"
lazy val authorEMail  = "contact@sciss.de"

lazy val basicJavaOpts = Seq("-source", "1.8")

lazy val projectVersion   = "1.4.2-SNAPSHOT"

lazy val jcolliderVersion = "1.0.0"
lazy val scissLibVersion  = "1.1.5"
lazy val subminVersion    = "0.3.4"
lazy val weblafVersion    = "1.2.11"

lazy val commonSettings = Seq(
  name             := "Eisenkraut",
  version          := projectVersion,
  organization     := "de.sciss",
  description      := "A multi-channel and hi-res capable audio file editor",
  homepage         := Some(url("https://git.iem.at/sciss/Eisenkraut")),
  licenses         := Seq("AGPL v3+" -> url("http://www.gnu.org/licenses/agpl-3.0.txt")),
  javacOptions     := Seq("-encoding", "utf8"),
  // note: --release cannot be used in conjunction with -source or -target (YEAH, GREAT THINKING THERE)
  javacOptions ++= (if (scala.util.Properties.isJavaAtLeast("9")) Seq("--release", "8") else basicJavaOpts ++ Seq("-target", "1.8")), // JDK >8 breaks API; skip scala-doc
  javacOptions in (Compile, doc) := basicJavaOpts,  // doesn't eat `-encoding`
  scalaVersion     := "2.13.5", // not used
  autoScalaLibrary := false,
  crossPaths       := false,
  mainClass        := Some("de.sciss.eisenkraut.Main"),
  libraryDependencies ++= Seq(
    "de.sciss" %  "jcollider" % jcolliderVersion,
    "de.sciss" %  "scisslib"  % scissLibVersion,
    "de.sciss" %  "submin"    % subminVersion,
    "com.weblookandfeel" % "weblaf-core"  % weblafVersion,
    "com.weblookandfeel" % "weblaf-ui"    % weblafVersion
  )
)

// ---- publishing ----

lazy val publishSettings = Seq(
  publishMavenStyle := true,
  publishTo := {
    Some(if (isSnapshot.value)
      "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
    else
      "Sonatype Releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
    )
  },
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  pomExtra := { val n = name.value
<scm>
  <url>git@git.iem.at:sciss/{n}.git</url>
  <connection>scm:git:git@git.iem.at:sciss/{n}.git</connection>
</scm>
<developers>
  <developer>
    <id>sciss</id>
    <name>Hanns Holger Rutz</name>
    <url>http://www.sciss.de</url>
  </developer>
</developers>
  }
)

// ---- packaging ----

lazy val assemblySettings = Seq(
  test            in assembly := {},
  target          in assembly := baseDirectory.value,
  assemblyJarName in assembly := s"${name.value}.jar"
)

//////////////// universal (directory) installer
lazy val pkgUniversalSettings = Seq(
  // NOTE: doesn't work on Windows, where we have to
  // provide manual file `SYSSON_config.txt` instead!
  javaOptions in Universal ++= Seq(
    // -J params will be added as jvm parameters
    "-J-Xmx1024m"
    // others will be added as app parameters
    // "-Dproperty=true",
  ),
  // Since our class path is very very long,
  // we use instead the wild-card, supported
  // by Java 6+. In the packaged script this
  // results in something like `java -cp "../lib/*" ...`.
  // NOTE: `in Universal` does not work. It therefore
  // also affects debian package building :-/
  // We need this settings for Windows.
  scriptClasspath /* in Universal */ := Seq("*")
)

//////////////// debian installer
lazy val pkgDebianSettings: Seq[sbt.Def.Setting[_]] = Seq(
  maintainer in Debian := s"$authorName <$authorEMail>",
  debianPackageDependencies in Debian += "java7-runtime",
  packageSummary in Debian := description.value,
  packageDescription in Debian :=
    """The audio file editor is distinguished by a number of
      | traits which makes it particularly interesting in the field
      | of computer music: There are no restrictions with regard to
      | the number of channels in a file, and there is an OSC interface
      | via which the functionality can be extended. Other features
      | include waveform caching, easy and precise time selections and
      | marker manipulations, configurable automatic cross-fades during
      | cut and paste, overwrite and mix modes for pasting, half and
      | double speed playback, adjustable solo, mute and panorama per
      | channel, and editable input and output configurations for
      | multichannel sound cards.
      |""".stripMargin,
  // include all files in src/debian in the installed base directory
  linuxPackageMappings in Debian ++= {
    val n     = (name            in Debian).value.toLowerCase
    val dir   = (sourceDirectory in Debian).value / "debian"
    val f1    = (dir * "*").filter(_.isFile).get  // direct child files inside `debian` folder
    val f2    = ((dir / "doc") * "*").get
    //
    def readOnly(in: LinuxPackageMapping) =
      in.withUser ("root")
        .withGroup("root")
        .withPerms("0644")  // http://help.unc.edu/help/how-to-use-unix-and-linux-file-permissions/
    //
    val aux   = f1.map { fIn => packageMapping(fIn -> s"/usr/share/$n/${fIn.name}") }
    val doc   = f2.map { fIn => packageMapping(fIn -> s"/usr/share/doc/$n/${fIn.name}") }
    (aux ++ doc).map(readOnly)
  }
)

lazy val root = project.withId(baseNameL).in(file("."))
  .enablePlugins(JavaAppPackaging, DebianPlugin)
  .settings(commonSettings)
  .settings(assemblySettings)
  .settings(pkgUniversalSettings)
  .settings(useNativeZip) // cf. https://github.com/sbt/sbt-native-packager/issues/334
  .settings(pkgDebianSettings)
  .settings(publishSettings)

