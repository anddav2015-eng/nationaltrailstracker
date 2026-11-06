package com.anddav.nationaltrailstracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anddav.nationaltrailstracker.data.map.OfflineMapStore
import com.anddav.nationaltrailstracker.domain.map.OfflineMapKeys
import com.anddav.nationaltrailstracker.repo.ProgressRepository
import com.anddav.nationaltrailstracker.repo.TrailRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One saved offline map, ready to show. */
data class OfflineMapRow(
    val key: String,
    val title: String,
    val subtitle: String?,
    /** For ordering: whole-trail maps first, then stages in walking order. */
    val stageIndex: Int?,
    val sizeBytes: Long,
    val isComplete: Boolean,
)

data class SettingsUiState(
    val offlineMapsLoading: Boolean = true,
    val offlineMaps: List<OfflineMapRow> = emptyList(),
    /** True while maps are being removed. */
    val removing: Boolean = false,
    val message: String? = null,
) {
    val offlineMapsTotalBytes: Long get() = offlineMaps.sumOf { it.sizeBytes }
}

class SettingsViewModel(
    private val progressRepository: ProgressRepository,
    private val trailRepository: TrailRepository,
    private val offlineMapStore: OfflineMapStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { refreshOfflineMaps() }
    }

    /** Plain suspend functions so the file-picker flow in the screen can await them and report back. */
    suspend fun exportBackup(): String = progressRepository.exportBackupJson()

    suspend fun importBackup(text: String) = progressRepository.importBackupJson(text)

    fun removeOfflineMap(key: String) = remove(listOf(key))

    fun removeAllOfflineMaps() = remove(_uiState.value.offlineMaps.map { it.key })

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }

    private fun remove(keys: List<String>) {
        if (keys.isEmpty() || _uiState.value.removing) return
        viewModelScope.launch {
            _uiState.update { it.copy(removing = true) }
            val failed = keys.count { !offlineMapStore.delete(it) }
            refreshOfflineMaps()
            _uiState.update {
                it.copy(
                    removing = false,
                    message = when {
                        failed > 0 -> "Couldn't remove $failed saved map${if (failed == 1) "" else "s"}."
                        keys.size == 1 -> "Offline map removed."
                        else -> "All offline maps removed."
                    },
                )
            }
        }
    }

    private suspend fun refreshOfflineMaps() {
        val rows = offlineMapStore.list().map { saved ->
            val parsed = OfflineMapKeys.parse(saved.key)
            val trail = parsed?.let { (trailId, _) -> runCatching { trailRepository.trail(trailId) }.getOrNull() }
            val stageIndex = parsed?.second
            val stage = stageIndex?.let { trail?.defaultStages?.getOrNull(it) }
            OfflineMapRow(
                key = saved.key,
                title = trail?.name ?: saved.key,
                subtitle = when {
                    trail == null -> null
                    stageIndex == null -> "Whole trail"
                    stage != null -> "Stage ${stageIndex + 1}: ${stage.fromLandmark} → ${stage.toLandmark}"
                    else -> "Stage ${stageIndex + 1}"
                },
                stageIndex = stageIndex,
                sizeBytes = saved.sizeBytes,
                isComplete = saved.isComplete,
            )
        }.sortedWith(compareBy({ it.title }, { it.stageIndex ?: -1 }))
        _uiState.update { it.copy(offlineMapsLoading = false, offlineMaps = rows) }
    }
}
