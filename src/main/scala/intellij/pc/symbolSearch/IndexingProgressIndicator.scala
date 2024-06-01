package intellij.pc.symbolSearch

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator

trait ProgressIndicatorWrapper {
  def step(procesedEntry: String): Unit
  def nextProgress(name: String, totalSize: Option[Int]): Unit
  def isCancelled: Boolean
}

object ProgressIndicatorWrapper {
  def empty: ProgressIndicatorWrapper = new ProgressIndicatorWrapper {
    override def step(procesedEntry: String): Unit = ()
    override def nextProgress(name: String, totalSize: Option[Int]): Unit = ()
    override def isCancelled: Boolean = false
  }
}

case class IndexingProgressIndicator(progressIndicator: ProgressIndicator) extends ProgressIndicatorWrapper {
  final private val logger = Logger.getInstance(classOf[IndexingProgressIndicator])

  progressIndicator.setIndeterminate(false)

  private var progressName = ""
  private var allFiles: Option[Int] = None
  private var processed = 0

  def isCancelled: Boolean = progressIndicator.isCanceled

  /**
   * Increments the count if present, updates [[text2]]
   */
  def step(processedEntry: String): Unit =
    allFiles match {
      case Some(allFiles) =>
        processed += 1
        progressIndicator.setText(s"$progressName [$processed / $allFiles]")
        progressIndicator.setText2(processedEntry)
        progressIndicator.setFraction(processed.toDouble / allFiles.toDouble)
      case None =>
        logger.warn("Trying to step on progress bar without known size.")
    }

  def nextProgress(name: String, totalSize: Option[Int]): Unit = {
    allFiles = totalSize
    progressName = name
    // reset
    progressIndicator.setIndeterminate(totalSize.isEmpty)
    progressIndicator.setFraction(0d)
    progressIndicator.setText2("")

    // start next progress
    progressIndicator.setText(progressName)
  }


}
