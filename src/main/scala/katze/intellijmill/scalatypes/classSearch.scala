package katze.intellijmill.scalatypes

import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt}
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType

def classSearch(psi : ScalaPsiManager, scope : GlobalSearchScope, className : String) : Option[PsiClass] =
  psi.getCachedClass(scope, className)
end classSearch


def scTypeSearch(psi : ScalaPsiManager, scope : GlobalSearchScope, className : String) : Option[ScType] =
  classSearch(psi, scope, className).map(ScDesignatorType(_))
end scTypeSearch

def extractTemplateDefinition(scType: ScType): Option[ScTemplateDefinition] = 
  val maybePsiClass: Option[PsiClass] = scType.extractClass
  maybePsiClass.collect:
    case templateDef: ScTemplateDefinition => templateDef
end extractTemplateDefinition    
