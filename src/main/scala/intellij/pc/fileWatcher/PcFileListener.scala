package intellij.pc.fileWatcher

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.{VFileContentChangeEvent, VFileCreateEvent, VFileDeleteEvent, VFileEvent}
import com.intellij.util.concurrency.AppExecutorUtil
import intellij.pc.PresentationCompilerPluginService
import intellij.pc.symbolSearch.Indexer

import java.util
import scala.jdk.CollectionConverters.CollectionHasAsScala

final class PcFileListener(project: Project) extends BulkFileListener {
  override def after(events: util.List[_ <: VFileEvent]): Unit = {
    lazy val fileStateService = project.getService(classOf[FileStateService])
    def getModule(file: VirtualFile) =
      Option(
        ProjectRootManager
          .getInstance(project)
          .getFileIndex
          .getModuleForFile(file)
      )
    val modules = events.asScala.flatMap {
      case createdEvent: VFileCreateEvent => getModule(createdEvent.getFile)
      case deletedEvent: VFileDeleteEvent => getModule(deletedEvent.getFile)
      case _                              => None
    }.toSet
    val changedFiles = events.asScala.collect {
      case changeEvent: VFileContentChangeEvent => changeEvent.getFile
    }.toSet

    changedFiles.foreach(fileStateService.modified)
    if (modules.nonEmpty)
      project
        .getService(classOf[PresentationCompilerPluginService])
        .restartPc(modules)
    if (changedFiles.nonEmpty)
      ReadAction
        .nonBlocking[Unit] { () =>
          changedFiles.foreach(
            project.getService(classOf[Indexer]).indexSourceFile
          )
        }
        .submit(AppExecutorUtil.getAppExecutorService)
  }
}
