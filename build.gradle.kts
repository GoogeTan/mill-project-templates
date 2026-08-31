import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.intellij.platform") version "2.16.0"
    id("org.jetbrains.changelog") version "2.5.0"
    id("scala")
}

// Add this repositories block!
repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    compileOnly("org.scala-lang:scala3-library_3:3.9.0")
    testImplementation("org.scala-lang:scala3-library_3:3.9.0")
    implementation("org.typelevel:cats-core_3:2.13.0") {
        exclude(group = "org.scala-lang")
    }
    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea("2026.1")
        plugin("org.intellij.scala", "2026.1.16")
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.kotlin")
        bundledPlugin("org.jetbrains.idea.reposearch")
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.Java)
        bundledPlugin("org.jetbrains.plugins.yaml")
    }
}

// Configure IntelliJ Platform Gradle Plugin - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html
intellijPlatform {
    pluginConfiguration {
        version.set(project.version.toString())

        ideaVersion {
            sinceBuild.set("261")
            untilBuild.set("263.*")
        }

        val changelog = project.changelog
        changeNotes = version.map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }
    }

    pluginVerification {
        ides {
            current()
        }
    }

    signing {
        val certPath = providers.gradleProperty("millijPlugin.signing.cert")
        val keyPath = providers.gradleProperty("millijPlugin.signing.key")
        val keyPass = providers.gradleProperty("millijPlugin.signing.password")

        if (certPath.isPresent && keyPath.isPresent) {
            certificateChain = file(certPath.get()).readText()
            privateKey = file(keyPath.get()).readText()
            password = keyPass.orNull
        }
    }
}

// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    groups.empty()
    repositoryUrl = providers.gradleProperty("pluginRepositoryUrl")
    versionPrefix = ""
}

tasks {
    publishPlugin {
        dependsOn(patchChangelog)
    }

    register("generateUpdatePluginsXml") {
        group = "publishing"
        description = "Generates updatePlugins.xml from plugin.xml"

        val patchPluginXmlTask = named<org.jetbrains.intellij.platform.gradle.tasks.PatchPluginXmlTask>("patchPluginXml")
        val patchPluginXmlFile = patchPluginXmlTask.flatMap { it.outputFile }

        inputs.file(patchPluginXmlFile)

        val outputFile = layout.projectDirectory.file("docs/updatePlugins.xml")
        outputs.file(outputFile)

        val projectVersion = project.version.toString()
        val repoUrlVal = providers.gradleProperty("pluginRepositoryUrl").getOrElse("https://github.com/GoogeTan/Millij")

        doLast {
            val xmlFile = patchPluginXmlFile.get().asFile
            if (!xmlFile.exists()) {
                throw GradleException("patched plugin.xml not found at ${xmlFile.absolutePath}")
            }

            // Parse plugin.xml
            val dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            val db = dbf.newDocumentBuilder()
            val doc = db.parse(xmlFile)
            doc.documentElement.normalize()

            val id = doc.getElementsByTagName("id").item(0)?.textContent?.trim() ?: ""
            val name = doc.getElementsByTagName("name").item(0)?.textContent?.trim() ?: ""
            val version = doc.getElementsByTagName("version").item(0)?.textContent?.trim() ?: projectVersion
            val description = doc.getElementsByTagName("description").item(0)?.textContent?.trim() ?: ""

            val changeNotesNode = doc.getElementsByTagName("change-notes").item(0)
            val changeNotes = changeNotesNode?.textContent?.trim() ?: ""

            val ideaVersionNode = doc.getElementsByTagName("idea-version").item(0) as? org.w3c.dom.Element
            val sinceBuild = ideaVersionNode?.getAttribute("since-build") ?: ""
            val untilBuild = ideaVersionNode?.getAttribute("until-build") ?: ""

            val downloadUrl = "$repoUrlVal/releases/download/$version/millij-$version-signed.zip"

            val output = outputFile.asFile
            output.parentFile.mkdirs()

            val xmlBuilder = StringBuilder()
            xmlBuilder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            xmlBuilder.append("<plugins>\n")
            xmlBuilder.append("    <plugin id=\"$id\" url=\"$downloadUrl\" version=\"$version\">\n")
            xmlBuilder.append("        <name>$name</name>\n")
            if (description.isNotEmpty()) {
                xmlBuilder.append("        <description><![CDATA[$description]]></description>\n")
            }
            if (changeNotes.isNotEmpty()) {
                xmlBuilder.append("        <change-notes><![CDATA[$changeNotes]]></change-notes>\n")
            }
            if (sinceBuild.isNotEmpty() || untilBuild.isNotEmpty()) {
                xmlBuilder.append("        <idea-version")
                if (sinceBuild.isNotEmpty()) {
                    xmlBuilder.append(" since-build=\"$sinceBuild\"")
                }
                if (untilBuild.isNotEmpty()) {
                    xmlBuilder.append(" until-build=\"$untilBuild\"")
                }
                xmlBuilder.append(" />\n")
            }
            xmlBuilder.append("    </plugin>\n")
            xmlBuilder.append("</plugins>\n")

            output.writeText(xmlBuilder.toString())
            logger.lifecycle("Generated updatePlugins.xml at ${output.absolutePath}")
        }
    }
}

tasks.withType(Jar::class.java) {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<Test> {
    systemProperty("jna.nosys", "true")
    testLogging {
        showStandardStreams = true
    }
}

tasks.withType<org.gradle.api.tasks.scala.ScalaCompile> {
    scalaCompileOptions.forkOptions.memoryMaximumSize = "4g"
}