val buildSettings = Seq(
  organization := "com.blinkbox.books",
  name := "spray-search-service",
  version := scala.util.Try(scala.io.Source.fromFile("VERSION").mkString.trim).getOrElse("0.0.0"),
  scalaVersion  := "2.11.5",
  scalacOptions := Seq("-unchecked", "-deprecation", "-feature", "-encoding", "utf8", "-target:jvm-1.7", "-Xfatal-warnings", "-Xfuture")
)

val dependencySettings = Seq(
  libraryDependencies ++= {
    val solrV = "4.8.0"
    Seq(
      "com.blinkbox.books"  %%  "common-spray"    % "0.24.0",
      "com.blinkbox.books"  %%  "common-config"   % "2.3.1",
      "com.blinkbox.books"  %%  "common-scala-test"   % "0.3.0" % Test,
      "io.spray"            %%  "spray-testkit"   % "1.3.2"  % Test,
      "com.typesafe.akka"   %%  "akka-testkit"    % "2.3.7"  % Test,
      "com.google.guava"    %   "guava"           % "14.0.1",
      "com.google.code.findbugs" % "jsr305"       % "1.3.9",
      "commons-lang"        %   "commons-lang"    % "2.6",
      "commons-logging"     %   "commons-logging" % "1.1.3",
      "ch.qos.logback"      %   "logback-classic" % "1.1.2",
      "org.apache.solr"     %   "solr-solrj"      % solrV,
      "org.apache.solr"     %   "solr-core"       % solrV % Test
    )
  }
)

Revolver.settings

val root = (project in file(".")).
  settings(buildSettings: _*).
  settings(dependencySettings: _*)
