organization  := "com.example"

version       := "0.1"

scalaVersion  := "2.11.6"

scalacOptions := Seq("-unchecked", "-deprecation", "-feature", "-encoding", "utf8")

resolvers ++= Seq(
  Resolver.mavenLocal,
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
  "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/",
  "RoundEights" at "http://maven.spikemark.net/roundeights"
)

libraryDependencies ++= {
  val akkaV  = "2.3.9"
  val sprayV = "1.3.3"
  Seq(
    "io.spray"           %% "spray-can"                % sprayV,
    "io.spray"           %% "spray-client"             % sprayV,
    "io.spray"           %% "spray-routing-shapeless2" % sprayV,
    "io.spray"           %% "spray-json"               % "1.3.1",
    "com.typesafe.akka"  %% "akka-actor"               % akkaV,
    "com.typesafe.slick" %% "slick"                    % "3.0.0-RC3",
    "org.slf4j"          %  "slf4j-nop"                % "1.6.4",
    "com.zaxxer"         %  "HikariCP-java6"           % "2.3.5",
    "org.postgresql"     %  "postgresql"               % "9.4-1201-jdbc41",
    "com.kifi"           %% "franz"                    % "0.3.9",
    "io.spray"           %% "spray-testkit"            % sprayV       % "test",
    "com.typesafe.akka"  %% "akka-testkit"             % akkaV        % "test",
    "org.specs2"         %% "specs2-core"              % "2.4.17"     % "test"
  )
}

Revolver.settings
