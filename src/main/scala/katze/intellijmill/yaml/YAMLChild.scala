package katze.intellijmill.yaml

import org.jetbrains.yaml.psi.YAMLPsiElement

object YAMLChild:
  def unapply(element: YAMLPsiElement | AutocompletePosition) : Option[(YamlPossibleParent, element.type)] =
    element.getParent match
      case YamlPossibleParent(parent) => Some((parent, element))
      case _ => None
    end match
  end unapply
end YAMLChild