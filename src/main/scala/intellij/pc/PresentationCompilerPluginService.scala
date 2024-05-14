package intellij.pc

import java.net.URLClassLoader

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.psi.search.{GlobalSearchScope, PsiShortNamesCache}
import java.nio.file.{Path, Paths}
import java.util.ServiceLoader
import scala.collection.concurrent.TrieMap
import scala.jdk.CollectionConverters._
import scala.meta.internal.pc.ScalaPresentationCompiler
import scala.meta.pc.PresentationCompiler

import coursierapi.{
  Dependency,
  Fetch,
  MavenRepository,
  Repository,
  ResolutionParams
}
import intellij.pc.Embedded.presentationCompiler

class PresentationCompilerPluginService(val project: Project) {
  val logger = Logger.getInstance(getClass.getName)
  private val compilers: TrieMap[module.Module, PresentationCompiler] =
    new TrieMap()
  def startPc(module0: module.Module): Option[PresentationCompiler] = { // TODO: restart pc when classpath changes (on compilation finish)
    for {
      scalaVersion <- getScalaVersion(module0)
    } yield {
      val originalClasspath = OrderEnumerator
        .orderEntries(module0)
        .recursively()
        .withoutSdk()
        .getClassesRoots
        .map(_.getPresentableUrl)
      val fullClasspath = (originalClasspath.toList).map(Paths.get(_))

      val cache = PsiShortNamesCache.getInstance(project)
      val searchScope = GlobalSearchScope
        .moduleWithDependenciesAndLibrariesScope(module0, false)
      val intelliJSymbolSearch = new IntelliJSymbolSearch(cache, searchScope)

      val pc = presentationCompiler(scalaVersion)
        .withSearch(
          intelliJSymbolSearch
        ) // TODO: should not be included before indexing is finished, in the future should have metals implementation of workspace search
        .newInstance(module0.getName, fullClasspath.asJava, Nil.asJava)

      compilers.addOne(module0 -> pc)
      pc
    }

  }

  private def getScalaVersion(module0: module.Module) = {
    var sdkScalaVersion: String = null
    var scalaVersion: String = null
    OrderEnumerator.orderEntries(module0).forEachLibrary { library =>
      val name = library.getPresentableName
      if (name.contains("scala-sdk-")) {
        name match {
          case PresentationCompilerPluginService.scalaSdkPattern(version) =>
            scalaVersion = version
            false
          case _ => true
        }
      } else if (name.contains("org.scala-lang:scala-library:")) {
        name match {
          case PresentationCompilerPluginService.pattern(version) =>
            scalaVersion = version
            true
          case _ => true
        }
      } else true
    }
    Option(sdkScalaVersion).orElse(Option(scalaVersion))
  }

  def getPresentationCompiler(
      module0: module.Module
  ): Option[PresentationCompiler] = {
    Option(compilers.getOrElseUpdate(module0, startPc(module0).get))
  }
}

object PresentationCompilerPluginService {
  val pattern = ".*org.scala-lang:scala-library:(\\d+\\.\\d+\\.\\d+).*".r
  val scalaSdkPattern = ".*scala-sdk-(\\d+\\.\\d+\\.\\d+).*".r
}
