package com.example.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.hermes.HermesApiClient
import com.example.data.model.ConnectionProfile
import com.example.data.model.ServerConfig
import com.example.data.security.EncryptedStorage
import com.jcraft.jsch.JSch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val profiles: List<ConnectionProfile> = emptyList(),
    val activeProfileId: String = ConnectionProfile.PROFILE_HOME_ID,
    val profileName: String = "",
    val host: String = "",
    val portText: String = "22",
    val user: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val hermesUrl: String = "",
    val hermesKey: String = "",
    val hermesKeyVisible: Boolean = false,
    val hermesModel: String = "",
    val isTestingSsh: Boolean = false,
    val sshTestResult: Pair<Boolean, String>? = null,
    val isTestingHermes: Boolean = false,
    val hermesTestResult: Pair<Boolean, String>? = null,
    val isDetectingModels: Boolean = false,
    val detectedModels: List<String> = emptyList(),
    val isSaving: Boolean = false,
    val statusMessage: String? = null
) {
    val activeProfile: ConnectionProfile
        get() = profiles.find { it.id == activeProfileId }
            ?: profiles.firstOrNull()
            ?: ConnectionProfile.defaultHomeProfile()

    fun buildServerConfig(): ServerConfig {
        val parsedPort = portText.toIntOrNull() ?: 22
        return ServerConfig(
            sshHost = host.trim(),
            sshPort = parsedPort,
            sshUser = user.trim(),
            sshPassword = password,
            hermesBaseUrl = hermesUrl.trim(),
            hermesApiKey = hermesKey.trim(),
            hermesModel = hermesModel.trim()
        )
    }
}

