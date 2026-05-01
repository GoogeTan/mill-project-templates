package katze.intellijmill.yaml

import org.jetbrains.yaml.psi.{YAMLDocument, YAMLFile, YAMLPsiElement}

type YamlPossibleParent = YamlPossibleNonFileParent | YAMLFile | YAMLDocument

object YamlPossibleParent:
  def unapply(value : YAMLPsiElement) : Option[YamlPossibleParent] =
    value match
      case YamlPossibleNonFileParent(value) =>
        Some(value)
      case document: YAMLDocument => Some(document)
      case file: YAMLFile => Some(file)
      case _ => None
    end match
  end unapply
end YamlPossibleParent
