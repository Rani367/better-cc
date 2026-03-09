package com.claudecode.jetbrains.context

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

data class ProjectContext(
    val projectName: String,
    val projectType: String?,
    val buildTool: String?,
    val languages: List<String>,
    val basePath: String
)

@Service(Service.Level.PROJECT)
class ProjectContextProvider(private val project: Project) {

    private val logger = Logger.getInstance(ProjectContextProvider::class.java)

    @Volatile
    private var cachedContext: ProjectContext? = null

    /**
     * Get project context, detecting type/language/build tool from project files.
     */
    fun getProjectContext(): ProjectContext {
        cachedContext?.let { return it }

        val context = detectProjectContext()
        cachedContext = context
        return context
    }

    /**
     * Invalidate cache so next call re-detects.
     */
    fun invalidateCache() {
        cachedContext = null
    }

    /**
     * Format project context for inclusion in a Claude prompt.
     */
    fun formatProjectContext(context: ProjectContext): String {
        val sb = StringBuilder()
        sb.appendLine("Project: ${context.projectName}")
        if (context.projectType != null) {
            sb.appendLine("Type: ${context.projectType}")
        }
        if (context.buildTool != null) {
            sb.appendLine("Build tool: ${context.buildTool}")
        }
        if (context.languages.isNotEmpty()) {
            sb.appendLine("Languages: ${context.languages.joinToString(", ")}")
        }
        return sb.toString()
    }

    private fun detectProjectContext(): ProjectContext {
        val basePath = project.basePath ?: ""
        val baseDir = project.projectFile?.parent?.parent
            ?: project.workspaceFile?.parent?.parent

        val languages = mutableListOf<String>()
        val buildTool: String?
        val projectType: String?

        // Detect build tool and project type from marker files
        val markers = detectMarkerFiles(baseDir)

        buildTool = when {
            "build.gradle.kts" in markers -> "Gradle (Kotlin DSL)"
            "build.gradle" in markers -> "Gradle"
            "pom.xml" in markers -> "Maven"
            "package.json" in markers -> "npm"
            "Cargo.toml" in markers -> "Cargo"
            "go.mod" in markers -> "Go Modules"
            "Gemfile" in markers -> "Bundler"
            "requirements.txt" in markers ||
                "setup.py" in markers ||
                "pyproject.toml" in markers -> "pip/Python"
            "Package.swift" in markers -> "Swift Package Manager"
            "CMakeLists.txt" in markers -> "CMake"
            "Makefile" in markers -> "Make"
            else -> null
        }

        // Detect languages
        if ("build.gradle.kts" in markers || "build.gradle" in markers) {
            if (hasDirectory(baseDir, "src/main/kotlin")) {
                languages.add("Kotlin")
            }
            if (hasDirectory(baseDir, "src/main/java")) {
                languages.add("Java")
            }
            if (languages.isEmpty()) {
                languages.add("Kotlin/Java")
            }
        }
        if ("package.json" in markers) {
            if ("tsconfig.json" in markers) {
                languages.add("TypeScript")
            } else {
                languages.add("JavaScript")
            }
        }
        if ("Cargo.toml" in markers) languages.add("Rust")
        if ("go.mod" in markers) languages.add("Go")
        if ("Gemfile" in markers) languages.add("Ruby")
        if ("requirements.txt" in markers ||
            "setup.py" in markers ||
            "pyproject.toml" in markers
        ) {
            languages.add("Python")
        }
        if ("Package.swift" in markers) languages.add("Swift")
        if ("pom.xml" in markers && languages.isEmpty()) {
            languages.add("Java")
        }

        // Detect project type/framework
        projectType = when {
            "build.gradle.kts" in markers &&
                languages.contains("Kotlin") -> "Kotlin Project"
            "pom.xml" in markers -> "Java/Maven Project"
            "package.json" in markers &&
                "tsconfig.json" in markers -> "TypeScript Project"
            "package.json" in markers -> "JavaScript/Node.js Project"
            "Cargo.toml" in markers -> "Rust Project"
            "go.mod" in markers -> "Go Project"
            else -> null
        }

        return ProjectContext(
            projectName = project.name,
            projectType = projectType,
            buildTool = buildTool,
            languages = languages.distinct(),
            basePath = basePath
        )
    }

    private fun detectMarkerFiles(baseDir: VirtualFile?): Set<String> {
        if (baseDir == null) return emptySet()

        val markerNames = listOf(
            "build.gradle.kts", "build.gradle", "pom.xml",
            "package.json", "tsconfig.json",
            "Cargo.toml", "go.mod", "Gemfile",
            "requirements.txt", "setup.py", "pyproject.toml",
            "Package.swift", "CMakeLists.txt", "Makefile"
        )

        return markerNames.filter { name ->
            baseDir.findChild(name) != null
        }.toSet()
    }

    private fun hasDirectory(baseDir: VirtualFile?, path: String): Boolean {
        if (baseDir == null) return false
        var current: VirtualFile? = baseDir
        for (segment in path.split("/")) {
            current = current?.findChild(segment) ?: return false
        }
        return current?.isDirectory == true
    }

    companion object {
        fun getInstance(project: Project): ProjectContextProvider =
            project.getService(ProjectContextProvider::class.java)
    }
}
