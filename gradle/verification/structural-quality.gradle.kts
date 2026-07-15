import java.io.File
import java.util.SortedSet

private val maxConstructorParameters = 7
private val maxFactoryParameters = 4
private val maxFunctionParameters = 10
private val maxSourceLines = 300

private data class ParameterList(
    val count: Int,
    val endIndex: Int,
)

private val typeDeclaration =
    Regex(
        "(?m)^[ \\t]*(?!(?:private)\\b)" +
            "(?:(?:public|internal|protected|open|abstract|sealed|data|enum|value|fun|annotation|" +
            "expect|actual|inline)\\s+)*(class|interface|object)\\s+([A-Za-z_]\\w*)",
    )
private val functionDeclaration =
    Regex(
        "(?m)^[ \\t]*(?!(?:private)\\b)" +
            "(?:(?:public|internal|protected|suspend|inline|operator|infix|tailrec|external|" +
            "override)\\s+)*fun\\s+(?:<[^>]+>\\s*)?(?:[A-Za-z0-9_<>?., ]+\\.)?" +
            "([A-Za-z_]\\w*)\\s*\\(",
    )
private val dependencyBagName =
    Regex("^(?:.*(?:Dependencies|Services|Components)|PlayerContext|WorldContext)$")
private val sessionConstruction = Regex("\\b(?:OnlinePlayer|Player[A-Z]\\w*)\\s*\\(")

private fun stripNonCode(source: String): String {
    val result = source.toCharArray()
    var index = 0
    var state = "code"
    while (index < result.size) {
        val current = result[index]
        val next = result.getOrNull(index + 1)
        when (state) {
            "code" ->
                when {
                    current == '/' && next == '/' -> {
                        result[index] = ' '
                        result[index + 1] = ' '
                        index++
                        state = "line-comment"
                    }
                    current == '/' && next == '*' -> {
                        result[index] = ' '
                        result[index + 1] = ' '
                        index++
                        state = "block-comment"
                    }
                    current == '"' && source.startsWith("\"\"\"", index) -> {
                        repeat(3) { result[index + it] = ' ' }
                        index += 2
                        state = "raw-string"
                    }
                    current == '"' -> {
                        result[index] = ' '
                        state = "string"
                    }
                    current == '\'' -> {
                        result[index] = ' '
                        state = "char"
                    }
                }
            "line-comment" -> {
                if (current == '\n') state = "code" else result[index] = ' '
            }
            "block-comment" -> {
                if (current == '*' && next == '/') {
                    result[index] = ' '
                    result[index + 1] = ' '
                    index++
                    state = "code"
                } else if (current != '\n') {
                    result[index] = ' '
                }
            }
            "raw-string" -> {
                if (source.startsWith("\"\"\"", index)) {
                    repeat(3) { result[index + it] = ' ' }
                    index += 2
                    state = "code"
                } else if (current != '\n') {
                    result[index] = ' '
                }
            }
            "string", "char" -> {
                val terminator = if (state == "string") '"' else '\''
                if (current == '\\') {
                    result[index] = ' '
                    if (index + 1 < result.size) {
                        result[index + 1] = ' '
                        index++
                    }
                } else {
                    if (current == terminator) state = "code"
                    if (current != '\n') result[index] = ' '
                }
            }
        }
        index++
    }
    return result.concatToString()
}

private fun topLevelAt(code: String, index: Int): Boolean {
    var depth = 0
    for (position in 0 until index) {
        when (code[position]) {
            '{' -> depth++
            '}' -> depth--
        }
    }
    return depth == 0
}

private fun parameterList(code: String, openIndex: Int): ParameterList? {
    if (openIndex !in code.indices || code[openIndex] != '(') return null
    var parentheses = 1
    var brackets = 0
    var braces = 0
    var angles = 0
    var commas = 0
    var hasContent = false
    var index = openIndex + 1
    while (index < code.length) {
        when (code[index]) {
            '(' -> parentheses++
            ')' -> {
                parentheses--
                if (parentheses == 0) {
                    return ParameterList(if (hasContent) commas + 1 else 0, index)
                }
            }
            '[' -> brackets++
            ']' -> brackets--
            '{' -> braces++
            '}' -> braces--
            '<' -> angles++
            '>' -> if (angles > 0) angles--
            ',' -> if (parentheses == 1 && brackets == 0 && braces == 0 && angles == 0) commas++
        }
        if (!code[index].isWhitespace() && parentheses == 1) hasContent = true
        index++
    }
    return null
}

