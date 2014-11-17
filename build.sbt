name := "search-service-spray"

organization := "com.blinkbox.books"

version := scala.util.Try(scala.io.Source.fromFile("VERSION").mkString.trim).getOrElse("0.0.0")

scalaVersion  := "2.11.4"

scalacOptions := Seq("-unchecked", "-deprecation", "-feature", "-encoding", "utf8", "-target:jvm-1.7", "-Xfatal-warnings", "-Xfuture")

libraryDependencies ++= {
  val akkaV = "2.3.7"
  val sprayV = "1.3.2"
  val solrV = "4.8.0"
  Seq(
    "com.blinkbox.books"  %%  "common-spray"    % "0.19.1",
    "com.blinkbox.books"  %%  "common-config"   % "1.4.1",
    "com.blinkbox.books"  %%  "common-scala-test"   % "0.3.0" % Test,
//TODO!    "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
    "ch.qos.logback"      %   "logback-classic" % "1.1.2",
    "io.spray"            %%   "spray-testkit"  % sprayV  % Test,
    "com.typesafe.akka"   %%  "akka-testkit"    % akkaV   % Test,
    "commons-lang"        %   "commons-lang"    % "2.6",
    "com.google.guava"    %   "guava"           % "14.0.1",
    "com.google.code.findbugs" % "jsr305"       % "1.3.9",
    "org.apache.solr"     %   "solr-solrj"      % solrV,
    "commons-logging"     %   "commons-logging" % "1.1.3",
    "org.apache.solr"     %   "solr-core"       % solrV % Test
  )
}

Revolver.settings
