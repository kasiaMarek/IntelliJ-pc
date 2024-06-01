package intellij.pc.symbolSearch

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{OrderEnumerator, ProjectRootManager}

import java.nio.file.{Files, Path}
import scala.collection.concurrent.TrieMap
import scala.meta.internal.metals.{Fuzzy, WorkspaceSymbolInformation, WorkspaceSymbolQuery}
import scala.meta.pc.SymbolSearchVisitor

final class WorkspaceSymbolProvider(project: Project) {
  private val logger = Logger.getInstance(getClass.getName)
  private val MaxWorkspaceMatchesForShortQuery = 100
  private val inWorkspace: TrieMap[Path, WorkspaceSymbolsIndex] =
    TrieMap.empty[Path, WorkspaceSymbolsIndex]
  private val inWorkspaceMethods
      : TrieMap[Path, Seq[WorkspaceSymbolInformation]] =
    TrieMap.empty[Path, Seq[WorkspaceSymbolInformation]]

  val symbolSearch = new MetalsSymbolSearch(this, project)

  def didChange(
      source: Path,
      symbols: Seq[WorkspaceSymbolInformation],
      methodSymbols: Seq[WorkspaceSymbolInformation]
  ): Unit = {
    val bloom = Fuzzy.bloomFilterSymbolStrings(symbols.map(_.symbol))
    inWorkspace(source) = WorkspaceSymbolsIndex(bloom, symbols)

    if (methodSymbols.nonEmpty)
      inWorkspaceMethods(source) = methodSymbols
  }

  def searchMethods(
      query: String,
      visitor: SymbolSearchVisitor,
      module: Module
  ): Unit = {
    val fileIndex = ProjectRootManager.getInstance(project).getFileIndex
    OrderEnumerator.orderEntries(module).recursively().forEachModule { module =>
      Indexer.forAllSourceFiles(
        fileIndex,
        module,
        ProgressIndicatorWrapper.empty,
        { source =>
          val path = source.toNioPath
          for {
            symbols <- inWorkspaceMethods.get(path)
            isDeleted = !Files.isRegularFile(path)
            _ = if (isDeleted) inWorkspaceMethods.remove(path)
            if !isDeleted
            symbol <- symbols
            if Fuzzy.matches(query, symbol.symbol)
          }
            visitor.visitWorkspaceSymbol(
              path,
              symbol.symbol,
              symbol.kind,
              symbol.range
            )
        }
      )
      true
    }
  }

  def search(
      query: WorkspaceSymbolQuery,
      visitor: SymbolSearchVisitor,
      module: Module
  ): Int = {
    val fileIndex = ProjectRootManager.getInstance(project).getFileIndex
    var count = 0
    OrderEnumerator.orderEntries(module).recursively().forEachModule { module =>
      Indexer.forAllSourceFiles(
        fileIndex,
        module,
        ProgressIndicatorWrapper.empty,
        { source =>
          val path = source.toNioPath
          for {
            index <- inWorkspace.get(path)
            if query.matches(index.bloom)
            symbol <- index.symbols
            if query.matches(symbol.symbol)
            if !query.isShortQuery || count < MaxWorkspaceMatchesForShortQuery
          } {
            val added = visitor.visitWorkspaceSymbol(
              path,
              symbol.symbol,
              symbol.kind,
              symbol.range
            )
            count += added
          }
        }
      )
      true
    }
    count
  }
}
