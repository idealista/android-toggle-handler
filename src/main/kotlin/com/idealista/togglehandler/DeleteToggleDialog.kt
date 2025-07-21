package com.idealista.togglehandler

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class DeleteToggleDialog(private val project: Project) : DialogWrapper(project) {
    private val fileModifier = ToggleFileModifier(project)
    private val toggleComboBox = ComboBox<String>()

    init {
        title = "Delete Toggle"
        init()
        loadAvailableToggles()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()

        gbc.insets = JBUI.insets(5)
        gbc.anchor = GridBagConstraints.WEST

        gbc.gridx = 0
        gbc.gridy = 0
        gbc.weightx = 0.0
        panel.add(JLabel("Select a toggle:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        toggleComboBox.preferredSize = Dimension(300, toggleComboBox.preferredSize.height)
        panel.add(toggleComboBox, gbc)

        gbc.gridx = 0
        gbc.gridy = 1
        gbc.gridwidth = 2
        gbc.weightx = 1.0
        gbc.insets = JBUI.insets(15, 5, 5, 5)
        val infoLabel = JLabel("<html><i>This action will delete it from Toggle.kt and RemoteSettingsDefault,\nbut not from ServiceExtensions.kt</i></html>")
        panel.add(infoLabel, gbc)

        return panel
    }

    private fun loadAvailableToggles() {
        val toggleFiles = findToggleFiles()
        val toggleFile = toggleFiles.find { it.second == ToggleDefinitions.ToggleFile }?.first

        if (toggleFile != null) {
            val toggleNames = fileModifier.extractToggleNames(toggleFile).filter { it != "None" }
            toggleComboBox.model = DefaultComboBoxModel(toggleNames.toTypedArray())
        } else {
            Messages.showErrorDialog(
                project,
                "Could not find file Toggle.kt",
                "Error"
            )
        }
    }

    override fun doOKAction() {
        val selectedToggle = toggleComboBox.selectedItem as? String

        if (selectedToggle.isNullOrEmpty()) {
            Messages.showWarningDialog(
                project,
                "Please select a toggle to delete",
                "Required Action"
            )
            return
        }

        val toggleData = ToggleDefinitions.ToggleData(selectedToggle)
        deleteToggle(toggleData)
        super.doOKAction()
    }

    private fun deleteToggle(toggleData: ToggleDefinitions.ToggleData) {
        val filesToModify = findToggleFiles()

        if (filesToModify.isEmpty()) {
            Messages.showErrorDialog(
                project,
                "No files found to modify",
                "Error"
            )
            return
        }

        try {
            modifyFilesToRemoveToggle(filesToModify, toggleData)
        } catch (e: Exception) {
            Messages.showErrorDialog(
                project,
                "Error deleting toggle: ${e.message}",
                "Error"
            )
        }
    }

    private fun findToggleFiles(): List<Pair<PsiFile, Any>> {
        val foundFiles = mutableListOf<Pair<PsiFile, Any>>()
        val scope = GlobalSearchScope.projectScope(project)

        ToggleDefinitions.TARGET_FILES.forEach { (fileName, fileType) ->
            val files = FilenameIndex.getVirtualFilesByName(fileName, scope).mapNotNull { virtualFile ->
                PsiManager.getInstance(project).findFile(virtualFile)
            }

            files.forEach { file ->
                foundFiles.add(file to fileType)
            }
        }

        return foundFiles
    }

    private fun modifyFilesToRemoveToggle(files: List<Pair<PsiFile, Any>>, toggleData: ToggleDefinitions.ToggleData) {
        files.forEach { (file, fileType) ->
            when (fileType) {
                ToggleDefinitions.ToggleFile -> {
                    fileModifier.removeToggleDefinition(file, toggleData)
                }

                ToggleDefinitions.ServiceExtensionsFile -> {
                    fileModifier.openServiceExtension(file)
                }

                ToggleDefinitions.RemoteSettingsDefaultsFile -> {
                    fileModifier.removeRemoteSettingsDefaults(file, toggleData)
                }
            }
        }
    }
}
