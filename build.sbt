name := "search-service-spray"

organization := "com.blinkbox.books"

version := scala.util.Try(scala.io.Source.fromFile("VERSION").mkString.trim).getOrElse("0.0.0")

scalaVersion  := "2.10.4"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8", "-feature")

resolvers += "spray" at "http://repo.spray.io/"

resolvers += "Restlet" at "http://maven.restlet.org"

libraryDependencies ++= {
  val akkaV = "2.3.2"
  val sprayV = "1.3.1"
  val solrV = "4.8.0"
  Seq(
    "com.blinkbox.books"  %%  "common-spray"    % "0.3.0",
    "com.blinkbox.books"  %%  "common-config"   % "0.0.1",
    "com.typesafe"        %%  "scalalogging-slf4j" % "1.1.0",
    "ch.qos.logback"      %   "logback-classic" % "1.1.2",
    "org.scalatest"       %%  "scalatest"       % "2.2.0-M1" % "test",
    "org.mockito"         %   "mockito-core"    % "1.9.5" % "test",
    "junit"               %   "junit"           % "4.11" % "test",
    "com.novocode"        %   "junit-interface" % "0.10" % "test",
    "io.spray"            %   "spray-testkit"   % sprayV  % "test",
    "com.typesafe.akka"   %%  "akka-testkit"    % akkaV   % "test",
    "commons-lang"        %   "commons-lang"    % "2.6",
    "com.google.guava"    %   "guava"           % "14.0.1",
    "com.google.code.findbugs" % "jsr305"       % "1.3.9",
    "org.apache.solr"     %   "solr-solrj"      % solrV,
    "commons-logging"     %   "commons-logging" % "1.1.3",
    "org.apache.solr"     %   "solr-core"       % solrV % "test"
  )
}

Revolver.settings
