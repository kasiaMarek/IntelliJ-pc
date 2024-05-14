package intellij.pc

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.openapi.extensions.Extensions

import com.intellij.openapi.project.Project

import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.util.KeyedLazyInstance

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.startup.ProjectActivity
import kotlin.coroutines.Continuation

class ReflectionUnregisterUtil extends ProjectActivity {

  val logger = Logger.getInstance(getClass.getName)

  override def execute(project: Project, continuation: Continuation[_ >: kotlin.Unit]): AnyRef = {
    val extensionPoint = Extensions.getRootArea.getExtensionPoint(CompletionContributor.EP.getName).asInstanceOf[ExtensionPoint[KeyedLazyInstance[CompletionContributor]]]
    extensionPoint.unregisterExtensions({(_, y) =>
      y.pluginDescriptor.getPluginId.toString == "completions.with.presenatation.compiler"
    }, false)
    project
  }
}
