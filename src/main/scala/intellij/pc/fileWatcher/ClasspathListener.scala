package intellij.pc.fileWatcher

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{ModuleRootEvent, ModuleRootListener}
import intellij.pc.PresentationCompilerPluginService

final class ClasspathListener(project: Project) extends ModuleRootListener {

  override def beforeRootsChange(event: ModuleRootEvent): Unit = {
    project.getService(classOf[PresentationCompilerPluginService]).stopPc()
    super.beforeRootsChange(event)
  }

}
