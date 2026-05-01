package katze.intellijmill

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import org.jetbrains.yaml.psi.*

final class YamlReferenceContributor extends PsiReferenceContributor:
  override def registerReferenceProviders(registrar: PsiReferenceRegistrar): Unit = 
    registrar.registerReferenceProvider(
      PlatformPatterns.psiElement(classOf[YAMLPsiElement]),
      new PsiReferenceProvider:
        override def getReferencesByElement(
          element: PsiElement,
          context: ProcessingContext
        ): Array[PsiReference] =
          val project = element.getProject
          if (!project.isInitialized)
            return Array.empty
          Array.empty
        end getReferencesByElement
    )

    registrar.registerReferenceProvider(
      PlatformPatterns.psiElement(classOf[YAMLKeyValue]),
      new PsiReferenceProvider:
        override def getReferencesByElement(
          element: PsiElement,
          context: ProcessingContext
        ): Array[PsiReference] = 
          val project = element.getProject
          if (!project.isInitialized) 
            return Array.empty
          return Array.empty
        end getReferencesByElement
    )
  end registerReferenceProviders
end YamlReferenceContributor