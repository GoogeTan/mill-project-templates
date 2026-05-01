package katze.intellijmill.yaml

import org.jetbrains.yaml.psi.*

type YamlPossibleNonFileParent = YAMLMapping | YAMLSequence | YAMLSequenceItem | YAMLKeyValue | YAMLScalar | YAMLAnchor | YAMLAlias

object YamlPossibleNonFileParent:
  def unapply(value : YAMLPsiElement) : Option[YamlPossibleNonFileParent] =
    value match
      case nonRoot @ (
        _: YAMLMapping | _: YAMLSequence | _: YAMLSequenceItem | _: YAMLKeyValue | _: YAMLScalar | _: YAMLAlias | _ : YAMLAnchor
      ) =>
        Some(nonRoot)
      case _ => None
    end match
  end unapply
end YamlPossibleNonFileParent
