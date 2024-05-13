package intellij.pc


import com.google.gson.Gson
import com.intellij.codeInsight.completion.{CompletionContributor, CompletionInitializationContext, CompletionParameters, CompletionProvider, CompletionResultSet, CompletionType, PrioritizedLookupElement}
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons.Nodes
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.{Project, ProjectLocator, ProjectManager}
import com.intellij.openapi.roots.{CompilerModuleExtension, ProjectRootManager}
import com.intellij.patterns.{CharPattern, ElementPattern, PlatformPatterns, StandardPatterns}
import com.intellij.psi.{PsiClass, PsiElement, PsiFile}
import com.intellij.psi.impl.{FakePsiElement, RenameableFakePsiElement}
import com.intellij.psi.search.{FilenameIndex, GlobalSearchScope, PsiShortNamesCache}
import com.intellij.util.ProcessingContext
import org.eclipse.lsp4j
import org.eclipse.lsp4j.{CompletionItem, CompletionItemKind, CompletionItemTag, Location, SymbolKind}
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.caches.ScalaShortNamesCacheManager
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.completion.ScalaGlobalMembersCompletionContributor
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import java.net.URI
import java.nio.file.Paths
import java.util
import java.util.Optional
import scala.jdk.FutureConverters.CompletionStageOps

import javax.swing.Icon
import scala.meta.internal.metals.CompilerOffsetParams
import scala.meta.internal.pc.CompletionItemData
import scala.meta.pc.{ParentSymbols, PresentationCompiler, SymbolDocumentation, SymbolSearch, SymbolSearchVisitor}

import org.jetbrains.plugins.scala.extensions.executionContext.appExecutionContext
//import scala.meta.pc.OffsetParams
import org.jetbrains.plugins.scala.caches.ScalaShortNamesCache

import scala.jdk.CollectionConverters._

final class CompletionIdentifier(val item: CompletionItem, project: Project, file: PsiFile, element: PsiElement, val pc: PresentationCompiler) extends FakePsiElement {
  override def getParent: PsiElement = null

  override def getProject: Project = project

  override def getContainingFile: PsiFile = file

  override def getText: String = item.getLabel
}


object SemanticDbSymbolCreator {
  import com.intellij.psi._

  def createSemanticDbSymbol(element: PsiElement): Option[String] = element match {
    case psiClass: PsiClass             => Some(getClassSymbol(psiClass))
    case psiMethod: PsiMethod           => getMethodSymbol(psiMethod)
    case psiField: PsiField             => getFieldSymbol(psiField)
    case psiLocalVariable: PsiLocalVariable => getLocalVariableSymbol(psiLocalVariable)
    // Extend this with additional cases for other PsiElement types if necessary
    case _                              => None
  }

  private def getClassSymbol(psiClass: PsiClass): String = {
    val suffix = if (psiClass.getLanguage.is(ScalaLanguage.INSTANCE)) "."
    else "#"
    Option(psiClass.getQualifiedName)
      .map(_.replace(".", "/") + suffix)
      .getOrElse("")
  }

  private def getMethodSymbol(psiMethod: PsiMethod): Option[String] = {
    for {
      containingClass <- Option(psiMethod.getContainingClass)
      methodName <- Option(psiMethod.getName)
    } yield getClassSymbol(containingClass) + "." + methodName + "()"
  }

  private def getFieldSymbol(psiField: PsiField): Option[String] = {
    for {
      containingClass <- Option(psiField.getContainingClass)
      fieldName <- Option(psiField.getName)
    } yield getClassSymbol(containingClass) + "." + fieldName
  }

  private def getLocalVariableSymbol(psiLocalVariable: PsiLocalVariable): Option[String] = {
    Option(psiLocalVariable.getParent)
      .collect { case psiMethod: PsiMethod => psiMethod }
      .flatMap(psiMethod => getMethodSymbol(psiMethod))
      .map(methodSymbol => methodSymbol + "/" + psiLocalVariable.getName)
  }
}



final class PcCompletionProvider() extends CompletionContributor {
  val logger = Logger.getInstance(getClass.getName)
  logger.warn("Starting PCCompletionProvider")



  override def fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet): Unit = {
    val project = parameters.getEditor.getProject
    val pattern = StandardPatterns.string().withLength(4)
    result.restartCompletionOnPrefixChange(pattern)
    val compilersService = Compilers.getInstance(project)
    val file = parameters.getOriginalFile.getVirtualFile
    val module = ProjectRootManager.getInstance(project).getFileIndex.getModuleForFile(file)
    val pc = compilersService.getPresentationCompiler(module)

    val path = Paths.get(file.getCanonicalPath)
    val params = CompilerOffsetParams(path.toUri, parameters.getOriginalFile.getText, parameters.getOffset)

      pc.complete(params).get().getItems.asScala.toList.sortBy(_.getSortText).zipWithIndex.foreach { case (item, i) =>
      val element = LookupElementBuilder.create(item.getFilterText)
      val withTailText = if (item.getKind != CompletionItemKind.Field) {
        element.withTailText(item.getDetail)
      } else element.withTypeText(item.getDetail)
      val withDeprecation = if (item.getTags.asScala.contains(CompletionItemTag.Deprecated))
        withTailText.withStrikeoutness(true)
      else withTailText
      val withIcon = lspToIJIcon(item.getKind).map(icon => withDeprecation.withIcon(icon)).getOrElse(withDeprecation)
      val withFilterText = withIcon.withLookupString(item.getFilterText)
      val identifier = new CompletionIdentifier(item, project, parameters.getOriginalFile, parameters.getOriginalPosition, pc)
      val withPsiElement = withFilterText.withPsiElement(identifier)
      val withPriority = PrioritizedLookupElement.withPriority(withPsiElement, 1000 - i)
      result.addElement(withPriority)
    }
    result.stopHere() //this should stop IJ completions

    super.fillCompletionVariants(parameters, result)
  }

  private def lspToIJIcon(kind: CompletionItemKind): Option[Icon] = {
    kind match {
      case CompletionItemKind.Text => None
      case CompletionItemKind.Method => Some(Nodes.Method)
      case CompletionItemKind.Function => Some(Icons.FUNCTION)
      case CompletionItemKind.Constructor => Some(Nodes.Method)
      case CompletionItemKind.Field => Some(Icons.FIELD_VAL)
      case CompletionItemKind.Variable => Some(Icons.FIELD_VAR)
      case CompletionItemKind.Class => Some(Icons.CLASS)
      case CompletionItemKind.Interface => Some(Icons.TRAIT)
      case CompletionItemKind.Module => Some(Icons.OBJECT)
      case CompletionItemKind.Property => None
      case CompletionItemKind.Unit => None
      case CompletionItemKind.Value => Some(Icons.PARAMETER)
      case CompletionItemKind.Enum => Some(Icons.ENUM)
      case CompletionItemKind.Keyword => None
      case CompletionItemKind.Snippet => None
      case CompletionItemKind.Color => None
      case CompletionItemKind.File => None
      case CompletionItemKind.Reference => None
      case CompletionItemKind.Folder => None
      case CompletionItemKind.EnumMember => Some(Icons.ENUM)
      case CompletionItemKind.Constant => Some(Icons.VAL)
      case CompletionItemKind.Struct => Some(Icons.ABSTRACT_CLASS)
      case CompletionItemKind.Event => None
      case CompletionItemKind.Operator => None
      case CompletionItemKind.TypeParameter => Some(Icons.TYPE_ALIAS)
    }

  }

}
