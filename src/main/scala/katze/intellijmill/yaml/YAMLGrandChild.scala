package katze.intellijmill.yaml

import com.intellij.psi.PsiElement
import org.jetbrains.yaml.psi.YAMLPsiElement


object YAMLGrandChild:
  def unapply(element: PsiElement) : Option[(YamlPossibleParent, YamlPossibleParent, element.type)] =
    element match
      case YAMLChild(YAMLChild(grandParent, parent), self) => Some((grandParent, parent, element))
      case _ => None
    end match
  end unapply
end YAMLGrandChild
