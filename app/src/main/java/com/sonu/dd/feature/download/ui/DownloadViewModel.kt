package com.sonu.dd.feature.download.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonu.dd.core.data.datastore.DDPreferences
import com.sonu.dd.core.data.db.DownloadDao
import com.sonu.dd.core.data.db.DownloadEntity
import com.sonu.dd.core.domain.model.DownloadState
import com.sonu.dd.core.domain.model.DownloadStatus
import com.sonu.dd.core.domain.model.TorrentSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class DownloadsUiState(
    val totalSpeed: Long = 0L,
    val peakSpeed: Long = 0L,
    val activeCount: Int = 0,
)

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val downloadDao: DownloadDao,
    private val preferences: DDPreferences,
) : ViewModel() {

    val allDownloads = downloadDao.getAllDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeDownloads = downloadDao.getActiveDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _downloadsState = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadsState: StateFlow<Map<String, DownloadState>> = _downloadsState.asStateFlow()

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    private val _totalSpeed = MutableStateFlow(0L)
    val totalSpeed: StateFlow<Long> = _totalSpeed.asStateFlow()

    private val _peakSpeed = MutableStateFlow(0L)
    val peakSpeed: StateFlow<Long> = _peakSpeed.asStateFlow()

    /**
     * Add a new download from a magnet URI.
     */
    fun addDownload(
        magnetUri: String,
        name: String,
        size: Long,
        source: String,
        selectedFormat: String = "original"
    ) {
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            val entity = DownloadEntity(
                id = id,
                name = name,
                magnetUri = magnetUri,
                totalSize = size,
                downloadedSize = 0L,
                progress = 0f,
                status = DownloadStatus.QUEUED.name,
                source = source,
                filePath = "",
                selectedFormat = selectedFormat,
                addedTimestamp = System.currentTimeMillis()
            )
            downloadDao.insertDownload(entity)

            // Start the download simulation (in production, this would start LibTorrent4J)
            startDownloadSimulation(id, name, magnetUri, size, source)
        }
    }

    /**
     * Simulates download progress for demonstration.
     * In production, this would be driven by LibTorrent4J session stats.
     */
    private fun startDownloadSimulation(
        id: String,
        name: String,
        magnetUri: String,
        totalSize: Long,
        source: String
    ) {
        viewModelScope.launch {
            val actualSize = if (totalSize > 0) totalSize else 700 * 1024 * 1024L // default 700MB

            // Metadata phase
            updateDownloadState(id, DownloadState(
                id = id, name = name, magnetUri = magnetUri,
                totalSize = actualSize, downloadedSize = 0,
                progress = 0f, downloadSpeed = 0, uploadSpeed = 0,
                seeds = 0, peers = 0, eta = 0,
                status = DownloadStatus.METADATA,
                source = try { TorrentSource.valueOf(source) } catch (_: Exception) { TorrentSource.TPB }
            ))
            downloadDao.updateStatus(id, DownloadStatus.METADATA.name)
            delay(2000)

            // Downloading phase
            var downloaded = 0L
            val baseSpeed = (2 * 1024 * 1024L)..( 15 * 1024 * 1024L) // 2-15 MB/s
            var peakSpeedLocal = 0L

            downloadDao.updateStatus(id, DownloadStatus.DOWNLOADING.name)

            while (downloaded < actualSize) {
                val currentState = _downloadsState.value[id]
                if (currentState?.status == DownloadStatus.PAUSED) {
                    delay(500)
                    continue
                }
                if (currentState?.status == DownloadStatus.FAILED) {
                    return@launch
                }

                val speed = baseSpeed.random()
                val chunk = (speed * 0.3).toLong() // 300ms interval
                downloaded = (downloaded + chunk).coerceAtMost(actualSize)
                val progress = downloaded.toFloat() / actualSize
                val eta = if (speed > 0) (actualSize - downloaded) / speed else 0L

                if (speed > peakSpeedLocal) peakSpeedLocal = speed

                updateDownloadState(id, DownloadState(
                    id = id, name = name, magnetUri = magnetUri,
                    totalSize = actualSize, downloadedSize = downloaded,
                    progress = progress, downloadSpeed = speed,
                    uploadSpeed = speed / 8,
                    seeds = (5..50).random(), peers = (2..20).random(),
                    eta = eta, status = DownloadStatus.DOWNLOADING,
                source = try { TorrentSource.valueOf(source) } catch (_: Exception) { TorrentSource.TPB }
                ))

                downloadDao.updateProgress(id, progress, downloaded)
                updateTotalSpeed()

                delay(300)
            }

            // Completed
            updateDownloadState(id, DownloadState(
                id = id, name = name, magnetUri = magnetUri,
                totalSize = actualSize, downloadedSize = actualSize,
                progress = 1f, downloadSpeed = 0, uploadSpeed = 0,
                seeds = 0, peers = 0, eta = 0,
                status = DownloadStatus.COMPLETED,
                source = try { TorrentSource.valueOf(source) } catch (_: Exception) { TorrentSource.TPB }
            ))
            downloadDao.updateStatus(id, DownloadStatus.COMPLETED.name)
            downloadDao.updateProgress(id, 1f, actualSize)
            updateTotalSpeed()
        }
    }

    private fun updateDownloadState(id: String, state: DownloadState) {
        _downloadsState.value = _downloadsState.value.toMutableMap().apply {
            put(id, state)
        }
    }

    private fun updateTotalSpeed() {
        val speeds = _downloadsState.value.values
            .filter { it.status == DownloadStatus.DOWNLOADING }
            .sumOf { it.downloadSpeed }
        _totalSpeed.value = speeds

        if (speeds > _peakSpeed.value) {
            _peakSpeed.value = speeds
        }

        _uiState.value = _uiState.value.copy(
            totalSpeed = speeds,
            peakSpeed = _peakSpeed.value,
            activeCount = _downloadsState.value.values.count { it.status.isActive }
        )
    }

    fun pauseDownload(id: String) {
        val current = _downloadsState.value[id] ?: return
        updateDownloadState(id, current.copy(status = DownloadStatus.PAUSED, downloadSpeed = 0))
        viewModelScope.launch {
            downloadDao.updateStatus(id, DownloadStatus.PAUSED.name)
            updateTotalSpeed()
        }
    }

    fun resumeDownload(id: String) {
        val current = _downloadsState.value[id] ?: return
        updateDownloadState(id, current.copy(status = DownloadStatus.DOWNLOADING))
        viewModelScope.launch {
            downloadDao.updateStatus(id, DownloadStatus.DOWNLOADING.name)
        }
    }

    fun cancelDownload(id: String) {
        val current = _downloadsState.value[id]
        if (current != null) {
            updateDownloadState(id, current.copy(status = DownloadStatus.FAILED))
        }
        _downloadsState.value = _downloadsState.value.toMutableMap().apply { remove(id) }
        viewModelScope.launch {
            downloadDao.deleteDownload(id)
            updateTotalSpeed()
        }
    }

    fun pauseAll() {
        _downloadsState.value.keys.forEach { pauseDownload(it) }
    }

    fun resumeAll() {
        _downloadsState.value.filter { it.value.status == DownloadStatus.PAUSED }
            .keys.forEach { resumeDownload(it) }
    }
}
