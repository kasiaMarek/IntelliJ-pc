package intellij.pc

import com.google.gson.Gson
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionInitializationContext
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons.Nodes
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.wm.IdeFrame
import com.intellij.patterns.CharPattern
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.FakePsiElement
import com.intellij.psi.impl.RenameableFakePsiElement
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.util.ProcessingContext
import org.eclipse.lsp4j
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.CompletionItemTag
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.SymbolKind
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.caches.ScalaShortNamesCacheManager
import org.jetbrains.plugins.scala.debugger.ui.util.CompletableFutureOps
import org.jetbrains.plugins.scala.extensions.executionContext.appExecutionContext
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.completion.ScalaGlobalMembersCompletionContributor
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

import java.net.URI
import java.nio.file.Paths
import java.util
import java.util.Optional
import javax.swing.Icon
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters.CompletionStageOps
import scala.meta.internal.metals.CompilerOffsetParams

final class PcCompletionProvider()
    extends CompletionContributor
    with DumbAware {
  val logger = Logger.getInstance(getClass.getName)
  logger.warn("Starting PCCompletionProvider")

  override def fillCompletionVariants(
      parameters: CompletionParameters,
      result: CompletionResultSet
  ): Unit = {
    val project = parameters.getEditor.getProject
    logger.warn(s"completions for: ${parameters.getOriginalFile.getName}")
    val pattern = StandardPatterns.string().withLength(4)
    result.restartCompletionOnPrefixChange(pattern)
    val compilersService =
      project.getService(classOf[PresentationCompilerPluginService])
    val file = parameters.getOriginalFile.getVirtualFile
    val module = ProjectRootManager
      .getInstance(project)
      .getFileIndex
      .getModuleForFile(file)
    logger.warn(
      s"module for: ${if (module != null) module.getName else "null"}"
    )
    val optPc = Option(module).flatMap(compilersService.getPresentationCompiler)

    val path = Paths.get(file.getCanonicalPath)
    val params = CompilerOffsetParams(
      path.toUri,
      parameters.getOriginalFile.getText,
      parameters.getOffset
    )

    optPc.foreach { pc =>
      val completions = pc
        .complete(params)
        .map { res =>
          res.getItems.asScala.toList
            .sortBy(_.getSortText)
            .zipWithIndex
            .map { case (item, i) =>
              val element = LookupElementBuilder.create(item.getFilterText)
              val withTailText = if (item.getKind != CompletionItemKind.Field) {
                element.withTailText(item.getDetail)
              } else element.withTypeText(item.getDetail)
              val withDeprecation =
                if (
                  item.getTags != null && item.getTags.asScala.contains(
                    CompletionItemTag.Deprecated
                  )
                )
                  withTailText.withStrikeoutness(true)
                else withTailText
              val withIcon = lspToIJIcon(item.getKind)
                .map(icon => withDeprecation.withIcon(icon))
                .getOrElse(withDeprecation)
              val withFilterText = withIcon.withLookupString(item.getFilterText)
              val withPriority =
                PrioritizedLookupElement.withPriority(withFilterText, 1000 - i)
              withPriority
            }
        }
      ApplicationUtil.runWithCheckCanceled(
        completions,
        ProgressIndicatorProvider.getInstance().getProgressIndicator
      )
      result.addAllElements(completions.get().asJava)
      result.stopHere()
    }
  }

  private def lspToIJIcon(kind: CompletionItemKind): Option[Icon] = {
    kind match {
      case CompletionItemKind.Text          => None
      case CompletionItemKind.Method        => Some(Nodes.Method)
      case CompletionItemKind.Function      => Some(Icons.FUNCTION)
      case CompletionItemKind.Constructor   => Some(Nodes.Method)
      case CompletionItemKind.Field         => Some(Icons.FIELD_VAL)
      case CompletionItemKind.Variable      => Some(Icons.FIELD_VAR)
      case CompletionItemKind.Class         => Some(Icons.CLASS)
      case CompletionItemKind.Interface     => Some(Icons.TRAIT)
      case CompletionItemKind.Module        => Some(Icons.OBJECT)
      case CompletionItemKind.Property      => None
      case CompletionItemKind.Unit          => None
      case CompletionItemKind.Value         => Some(Icons.PARAMETER)
      case CompletionItemKind.Enum          => Some(Icons.ENUM)
      case CompletionItemKind.Keyword       => None
      case CompletionItemKind.Snippet       => None
      case CompletionItemKind.Color         => None
      case CompletionItemKind.File          => None
      case CompletionItemKind.Reference     => None
      case CompletionItemKind.Folder        => None
      case CompletionItemKind.EnumMember    => Some(Icons.ENUM)
      case CompletionItemKind.Constant      => Some(Icons.VAL)
      case CompletionItemKind.Struct        => Some(Icons.ABSTRACT_CLASS)
      case CompletionItemKind.Event         => None
      case CompletionItemKind.Operator      => None
      case CompletionItemKind.TypeParameter => Some(Icons.TYPE_ALIAS)
    }

  }

}