private fun hasAdjacentKdoc(source: String, declarationIndex: Int): Boolean {
    var prefix = source.substring(0, declarationIndex).trimEnd()
    while (prefix.substringAfterLast('\n').trimStart().startsWith("@")) {
        prefix = prefix.substringBeforeLast('\n', missingDelimiterValue = "").trimEnd()
    }
    if (!prefix.endsWith("*/")) return false
    val open = prefix.lastIndexOf("/**")
    val previousClose = prefix.lastIndexOf("*/", prefix.length - 3)
    return open > previousClose
}

private fun structuralQualityViolations(repository: File): SortedSet<String> =
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
                val text = source.readText()
                val code = stripNonCode(text)
                val lines = text.lineSequence().count()
                if (lines > maxSourceLines) add("large-file|$path|$lines")

                for (match in typeDeclaration.findAll(code).filter { topLevelAt(code, it.range.first) }) {
                    val name = match.groupValues[2]
                    if (!hasAdjacentKdoc(text, match.range.first)) add("missing-kdoc|$path|type:$name")
                    if (dependencyBagName.matches(name)) add("dependency-bag|$path|$name")
                    if (match.groupValues[1] != "class") continue
                    val body = code.indexOf('{', match.range.last + 1).let { if (it < 0) code.length else it }
                    val open = code.indexOf('(', match.range.last + 1)
                    if (open < 0 || open > body) continue
                    val parameters = parameterList(code, open)?.count ?: continue
                    val limit = if (name.endsWith("Factory")) maxFactoryParameters else maxConstructorParameters
                    if (parameters > limit) add("constructor-parameters|$path|$name|$parameters>$limit")
                }

                for (match in functionDeclaration.findAll(code).filter { topLevelAt(code, it.range.first) }) {
                    val name = match.groupValues[1]
                    if (name != "main" && !hasAdjacentKdoc(text, match.range.first)) {
                        add("missing-kdoc|$path|function:$name")
                    }
                    val open = code.indexOf('(', match.range.first)
                    val parameters = parameterList(code, open)?.count ?: continue
                    if (parameters > maxFunctionParameters) {
                        add("function-parameters|$path|$name|$parameters>$maxFunctionParameters")
                    }
                }

                if ("/server/world/src/main/kotlin/emu/server/world/session/" in "/$path") {
                    val playerImports =
                        text.lineSequence().count { it.startsWith("import emu.server.world.player.") }
                    val playerConstructions = sessionConstruction.findAll(code).count()
                    if (playerImports > 0) add("session-player-imports|$path|$playerImports")
                    if (playerConstructions > 0) {
                        add("session-player-constructions|$path|$playerConstructions")
                    }
                }
            }
    }.toSortedSet()

private fun readStructuralBaseline(file: File): SortedSet<String> =
    if (!file.isFile) {
        sortedSetOf()
    } else {
        file.readLines()
            .map(String::trim)
            .filter { line -> line.isNotEmpty() && !line.startsWith('#') }
            .toSortedSet()
    }

private val structuralBaseline = rootProject.file("gradle/verification/structural-quality-baseline.txt")

tasks.register("structuralQualityCheck") {
    group = "verification"
    description = "Rejects new or stale constructor, composition, size, and KDoc debt."
    inputs.files(
        rootProject.subprojects.map { subproject ->
            subproject.fileTree("src/main") { include("**/*.kt") }
        },
    )
    if (structuralBaseline.isFile) inputs.file(structuralBaseline)

    doLast {
        val actual = structuralQualityViolations(rootProject.projectDir)
        val baseline = readStructuralBaseline(structuralBaseline)
        val introduced = actual - baseline
        val stale = baseline - actual
        check(introduced.isEmpty() && stale.isEmpty()) {
            buildString {
                appendLine("structural-quality baseline differs")
                if (introduced.isNotEmpty()) {
                    appendLine("new violations:")
                    introduced.forEach { appendLine("+ $it") }
                }
                if (stale.isNotEmpty()) {
                    appendLine("resolved violations still in baseline:")
                    stale.forEach { appendLine("- $it") }
                }
                append("run :printStructuralQualityBaseline only for a reviewed baseline reduction")
            }
        }
    }
}

tasks.register("printStructuralQualityBaseline") {
    group = "verification"
    description = "Prints current structural debt for deliberate baseline review."
    doLast { structuralQualityViolations(rootProject.projectDir).forEach(::println) }
}
