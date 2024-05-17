package intellij.pc

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons.Nodes
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.patterns.StandardPatterns
import org.eclipse.lsp4j.{
  CompletionItemKind,
  CompletionItemTag,
  Position,
  TextEdit
}
import org.jetbrains.plugins.scala.debugger.ui.util.CompletableFutureOps
import org.jetbrains.plugins.scala.extensions.executionContext.appExecutionContext
import org.jetbrains.plugins.scala.icons.Icons
import java.nio.file.Paths

import javax.swing.Icon
import scala.jdk.CollectionConverters._
import scala.meta.internal.metals.CompilerOffsetParams

import com.intellij.openapi.application.{ApplicationManager, WriteAction}
import com.intellij.openapi.editor.{Document, Editor}

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
              val element = LookupElementBuilder
                .create(item.getTextEdit.getLeft.getNewText)
                .withInsertHandler { (context, _) =>
                  val edits = Option(
                    item.getAdditionalTextEdits
                  ).toList.flatMap(_.asScala.toList)
                  applyEdits(context.getEditor, context.getDocument, edits)
                }
                .withRenderer { (_, presentation) =>
                  presentation.setItemText(item.getLabel)
                  if (item.getKind != CompletionItemKind.Field) {
                    presentation.setTailText(item.getDetail)
                  } else presentation.setTypeText(item.getDetail)
                  lspToIJIcon(item.getKind).foreach(presentation.setIcon)
                  val isDeprecated =
                    item.getTags != null && item.getTags.asScala.contains(
                      CompletionItemTag.Deprecated
                    )
                  if (isDeprecated) presentation.setStrikeout(true)
                }
                .withLookupString(item.getFilterText)
              PrioritizedLookupElement.withPriority(element, 1000 - i)
            }
        }
      ApplicationUtil.runWithCheckCanceled(
        completions,
        ProgressIndicatorProvider.getInstance().getProgressIndicator
      )
      result.addAllElements(completions.get().asJava)
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

  // copied from: https://github.com/redhat-developer/lsp4ij
  def applyEdits(
      editor: Editor,
      document: Document,
      edits: List[TextEdit]
  ): Unit = {
    if (ApplicationManager.getApplication.isWriteAccessAllowed)
      edits.foreach(edit => applyEdit(editor, edit, document))
    else
      WriteAction.run(() =>
        edits.foreach(edit => applyEdit(editor, edit, document))
      )
  }

  private def applyEdit(
      editor: Editor,
      textEdit: TextEdit,
      document: Document
  ): Unit = {
    val marker = document.createRangeMarker(
      toOffset(textEdit.getRange.getStart, document),
      toOffset(textEdit.getRange.getEnd, document)
    )
    marker.setGreedyToRight(true)
    val startOffset = marker.getStartOffset
    val endOffset = marker.getEndOffset
    var text = textEdit.getNewText
    if (text != null) text = text.replaceAll("\r", "")
    if (text == null || text.isEmpty)
      document.deleteString(startOffset, endOffset)
    else if (endOffset - startOffset <= 0)
      document.insertString(startOffset, text)
    else document.replaceString(startOffset, endOffset, text)
    if (text != null && text.nonEmpty)
      editor.getCaretModel.moveToOffset(marker.getEndOffset)
    marker.dispose()
  }

  private def toOffset(position: Position, document: Document): Int = {
    val line = position.getLine
    if (line >= document.getLineCount) {
      document.getTextLength
    } else if (line < 0) 0
    else {
      val lineOffset = document.getLineStartOffset(line)
      val nextLineOffset = document.getLineEndOffset(line)
      Math.max(
        Math.min(lineOffset + position.getCharacter, nextLineOffset),
        lineOffset
      )
    }
  }

}
