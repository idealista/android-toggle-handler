package com.idealista.togglehandler

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.PsiFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ItemEvent
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.*

class CreateToggleDialog(private val project: Project) : DialogWrapper(project) {

    companion object {
        data class ToggleData(
            val name: String,
            val jiraTask: String,
            val description: String,
            val isRemotelyConfigurable: Boolean,
            val activationDate: String,
            val activationVersion: String,
            val deprecationDate: String,
        )

        data object ToggleFile
        data object ToggleDocFile
        data object ServiceExtensionsFile
        data object RemoteSettingsDefaultsFile

        private val TARGET_FILES = mapOf(
            "Toggle.kt" to ToggleFile,
            "ToggleDoc.kt" to ToggleDocFile,
            "ServiceExtensions.kt" to ServiceExtensionsFile,
            "RemoteSettingsDefaults.kt" to RemoteSettingsDefaultsFile
        )
    }

    private val fileModifier = ToggleFileModifier(project)
    private val toggleNameField = JBTextField()
    private val jiraTaskField = JBTextField()
    private val description = JBTextField()
    private val isRemotelyConfigurableCheckBox = JBCheckBox("Toggle is remotely configurable")
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy")
    private val activationDateSpinner = JSpinner(SpinnerDateModel())
    private val activationVersion = JBTextField()
    private val deprecationDateSpinner = JSpinner(SpinnerDateModel())
    private val activationDateLabel = JLabel("Activation date:")
    private val activationVersionLabel = JLabel("Activation version:")
    private val deprecationDateLabel = JLabel("Deprecation date:")

    init {
        title = "Create Toggle"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()

        // Label and field for toggle name
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.anchor = GridBagConstraints.WEST
        gbc.insets = JBUI.insets(5)
        panel.add(JLabel("Toggle name:"), gbc)

        gbc.gridx = 1
        gbc.gridy = 0
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        toggleNameField.columns = 30
        toggleNameField.emptyText.text = "AsyncSearchActivityMigration"
        panel.add(toggleNameField, gbc)

        // Jira task
        gbc.gridx = 0
        gbc.gridy = 1
        gbc.anchor = GridBagConstraints.WEST
        gbc.insets = JBUI.insets(5)
        panel.add(JLabel("Jira task:"), gbc)

        gbc.gridx = 1
        gbc.gridy = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        jiraTaskField.columns = 30
        jiraTaskField.emptyText.text = "IMASD-45809"
        panel.add(jiraTaskField, gbc)

        // Description
        gbc.gridx = 0
        gbc.gridy = 2
        gbc.anchor = GridBagConstraints.WEST
        gbc.insets = JBUI.insets(5)
        panel.add(JLabel("Description:"), gbc)

        gbc.gridx = 1
        gbc.gridy = 2
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        description.columns = 30
        description.emptyText.text = "Migrate AsyncSearchActivity from Java to Kotlin"
        panel.add(description, gbc)

        // Checkbox "isRemotelyConfigurable"
        gbc.gridx = 0
        gbc.gridy = 3
        gbc.gridwidth = 2
        gbc.fill = GridBagConstraints.NONE
        gbc.weightx = 0.0
        gbc.insets = JBUI.insets(10, 5, 5, 5)
        panel.add(isRemotelyConfigurableCheckBox, gbc)

        activationDateSpinner.model = SpinnerDateModel(Date(), null, null, Calendar.DAY_OF_MONTH)
        activationDateSpinner.editor = JSpinner.DateEditor(activationDateSpinner, "dd/MM/yyyy")
        deprecationDateSpinner.model = SpinnerDateModel(Date(), null, null, Calendar.DAY_OF_MONTH)
        deprecationDateSpinner.editor = JSpinner.DateEditor(deprecationDateSpinner, "dd/MM/yyyy")

        val spinnerPreferredSize = Dimension(120, activationDateSpinner.preferredSize.height)
        activationDateSpinner.preferredSize = spinnerPreferredSize
        deprecationDateSpinner.preferredSize = spinnerPreferredSize

        // Activation date
        gbc.gridx = 0
        gbc.gridy = 4
        gbc.gridwidth = 1
        gbc.anchor = GridBagConstraints.WEST
        gbc.insets = JBUI.insets(5)
        panel.add(activationDateLabel, gbc)

        gbc.gridx = 1
        gbc.gridy = 4
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        panel.add(activationDateSpinner, gbc)

        // Activation version
        gbc.gridx = 0
        gbc.gridy = 5
        gbc.anchor = GridBagConstraints.WEST
        gbc.insets = JBUI.insets(5)
        panel.add(activationVersionLabel, gbc)

        gbc.gridx = 1
        gbc.gridy = 5
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        activationVersion.columns = 10
        activationVersion.emptyText.text = "13.6.0"
        panel.add(activationVersion, gbc)

        // Deprecation date
        gbc.gridx = 0
        gbc.gridy = 6
        gbc.anchor = GridBagConstraints.WEST
        gbc.insets = JBUI.insets(5)
        panel.add(deprecationDateLabel, gbc)

        gbc.gridx = 1
        gbc.gridy = 6
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        panel.add(deprecationDateSpinner, gbc)

        updateDateFieldsVisibility(false)

        isRemotelyConfigurableCheckBox.addItemListener { e ->
            val isSelected = e.stateChange == ItemEvent.SELECTED
            updateDateFieldsVisibility(isSelected)
        }

        panel.minimumSize = Dimension(500, 400)
        panel.preferredSize = Dimension(500, 400)

        return panel
    }