class SettingsViewModel(
    private val encryptedStorage: EncryptedStorage,
    private val hermesClient: HermesApiClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        val loadedProfiles = encryptedStorage.loadProfiles()
        val activeId = encryptedStorage.getActiveProfileId()
        val active = loadedProfiles.find { it.id == activeId } ?: loadedProfiles.firstOrNull() ?: ConnectionProfile.defaultHomeProfile()

        _uiState.update { current ->
            current.copy(
                profiles = loadedProfiles,
                activeProfileId = active.id,
                profileName = active.name,
                host = active.config.sshHost,
                portText = active.config.sshPort.toString(),
                user = active.config.sshUser,
                password = active.config.sshPassword,
                hermesUrl = active.config.hermesBaseUrl,
                hermesKey = active.config.hermesApiKey,
                hermesModel = active.config.hermesModel
            )
        }
    }

    fun updateProfileName(name: String) {
        _uiState.update { it.copy(profileName = name) }
    }

    fun updateHost(host: String) {
        _uiState.update { it.copy(host = host) }
    }

    fun updatePort(port: String) {
        _uiState.update { it.copy(portText = port) }
    }

    fun updateUser(user: String) {
        _uiState.update { it.copy(user = user) }
    }

    fun updatePassword(password: String) {
        _uiState.update { it.copy(password = password) }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    fun updateHermesUrl(url: String) {
        _uiState.update { it.copy(hermesUrl = url) }
    }

    fun updateHermesKey(key: String) {
        _uiState.update { it.copy(hermesKey = key) }
    }

    fun toggleHermesKeyVisibility() {
        _uiState.update { it.copy(hermesKeyVisible = !it.hermesKeyVisible) }
    }

    fun updateHermesModel(model: String) {
        _uiState.update { it.copy(hermesModel = model) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun selectProfile(newId: String, onConfigUpdated: (ServerConfig) -> Unit) {
        val state = _uiState.value
        if (newId == state.activeProfileId) return

        // Auto-save current draft before switching
        val currentDraft = state.activeProfile.copy(
            name = state.profileName.ifBlank { "Profil ${state.activeProfileId}" },
            config = state.buildServerConfig()
        )
        encryptedStorage.saveProfile(currentDraft)

        encryptedStorage.setActiveProfileId(newId)
        val reloaded = encryptedStorage.loadProfiles()
        val switched = reloaded.find { it.id == newId } ?: reloaded.first()

        _uiState.update {
            it.copy(
                profiles = reloaded,
                activeProfileId = newId,
                profileName = switched.name,
                host = switched.config.sshHost,
                portText = switched.config.sshPort.toString(),
                user = switched.config.sshUser,
                password = switched.config.sshPassword,
                hermesUrl = switched.config.hermesBaseUrl,
                hermesKey = switched.config.hermesApiKey,
                hermesModel = switched.config.hermesModel,
                sshTestResult = null,
                hermesTestResult = null,
                detectedModels = emptyList(),
                statusMessage = "Beralih ke profil: ${switched.name}"
            )
        }
        onConfigUpdated(switched.config)
    }

    fun addNewProfile(onConfigUpdated: (ServerConfig) -> Unit) {
        val state = _uiState.value
        val currentDraft = state.activeProfile.copy(
            name = state.profileName.ifBlank { "Profil ${state.activeProfileId}" },
            config = state.buildServerConfig()
        )
        encryptedStorage.saveProfile(currentDraft)

        val newId = "profile_${System.currentTimeMillis()}"
        val newProfile = ConnectionProfile(
            id = newId,
            name = "Profil Baru (${state.profiles.size + 1})",
            config = ServerConfig(
                sshHost = "192.168.1.1",
                sshPort = 22,
                sshUser = "admin",
                sshPassword = "",
                hermesBaseUrl = "https://192.168.1.1:11434",
                hermesApiKey = "",
                hermesModel = "hermes-3-llama-3.1-8b"
            )
        )
        encryptedStorage.saveProfile(newProfile)
        encryptedStorage.saveProfileIds(state.profiles.map { it.id } + newId)
        selectProfile(newId, onConfigUpdated)
    }

    fun deleteCurrentProfile(onConfigUpdated: (ServerConfig) -> Unit) {
        val state = _uiState.value
        if (state.profiles.size <= 1) {
            _uiState.update { it.copy(statusMessage = "Minimal harus ada satu profil tersisa") }
            return
        }
        val toDelete = state.activeProfileId
        val remaining = state.profiles.filter { it.id != toDelete }
        encryptedStorage.deleteProfile(toDelete)
        val nextId = remaining.first().id
        encryptedStorage.setActiveProfileId(nextId)
        val next = remaining.first()

        _uiState.update {
            it.copy(
                profiles = remaining,
                activeProfileId = nextId,
                profileName = next.name,
                host = next.config.sshHost,
                portText = next.config.sshPort.toString(),
                user = next.config.sshUser,
                password = next.config.sshPassword,
                hermesUrl = next.config.hermesBaseUrl,
                hermesKey = next.config.hermesApiKey,
                hermesModel = next.config.hermesModel,
                sshTestResult = null,
                hermesTestResult = null,
                statusMessage = "Profil dihapus"
            )
        }
        onConfigUpdated(next.config)
    }

    fun saveSettings(onConfigUpdated: (ServerConfig) -> Unit) {
        val state = _uiState.value
        val config = state.buildServerConfig()
        val updatedProfile = state.activeProfile.copy(
            name = state.profileName.ifBlank { "Profil ${state.activeProfileId}" },
            config = config
        )
        encryptedStorage.saveProfile(updatedProfile)
        encryptedStorage.saveConfig(config)
        val reloaded = encryptedStorage.loadProfiles()

        _uiState.update {
            it.copy(
                profiles = reloaded,
                statusMessage = "Kredensial '${updatedProfile.name}' berhasil disimpan ke Keystore!"
            )
        }
        onConfigUpdated(config)
    }

    fun exportProfiles(): String {
        // Auto-save current form into active profile before exporting
        val state = _uiState.value
        val config = state.buildServerConfig()
        val currentDraft = state.activeProfile.copy(
            name = state.profileName.ifBlank { "Profil ${state.activeProfileId}" },
            config = config
        )
        encryptedStorage.saveProfile(currentDraft)
        return encryptedStorage.exportProfilesPayload()
    }

    fun importProfiles(payload: String, onConfigUpdated: (ServerConfig) -> Unit): Result<Int> {
        val result = encryptedStorage.importProfilesPayload(payload)
        return result.mapCatching { importedList ->
            val first = importedList.first()
            _uiState.update {
                it.copy(
                    profiles = importedList,
                    activeProfileId = first.id,
                    profileName = first.name,
                    host = first.config.sshHost,
                    portText = first.config.sshPort.toString(),
                    user = first.config.sshUser,
                    password = first.config.sshPassword,
                    hermesUrl = first.config.hermesBaseUrl,
                    hermesKey = first.config.hermesApiKey,
                    hermesModel = first.config.hermesModel,
                    sshTestResult = null,
                    hermesTestResult = null,
                    statusMessage = "${importedList.size} Profil berhasil dipulihkan!"
                )
            }
            onConfigUpdated(first.config)
            importedList.size
        }
    }

    fun testSshConnection() {
        val state = _uiState.value
        val cfg = state.buildServerConfig()
        if (!cfg.isSshConfigured) {
            _uiState.update { it.copy(statusMessage = "Isi Host, User, dan Password dulu") }
            return
        }

        _uiState.update { it.copy(isTestingSsh = true, sshTestResult = null) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsch = JSch()
                val session = jsch.getSession(cfg.sshUser, cfg.sshHost, cfg.sshPort)
                session.setPassword(cfg.sshPassword)
                session.setConfig("StrictHostKeyChecking", "no")
                session.connect(10000)
                session.disconnect()
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(isTestingSsh = false, sshTestResult = Pair(true, "Koneksi SSH Homeserver Berhasil!"))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(isTestingSsh = false, sshTestResult = Pair(false, "Gagal: ${e.localizedMessage}"))
                    }
                }
            }
        }
    }

    fun testHermesEndpoint() {
        val state = _uiState.value
        val cfg = state.buildServerConfig()
        _uiState.update { it.copy(isTestingHermes = true, hermesTestResult = null) }

        viewModelScope.launch {
            val result = hermesClient.testConnection(cfg)
            result.onSuccess { msg ->
                _uiState.update { it.copy(isTestingHermes = false, hermesTestResult = Pair(true, msg)) }
            }.onFailure { err ->
                _uiState.update { it.copy(isTestingHermes = false, hermesTestResult = Pair(false, "Error: ${err.localizedMessage}")) }
            }
        }
    }

    fun autoDetectModels() {
        val state = _uiState.value
        val url = state.hermesUrl
        val key = state.hermesKey
        if (url.isBlank()) {
            _uiState.update { it.copy(statusMessage = "Isi Base URL API terlebih dahulu") }
            return
        }

        _uiState.update { it.copy(isDetectingModels = true) }

        viewModelScope.launch {
            val res = hermesClient.fetchAvailableModels(url, key)
            _uiState.update { current ->
                current.copy(isDetectingModels = false)
            }
            res.onSuccess { models ->
                _uiState.update { current ->
                    val chosen = if (current.hermesModel.isBlank() || current.hermesModel !in models) {
                        models.firstOrNull() ?: current.hermesModel
                    } else {
                        current.hermesModel
                    }
                    current.copy(
                        detectedModels = models,
                        hermesModel = chosen,
                        statusMessage = if (models.isNotEmpty()) "Terdeteksi ${models.size} model: ${models.first()}" else "Tidak ada model ditemukan"
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(statusMessage = "Gagal deteksi: ${err.localizedMessage}") }
            }
        }
    }

    fun applyPresetTailscale(onConfigUpdated: (ServerConfig) -> Unit) {
        _uiState.update {
            it.copy(
                host = "100.115.92.40",
                portText = "22",
                user = "admin",
                password = "REMOVED_CREDENTIAL",
                hermesUrl = "https://100.115.92.40:11434",
                hermesKey = "REMOVED_CREDENTIAL",
                hermesModel = "hermes-3-llama-3.1-8b"
            )
        }
        saveSettings(onConfigUpdated)
    }

    companion object {
        fun provideFactory(
            encryptedStorage: EncryptedStorage,
            hermesClient: HermesApiClient
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(encryptedStorage, hermesClient) as T
            }
        }
    }
}
