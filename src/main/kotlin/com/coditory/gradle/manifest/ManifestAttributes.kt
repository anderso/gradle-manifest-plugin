package com.coditory.gradle.manifest

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants.HEAD
import org.gradle.api.Project
import org.gradle.api.java.archives.Attributes
import org.gradle.api.logging.LogLevel.INFO
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.jvm.tasks.Jar
import java.nio.file.Paths
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit.SECONDS

internal object ManifestAttributes {
    fun fillAttributes(clock: Clock, hostNameResolver: HostNameResolver, project: Project, extension: ManifestPluginExtension) {
        project.tasks.named(JavaPlugin.JAR_TASK_NAME, Jar::class.java) {
            fillAttributes(clock, hostNameResolver, project, it.manifest.attributes, extension)
        }
    }

    private fun fillAttributes(
        clock: Clock,
        hostNameResolver: HostNameResolver,
        project: Project,
        attributes: Attributes,
        extension: ManifestPluginExtension,
    ) {
        val generated = mapOf(
            "Main-Class" to orEmpty { project.properties["mainClassName"] },
        )
            .plus(implementationAttributes(project, extension))
            .plus(buildAttributes(clock, hostNameResolver, extension))
            .plus(scmAttributes(project, extension))
            .plus(customAttributes(extension))
            .plus(classpathAttribute(project, extension))
            .filter { !attributes.containsKey(it.key) }
            .map { it.key to it.value?.toString()?.trim() }
            .filter { !it.second.isNullOrBlank() }
        attributes.putAll(generated)
    }

    private fun customAttributes(extension: ManifestPluginExtension): Map<String, Any?> {
        val attributes = extension.attributes.orNull ?: return mapOf()
        return attributes.entries
            .filter { it.key.isNotBlank() && it.value != null }
            .associate { it.key to orEmpty { it.value } }
    }

    private fun classpathAttribute(project: Project, extension: ManifestPluginExtension): Map<String, Any?> {
        val classpathPrefix = extension.classpathPrefix.orNull ?: return mapOf()
        val classPath = project.configurations.getByName(RUNTIME_CLASSPATH_CONFIGURATION_NAME)
            .map { Paths.get(classpathPrefix, it.name).toString() }
            .joinToString(" ") { it.replace('\\', '/').replace("//+".toRegex(), "/") }
        return mapOf("Class-Path" to classPath)
    }

    private fun implementationTitle(project: Project): String {
        return BackwardCompatibilities.archivesBaseName(project)
    }

    private fun systemProperties(vararg names: String): String {
        return names
            .map { System.getProperty(it)?.trim() }
            .filter { !it.isNullOrBlank() }
            .joinToString(" ")
    }

    private fun isSetAndFalse(property: Property<Boolean>): Boolean {
        return !property.getOrElse(true)
    }

    private fun implementationAttributes(
        project: Project,
        extension: ManifestPluginExtension,
    ): Map<String, Any?> {
        if (isSetAndFalse(extension.implementationAttributes)) {
            return mapOf()
        }
        return mapOf(
            "Implementation-Title" to lazy { implementationTitle(project) },
            "Implementation-Group" to lazy { project.group },
            "Implementation-Version" to lazy { project.version },
        )
    }

    private fun buildAttributes(
        clock: Clock,
        hostNameResolver: HostNameResolver,
        extension: ManifestPluginExtension
    ): Map<String, String?> {
        if (isSetAndFalse(extension.buildAttributes)) {
            return mapOf()
        }
        return mapOf(
            "Built-By" to systemProperties("user.name"),
            "Built-Host" to orEmpty { hostNameResolver.resolveHostName() },
            "Built-Date" to format(clock.instant()),
            "Built-OS" to systemProperties("os.name", "os.version", "os.arch"),
            "Built-JDK" to systemProperties("java.version", "java.vendor"),
        )
    }

    private fun scmAttributes(project: Project, extension: ManifestPluginExtension): Map<String, String?> {
        if (isSetAndFalse(extension.scmAttributes)) {
            return mapOf()
        }
        return try {
            val repository = Git.open(project.rootProject.projectDir).repository
            val head = repository.parseCommit(repository.resolve(HEAD))
            return mapOf(
                "SCM-Repository" to orEmpty { repository.config.getString("remote", "origin", "url") },
                "SCM-Branch" to orEmpty { repository.fullBranch },
                "SCM-Commit-Message" to orEmpty { head.shortMessage },
                "SCM-Commit-Hash" to orEmpty { head.name() },
                "SCM-Commit-Author" to orEmpty { "${head.authorIdent.name.trim()} <${head.authorIdent.emailAddress.trim()}>" },
                "SCM-Commit-Date" to orEmpty { format(head.authorIdent.`when`.toInstant()) },
            )
        } catch (e: Throwable) {
            project.logger.log(INFO, "Could not resolve manifest SCM attributes. Using fallback.", e)
            mapOf()
        }
    }

    private fun format(instant: Instant): String {
        return instant.truncatedTo(SECONDS).toString()
    }

    private fun lazy(provider: () -> Any?): Any {
        return object {
            override fun toString(): String {
                return orEmpty(provider)
            }
        }
    }

    private fun orEmpty(provider: () -> Any?): String {
        return try {
            unwrapGradleProvider(provider())?.toString() ?: ""
        } catch (e: Throwable) {
            ""
        }
    }

    private fun unwrapGradleProvider(value: Any?): Any? {
        return when (value) {
            is Provider<*> -> value.orNull
            else -> value
        }
    }
}
