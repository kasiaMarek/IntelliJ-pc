package intellij.pc.fileWatcher

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil

class PcFileOpenedListener(project: Project) extends FileEditorManagerListener {

  override def selectionChanged(event: FileEditorManagerEvent) = {
    if (event.getNewFile != null) {
      ReadAction
        .nonBlocking[Unit] { () =>
          project.getService(classOf[FileStateService]).inFocusChanged()
        }
        .submit(AppExecutorUtil.getAppExecutorService)
    }
  }

}
