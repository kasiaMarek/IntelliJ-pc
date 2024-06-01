package intellij.pc.symbolSearch

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager}
import com.intellij.openapi.project.Project
import com.jetbrains.rd.util.AtomicReference
import intellij.pc.util.PerformanceUtils

import java.nio.file.Path
import scala.meta.internal.metals.{ClasspathSearch, CompressedPackageIndex, ExcludedPackagesHandler, PackageIndex}

final class LazyClasspathSearch(project: Project, classpath: Seq[Path]) {
  private val logger = Logger.getInstance(getClass.getName)
  private val search: AtomicReference[ClasspathSearch] = new AtomicReference[ClasspathSearch](ClasspathSearch.empty)

  def getSearch: ClasspathSearch = search.get()

  private def watchingClasspathSearch(indicator: ProgressIndicatorWrapper): ClasspathSearch.Indexer = {
      (classpath, excludePackages, bucketSize) => {
        indicator.nextProgress("Indexing classpath", Some(classpath.size))
        val packages = WatchedPackageIndex.fromClasspath(
          classpath,
          excludePackages.isExcludedPackage,
          indicator
        )

        indicator.nextProgress("Compressing classpath", None)
        val map = CompressedPackageIndex.fromPackages(
          packages,
          excludePackages.isExcludedPackage,
          bucketSize
        )
        new ClasspathSearch(map)
      }
  }

  ProgressManager
    .getInstance
    .run(new Backgroundable(project, "Indexing classpath...", true) {
      override def run(indicator: ProgressIndicator): Unit = {
        PerformanceUtils.timed("Classpath indexing") {
          try {
            val indexingProgress = IndexingProgressIndicator(indicator)
            logger.warn("Starting classpath indexation")
            val indexedSearch = watchingClasspathSearch(indexingProgress).index(classpath, ExcludedPackagesHandler.default)
            search.getAndSet(indexedSearch)
          } finally {
            indicator.setIndeterminate(false)
          }
        }
      }
    }.setCancelText("Stop Classpath Indexation"))
}

private class WatchedPackageIndex(indicator: ProgressIndicatorWrapper) extends PackageIndex {

  override def visit(entry: Path): Unit =
    if (!indicator.isCancelled) {
      indicator.step(entry.toString)
      super.visit(entry)
    }
}

object WatchedPackageIndex {
  def fromClasspath(
                     classpath: collection.Seq[Path],
                     isExcludedPackage: String => Boolean,
                     progressIndicator: ProgressIndicatorWrapper,
                   ): PackageIndex = {
    val packages = new WatchedPackageIndex(progressIndicator)
    packages.visitBootClasspath(isExcludedPackage)
    classpath.foreach { path => packages.visit(path) }
    packages
  }
}


