package com.idealista.togglehandler

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile

class ToggleFileModifier(private val project: Project) {

    fun addToggleDefinition(file: PsiFile, toggleData: ToggleDefinitions.ToggleData) {
        val document = PsiDocumentManager.getInstance(project).getDocument(file)

        if (document == null) {
            Messages.showErrorDialog(
                project,
                "Could not access document for file: ${file.name}",
                "Error"
            )
            return
        }

        val fileText = document.text
        val snakeCaseName = convertToSnakeCase(toggleData.name)

        val description = if (toggleData.jiraTask.isNotEmpty()) {
            "${toggleData.jiraTask} ${toggleData.description}"
        } else {
            toggleData.description.ifEmpty { "Información desconocida" }
        }

        val newToggleDefinition = """
    data object ${toggleData.name} : Toggle(
        name = "$snakeCaseName",
        isRemoteConfigurable = ${toggleData.isRemotelyConfigurable},
        activationDate = "${toggleData.activationDate}",
        activationVersion = "${toggleData.activationVersion}",
        deprecationDate = "${toggleData.deprecationDate}",
        description = "$description"
    )"""

        val classToggleEndIndex = fileText.lastIndexOf("}")

        if (classToggleEndIndex == -1) {
            Messages.showErrorDialog(
                project,
                "Could not find closing brace of Toggle class",
                "Error"
            )
            return
        }

        WriteCommandAction.runWriteCommandAction(project) {
            try {
                document.insertString(classToggleEndIndex, "$newToggleDefinition\n")
                PsiDocumentManager.getInstance(project).commitDocument(document)
            } catch (e: Exception) {
                Messages.showErrorDialog(
                    project,
                    "Error adding toggle: ${e.message}",
                    "Error"
                )
            }
        }
    }

    fun addServiceExtensions(file: PsiFile, toggleData: ToggleDefinitions.ToggleData) {
        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return
        val fileText = document.text
        val functionName = "is${toggleData.name}Enabled"
        val newServiceExtension = "\nfun $functionName(): Boolean = DI.serviceProvider.remoteService.isToggled(Toggle.${toggleData.name})"

        WriteCommandAction.runWriteCommandAction(project) {
            try {
                document.insertString(fileText.length, newServiceExtension)
                PsiDocumentManager.getInstance(project).commitDocument(document)
            } catch (e: Exception) {
                Messages.showErrorDialog(
                    project,
                    "Error adding service extension: ${e.message}",
                    "Error"
                )
            }
        }
    }

    fun addRemoteSettingsDefaults(file: PsiFile, toggleData: ToggleDefinitions.ToggleData) {
        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return
        val fileText = document.text
        val remoteSettingsPattern = Regex("""fun\s+remoteSettingsDefaults\(\)\s*=\s*mapOf\s*\(""")
        val match = remoteSettingsPattern.find(fileText)

        if (match == null) {
            Messages.showErrorDialog(
                project,
                "Function remoteSettingsDefaults() not found in file ${file.name}",
                "Error"
            )

            return
        }

        var openParens = 1
        var currentIndex = match.range.last + 1
        var hasElements = false

        while (currentIndex < fileText.length && openParens > 0) {
            when (fileText[currentIndex]) {
                '(' -> openParens++
                ')' -> openParens--
                ',' -> if (openParens == 1) hasElements = true
            }

            if (openParens == 1 && fileText[currentIndex].isLetterOrDigit()) {
                hasElements = true
            }
            currentIndex++
        }

        if (openParens > 0) {
            Messages.showErrorDialog(
                project,
                "Bad analysis of mapOf() in remoteSettingsDefaults()",
                "Error"
            )

            return
        }

        val closingParenIndex = currentIndex - 1

        WriteCommandAction.runWriteCommandAction(project) {
            try {
                if (hasElements) {
                    var insertIndex = closingParenIndex
                    while (insertIndex > 0 && (fileText[insertIndex - 1].isWhitespace() || fileText[insertIndex - 1] == ')')) {
                        insertIndex--
                    }
                    document.insertString(insertIndex, ",\n        Toggle.${toggleData.name}.name to false")
                } else {
                    document.insertString(closingParenIndex, "\n        Toggle.${toggleData.name}.name to false\n    ")
                }
                PsiDocumentManager.getInstance(project).commitDocument(document)
            } catch (e: Exception) {
                Messages.showErrorDialog(
                    project,
                    "Error adding toggle default settings: ${e.message}",
                    "Error"
                )
            }
        }
    }

