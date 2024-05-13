package intellij.pc

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

class PresentationCompilerPluginService(val project: Project) {
  private val presentationCompiler = new Compilers(project)
  def getPresentationCompiler: Compilers = presentationCompiler
}
