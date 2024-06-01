package intellij.pc.symbolSearch

import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.Project
import org.eclipse.lsp4j.Location

import java.net.URI
import java.util.{Collections, Optional}
import java.{util => ju}
import scala.meta.internal.metals.WorkspaceSymbolQuery
import scala.meta.pc.{ParentSymbols, SymbolDocumentation, SymbolSearch, SymbolSearchVisitor}

/** Implementation of SymbolSearch that delegates to WorkspaceSymbolProvider and
  * SymbolDocumentationIndexer.
  */
class MetalsSymbolSearch(wsp: WorkspaceSymbolProvider, project: Project)
    extends SymbolSearch {

  override def documentation(
      symbol: String,
      parents: ParentSymbols
  ): Optional[SymbolDocumentation] = Optional.empty()

  def definition(symbol: String, source: URI): ju.List[Location] =
    Collections.emptyList()

  override def definitionSourceToplevels(
      symbol: String,
      source: URI
  ): ju.List[String] = Collections.emptyList()

  override def search(
      query: String,
      moduleId: String,
      visitor: SymbolSearchVisitor
  ): SymbolSearch.Result = {
    def search(query: WorkspaceSymbolQuery, module: Module) = {
      wsp.search(
        query,
        visitor,
        module
      )
    }
    getModuleForId(moduleId).foreach { module =>
      val wQuery = WorkspaceSymbolQuery.exact(query)
      val count = search(wQuery, module)
      if (wQuery.isShortQuery && count == 0)
        search(
          WorkspaceSymbolQuery.exact(query, isShortQueryRetry = true),
          module
        )
    }
    SymbolSearch.Result.COMPLETE
  }

  override def searchMethods(
      query: String,
      moduleId: String,
      visitor: SymbolSearchVisitor
  ): SymbolSearch.Result = {
    getModuleForId(moduleId).foreach(
      wsp.searchMethods(
        query,
        visitor,
        _
      )
    )
    SymbolSearch.Result.COMPLETE
  }

  private def getModuleForId(id: String) = {
    ModuleManager.getInstance(project).getModules.find(_.getName == id)
  }
}
