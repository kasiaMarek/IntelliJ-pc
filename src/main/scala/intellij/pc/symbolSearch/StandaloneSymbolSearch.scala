package intellij.pc.symbolSearch

import java.net.URI
import java.nio.file.Path
import java.util
import java.util.{Collections, Optional}
import scala.meta.internal.metals.{
  ClasspathSearch,
  ExcludedPackagesHandler,
  WorkspaceSymbolQuery
}
import scala.meta.pc.{
  ParentSymbols,
  SymbolDocumentation,
  SymbolSearch,
  SymbolSearchVisitor
}

import org.eclipse.lsp4j.Location

class StandaloneSymbolSearch(
    classpath: Seq[Path],
    workspaceSearch: SymbolSearch
) extends SymbolSearch {

  private val classpathSearch =
    ClasspathSearch.fromClasspath(classpath, ExcludedPackagesHandler.default)

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
    classpathSearch.search(WorkspaceSymbolQuery.exact(query), visitor)
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
