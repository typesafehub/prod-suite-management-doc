import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}

organization in ThisBuild := "com.lightbend"
version in ThisBuild := "0.1"

// the Scala version that will be used for cross-compiled libraries
scalaVersion in ThisBuild := "2.11.8"

val macwire = "com.softwaremill.macwire" %% "macros" % "2.2.5" % "provided"
val scalaTest = "org.scalatest" %% "scalatest" % "3.0.1" % Test
val scalaDns = "com.lightbend" %% "lagom13-scala-service-locator-dns" % "2.0.0"
val commonSettings = Seq(
  resolvers += Resolver.bintrayRepo("hajile", "maven")
)

lazy val `hello` = (project in file("."))
  .aggregate(`lagom-api`, `lagom-impl`)

lazy val `lagom-api` = (project in file("lagom-api"))
  .settings(
    libraryDependencies ++= Seq(lagomScaladslApi)
  ).settings(commonSettings: _*)

lazy val `lagom-impl` = (project in file("lagom-impl"))
  .enablePlugins(LagomScala, JavaAppPackaging)
  .settings(
    libraryDependencies ++= Seq(lagomScaladslClient, macwire, scalaDns),
    dockerRepository := Some("lagom"),
    dockerUpdateLatest := true,
    dockerEntrypoint ++= """-Dplay.crypto.secret="${APPLICATION_SECRET:-none}" -Dplay.akka.actor-system="${AKKA_ACTOR_SYSTEM_NAME:-lagomservice-v1}" -Dhttp.address="$LAGOMSERVICE_BIND_IP" -Dhttp.port="$LAGOMSERVICE_BIND_PORT" -Dakka.io.dns.resolver=async-dns -Dakka.io.dns.async-dns.resolve-srv=true -Dakka.io.dns.async-dns.resolv-conf=on""".split(" ").toSeq,
    dockerCommands :=
      dockerCommands.value.flatMap {
        case ExecCmd("ENTRYPOINT", args@_*) => Seq(Cmd("ENTRYPOINT", args.mkString(" ")))
        case v => Seq(v)
      },
    version := "1.0-SNAPSHOT"
  )
  .settings(lagomForkedTestSettings: _*)
  .settings(commonSettings: _*)
  .dependsOn(`lagom-api`)

// #local-liberty
lagomUnmanagedServices in ThisBuild := Map("libertyservice" -> "http://localhost:9080")
// #local-liberty
