package intellij.pc

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEnumerator
import intellij.pc.Embedded.presentationCompiler
import intellij.pc.symbolSearch.{
  StandaloneSymbolSearch,
  WorkspaceSymbolProvider
}
import java.nio.file.Paths
import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._
import scala.meta.pc.PresentationCompiler
import scala.meta.internal.pc.PresentationCompilerConfigImpl

class PresentationCompilerPluginService(val project: Project) {
  private val logger = Logger.getInstance(getClass.getName)
  private val compilers: TrieMap[module.Module, PresentationCompiler] =
    new TrieMap()
  private val moduleScalaVersionCache: TrieMap[module.Module, String] =
    new TrieMap()

  def startPc(module0: module.Module): Option[PresentationCompiler] = {
    for {
      scalaVersion <- getScalaVersion(module0)
    } yield {
      val originalClasspath = OrderEnumerator
        .orderEntries(module0)
        .recursively()
        .withoutSdk()
        .getClassesRoots
        .map(_.getPresentableUrl)
      logger.warn(s"classpath # ${originalClasspath.length}")
      val fullClasspath = originalClasspath.toList.map(Paths.get(_))
      val workspaceSymbolSearch =
        project.getService(classOf[WorkspaceSymbolProvider]).symbolSearch
      val pc = presentationCompiler(scalaVersion)
        .withSearch(
          new StandaloneSymbolSearch(fullClasspath, workspaceSymbolSearch)
        )
        .withConfiguration(
          PresentationCompilerConfigImpl()
            .copy(isCompletionSnippetsEnabled = false)
        )
        .newInstance(module0.getName, fullClasspath.asJava, Nil.asJava)

      compilers.addOne(module0 -> pc)
      pc
    }

  }

  def getPresentationCompiler(
      module0: module.Module
  )(implicit ec: ExecutionContext) = {
    Option(compilers.getOrElseUpdate(module0, startPc(module0).get))
  }

  def restarPc(modules: Set[module.Module]) = {
    val allModules = getInverseDepModules(modules)
    for {
      module <- allModules
      pc <- compilers.get(module)
    } {
      logger.warn(s"Restarting pc for module ${module.getName}")
      pc.restart()
    }
  }

  def getScalaVersion(module0: module.Module) = {
    val result = moduleScalaVersionCache.getOrElseUpdate(
      module0, {
        var sdkScalaVersion: String = null
        var scalaVersion: String = null
        OrderEnumerator.orderEntries(module0).forEachLibrary { library =>
          val name = library.getPresentableName
          if (name.contains("scala-sdk-")) {
            name match {
              case PresentationCompilerPluginService.scalaSdkPattern(version) =>
                sdkScalaVersion = version
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
        if (sdkScalaVersion != null) sdkScalaVersion
        else scalaVersion
      }
    )
    Option(result)
  }

  def stopPc() = {
    moduleScalaVersionCache.clear()
    compilers.foreach { case (_, pc) => pc.shutdown() }
    compilers.clear()
  }

  private def getInverseDepModules(modules: Set[module.Module]) = {
    val moduleManager = ModuleManager.getInstance(project)
    moduleManager.getModules.filter { module =>
      modules.contains(module) || modules.exists(m =>
        moduleManager.getModuleDependentModules(module).contains(m)
      )
    }
  }
}

object PresentationCompilerPluginService {
  private val pattern =
    ".*org.scala-lang:scala-library:(\\d+\\.\\d+\\.\\d+).*".r
  private val scalaSdkPattern = ".*scala-sdk-(\\d+\\.\\d+\\.\\d+).*".r
}
