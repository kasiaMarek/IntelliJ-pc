package intellij.pc

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.{ExtensionPoint, Extensions}
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.util.KeyedLazyInstance
import kotlin.coroutines.Continuation

import scala.util.control.NonFatal

class ReflectionUnregisterUtil extends ProjectActivity {

  private val logger = Logger.getInstance(getClass.getName)

  override def execute(
      project: Project,
      continuation: Continuation[_ >: kotlin.Unit]
  ): AnyRef = {
    try {
      val extensionPoint = Extensions.getRootArea
        .getExtensionPoint(CompletionContributor.EP.getName)
        .asInstanceOf[ExtensionPoint[KeyedLazyInstance[CompletionContributor]]]
      extensionPoint.unregisterExtensions(
        { (_, y) =>
          y.pluginDescriptor.getPluginId.toString == "completions.with.presenatation.compiler"
        },
        false
      )
    } catch {
      case NonFatal(e) =>
        logger.warn(
          "Failed to unregister completions.with.presenatation.compiler extension",
          e
        )
    }
    project
  }
}
