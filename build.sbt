name := "search-service-spray"

organization := "com.blinkboxbooks"

version := "0.1.0"

scalaVersion  := "2.10.4"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

resolvers += "spray" at "http://repo.spray.io/"

libraryDependencies ++= {
  val akkaV = "2.3.0"
  val sprayV = "1.3.1"
  Seq(
    "io.spray"            %   "spray-can"       % sprayV,
    "io.spray"            %   "spray-routing"   % sprayV,
    "io.spray"            %%  "spray-json"      % "1.2.6",
    "io.spray"            %   "spray-testkit"   % sprayV  % "test",
    "com.typesafe.akka"   %%  "akka-actor"      % akkaV,
    "com.typesafe.akka"   %%  "akka-testkit"    % akkaV   % "test",
    "org.scalatest"       %%  "scalatest"       % "1.9.1" % "test",
    "org.mockito"         %   "mockito-core"    % "1.9.5",
    "org.json4s"          %%  "json4s-jackson"  % "3.2.7",
    "junit"               %   "junit"           % "4.11" % "test",
    "com.novocode"        %   "junit-interface" % "0.10" % "test"
  )
}

// Add Mobcast Releases to resolvers
resolvers += "Sonatype Nexus Repository Manager" at "http://nexus.mobcast.co.uk/nexus/content/repositories/releases"

// Pick up login credentials for Nexus from user's directory
credentials += Credentials(Path.userHome / ".sbt" / ".nexus")

Revolver.settings

