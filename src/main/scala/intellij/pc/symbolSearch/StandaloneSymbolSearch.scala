package intellij.pc.symbolSearch

import com.intellij.openapi.project.Project
import org.eclipse.lsp4j.Location

import java.net.URI
import java.nio.file.Path
import java.util
import java.util.{Collections, Optional}
import scala.meta.internal.metals.WorkspaceSymbolQuery
import scala.meta.pc.{ParentSymbols, SymbolDocumentation, SymbolSearch, SymbolSearchVisitor}

final class StandaloneSymbolSearch(
    project: Project,
    classpath: Seq[Path],
    workspaceSearch: SymbolSearch,
) extends SymbolSearch {

  val lazyClasspathSearch = new LazyClasspathSearch(project, classpath)

  override def documentation(
      symbol: String,
      parents: ParentSymbols
  ): Optional[SymbolDocumentation] = Optional.empty()

  override def definition(symbol: String, sourceUri: URI): util.List[Location] =
    Collections.emptyList()

  override def definitionSourceToplevels(
      symbol: String,
      sourceUri: URI
  ): util.List[String] = Collections.emptyList()

  override def search(
      query: String,
      buildTargetIdentifier: String,
      visitor: SymbolSearchVisitor
  ): SymbolSearch.Result = {
    lazyClasspathSearch.getSearch.search(WorkspaceSymbolQuery.exact(query), visitor)
    workspaceSearch.search(query, buildTargetIdentifier, visitor)
  }

  override def searchMethods(
      query: String,
      buildTargetIdentifier: String,
      visitor: SymbolSearchVisitor
  ): SymbolSearch.Result = {
    workspaceSearch.searchMethods(query, buildTargetIdentifier, visitor)
  }

}
