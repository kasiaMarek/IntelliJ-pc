import org.jetbrains.sbtidea.Keys._

lazy val intellijPc =
  project.in(file("."))
    .enablePlugins(SbtIdeaPlugin)
    .settings(
      version := "0.0.1-SNAPSHOT",
      scalaVersion := "2.13.13",
      ThisBuild / intellijPluginName := "Completions via Presentation Compiler",
      ThisBuild / intellijBuild      := "241.15989.150",
      ThisBuild / intellijPlatform   := IntelliJPlatform.IdeaCommunity,
      Global    / intellijAttachSources := true,
      Compile / javacOptions ++= "--release" :: "17" :: Nil,
      intellijPlugins += "org.intellij.scala".toPlugin,
      libraryDependencies += "org.scala-lang" % "scala3-presentation-compiler_3" % "3.3.3",
      Compile / unmanagedResourceDirectories += baseDirectory.value / "resources",
      Test / unmanagedResourceDirectories    += baseDirectory.value / "testResources",
      scalacOptions ++= Seq(
          "-deprecation", // Emit warning and location for usages of deprecated APIs.
          "-Ytasty-reader"
      )
    )
