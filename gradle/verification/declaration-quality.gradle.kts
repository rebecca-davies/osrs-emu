import java.io.File
import java.util.SortedSet

private data class SourceDeclaration(val kind: String, val name: String)

private val topLevelType =
    Regex(
        "^(?!private\\b)(?:(?:public|internal|protected|open|abstract|sealed|data|enum|value|fun|" +
            "annotation|expect|actual|inline)\\s+)*(class|interface|object)\\s+([A-Za-z_]\\w*)",
    )
private val topLevelTypeAlias =
    Regex("^(?!private\\b)(?:(?:public|internal|protected|actual|expect)\\s+)*typealias\\s+([A-Za-z_]\\w*)")
private val topLevelFunction =
    Regex(
        "^(?!private\\b)(?:(?:public|internal|protected|suspend|inline|operator|infix|tailrec|" +
            "external|override)\\s+)*fun\\s+(?:<[^>]+>\\s*)?(?:[A-Za-z0-9_<>?., ]+\\.)?" +
            "([A-Za-z_]\\w*)\\s*\\(",
    )
private val topLevelProperty =
    Regex(
        "^(?!private\\b)(?:(?:public|internal|protected|const|lateinit|override)\\s+)*" +
            "(val|var)\\s+([A-Za-z_]\\w*)",
    )

private fun String.upperCamel(): String = replaceFirstChar { first -> first.uppercase() }

private fun sourceDeclarations(source: File): List<SourceDeclaration> =
    buildList {
        source.forEachLine { line ->
            topLevelType.find(line)?.let { match ->
                add(SourceDeclaration("type", match.groupValues[2]))
                return@forEachLine
            }
            topLevelTypeAlias.find(line)?.let { match ->
                add(SourceDeclaration("typealias", match.groupValues[1]))
                return@forEachLine
            }
            topLevelFunction.find(line)?.let { match ->
                add(SourceDeclaration("function", match.groupValues[1]))
                return@forEachLine
            }
            topLevelProperty.find(line)?.let { match ->
                add(SourceDeclaration(match.groupValues[1], match.groupValues[2]))
            }
        }
    }

private fun declarationQualityViolations(repository: File): SortedSet<String> =
    buildSet {
        repository.walkTopDown()
            .filter { source ->
                source.isFile &&
                    source.extension == "kt" &&
                    "/src/main/" in source.invariantSeparatorsPath &&
                    "/build/" !in source.invariantSeparatorsPath
            }
            .forEach { source ->
                val path = source.relativeTo(repository).invariantSeparatorsPath
                val declarations = sourceDeclarations(source)
                when {
                    declarations.isEmpty() -> add("no-primary|$path")
                    declarations.size > 1 ->
                        add(
                            "multiple|$path|" +
                                declarations.joinToString(",") { declaration ->
                                    "${declaration.kind}:${declaration.name}"
                                },
                        )
                    else -> {
                        val declaration = declarations.single()
                        val expectedFile =
                            if (declaration.kind == "function" && declaration.name == "main") {
                                "Main"
                            } else {
                                declaration.name.upperCamel()
                            }
                        if (source.nameWithoutExtension != expectedFile) {
                            add("filename|$path|$expectedFile|${declaration.kind}:${declaration.name}")
                        }
                    }
                }
                declarations.filter { it.kind == "var" }.forEach { declaration ->
                    add("mutable|$path|${declaration.name}")
                }
            }
    }.toSortedSet()

private fun readDeclarationBaseline(file: File): SortedSet<String> =
    if (!file.isFile) {
        sortedSetOf()
    } else {
        file.readLines()
            .map(String::trim)
            .filter { line -> line.isNotEmpty() && !line.startsWith('#') }
            .toSortedSet()
    }

private val declarationBaseline = rootProject.file("gradle/verification/declaration-quality-baseline.txt")

tasks.register("declarationQualityCheck") {
    group = "verification"
    description = "Rejects new or stale production declaration-layout debt."

    inputs.files(
        rootProject.subprojects.map { subproject ->
            subproject.fileTree("src/main") { include("**/*.kt") }
        },
    )
    if (declarationBaseline.isFile) inputs.file(declarationBaseline)

    doLast {
        val actual = declarationQualityViolations(rootProject.projectDir)
        val baseline = readDeclarationBaseline(declarationBaseline)
        val introduced = actual - baseline
        val stale = baseline - actual
        check(introduced.isEmpty() && stale.isEmpty()) {
            buildString {
                appendLine("declaration-quality baseline differs")
                if (introduced.isNotEmpty()) {
                    appendLine("new violations:")
                    introduced.forEach { appendLine("+ $it") }
                }
                if (stale.isNotEmpty()) {
                    appendLine("resolved violations still in baseline:")
                    stale.forEach { appendLine("- $it") }
                }
                append("run :printDeclarationQualityBaseline only to review a deliberate baseline reduction")
            }
        }
    }
}

tasks.register("printDeclarationQualityBaseline") {
    group = "verification"
    description = "Prints the current declaration-layout debt for deliberate baseline review."
    doLast { declarationQualityViolations(rootProject.projectDir).forEach(::println) }
}
