package katze.intellijmill.yaml

import com.intellij.psi.{PsiElement, PsiErrorElement, PsiWhiteSpace}
import com.intellij.psi.impl.source.tree.LeafPsiElement

type AutocompletePosition = LeafPsiElement | PsiWhiteSpace | PsiErrorElement

object AutocompletePosition:
  def unapply(element : PsiElement) : Option[AutocompletePosition & element.type] =
    element match
      case a : LeafPsiElement => Some(a.asInstanceOf[LeafPsiElement & element.type])
      case a : PsiWhiteSpace => Some(a.asInstanceOf[PsiWhiteSpace & element.type])
      case a : PsiErrorElement => Some(a.asInstanceOf[PsiErrorElement & element.type])
      case _ => None
  end unapply
end AutocompletePosition
