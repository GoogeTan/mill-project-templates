package katze.intellijmill.yaml

import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.{PsiElement, PsiErrorElement, PsiWhiteSpace}
import org.jetbrains.yaml.psi.*

type ParentOfCompletionTarget = YAMLScalar | YAMLKeyValue | YAMLAlias | YAMLAnchor
 
object ParentOfCompletionTarget:
  def unapply(element : YAMLPsiElement) : Option[ParentOfCompletionTarget & element.type] =
    element match
      case a: YAMLScalar => Some(a.asInstanceOf)
      case a : YAMLKeyValue => Some(a.asInstanceOf)
      case a : YAMLAlias => Some(a.asInstanceOf)
      case a : YAMLAnchor => Some(a.asInstanceOf)
      case _ => None
    end match
  end unapply
end ParentOfCompletionTarget

object CompletionTarget:
  def unapply(element : AutocompletePosition) : Option[(ParentOfCompletionTarget, element.type)] =
    element.getParent match
      case ParentOfCompletionTarget(parent) => Some((parent, element))
      case _ => None
    end match
  end unapply
end CompletionTarget

