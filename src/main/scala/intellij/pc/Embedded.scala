package intellij.pc

import com.intellij.openapi.diagnostic.Logger
import coursierapi.Dependency
import coursierapi.Fetch
import coursierapi.MavenRepository
import coursierapi.Repository
import coursierapi.ResolutionParams

import java.net.URLClassLoader
import java.nio.file.Path
import java.util.ServiceLoader
import scala.collection.concurrent.TrieMap
import scala.jdk.CollectionConverters._
import scala.meta.internal.pc.ScalaPresentationCompiler
import scala.meta.pc.PresentationCompiler

object Embedded {
  private val logger = Logger.getInstance(getClass.getName)
  private val presentationCompilers: TrieMap[String, URLClassLoader] =
    TrieMap.empty

  private lazy val repositories: List[Repository] =
    Repository.defaults().asScala.toList ++
      List(
        Repository.central(),
        Repository.ivy2Local(),
        MavenRepository.of(
          "https://oss.sonatype.org/content/repositories/public/"
        ),
        MavenRepository.of(
          "https://oss.sonatype.org/content/repositories/snapshots/"
        )
      )

  def presentationCompiler(scalaVersion: String): PresentationCompiler = {
    val shouldUseDottyPc = useDottyPc(scalaVersion)
    val classpath =
      if (shouldUseDottyPc) downloadScala3PresentationCompiler(scalaVersion)
      else downloadMtags(scalaVersion)

    val classloader = presentationCompilers.getOrElseUpdate(
      scalaVersion,
      newPresentationCompilerClassLoader(classpath)
    )

    val presentationCompilerClassname =
      if (shouldUseDottyPc) "dotty.tools.pc.ScalaPresentationCompiler"
      else classOf[ScalaPresentationCompiler].getName()

    serviceLoader(
      classOf[PresentationCompiler],
      presentationCompilerClassname,
      classloader
    )
  }
  private def newPresentationCompilerClassLoader(
      classpath: Seq[Path]
  ): URLClassLoader = {
    val allURLs = classpath.map(_.toUri.toURL).toArray
    // Share classloader for a subset of types.
    val parent = new PresentationCompilerClassLoader(
      this.getClass.getClassLoader
    )
    new URLClassLoader(allURLs, parent)
  }

  private def serviceLoader[T](
      cls: Class[T],
      className: String,
      classloader: URLClassLoader
  ): T = {
    val services = ServiceLoader.load(cls, classloader).iterator()
    if (services.hasNext) services.next()
    else {
      val cls = classloader.loadClass(className)
      val ctor = cls.getDeclaredConstructor()
      ctor.setAccessible(true)
      ctor.newInstance().asInstanceOf[T]
    }
  }

  private def downloadMtags(scalaVersion: String): List[Path] =
    downloadDependency(
      Dependency.of(
        "org.scalameta",
        s"mtags_$scalaVersion",
        BuildInfo.metalsVersion
      ),
      Some(scalaVersion)
    )

  private def downloadScala3PresentationCompiler(
      scalaVersion: String
  ): List[Path] =
    downloadDependency(
      Dependency.of(
        "org.scala-lang",
        s"scala3-presentation-compiler_3",
        scalaVersion
      ),
      Some(scalaVersion)
    )

  private def downloadDependency(
      dep: Dependency,
      scalaVersion: Option[String],
      classfiers: Seq[String] = Seq.empty,
      resolution: Option[ResolutionParams] = None
  ): List[Path] = {
    logger.warn(
      s"Downloading dep: ${dep.getModule().getOrganization()}:${dep.getModule().getName()}:${dep.getVersion()}"
    )
    fetchSettings(dep, scalaVersion, resolution)
      .addClassifiers(classfiers: _*)
      .fetch()
      .asScala
      .toList
      .map(_.toPath())
  }

  private def fetchSettings(
      dep: Dependency,
      scalaVersion: Option[String],
      resolution: Option[ResolutionParams] = None
  ): Fetch = {

    val resolutionParams = resolution.getOrElse(ResolutionParams.create())

    scalaVersion.foreach { scalaVersion =>
      if (!scalaVersion.startsWith("3"))
        resolutionParams.forceVersions(
          List(
            Dependency.of("org.scala-lang", "scala-library", scalaVersion),
            Dependency.of("org.scala-lang", "scala-compiler", scalaVersion),
            Dependency.of("org.scala-lang", "scala-reflect", scalaVersion)
          ).map(d => (d.getModule, d.getVersion)).toMap.asJava
        )
      else
        resolutionParams.forceVersions(
          List(
            Dependency.of("org.scala-lang", "scala3-library_3", scalaVersion),
            Dependency.of("org.scala-lang", "scala3-compiler_3", scalaVersion),
            Dependency.of("org.scala-lang", "tasty-core_3", scalaVersion)
          ).map(d => (d.getModule, d.getVersion)).toMap.asJava
        )
    }

    Fetch
      .create()
      .addRepositories(repositories: _*)
      .withDependencies(dep)
      .withResolutionParams(resolutionParams)
      .withMainArtifacts()
  }

  private def useDottyPc(scalaVersion: String): Boolean =
    scalaVersion.split("\\.").toList match {
      case major :: minor :: _ =>
        major.toInt == 3 && minor.toInt >= 4
      case version =>
        logger.warn(s"unexpected scala version: $scalaVersion")
        false
    }

}
