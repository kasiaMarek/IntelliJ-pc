package intellij.pc.fileWatcher

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import intellij.pc.PresentationCompilerPluginService

import java.util.concurrent.atomic.AtomicReference

class FileStateService(project: Project) {
  private val modifiedFiles = new AtomicReference[Set[VirtualFile]](Set.empty)

  def inFocusChanged(): Unit = {
    val modules = modifiedFiles.getAndSet(Set.empty).flatMap { file =>
      Option(
        ProjectRootManager
          .getInstance(project)
          .getFileIndex
          .getModuleForFile(file)
      )
    }
    if (modules.nonEmpty) {
      project
        .getService(classOf[PresentationCompilerPluginService])
        .restarPc(modules)
    }
  }

  def modified(vFile: VirtualFile): Unit = {
    modifiedFiles.updateAndGet(_ + vFile)
  }

}
