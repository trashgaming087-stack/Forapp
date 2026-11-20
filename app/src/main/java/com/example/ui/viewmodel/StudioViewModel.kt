package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.BuildRecordEntity
import com.example.data.local.SigningKeyEntity
import com.example.data.model.ApkInspectionReport
import com.example.data.model.BuildLogEntry
import com.example.data.model.BuildPipelineState
import com.example.data.model.ProjectDraft
import com.example.data.model.SourceType
import com.example.data.model.StarterTemplate
import com.example.data.model.StarterTemplates
import com.example.data.repository.ForgeRepository
import com.example.engine.ApkForgeEngine
import com.example.engine.ForgeProgressEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface InspectorUiState {
    data object Idle : InspectorUiState
    data object Loading : InspectorUiState
    data class Loaded(val report: ApkInspectionReport, val apkPath: String) : InspectorUiState
    data class Error(val message: String) : InspectorUiState
}

class StudioViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ForgeRepository(application)

    private val _draft = MutableStateFlow(ProjectDraft())
    val draft: StateFlow<ProjectDraft> = _draft.asStateFlow()

    private val _pipelineState = MutableStateFlow<BuildPipelineState>(BuildPipelineState.Idle)
    val pipelineState: StateFlow<BuildPipelineState> = _pipelineState.asStateFlow()

    private val _inspectorState = MutableStateFlow<InspectorUiState>(InspectorUiState.Idle)
    val inspectorState: StateFlow<InspectorUiState> = _inspectorState.asStateFlow()

    private val _selectedTemplateId = MutableStateFlow(StarterTemplates.all.first().id)
    val selectedTemplateId: StateFlow<String> = _selectedTemplateId.asStateFlow()

    val builds: StateFlow<List<BuildRecordEntity>> = repository.builds.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val signingKeys: StateFlow<List<SigningKeyEntity>> = repository.signingKeys.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private var activeBuildJob: Job? = null

    init {
        viewModelScope.launch {
            val defaultKey = repository.ensureDefaultSigningKey()
            _draft.update { current ->
                if (current.selectedKeyId == 0L) current.copy(selectedKeyId = defaultKey.id) else current
            }
        }
    }

    fun updateAppName(value: String) {
        _draft.update {
            val trimmed = value.take(30)
            val suggestedBadge = trimmed.trim().split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
                .take(2)
                .joinToString("") { w -> w.first().uppercaseChar().toString() }
                .ifBlank { it.badgeLabel }
            it.copy(appName = trimmed, badgeLabel = suggestedBadge)
        }
    }

    fun updatePackageName(value: String) {
        _draft.update { it.copy(packageName = value.lowercase().replace(" ", "")) }
    }

    fun updateVersionName(value: String) {
        _draft.update { it.copy(versionName = value) }
    }

    fun incrementVersionCode(delta: Int) {
        _draft.update { it.copy(versionCode = (it.versionCode + delta).coerceIn(1, 99999)) }
    }

    fun updateSourceType(type: SourceType) {
        _draft.update { it.copy(sourceType = type) }
    }

    fun updateTargetUrl(url: String) {
        _draft.update { it.copy(targetUrl = url) }
    }

    fun updateHtmlContent(html: String) {
        _draft.update { it.copy(htmlContent = html) }
    }

    fun applyTemplate(template: StarterTemplate) {
        _selectedTemplateId.value = template.id
        _draft.update {
            it.copy(
                sourceType = SourceType.TEMPLATE,
                appName = template.defaultAppName,
                packageName = template.defaultPackage,
                themeColorHex = template.themeHex,
                badgeLabel = template.badgeText,
                htmlContent = template.htmlCode
            )
        }
    }

    fun updateThemeColorHex(hex: String) {
        _draft.update { it.copy(themeColorHex = hex) }
    }

    fun updateAccentColorHex(hex: String) {
        _draft.update { it.copy(accentColorHex = hex) }
    }

    fun updateBadgeLabel(label: String) {
        _draft.update { it.copy(badgeLabel = label.uppercase().take(2)) }
    }

    fun updateCustomIconUri(uriString: String?) {
        _draft.update { it.copy(customIconUri = uriString) }
    }

    fun updateEnableJavaScript(enabled: Boolean) {
        _draft.update { it.copy(enableJavaScript = enabled) }
    }

    fun updateDomStorage(enabled: Boolean) {
        _draft.update { it.copy(domStorageEnabled = enabled) }
    }

    fun updateFullscreen(fullscreen: Boolean) {
        _draft.update { it.copy(fullscreen = fullscreen) }
    }

    fun updatePullToRefresh(enabled: Boolean) {
        _draft.update { it.copy(pullToRefresh = enabled) }
    }

    fun selectSigningKey(keyId: Long) {
        _draft.update { it.copy(selectedKeyId = keyId) }
    }

    fun isPackageValid(pkg: String): Boolean = ApkForgeEngine.PACKAGE_REGEX.matches(pkg.trim())

    fun isUrlValid(url: String): Boolean {
        val t = url.trim()
        return t.startsWith("http://") || t.startsWith("https://")
    }

    fun canBuild(d: ProjectDraft = _draft.value): Boolean {
        if (d.appName.trim().isEmpty()) return false
        if (!isPackageValid(d.packageName)) return false
        return if (d.sourceType == SourceType.URL) isUrlValid(d.targetUrl) else d.htmlContent.trim().isNotEmpty()
    }

    fun loadProjectIntoStudio(projectId: Long) {
        viewModelScope.launch {
            val loaded = repository.getProjectDraft(projectId)
            if (loaded != null) {
                _draft.value = loaded
                _pipelineState.value = BuildPipelineState.Idle
            }
        }
    }

    fun forgeApk() {
        val currentDraft = _draft.value
        if (!canBuild(currentDraft)) return
        activeBuildJob?.cancel()

        activeBuildJob = viewModelScope.launch {
            val savedProjectId = repository.saveProjectDraft(currentDraft)
            val updatedDraft = currentDraft.copy(id = savedProjectId)
            _draft.value = updatedDraft

            val key = repository.resolveSigningKey(updatedDraft.selectedKeyId)
            val logList = mutableListOf<BuildLogEntry>()

            repository.forgeApk(updatedDraft, key).collect { event ->
                when (event) {
                    is ForgeProgressEvent.Stage -> {
                        logList.add(event.logEntry)
                        _pipelineState.value = BuildPipelineState.Running(
                            stepIndex = event.stepIndex,
                            totalSteps = event.totalSteps,
                            currentStage = event.stageTitle,
                            logs = logList.toList()
                        )
                    }
                    is ForgeProgressEvent.Complete -> {
                        val buildId = repository.recordBuild(savedProjectId, updatedDraft, key, event)
                        _pipelineState.value = BuildPipelineState.Succeeded(
                            buildId = buildId,
                            apkPath = event.outputFile.absolutePath,
                            fileName = event.outputFile.name,
                            fileSizeBytes = event.fileSizeBytes,
                            durationMs = event.durationMs,
                            certSha256 = event.certSha256,
                            isVerified = event.isVerified,
                            logs = event.logs
                        )
                    }
                    is ForgeProgressEvent.Failure -> {
                        _pipelineState.value = BuildPipelineState.Failed(
                            errorMessage = event.message,
                            failedStage = event.stageTitle,
                            logs = event.logs
                        )
                    }
                }
            }
        }
    }

    fun resetPipeline() {
        _pipelineState.value = BuildPipelineState.Idle
    }

    fun deleteBuild(build: BuildRecordEntity) {
        viewModelScope.launch {
            repository.deleteBuild(build)
        }
    }

    fun createSigningKey(
        alias: String,
        commonName: String,
        organization: String,
        validityYears: Int,
        onDone: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                val created = repository.createSigningKey(alias, commonName, organization, validityYears)
                _draft.update { it.copy(selectedKeyId = created.id) }
            }.onSuccess {
                onDone(true, null)
            }.onFailure { err ->
                onDone(false, err.message ?: "Failed to create signing key")
            }
        }
    }

    fun deleteSigningKey(key: SigningKeyEntity) {
        viewModelScope.launch {
            repository.deleteSigningKey(key)
            if (_draft.value.selectedKeyId == key.id) {
                val defaultKey = repository.ensureDefaultSigningKey()
                _draft.update { it.copy(selectedKeyId = defaultKey.id) }
            }
        }
    }

    fun inspectApk(apkPath: String) {
        _inspectorState.value = InspectorUiState.Loading
        viewModelScope.launch {
            runCatching {
                repository.inspectApk(apkPath)
            }.onSuccess { report ->
                _inspectorState.value = InspectorUiState.Loaded(report, apkPath)
            }.onFailure { err ->
                _inspectorState.value = InspectorUiState.Error(err.message ?: "Unable to inspect APK")
            }
        }
    }
}
