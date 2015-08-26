name := "streaming-j-atto"

scalaVersion in ThisBuild := "2.11.7"

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-unchecked",
  "-Xfatal-warnings",
  "-Xlint",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard",
  "-Xfuture"
)

resolvers += "tpolecat"  at "http://dl.bintray.com/tpolecat/maven"

libraryDependencies ++= Seq(
  "org.tpolecat" %% "atto-core"   % "0.4.1",
  "org.tpolecat" %% "atto-stream" % "0.4.1"
)

