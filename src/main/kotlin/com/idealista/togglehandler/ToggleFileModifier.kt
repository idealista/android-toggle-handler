package com.idealista.togglehandler

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile

class ToggleFileModifier(private val project: Project) {

    fun addToggleDefinition(file: PsiFile, toggleData: CreateToggleDialog.Companion.ToggleData) {
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
        val newToggleDefinition =
            "    data object ${toggleData.name} : Toggle(\"$snakeCaseName\", ${toggleData.isRemotelyConfigurable})"
        val lastDataObjectIndex = fileText.lastIndexOf("data object")

        if (lastDataObjectIndex == -1) {
            Messages.showErrorDialog(
                project,
                "Could not find data object pattern in Toggle.kt",
                "Error"
            )

            return
        }

        val endOfLastDataObject = fileText.indexOf('\n', lastDataObjectIndex)

        if (endOfLastDataObject == -1) {
            Messages.showErrorDialog(
                project,
                "Could not find insertion point in Toggle.kt",
                "Error"
            )

            return
        }

        val insertPosition = endOfLastDataObject + 1

        WriteCommandAction.runWriteCommandAction(project) {
            try {
                document.insertString(insertPosition, "$newToggleDefinition\n")
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

    fun addToggleDocumentation(file: PsiFile, toggleData: CreateToggleDialog.Companion.ToggleData) {
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
        val lastIdToggleIndex = fileText.lastIndexOf("IdToggle(")

        if (lastIdToggleIndex == -1) {
            Messages.showErrorDialog(
                project,
                "Could not find IdToggle pattern in documentation file",
                "Error"
            )

            return
        }

        var currentIndex = lastIdToggleIndex
        var parenthesesCount = 0
        var foundOpeningParenthesis = false

        while (currentIndex < fileText.length) {
            val char = fileText[currentIndex]
            when (char) {
                '(' -> {
                    parenthesesCount++
                    foundOpeningParenthesis = true
                }

                ')' -> {
                    parenthesesCount--
                    if (foundOpeningParenthesis && parenthesesCount == 0) {
                        val nextCommaIndex = fileText.indexOf(',', currentIndex)
                        if (nextCommaIndex != -1) {
                            val nextNewlineIndex = fileText.indexOf('\n', nextCommaIndex)
                            currentIndex = if (nextNewlineIndex != -1) {
                                nextNewlineIndex + 1
                            } else {
                                fileText.length
                            }
                        }
                        break
                    }
                }
            }
            currentIndex++
        }

        val description = if (toggleData.jiraTask.isNotEmpty()) {
            "${toggleData.jiraTask} ${toggleData.description}"
        } else {
            toggleData.description.ifEmpty { "DESCRIPTION_UNKNOWN" }
        }

        val newIdToggle = """, IdToggle(
        toggle = Toggle.${toggleData.name},
        activationDate = "${toggleData.activationDate}",
        activationVersion = "${toggleData.activationVersion}",
        deprecationDate = "${toggleData.deprecationDate}",
        description = "$description"
    )"""

        WriteCommandAction.runWriteCommandAction(project) {
            try {
                document.insertString(currentIndex + 1, newIdToggle)
                PsiDocumentManager.getInstance(project).commitDocument(document)
            } catch (e: Exception) {
                Messages.showErrorDialog(
                    project,
                    "Error adding toggle documentation: ${e.message}",
                    "Error"
                )
            }
        }
    }

    fun addServiceExtensions(file: PsiFile, toggleData: CreateToggleDialog.Companion.ToggleData) {
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

    fun addRemoteSettingsDefaults(file: PsiFile, toggleData: CreateToggleDialog.Companion.ToggleData) {
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

    private fun convertToSnakeCase(pascalCase: String): String {
        return pascalCase
            .replace(Regex("([a-z])([A-Z])"), "$1_$2")
            .lowercase()
    }
}
