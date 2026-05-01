package katze.intellijmill

import cats.Monad
import cats.syntax.all.*
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.ProcessingContext
import katze.intellijmill.scalatypes.{extractTemplateDefinition, scTypeSearch}
import katze.intellijmill.yaml.{AutocompletePosition, YAMLChild, YAMLGrandChild}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt}
import org.jetbrains.yaml.YAMLLanguage
import org.jetbrains.yaml.psi.*

import scala.util.matching.Regex

final class YamlCompletionContributor extends CompletionContributor:
  extend(
    CompletionType.BASIC,
    PlatformPatterns.psiElement().withLanguage(YAMLLanguage.INSTANCE),
    new CompletionProvider[CompletionParameters]:
      override def addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        resultSet: CompletionResultSet
      ): Unit =
        val currentElement = parameters.getPosition.asInstanceOf[AutocompletePosition]
        val project = currentElement.getProject
        val search = (text :String) =>
          scTypeSearch(ScalaPsiManager.instance(project), GlobalSearchScope.allScope(project), text)
            .toRight(s"Couldn't find type $text")

        scopeOf[[T] =>> Either[String, T], CurrentScope[ScType]](
          element = currentElement,
          fieldScope = (scope, field) => fieldScope(scope, field).toRight("Couldn't find type"),
          objectScope = objectScope(
            _,
            _,
            _,
            search,
            (name, tie) => Left(s"Expected object body but got method right hand side $name with type $tie")
          ),
          sequenceScope = scope => sequenceScope[[T] =>> Either[String, T]](
            scope,
            Left(s"Expected RHS but got object $scope"),
            Left(s"Expected sequence scope but got $scope"),
          ),
          extendsOf = extendsOf(_, search),
          somethingElse = got => Left(s"Couldn't parse PSI. Got $got of class ${got.getClass} with parent ${got.getParent}")
        ).fold(
          println(_),
          {
            case CurrentScope.ObjectDefinition(extendList) =>
              println(s"Sxope: ${extendList}")
              extendList
                .flatMap(extractTemplateDefinition)
                .flatMap(getParameterlessMembersWithoutTask)
                .foreach((name, tie) =>
                  resultSet.addElement(
                    LookupElementBuilder
                      .create(name)
                      .withTypeText(tie.toString)
                      .withIcon(AllIcons.Nodes.Property)
                  )
                )
            case CurrentScope.OverrideRightHandSide(name, expectedType) =>
              extractTemplateDefinition(expectedType)
                .flatMap(getParameterlessMembersWithoutTask)
                .foreach((name, tie) =>
                  resultSet.addElement(
                    LookupElementBuilder
                      .create(name)
                      .withTypeText(tie.toString)
                      .withIcon(AllIcons.Nodes.Property)
                      .withInsertHandler(YamlKeyInsertHandler)
                  )
                )
          }
        )
  )

  val objectNameRegex: Regex = "^[a-zA-Z_$][\\w$]*$".r

  def scopeOf[F[_] : Monad, Scope](
    element: YAMLPsiElement | AutocompletePosition,
    fieldScope : (Scope, String) => F[Scope],
    objectScope : (Scope, String, YAMLMapping) => F[Scope],
    sequenceScope : Scope => F[Scope],
    extendsOf : YAMLMapping => F[Scope],
    somethingElse : PsiElement => F[Scope]
  ): F[Scope] =
    element match
      case YAMLChild(mapping: YAMLMapping, kv : YAMLScalar) =>
        scopeOf(mapping, fieldScope, objectScope, sequenceScope, extendsOf, somethingElse)
      case YAMLGrandChild(mapping: YAMLMapping, kv : YAMLKeyValue, self) =>
        self match
          case _ if kv.getKey == self  =>
            scopeOf(mapping, fieldScope, objectScope, sequenceScope, extendsOf, somethingElse)
          case self : YAMLMapping if kv.getValue == self && kv.getKeyText.matches(s"object $objectNameRegex") =>
            scopeOf(mapping, fieldScope, objectScope, sequenceScope, extendsOf, somethingElse)
              .flatMap(
                objectScope(_, kv.getKeyText, self)
              )
          case self if kv.getValue == self  =>
            scopeOf(mapping, fieldScope, objectScope, sequenceScope, extendsOf, somethingElse)
              .flatMap(
                fieldScope(_, kv.getKeyText)
              )
          case el =>
            somethingElse(el)
        end match
      case YAMLGrandChild(seq: YAMLSequence, _ : YAMLSequenceItem, self) =>
        scopeOf(seq, fieldScope, objectScope, sequenceScope, extendsOf, somethingElse).flatMap(sequenceScope)
      case YAMLGrandChild(document : YAMLDocument, parent : YAMLMapping, self) =>
        extendsOf(parent)

      case YAMLChild(parent : YAMLDocument, m : YAMLMapping) =>
        extendsOf(m)

      case YAMLChild(parent : YAMLPsiElement, AutocompletePosition(self)) =>
        scopeOf(parent, fieldScope, objectScope, sequenceScope, extendsOf, somethingElse)

      case elseElement =>
        somethingElse(elseElement)
    end match
  end scopeOf
end YamlCompletionContributor
