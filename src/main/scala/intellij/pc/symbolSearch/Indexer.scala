package intellij.pc.symbolSearch

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{ModuleRootManager, ProjectFileIndex, ProjectRootManager}
import com.intellij.openapi.vfs.VirtualFile
import intellij.pc.PresentationCompilerPluginService

import scala.collection.mutable.ArrayBuffer
import scala.meta.Dialect
import scala.meta.dialects._
import scala.meta.inputs.Input
import scala.meta.internal.metals.{EmptyReportContext, SemanticdbDefinition, WorkspaceSymbolInformation}
import scala.meta.internal.mtags.MtagsEnrichments.{XtensionSemanticdbRange, XtensionSymbolInformation, XtensionSymbolInformationKind}
import scala.util.control.NonFatal

final class Indexer(project: Project) {
  private val logger = Logger.getInstance(getClass.getName)

  // indexing works on all modules, maybe it should prioritize currently edited one ?
  def indexWorkspace(indicator: IndexingProgressIndicator) = {
    val fileIndex = ProjectRootManager.getInstance(project).getFileIndex
    for {
      module <- ModuleManager.getInstance(project).getModules
      scalaVersion <- project
        .getService(classOf[PresentationCompilerPluginService])
        .getScalaVersion(module)
      dialect = dialectForScalaVersion(scalaVersion)
    } {
      Indexer.forAllSourceFiles(fileIndex, module, indicator, indexSourceFile(_, dialect))
    }
    logger.warn(s"Finished indexing workspace for pc.")
  }

  def indexSourceFile(vFile: VirtualFile): Unit = {
    if (Indexer.isScalaOrJava(vFile)) {
      val optModule = Option(
        ProjectRootManager
          .getInstance(project)
          .getFileIndex
          .getModuleForFile(vFile)
      )
      for {
        module <- optModule
        scalaVersion <- project
          .getService(classOf[PresentationCompilerPluginService])
          .getScalaVersion(module)
      } indexSourceFile(vFile, dialectForScalaVersion(scalaVersion))
    }
  }

  private def indexSourceFile(vFile: VirtualFile, dialect: Dialect): Unit =
    try {
      val symbols = ArrayBuffer.empty[WorkspaceSymbolInformation]
      val methodSymbols = ArrayBuffer.empty[WorkspaceSymbolInformation]
      val input =
        Input.VirtualFile(vFile.getUrl, LoadTextUtil.loadText(vFile).toString)
      SemanticdbDefinition.foreach(input, dialect, includeMembers = true) {
        case SemanticdbDefinition(info, occ, _) =>
          if (info.isExtension) {
            occ.range.foreach { range =>
              methodSymbols += WorkspaceSymbolInformation(
                info.symbol,
                info.kind,
                range.toLsp
              )
            }
          } else {
            if (info.kind.isRelevantKind) {
              occ.range.foreach { range =>
                symbols += WorkspaceSymbolInformation(
                  info.symbol,
                  info.kind,
                  range.toLsp
                )
              }
            }
          }
      }(EmptyReportContext)
      project
        .getService(classOf[WorkspaceSymbolProvider])
        .didChange(vFile.toNioPath, symbols.toSeq, methodSymbols.toSeq)
    } catch {
      case NonFatal(e) => logger.warn(s"Failed to index $vFile", e)
    }

  private def dialectForScalaVersion(scalaVersion: String): Dialect = {
    val scalaBinaryVersion = scalaBinaryVersionFromFullVersion(scalaVersion)
    scalaBinaryVersion match {
      case "2.11"                             => Scala211
      case "2.12"                             => Scala212Source3
      case "2.13"                             => Scala213Source3
      case version if version.startsWith("3") => Scala3
      case _                                  => Scala213
    }
  }

  private def scalaBinaryVersionFromFullVersion(scalaVersion: String): String =
    if (scalaVersion.startsWith("3")) "3"
    else scalaVersion.split('.').take(2).mkString(".")

}

object Indexer {
  def forAllSourceFiles(
                         fileIndex: ProjectFileIndex,
                         module: Module,
                         indicator: ProgressIndicatorWrapper,
                         process: VirtualFile => Unit
  ): Unit = {
    for {
      root <- ModuleRootManager.getInstance(module).getContentRoots
    } yield {
      var totalSize = 0
      fileIndex.iterateContentUnderDirectory(root, fileOrDir => {
        if (!fileOrDir.isDirectory && isScalaOrJava(fileOrDir)) {
          totalSize += 1
        }
        true
      })
      indicator.nextProgress("Indexing workspace", Some(totalSize))

      fileIndex.iterateContentUnderDirectory(
        root,
        (fileOrDir: VirtualFile) => {
          if (!indicator.isCancelled && !fileOrDir.isDirectory && isScalaOrJava(fileOrDir)) {
            process(fileOrDir)
            indicator.step(root.getPath)
          }
          true
        }
    )}
  }

  private def isScalaOrJava(vFile: VirtualFile): Boolean = {
    val typeName = vFile.getFileType.getName
    typeName == "Scala" || typeName == "Java"
  }
}
