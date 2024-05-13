package intellij.pc

import com.intellij.psi.search.{GlobalSearchScope, PsiShortNamesCache}
import org.eclipse.lsp4j
import org.eclipse.lsp4j.{Location, SymbolKind}

import java.net.URI
import java.nio.file.Paths
import java.util
import java.util.Optional
import scala.meta.pc.{ParentSymbols, SymbolDocumentation, SymbolSearch, SymbolSearchVisitor}
import scala.jdk.CollectionConverters._

final class IntelliJSymbolSearch(cache: PsiShortNamesCache, searchScope: GlobalSearchScope) extends SymbolSearch {

  override def documentation(symbol: String, parents: ParentSymbols): Optional[SymbolDocumentation] = Optional.empty()

  override def definition(symbol: String, sourceUri: URI): util.List[Location] = Nil.asJava

  override def definitionSourceToplevels(symbol: String, sourceUri: URI): util.List[String] = Nil.asJava

  override def search(query: String, buildTargetIdentifier: String, visitor: SymbolSearchVisitor): SymbolSearch.Result = {
    //TODO: This is very slow. either make async / use copy of metals symbol search / no symbol search
    if (query.length > 3) {
      val allMatchingClasses = cache.getAllClassNames.filter(_.startsWith(query))
      allMatchingClasses.map { classname =>
        val classes = cache.getClassesByName(classname, searchScope)
        classes.flatMap { psiClass =>
          SemanticDbSymbolCreator.createSemanticDbSymbol(psiClass)
        }.map { semanticDbSymbol =>
          visitor.visitWorkspaceSymbol(Paths.get(""), semanticDbSymbol, SymbolKind.Null, new lsp4j.Range())
        }
      }
    }
    SymbolSearch.Result.COMPLETE
  }


  override def searchMethods(query: String, buildTargetIdentifier: String, visitor: SymbolSearchVisitor): SymbolSearch.Result =
    SymbolSearch.Result.COMPLETE
}
