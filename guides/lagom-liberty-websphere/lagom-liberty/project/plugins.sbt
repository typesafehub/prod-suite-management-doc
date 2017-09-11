
import sbt.Defaults.sbtPluginExtra

libraryDependencies ++= {
  val sbtV = (sbtBinaryVersion in update).value
  val scalaV = (scalaBinaryVersion in update).value

  val defaultPlugins: Seq[ModuleID] =
    Seq(
      sbtPluginExtra("com.lightbend.lagom" % "lagom-sbt-plugin" % "1.3.6", sbtV, scalaV),
      sbtPluginExtra("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "5.1.0", sbtV, scalaV),
      sbtPluginExtra("com.typesafe.sbt" % "sbt-native-packager" % "1.2.0", sbtV, scalaV)
    )

  defaultPlugins
}
