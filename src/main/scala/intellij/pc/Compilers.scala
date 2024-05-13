package intellij.pc

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.psi.search.{GlobalSearchScope, PsiShortNamesCache}
import dotty.tools.pc.ScalaPresentationCompiler

import java.nio.file.Paths
import scala.collection.concurrent.TrieMap
import scala.jdk.CollectionConverters._
import scala.meta.pc.PresentationCompiler


class Compilers(val project: Project) {
  val logger = Logger.getInstance(getClass.getName)
  private val compilers: TrieMap[module.Module, PresentationCompiler] = new TrieMap()

  def startPc(module0: module.Module): PresentationCompiler = {//TODO: restart pc when classpath changes (on compilation finish)
    val originalClasspath = OrderEnumerator.orderEntries(module0).recursively().withoutSdk().getClassesRoots.map(_.getPresentableUrl)
    val fullClasspath = (originalClasspath.toList).map(Paths.get(_))

    val cache = PsiShortNamesCache.getInstance(project)
    val searchScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module0, false)
    val intelliJSymbolSearch = new IntelliJSymbolSearch(cache, searchScope)

    val pc = new ScalaPresentationCompiler() //TODO: should be done via classloader
      .withSearch(intelliJSymbolSearch) //TODO: should not be included before indexing is finished, in the future should have metals implementation of workspace search
      .newInstance(module0.getName, fullClasspath.asJava, Nil.asJava)

    compilers.addOne(module0 -> pc)
    pc

  }

  def getPresentationCompiler(module0: module.Module): PresentationCompiler = {
    compilers.getOrElseUpdate(module0, startPc(module0))
  }
}

object Compilers {
  def getInstance(project: Project): Compilers = project.getService(classOf[PresentationCompilerPluginService]).getPresentationCompiler
}