    private fun updateDateFieldsVisibility(visible: Boolean) {
        activationDateLabel.isVisible = visible
        activationDateSpinner.isVisible = visible
        deprecationDateLabel.isVisible = visible
        deprecationDateSpinner.isVisible = visible
        activationDateLabel.parent?.revalidate()
        activationDateLabel.parent?.repaint()
        activationVersion.isVisible = visible
        activationVersionLabel.isVisible = visible
    }

    override fun doOKAction() {
        val activationDate = if (isRemotelyConfigurableCheckBox.isSelected) {
            dateFormat.format(activationDateSpinner.value)
        } else ""

        val deprecationDate = if (isRemotelyConfigurableCheckBox.isSelected) {
            dateFormat.format(deprecationDateSpinner.value)
        } else ""

        val toggleData = ToggleData(
            name = toggleNameField.text.trim(),
            jiraTask = jiraTaskField.text.trim(),
            description = description.text.trim(),
            isRemotelyConfigurable = isRemotelyConfigurableCheckBox.isSelected,
            activationDate = activationDate,
            activationVersion = activationVersion.text.trim(),
            deprecationDate = deprecationDate
        )

        createToggle(toggleData)

        super.doOKAction()
    }

    private fun createToggle(toggleData: ToggleData) {
        val filesToModify = findToggleFiles()

        if (filesToModify.isNotEmpty()) {
            modifyFilesToAddToggle(filesToModify, toggleData)
        }
    }

    private fun findToggleFiles(): List<Pair<PsiFile, Any>> {
        val foundFiles = mutableListOf<Pair<PsiFile, Any>>()
        val scope = GlobalSearchScope.projectScope(project)

        TARGET_FILES.forEach { (fileName, fileType) ->
            val files = FilenameIndex.getVirtualFilesByName(fileName, scope).mapNotNull { virtualFile ->
                com.intellij.psi.PsiManager.getInstance(project).findFile(virtualFile)
            }

            files.forEach { file ->
                foundFiles.add(file to fileType)
            }
        }

        return foundFiles
    }

    private fun modifyFilesToAddToggle(files: List<Pair<PsiFile, Any>>, toggleData: ToggleData) {
        files.forEach { (file, fileType) ->
            when (fileType) {
                ToggleFile -> { fileModifier.addToggleDefinition(file, toggleData) }

                ToggleDocFile -> { fileModifier.addToggleDocumentation(file, toggleData) }

                ServiceExtensionsFile -> { fileModifier.addServiceExtensions(file, toggleData) }

                RemoteSettingsDefaultsFile -> { fileModifier.addRemoteSettingsDefaults(file, toggleData) }
            }
        }
    }

    override fun getOKAction(): Action = super.getOKAction().apply {
        putValue(Action.NAME, "Create")
    }

    override fun getPreferredSize(): Dimension {
        return Dimension(600, 600)
    }
}
