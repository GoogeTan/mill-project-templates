package katze.intellijmill.scalatypes

import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.Project

def getMillConfigFilesSubproject(project: Project): Option[Module] = 
  val modules = ModuleManager.getInstance(project).getModules
  modules.find(_.getName.startsWith("mill-build"))
end getMillConfigFilesSubproject
