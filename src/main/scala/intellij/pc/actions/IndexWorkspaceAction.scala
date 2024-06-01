package intellij.pc.actions

import com.intellij.openapi.actionSystem.{ActionUpdateThread, AnAction, AnActionEvent}
import com.intellij.openapi.diagnostic.Logger
import intellij.pc.symbolSearch.IndexProcess

final class IndexWorkspaceAction extends AnAction("Reindex Workspace") {
  private def indexProcess    = IndexProcess.getInstance
  final private val logger = Logger.getInstance(classOf[IndexWorkspaceAction])

  override def actionPerformed(event: AnActionEvent): Unit = {
    try {
      val project   = event.getProject
      val workspace = project.getBasePath
      if (!indexProcess.isReindexingWorkspace.get()) indexProcess.startWorkspaceIndexing(project)
      logger.debug(s"Workspace reindex started: $workspace")
    } catch {
      case e => logger.error("Error occurred during workspace indexing", e)
    }
  }

  override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.BGT

  override def update(event: AnActionEvent): Unit = {
    event
      .getPresentation
      .setEnabled(!indexProcess.isReindexingWorkspace.get())
  }

}