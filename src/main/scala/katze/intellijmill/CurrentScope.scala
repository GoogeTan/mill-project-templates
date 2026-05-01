package katze.intellijmill

import cats.Monad
import cats.syntax.all.*
import katze.intellijmill.scalatypes.extractTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.{BaseTypes, ScType, ScTypeExt}
import org.jetbrains.plugins.scala.lang.psi.types.api.ParameterizedType
import org.jetbrains.yaml.psi.{YAMLKeyValue, YAMLMapping, YAMLScalar, YAMLSequence, YAMLSequenceItem}

import scala.jdk.CollectionConverters.*

enum CurrentScope[Type]:
  case ObjectDefinition(extendList: List[Type])
  case OverrideRightHandSide(name : String, expectedType: Type)
end CurrentScope

def objectScope[F[_] : Monad](
  scope: CurrentScope[ScType],
  objectName: String,
  objectYaml: YAMLMapping,
  classSource : String => F[ScType],
  onRightHandSide : (String, ScType) => F[CurrentScope[ScType]]
) : F[CurrentScope[ScType]] =
  scope match
    case CurrentScope.ObjectDefinition(extendList) =>
      extendsOf(objectYaml, classSource)
    case CurrentScope.OverrideRightHandSide(name, tie) =>
      onRightHandSide(name, tie)
end objectScope

def extendsOf[F[_] : Monad](
  mapping: YAMLMapping,
  classSource : String => F[ScType]
) : F[CurrentScope[ScType]] =
  mapping.getKeyValues.asScala.toList.collect {
    case kv : YAMLKeyValue if kv.getKeyText == "extends" && kv.getValue.isInstanceOf[YAMLScalar] =>
      List(kv.getValue.asInstanceOf[YAMLScalar])
    case kv : YAMLKeyValue if kv.getKeyText == "extends" && kv.getValue.isInstanceOf[YAMLSequence] =>
      kv.getValue.asInstanceOf[YAMLSequence].getItems.asScala.flatMap {
        case element : YAMLSequenceItem if element.getValue.isInstanceOf[YAMLScalar] =>
          List(element.getValue.asInstanceOf[YAMLScalar])
        case _ =>
          Nil
      }
  }
    .flatten
    .map(_.getTextValue)
    .traverse(classSource)
    .map(CurrentScope.ObjectDefinition(_))
end extendsOf

def sequenceScope[F[_] : Monad](
  scope : CurrentScope[ScType],
  onObjectDefinition : F[CurrentScope[ScType]],
  onNotSeq : F[CurrentScope[ScType]]
) : F[CurrentScope[ScType]] =
  scope match
    case CurrentScope.ObjectDefinition(extendList) =>
      onObjectDefinition
    case CurrentScope.OverrideRightHandSide(name, expectedType) =>
      println(s"Scope unwrapped for ${scope}")
      unwrapSeq(expectedType)
        .map(CurrentScope.OverrideRightHandSide(name, _))
        .map(_.pure)
        .getOrElse(onNotSeq)
  end match
end sequenceScope

def fieldScope(scope: CurrentScope[ScType], field: String): Option[CurrentScope[ScType]] =
  def resolveType(baseTypes: List[ScType]): Option[ScType] =
    baseTypes.iterator
      .flatMap(t => findMemberType(t, field))
      .nextOption()
      .map(unwrapMillTask)

  scope match
    case CurrentScope.ObjectDefinition(extendList) =>
      resolveType(extendList).map(
        CurrentScope.OverrideRightHandSide(field, _)
      )
    case CurrentScope.OverrideRightHandSide(_, expectedType) =>
      resolveType(List(expectedType)).map(
        CurrentScope.OverrideRightHandSide(field, _)
      )
end fieldScope

/**
 * Searches the base ScType for a method with 0 arguments or a field matching the name.
 */
def findMemberType(baseType: ScType, fieldName: String): Option[ScType] =
  // Safeguard: The YAML PSI can sometimes include trailing spaces or colons in getKeyText()
  val cleanFieldName = fieldName.trim.stripSuffix(":")

  extractTemplateDefinition(baseType).flatMap { template =>
    // 1. Search natively through direct Scala members (avoids Java PSI bridging issues entirely)
    val directMatch = template.members.iterator.collectFirst {
      case fn: ScFunction if fn.parameters.isEmpty && fn.name == cleanFieldName =>
        fn.returnType.toOption

      case v: ScValue if v.declaredElements.exists(_.name == cleanFieldName) =>
        v.declaredElements.find(_.name == cleanFieldName).flatMap(_.`type`().toOption)

      case v: ScVariable if v.declaredElements.exists(_.name == cleanFieldName) =>
        v.declaredElements.find(_.name == cleanFieldName).flatMap(_.`type`().toOption)
    }.flatten

    // 2. Fallback to inherited members
    // getAllMethods returns an array including Java PsiMethods.
    // We filter for the ones that retain their ScFunction identity.
    lazy val inheritedMatch = template.getAllMethods.iterator.collectFirst {
      case fn: ScFunction if fn.parameters.isEmpty && fn.name == cleanFieldName =>
        fn.returnType.toOption
    }.flatten

    directMatch.orElse(inheritedMatch)
  }
end findMemberType

/**
 * Checks if the type is a mill.api.Task[T] and unwraps it if so.
 */
def unwrapMillTask(tpe: ScType): ScType =
  val dealiasedType = tpe.removeAliasDefinitions()

  val hierarchyTypes = BaseTypes.get(dealiasedType)

  val taskTypeArgOpt = hierarchyTypes.iterator.collectFirst:
    case ParameterizedType(designator, Seq(typeArg))
      if designator.extractClass.exists(_.getQualifiedName == "mill.api.Task") =>
      typeArg

  taskTypeArgOpt.getOrElse(tpe)
end unwrapMillTask

/**
 * Checks if the type is a mill.api.Task[T] and unwraps it if so.
 */
def unwrapSeq(tpe: ScType): Option[ScType] =
  val dealiasedType = tpe.removeAliasDefinitions()

  val hierarchyTypes = BaseTypes.get(dealiasedType)

  val taskTypeArgOpt = hierarchyTypes.iterator.collectFirst:
    case ParameterizedType(designator, Seq(typeArg))
      if designator.extractClass.exists(_.getQualifiedName == "scala.collection.immutable.Seq") =>
        typeArg

  taskTypeArgOpt
end unwrapSeq

def getParameterlessMembersWithoutTask(template: ScTemplateDefinition): Seq[(String, ScType)] =
  // 1. Get the properties defined in the body { ... }
  val bodyElements: Seq[ScTypedDefinition] = template.members.flatMap:
    case fn: ScFunction if fn.parameters.isEmpty =>
      Seq(fn)
    case v: ScValue =>
      v.declaredElements
    case v: ScVariable =>
      v.declaredElements
    case _ =>
      Seq.empty

  // 2. Get the properties defined in the constructor ( ... ) if it's a class
  val constructorParams: Seq[ScTypedDefinition] = template match
    case clazz: ScClass =>
      // clazz.parameters returns the ScClassParameter elements
      clazz.parameters
    case _ =>
      Seq.empty

  // 3. Combine them and map to their types
  val allTargetElements = bodyElements ++ constructorParams

  allTargetElements.flatMap: typedDef =>
    typedDef
      .`type`()
      .toOption
      .map(unwrapMillTask)
      .map(otherType => (typedDef.name, otherType))
end getParameterlessMembersWithoutTask