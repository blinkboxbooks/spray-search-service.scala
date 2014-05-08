name := "search-service-spray"

organization := "com.blinkboxbooks"

version := "0.1.0"

scalaVersion  := "2.10.4"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

resolvers += "spray" at "http://repo.spray.io/"

resolvers += "Restlet" at "http://maven.restlet.org"

libraryDependencies ++= {
  val akkaV = "2.3.0"
  val sprayV = "1.3.1"
  val json4sV = "3.2.9"
  val solrV = "4.8.0"
  Seq(
    "org.slf4j"           %   "slf4j-log4j12"   % "1.7.5",
    "log4j"               %   "log4j"           % "1.2.17",
    "io.spray"            %   "spray-can"       % sprayV,
    "io.spray"            %   "spray-routing"   % sprayV,
    "io.spray"            %%  "spray-json"      % "1.2.6",
    "io.spray"            %   "spray-testkit"   % sprayV  % "test",
    "com.typesafe.akka"   %%  "akka-actor"      % akkaV,
    "com.typesafe.akka"   %%  "akka-testkit"    % akkaV   % "test",
    "org.scalatest"       %%  "scalatest"       % "1.9.1" % "test",
    "org.mockito"         %   "mockito-core"    % "1.9.5",
    "org.json4s"          %%  "json4s-native"   % json4sV,
    "org.json4s"          %%  "json4s-jackson"  % json4sV,
    "junit"               %   "junit"           % "4.11" % "test",
    "com.novocode"        %   "junit-interface" % "0.10" % "test",
    "commons-lang"        %   "commons-lang"    % "2.6",
    "com.google.guava"    %   "guava"           % "14.0.1",
    "com.google.code.findbugs" % "jsr305"       % "1.3.9",
    "org.apache.solr"     %   "solr-solrj"      % solrV,
    "commons-logging"     %   "commons-logging" % "1.1.3",
    "org.apache.solr"     %   "solr-core"       % solrV % "test"
  )
}

// Add Mobcast Releases to resolvers
resolvers += "Sonatype Nexus Repository Manager" at "http://nexus.mobcast.co.uk/nexus/content/repositories/releases"

// Pick up login credentials for Nexus from user's directory
credentials += Credentials(Path.userHome / ".sbt" / ".nexus")

Revolver.settings

