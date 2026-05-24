package com.sonu.dd.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonu.dd.core.data.datastore.DDPreferences
import com.sonu.dd.core.ui.theme.DDTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val theme: DDTheme = DDTheme.MIDNIGHT_DARK,
    val followSystem: Boolean = false,
    val animationLevel: Int = 0,
    val autoSaveGallery: Boolean = true,
    val simultaneousDownloads: Int = 2,
    val speedLimit: Long = 0L,
    val autoResume: Boolean = true,
    val smartConvert: Boolean = true,
    val keepOriginal: Boolean = false,
    val vpnReminder: Boolean = true,
    val dhtEnabled: Boolean = true,
    val pexEnabled: Boolean = true,
    val anonymousMode: Boolean = false,
    val progressNotification: Boolean = true,
    val appLockEnabled: Boolean = false,
    val incognitoDownloads: Boolean = false,
    val sourceYts: Boolean = true, val source1337x: Boolean = true,
    val sourceTpb: Boolean = true, val sourceEztv: Boolean = true,
    val sourceNyaa: Boolean = true, val sourceAcademic: Boolean = true,
    val sourceTorrentGalaxy: Boolean = true, val sourceLimeTorrents: Boolean = true,
    val sourceSolidTorrents: Boolean = true, val sourceBitsearch: Boolean = true,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: DDPreferences,
) : ViewModel() {

    // Combine ALL preference flows properly (split into groups to avoid combine limit)
    private val appearanceFlow = combine(
        prefs.themeFlow, prefs.followSystemThemeFlow, prefs.animationLevelFlow
    ) { theme, follow, anim -> Triple(theme, follow, anim) }

    private val downloadsFlow = combine(
        prefs.autoSaveGalleryFlow, prefs.simultaneousDownloadsFlow,
        prefs.autoResumeFlow, prefs.smartConvertFlow
    ) { autoSave, simDl, autoResume, smart -> listOf(autoSave, simDl, autoResume, smart) }

    private val networkFlow = combine(
        prefs.vpnReminderFlow, prefs.dhtEnabledFlow, prefs.pexEnabledFlow, prefs.anonymousModeFlow
    ) { vpn, dht, pex, anon -> listOf(vpn, dht, pex, anon) }

    private val securityFlow = combine(
        prefs.progressNotificationFlow, prefs.appLockEnabledFlow, prefs.incognitoDownloadsFlow
    ) { progress, lock, incognito -> Triple(progress, lock, incognito) }

    private val sourcesFlow = combine(
        prefs.sourceYtsFlow, prefs.source1337xFlow, prefs.sourceTpbFlow,
        prefs.sourceEztvFlow, prefs.sourceNyaaFlow
    ) { yts, x1337, tpb, eztv, nyaa -> listOf(yts, x1337, tpb, eztv, nyaa) }

    private val sources2Flow = combine(
        prefs.sourceAcademicFlow, prefs.sourceTorrentGalaxyFlow,
        prefs.sourceLimeTorrentsFlow, prefs.sourceSolidTorrentsFlow, prefs.sourceBitsearchFlow
    ) { academic, tg, lime, solid, bitsearch -> listOf(academic, tg, lime, solid, bitsearch) }

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<SettingsUiState> = combine(
        listOf(appearanceFlow, downloadsFlow, networkFlow, securityFlow, sourcesFlow, sources2Flow)
    ) { results ->
        val appearance = results[0] as Triple<DDTheme, Boolean, Int>
        val downloads = results[1] as List<Any>
        val network = results[2] as List<Any>
        val security = results[3] as Triple<Boolean, Boolean, Boolean>
        val sources = results[4] as List<Any>
        val sources2 = results[5] as List<Any>
        SettingsUiState(
            theme = appearance.first,
            followSystem = appearance.second,
            animationLevel = appearance.third,
            autoSaveGallery = downloads[0] as Boolean,
            simultaneousDownloads = downloads[1] as Int,
            autoResume = downloads[2] as Boolean,
            smartConvert = downloads[3] as Boolean,
            vpnReminder = network[0] as Boolean,
            dhtEnabled = network[1] as Boolean,
            pexEnabled = network[2] as Boolean,
            anonymousMode = network[3] as Boolean,
            progressNotification = security.first,
            appLockEnabled = security.second,
            incognitoDownloads = security.third,
            sourceYts = sources[0] as Boolean,
            source1337x = sources[1] as Boolean,
            sourceTpb = sources[2] as Boolean,
            sourceEztv = sources[3] as Boolean,
            sourceNyaa = sources[4] as Boolean,
            sourceAcademic = sources2[0] as Boolean,
            sourceTorrentGalaxy = sources2[1] as Boolean,
            sourceLimeTorrents = sources2[2] as Boolean,
            sourceSolidTorrents = sources2[3] as Boolean,
            sourceBitsearch = sources2[4] as Boolean,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun setTheme(t: DDTheme) = viewModelScope.launch { prefs.setTheme(t) }
    fun setFollowSystem(f: Boolean) = viewModelScope.launch { prefs.setFollowSystemTheme(f) }
    fun setAnimationLevel(l: Int) = viewModelScope.launch { prefs.setAnimationLevel(l) }
    fun setAutoSaveGallery(s: Boolean) = viewModelScope.launch { prefs.setAutoSaveGallery(s) }
    fun setSimultaneousDownloads(c: Int) = viewModelScope.launch { prefs.setSimultaneousDownloads(c) }
    fun setSpeedLimit(l: Long) = viewModelScope.launch { prefs.setSpeedLimit(l) }
    fun setAutoResume(r: Boolean) = viewModelScope.launch { prefs.setAutoResume(r) }
    fun setSmartConvert(c: Boolean) = viewModelScope.launch { prefs.setSmartConvert(c) }
    fun setVpnReminder(v: Boolean) = viewModelScope.launch { prefs.setVpnReminder(v) }
    fun setDhtEnabled(d: Boolean) = viewModelScope.launch { prefs.setDhtEnabled(d) }
    fun setPexEnabled(p: Boolean) = viewModelScope.launch { prefs.setPexEnabled(p) }
    fun setAnonymousMode(a: Boolean) = viewModelScope.launch { prefs.setAnonymousMode(a) }
    fun setProgressNotification(p: Boolean) = viewModelScope.launch { prefs.setProgressNotification(p) }
    fun setAppLockEnabled(e: Boolean) = viewModelScope.launch { prefs.setAppLockEnabled(e) }
    fun setIncognitoDownloads(i: Boolean) = viewModelScope.launch { prefs.setIncognitoDownloads(i) }
    fun setSourceYts(e: Boolean) = viewModelScope.launch { prefs.setSourceYts(e) }
    fun setSource1337x(e: Boolean) = viewModelScope.launch { prefs.setSource1337x(e) }
    fun setSourceTpb(e: Boolean) = viewModelScope.launch { prefs.setSourceTpb(e) }
    fun setSourceEztv(e: Boolean) = viewModelScope.launch { prefs.setSourceEztv(e) }
    fun setSourceNyaa(e: Boolean) = viewModelScope.launch { prefs.setSourceNyaa(e) }
    fun setSourceAcademic(e: Boolean) = viewModelScope.launch { prefs.setSourceAcademic(e) }
    fun setSourceTorrentGalaxy(e: Boolean) = viewModelScope.launch { prefs.setSourceTorrentGalaxy(e) }
    fun setSourceLimeTorrents(e: Boolean) = viewModelScope.launch { prefs.setSourceLimeTorrents(e) }
    fun setSourceSolidTorrents(e: Boolean) = viewModelScope.launch { prefs.setSourceSolidTorrents(e) }
    fun setSourceBitsearch(e: Boolean) = viewModelScope.launch { prefs.setSourceBitsearch(e) }
    fun clearSearchHistory() = viewModelScope.launch { prefs.clearSearchHistory() }
}
