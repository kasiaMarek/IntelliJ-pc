package intellij.pc.fileWatcher

import java.util
import scala.jdk.CollectionConverters.CollectionHasAsScala

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.{VFileContentChangeEvent, VFileCreateEvent, VFileDeleteEvent, VFileEvent}
import intellij.pc.PresentationCompilerPluginService

final class PcFileListener(project: Project) extends BulkFileListener {
  override def after(events: util.List[_ <: VFileEvent]): Unit = {
    def getModule(file: VirtualFile) =
      Option(ProjectRootManager.getInstance(project).getFileIndex.getModuleForFile(file))
    val modules = events.asScala.flatMap {
      case createdEvent: VFileCreateEvent => getModule(createdEvent.getFile)
      case deletedEvent: VFileDeleteEvent => getModule(deletedEvent.getFile)
      case changeEvent: VFileContentChangeEvent =>
        project.getService(classOf[FileStateService]).modified(changeEvent.getFile)
        None
      case _ => None
    }.toSet
    if(modules.nonEmpty) project.getService(classOf[PresentationCompilerPluginService]).restarPc(modules)
  }
}
