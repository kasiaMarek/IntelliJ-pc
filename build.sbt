import org.jetbrains.sbtidea.Keys._

lazy val intellijPc =
  project.in(file("."))
    .enablePlugins(SbtIdeaPlugin)
    .settings(
      version := "0.0.1-SNAPSHOT",
      scalaVersion := "2.13.13",
      intellijPluginName := "Completions via Presentation Compiler",
      intellijBuild      := "241.15989.150",
      intellijPlatform   := IntelliJPlatform.IdeaCommunity,
      Global    / intellijAttachSources := true,
      Compile / javacOptions ++= "--release" :: "17" :: Nil,
      intellijPlugins += "org.intellij.scala".toPlugin,
      libraryDependencies += "org.scalameta" % "mtags_2.13.13" % V.metalsVersion,
      libraryDependencies += "io.get-coursier" % "interface" % "1.0.19",
      Compile / unmanagedResourceDirectories += baseDirectory.value / "resources",
      Test / unmanagedResourceDirectories    += baseDirectory.value / "testResources",
      scalacOptions ++= Seq(
          "-deprecation", // Emit warning and location for usages of deprecated APIs.
          "-Ytasty-reader"
      )
    ).enablePlugins(BuildInfoPlugin).settings(
      buildInfoKeys := Seq[BuildInfoKey]("metalsVersion" -> V.metalsVersion),
      buildInfoPackage := "intellij.pc"
    )
