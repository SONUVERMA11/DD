package com.sonu.dd.core.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sonu.dd.core.ui.theme.DDTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "dd_settings")

@Singleton
class DDPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Appearance
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private val THEME_KEY = intPreferencesKey("theme")
    private val FOLLOW_SYSTEM_THEME = booleanPreferencesKey("follow_system_theme")
    private val ANIMATION_LEVEL = intPreferencesKey("animation_level") // 0=Full, 1=Reduced, 2=Off
    private val CORNER_RADIUS = intPreferencesKey("corner_radius") // 0-30
    private val NAV_STYLE = intPreferencesKey("nav_style") // 0=Floating pill, 1=Standard
    private val FONT_SIZE = intPreferencesKey("font_size") // 0=Small, 1=Default, 2=Large
    private val LIBRARY_VIEW = intPreferencesKey("library_view") // 0=Grid, 1=List

    val themeFlow: Flow<DDTheme> = dataStore.data.map { prefs ->
        DDTheme.fromOrdinal(prefs[THEME_KEY] ?: DDTheme.MIDNIGHT_DARK.ordinal)
    }

    val followSystemThemeFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[FOLLOW_SYSTEM_THEME] ?: false
    }

    val animationLevelFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[ANIMATION_LEVEL] ?: 0
    }

    val cornerRadiusFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[CORNER_RADIUS] ?: 16
    }

    val navStyleFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[NAV_STYLE] ?: 0
    }

    val fontSizeFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[FONT_SIZE] ?: 1
    }

    val libraryViewFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[LIBRARY_VIEW] ?: 0
    }

    suspend fun setTheme(theme: DDTheme) {
        dataStore.edit { it[THEME_KEY] = theme.ordinal }
    }

    suspend fun setFollowSystemTheme(follow: Boolean) {
        dataStore.edit { it[FOLLOW_SYSTEM_THEME] = follow }
    }

    suspend fun setAnimationLevel(level: Int) {
        dataStore.edit { it[ANIMATION_LEVEL] = level.coerceIn(0, 2) }
    }

    suspend fun setCornerRadius(radius: Int) {
        dataStore.edit { it[CORNER_RADIUS] = radius.coerceIn(0, 30) }
    }

    suspend fun setNavStyle(style: Int) {
        dataStore.edit { it[NAV_STYLE] = style.coerceIn(0, 1) }
    }

    suspend fun setFontSize(size: Int) {
        dataStore.edit { it[FONT_SIZE] = size.coerceIn(0, 2) }
    }

    suspend fun setLibraryView(view: Int) {
        dataStore.edit { it[LIBRARY_VIEW] = view.coerceIn(0, 1) }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Downloads
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private val DOWNLOAD_PATH = stringPreferencesKey("download_path")
    private val AUTO_SAVE_GALLERY = booleanPreferencesKey("auto_save_gallery")
    private val SAVE_VIDEOS_GALLERY = booleanPreferencesKey("save_videos_gallery")
    private val SAVE_IMAGES_GALLERY = booleanPreferencesKey("save_images_gallery")
    private val SAVE_MUSIC_LIBRARY = booleanPreferencesKey("save_music_library")
    private val SIMULTANEOUS_DOWNLOADS = intPreferencesKey("simultaneous_downloads")
    private val SPEED_LIMIT = longPreferencesKey("speed_limit") // 0 = unlimited
    private val AUTO_RESUME = booleanPreferencesKey("auto_resume")
    private val AUTO_DELETE_SEED = booleanPreferencesKey("auto_delete_seed")

    val downloadPathFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[DOWNLOAD_PATH] ?: ""
    }

    val autoSaveGalleryFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[AUTO_SAVE_GALLERY] ?: true
    }

    val saveVideosGalleryFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[SAVE_VIDEOS_GALLERY] ?: true
    }

    val saveImagesGalleryFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[SAVE_IMAGES_GALLERY] ?: true
    }

    val saveMusicLibraryFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[SAVE_MUSIC_LIBRARY] ?: true
    }

    val simultaneousDownloadsFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[SIMULTANEOUS_DOWNLOADS] ?: 2
    }

    val speedLimitFlow: Flow<Long> = dataStore.data.map { prefs ->
        prefs[SPEED_LIMIT] ?: 0L
    }

    val autoResumeFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[AUTO_RESUME] ?: true
    }

    val autoDeleteSeedFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[AUTO_DELETE_SEED] ?: true
    }

    suspend fun setDownloadPath(path: String) {
        dataStore.edit { it[DOWNLOAD_PATH] = path }
    }

    suspend fun setAutoSaveGallery(save: Boolean) {
        dataStore.edit { it[AUTO_SAVE_GALLERY] = save }
    }

    suspend fun setSaveVideosGallery(save: Boolean) {
        dataStore.edit { it[SAVE_VIDEOS_GALLERY] = save }
    }

    suspend fun setSaveImagesGallery(save: Boolean) {
        dataStore.edit { it[SAVE_IMAGES_GALLERY] = save }
    }

    suspend fun setSaveMusicLibrary(save: Boolean) {
        dataStore.edit { it[SAVE_MUSIC_LIBRARY] = save }
    }

    suspend fun setSimultaneousDownloads(count: Int) {
        dataStore.edit { it[SIMULTANEOUS_DOWNLOADS] = count.coerceIn(1, 4) }
    }

    suspend fun setSpeedLimit(limit: Long) {
        dataStore.edit { it[SPEED_LIMIT] = limit.coerceAtLeast(0) }
    }

    suspend fun setAutoResume(resume: Boolean) {
        dataStore.edit { it[AUTO_RESUME] = resume }
    }

    suspend fun setAutoDeleteSeed(delete: Boolean) {
        dataStore.edit { it[AUTO_DELETE_SEED] = delete }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Network & Privacy
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private val VPN_REMINDER = booleanPreferencesKey("vpn_reminder")
    private val PROXY_ENABLED = booleanPreferencesKey("proxy_enabled")
    private val PROXY_HOST = stringPreferencesKey("proxy_host")
    private val PROXY_PORT = intPreferencesKey("proxy_port")
    private val PROXY_TYPE = intPreferencesKey("proxy_type") // 0=HTTP, 1=SOCKS5
    private val DHT_ENABLED = booleanPreferencesKey("dht_enabled")
    private val PEX_ENABLED = booleanPreferencesKey("pex_enabled")
    private val ANONYMOUS_MODE = booleanPreferencesKey("anonymous_mode")

    // Source toggles
    private val SOURCE_YTS = booleanPreferencesKey("source_yts")
    private val SOURCE_1337X = booleanPreferencesKey("source_1337x")
    private val SOURCE_TPB = booleanPreferencesKey("source_tpb")
    private val SOURCE_EZTV = booleanPreferencesKey("source_eztv")
    private val SOURCE_NYAA = booleanPreferencesKey("source_nyaa")
    private val SOURCE_ACADEMIC = booleanPreferencesKey("source_academic")
    private val SOURCE_TORRENT_GALAXY = booleanPreferencesKey("source_torrent_galaxy")
    private val SOURCE_LIME_TORRENTS = booleanPreferencesKey("source_lime_torrents")
    private val SOURCE_SOLID_TORRENTS = booleanPreferencesKey("source_solid_torrents")
    private val SOURCE_BITSEARCH = booleanPreferencesKey("source_bitsearch")

    val vpnReminderFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[VPN_REMINDER] ?: true
    }

    val proxyEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PROXY_ENABLED] ?: false
    }

    val proxyHostFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[PROXY_HOST] ?: ""
    }

    val proxyPortFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[PROXY_PORT] ?: 1080
    }

    val proxyTypeFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[PROXY_TYPE] ?: 0
    }

    val dhtEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[DHT_ENABLED] ?: true
    }

    val pexEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PEX_ENABLED] ?: true
    }

    val anonymousModeFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[ANONYMOUS_MODE] ?: false
    }

    val sourceYtsFlow: Flow<Boolean> = dataStore.data.map { prefs -> prefs[SOURCE_YTS] ?: true }
    val source1337xFlow: Flow<Boolean> = dataStore.data.map { prefs -> prefs[SOURCE_1337X] ?: true }
    val sourceTpbFlow: Flow<Boolean> = dataStore.data.map { prefs -> prefs[SOURCE_TPB] ?: true }
    val sourceEztvFlow: Flow<Boolean> = dataStore.data.map { prefs -> prefs[SOURCE_EZTV] ?: true }
    val sourceNyaaFlow: Flow<Boolean> = dataStore.data.map { prefs -> prefs[SOURCE_NYAA] ?: true }
    val sourceAcademicFlow: Flow<Boolean> = dataStore.data.map { prefs -> prefs[SOURCE_ACADEMIC] ?: true }
    val sourceTorrentGalaxyFlow: Flow<Boolean> = dataStore.data.map { prefs -> prefs[SOURCE_TORRENT_GALAXY] ?: true }
    val sourceLimeTorrentsFlow: Flow<Boolean> = dataStore.data.map { prefs -> prefs[SOURCE_LIME_TORRENTS] ?: true }
    val sourceSolidTorrentsFlow: Flow<Boolean> = dataStore.data.map { prefs -> prefs[SOURCE_SOLID_TORRENTS] ?: true }
    val sourceBitsearchFlow: Flow<Boolean> = dataStore.data.map { prefs -> prefs[SOURCE_BITSEARCH] ?: true }

    suspend fun setVpnReminder(enabled: Boolean) { dataStore.edit { it[VPN_REMINDER] = enabled } }
    suspend fun setProxyEnabled(enabled: Boolean) { dataStore.edit { it[PROXY_ENABLED] = enabled } }
    suspend fun setProxyHost(host: String) { dataStore.edit { it[PROXY_HOST] = host } }
    suspend fun setProxyPort(port: Int) { dataStore.edit { it[PROXY_PORT] = port } }
    suspend fun setProxyType(type: Int) { dataStore.edit { it[PROXY_TYPE] = type } }
    suspend fun setDhtEnabled(enabled: Boolean) { dataStore.edit { it[DHT_ENABLED] = enabled } }
    suspend fun setPexEnabled(enabled: Boolean) { dataStore.edit { it[PEX_ENABLED] = enabled } }
    suspend fun setAnonymousMode(enabled: Boolean) { dataStore.edit { it[ANONYMOUS_MODE] = enabled } }

    suspend fun setSourceYts(enabled: Boolean) { dataStore.edit { it[SOURCE_YTS] = enabled } }
    suspend fun setSource1337x(enabled: Boolean) { dataStore.edit { it[SOURCE_1337X] = enabled } }
    suspend fun setSourceTpb(enabled: Boolean) { dataStore.edit { it[SOURCE_TPB] = enabled } }
    suspend fun setSourceEztv(enabled: Boolean) { dataStore.edit { it[SOURCE_EZTV] = enabled } }
    suspend fun setSourceNyaa(enabled: Boolean) { dataStore.edit { it[SOURCE_NYAA] = enabled } }
    suspend fun setSourceAcademic(enabled: Boolean) { dataStore.edit { it[SOURCE_ACADEMIC] = enabled } }
    suspend fun setSourceTorrentGalaxy(enabled: Boolean) { dataStore.edit { it[SOURCE_TORRENT_GALAXY] = enabled } }
    suspend fun setSourceLimeTorrents(enabled: Boolean) { dataStore.edit { it[SOURCE_LIME_TORRENTS] = enabled } }
    suspend fun setSourceSolidTorrents(enabled: Boolean) { dataStore.edit { it[SOURCE_SOLID_TORRENTS] = enabled } }
    suspend fun setSourceBitsearch(enabled: Boolean) { dataStore.edit { it[SOURCE_BITSEARCH] = enabled } }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Notifications
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private val PROGRESS_NOTIFICATION = booleanPreferencesKey("progress_notification")
    private val SPEED_IN_NOTIFICATION = booleanPreferencesKey("speed_in_notification")
    private val COMPLETION_ALERT = intPreferencesKey("completion_alert") // 0=Sound, 1=Vibrate, 2=Silent
    private val FAILED_ALERT = booleanPreferencesKey("failed_alert")
    private val NOTIFICATION_STYLE = intPreferencesKey("notification_style") // 0=Minimal, 1=Detailed

    val progressNotificationFlow: Flow<Boolean> = dataStore.data.map { prefs -> prefs[PROGRESS_NOTIFICATION] ?: true }
    val speedInNotificationFlow: Flow<Boolean> = dataStore.data.map { prefs -> prefs[SPEED_IN_NOTIFICATION] ?: true }
    val completionAlertFlow: Flow<Int> = dataStore.data.map { prefs -> prefs[COMPLETION_ALERT] ?: 0 }
    val failedAlertFlow: Flow<Boolean> = dataStore.data.map { prefs -> prefs[FAILED_ALERT] ?: true }
    val notificationStyleFlow: Flow<Int> = dataStore.data.map { prefs -> prefs[NOTIFICATION_STYLE] ?: 1 }

    suspend fun setProgressNotification(enabled: Boolean) { dataStore.edit { it[PROGRESS_NOTIFICATION] = enabled } }
    suspend fun setSpeedInNotification(enabled: Boolean) { dataStore.edit { it[SPEED_IN_NOTIFICATION] = enabled } }
    suspend fun setCompletionAlert(type: Int) { dataStore.edit { it[COMPLETION_ALERT] = type } }
    suspend fun setFailedAlert(enabled: Boolean) { dataStore.edit { it[FAILED_ALERT] = enabled } }
    suspend fun setNotificationStyle(style: Int) { dataStore.edit { it[NOTIFICATION_STYLE] = style } }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Security
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
    private val APP_LOCK_TYPE = intPreferencesKey("app_lock_type") // 0=PIN, 1=Fingerprint, 2=Face
    private val APP_LOCK_PIN = stringPreferencesKey("app_lock_pin")
    private val INCOGNITO_DOWNLOADS = booleanPreferencesKey("incognito_downloads")

    val appLockEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs -> prefs[APP_LOCK_ENABLED] ?: false }
    val appLockTypeFlow: Flow<Int> = dataStore.data.map { prefs -> prefs[APP_LOCK_TYPE] ?: 1 }
    val incognitoDownloadsFlow: Flow<Boolean> = dataStore.data.map { prefs -> prefs[INCOGNITO_DOWNLOADS] ?: false }

    suspend fun setAppLockEnabled(enabled: Boolean) { dataStore.edit { it[APP_LOCK_ENABLED] = enabled } }
    suspend fun setAppLockType(type: Int) { dataStore.edit { it[APP_LOCK_TYPE] = type } }
    suspend fun setAppLockPin(pin: String) { dataStore.edit { it[APP_LOCK_PIN] = pin } }
    suspend fun setIncognitoDownloads(enabled: Boolean) { dataStore.edit { it[INCOGNITO_DOWNLOADS] = enabled } }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Storage
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private val AUTO_DELETE_DAYS = intPreferencesKey("auto_delete_days") // 0=Never, 7, 14, 30
    private val DUPLICATE_DETECTION = booleanPreferencesKey("duplicate_detection")

    val autoDeleteDaysFlow: Flow<Int> = dataStore.data.map { prefs -> prefs[AUTO_DELETE_DAYS] ?: 0 }
    val duplicateDetectionFlow: Flow<Boolean> = dataStore.data.map { prefs -> prefs[DUPLICATE_DETECTION] ?: true }

    suspend fun setAutoDeleteDays(days: Int) { dataStore.edit { it[AUTO_DELETE_DAYS] = days } }
    suspend fun setDuplicateDetection(enabled: Boolean) { dataStore.edit { it[DUPLICATE_DETECTION] = enabled } }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // File Formats
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private val DEFAULT_VIDEO_FORMAT = stringPreferencesKey("default_video_format")
    private val DEFAULT_AUDIO_FORMAT = stringPreferencesKey("default_audio_format")
    private val DEFAULT_BOOK_FORMAT = stringPreferencesKey("default_book_format")
    private val SMART_CONVERT = booleanPreferencesKey("smart_convert")
    private val KEEP_ORIGINAL = booleanPreferencesKey("keep_original")

    val defaultVideoFormatFlow: Flow<String> = dataStore.data.map { prefs -> prefs[DEFAULT_VIDEO_FORMAT] ?: "MP4" }
    val defaultAudioFormatFlow: Flow<String> = dataStore.data.map { prefs -> prefs[DEFAULT_AUDIO_FORMAT] ?: "MP3" }
    val defaultBookFormatFlow: Flow<String> = dataStore.data.map { prefs -> prefs[DEFAULT_BOOK_FORMAT] ?: "EPUB" }
    val smartConvertFlow: Flow<Boolean> = dataStore.data.map { prefs -> prefs[SMART_CONVERT] ?: true }
    val keepOriginalFlow: Flow<Boolean> = dataStore.data.map { prefs -> prefs[KEEP_ORIGINAL] ?: false }

    suspend fun setDefaultVideoFormat(format: String) { dataStore.edit { it[DEFAULT_VIDEO_FORMAT] = format } }
    suspend fun setDefaultAudioFormat(format: String) { dataStore.edit { it[DEFAULT_AUDIO_FORMAT] = format } }
    suspend fun setDefaultBookFormat(format: String) { dataStore.edit { it[DEFAULT_BOOK_FORMAT] = format } }
    suspend fun setSmartConvert(enabled: Boolean) { dataStore.edit { it[SMART_CONVERT] = enabled } }
    suspend fun setKeepOriginal(keep: Boolean) { dataStore.edit { it[KEEP_ORIGINAL] = keep } }

    /**
     * Clear all search history preferences.
     */
    suspend fun clearSearchHistory() {
        dataStore.edit { prefs ->
            prefs.asMap().keys
                .filter { it.name.startsWith("search_history") }
                .forEach { prefs.remove(it) }
        }
    }
}