    fun removeToggleDefinition(file: PsiFile, toggleData: ToggleDefinitions.ToggleData) {
        val document = PsiDocumentManager.getInstance(project).getDocument(file)

        if (document == null) {
            Messages.showErrorDialog(
                project,
                "Could not access document for file: ${file.name}",
                "Error"
            )
            return
        }

        val fileText = document.text
        val togglePattern = Regex(
            """data\s+object\s+${toggleData.name}\s*:\s*Toggle\s*\(\s*
                name\s*=\s*"[^"]*",\s*
                isRemoteConfigurable\s*=\s*\w+,\s*
                activationDate\s*=\s*"[^"]*",\s*
                activationVersion\s*=\s*"[^"]*",\s*
                deprecationDate\s*=\s*"[^"]*",\s*
                description\s*=\s*"[^"]*"\s*
                \)""".trimIndent().replace("\n", "\\s*"),
            setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL)
        )

        val match = togglePattern.find(fileText)

        if (match == null) {
            val simplePattern = Regex(
                """data\s+object\s+${toggleData.name}\s*:\s*Toggle\s*\([^}]*?\)""",
                RegexOption.MULTILINE
            )
            val simpleMatch = simplePattern.find(fileText)

            if (simpleMatch == null) {
                Messages.showErrorDialog(
                    project,
                    "Toggle definition '${toggleData.name}' not found in file: ${file.name}",
                    "Error"
                )
                return
            }

            performRemoval(document, fileText, simpleMatch)
        } else {
            performRemoval(document, fileText, match)
        }
    }

    private fun performRemoval(document: com.intellij.openapi.editor.Document, fileText: String, match: MatchResult) {
        WriteCommandAction.runWriteCommandAction(project) {
            try {
                val matchStart = match.range.first
                val matchEnd = match.range.last + 1

                var lineStart = matchStart
                while (lineStart > 0 && fileText[lineStart - 1] != '\n' && fileText[lineStart - 1] != '\r') {
                    lineStart--
                }

                var lineEnd = matchEnd
                while (lineEnd < fileText.length && fileText[lineEnd] != '\n' && fileText[lineEnd] != '\r') {
                    lineEnd++
                }

                if (lineEnd < fileText.length && fileText[lineEnd] == '\r') {
                    lineEnd++
                }
                if (lineEnd < fileText.length && fileText[lineEnd] == '\n') {
                    lineEnd++
                }

                var nextToggleIndex = lineEnd
                while (nextToggleIndex < fileText.length && fileText[nextToggleIndex].isWhitespace()) {
                    nextToggleIndex++
                }

                val hasNextToggle = nextToggleIndex < fileText.length &&
                    fileText.substring(nextToggleIndex).trimStart().startsWith("data object")

                var prevToggleEnd = lineStart - 1
                while (prevToggleEnd >= 0 && fileText[prevToggleEnd].isWhitespace()) {
                    prevToggleEnd--
                }

                val hasPrevToggle = prevToggleEnd >= 0 && fileText[prevToggleEnd] == ')'

                if (hasPrevToggle && hasNextToggle) {
                    while (lineEnd < fileText.length && (fileText[lineEnd] == '\n' || fileText[lineEnd] == '\r' || fileText[lineEnd] == ' ' || fileText[lineEnd] == '\t')) {
                        if (fileText[lineEnd] == '\n' || fileText[lineEnd] == '\r') {
                            lineEnd++
                            break
                        }
                        lineEnd++
                    }
                }

                if (hasPrevToggle && !hasNextToggle) {
                    while (lineEnd < fileText.length && fileText[lineEnd].isWhitespace()) {
                        lineEnd++
                    }
                }

                document.deleteString(lineStart, lineEnd)
                PsiDocumentManager.getInstance(project).commitDocument(document)
            } catch (e: Exception) {
                Messages.showErrorDialog(
                    project,
                    "Error removing toggle definition: ${e.message}",
                    "Error"
                )
            }
        }
    }

    fun openServiceExtension(file: PsiFile) {
        try {
            val virtualFile: VirtualFile? = file.virtualFile
            if (virtualFile != null) {
                FileEditorManager.getInstance(project).openFile(virtualFile, true)
            } else {
                Messages.showErrorDialog(
                    project,
                    "Could not open file: ${file.name}. Virtual file not found.",
                    "Error"
                )
            }
        } catch (e: Exception) {
            Messages.showErrorDialog(
                project,
                "Error opening file: ${e.message}",
                "Error"
            )
        }
    }

    fun removeRemoteSettingsDefaults(file: PsiFile, toggleData: ToggleDefinitions.ToggleData) {
        val document = PsiDocumentManager.getInstance(project).getDocument(file)

        if (document == null) {
            Messages.showErrorDialog(
                project,
                "Could not access document for file: ${file.name}",
                "Error"
            )
            return
        }

        val fileText = document.text
        val toggleEntryPattern = Regex("""Toggle\.${toggleData.name}\.name\s+to\s+false""")
        val match = toggleEntryPattern.find(fileText)

        if (match == null) {
            Messages.showErrorDialog(
                project,
                "Remote settings entry for '${toggleData.name}' not found in file: ${file.name}",
                "Error"
            )
            return
        }

        WriteCommandAction.runWriteCommandAction(project) {
            try {
                val matchStart = match.range.first
                val matchEnd = match.range.last + 1

                var lineStart = matchStart
                while (lineStart > 0 && fileText[lineStart - 1] != '\n' && fileText[lineStart - 1] != '\r') {
                    lineStart--
                }

                var lineEnd = matchEnd
                while (lineEnd < fileText.length && fileText[lineEnd] != '\n' && fileText[lineEnd] != '\r') {
                    lineEnd++
                }

                if (lineEnd < fileText.length && fileText[lineEnd] == '\r') {
                    lineEnd++
                }
                if (lineEnd < fileText.length && fileText[lineEnd] == '\n') {
                    lineEnd++
                }

                val fullLine = fileText.substring(lineStart, lineEnd)
                val hasCommaInLine = fullLine.contains(",")

                if (hasCommaInLine) {
                    document.deleteString(lineStart, lineEnd)
                } else {
                    document.deleteString(lineStart, lineEnd)

                    var prevIndex = lineStart - 1
                    while (prevIndex >= 0 && fileText[prevIndex].isWhitespace()) {
                        prevIndex--
                    }

                    if (prevIndex >= 0 && fileText[prevIndex] == ',') {
                        document.deleteString(prevIndex, prevIndex + 1)
                    }
                }

                PsiDocumentManager.getInstance(project).commitDocument(document)
            } catch (e: Exception) {
                Messages.showErrorDialog(
                    project,
                    "Error removing remote settings defaults: ${e.message}",
                    "Error"
                )
            }
        }
    }

    private fun convertToSnakeCase(pascalCase: String): String {
        return pascalCase
            .replace(Regex("([a-z])([A-Z])"), "$1_$2")
            .lowercase()
    }

    fun extractToggleNames(file: PsiFile): List<String> {
        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return emptyList()
        val fileText = document.text
        val togglePattern = Regex("""data\s+object\s+(\w+)\s*:\s*Toggle\s*\(""")
        val matches = togglePattern.findAll(fileText)

        return matches.map { it.groupValues[1] }.toList()
    }
}
