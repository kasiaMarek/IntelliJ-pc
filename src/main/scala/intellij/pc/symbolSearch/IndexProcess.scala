package intellij.pc.symbolSearch

import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager}
import com.intellij.openapi.project.Project
import intellij.pc.util.PerformanceUtils

import java.util.concurrent.atomic.AtomicBoolean

final class IndexProcess {
  val isReindexingWorkspace: AtomicBoolean = new AtomicBoolean(false)

  def startWorkspaceIndexing(project: Project): Unit = if (!isReindexingWorkspace.get()) {
    isReindexingWorkspace.set(true)

    ProgressManager
      .getInstance
      .run(new Backgroundable(project, "Indexing workspace...", true) {
        override def run(indicator: ProgressIndicator): Unit = {
          PerformanceUtils.timed("Workspace indexing") {
            val indexingIndicator = IndexingProgressIndicator(indicator)
            try {
              project.getService(classOf[Indexer]).indexWorkspace(indexingIndicator)
            } finally {
              isReindexingWorkspace.set(false)
            }
          }
        }
      }.setCancelText("Stop Workspace Indexation"))
  }
}

object IndexProcess {
  lazy val getInstance: IndexProcess = new IndexProcess
}