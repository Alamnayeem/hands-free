package com.eyecontrol.ai.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "eye_control_settings")

class SettingsRepository(private val context: Context) {

    companion object {
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val CAMERA_SELECTION = stringPreferencesKey("camera_selection")
        val VOICE_LANGUAGE = stringPreferencesKey("voice_language")
        
        val CURSOR_SPEED = floatPreferencesKey("cursor_speed")
        val CURSOR_SMOOTHING = floatPreferencesKey("cursor_smoothing")
        val CURSOR_SIZE = intPreferencesKey("cursor_size")
        val EYE_TRACKING_ACTIVE = booleanPreferencesKey("eye_tracking_active")
        
        val DWELL_TIME = intPreferencesKey("dwell_time")
        val BLINK_SENSITIVITY = floatPreferencesKey("blink_sensitivity")
        val SCROLL_SPEED = floatPreferencesKey("scroll_speed")
        val DRAG_SPEED = floatPreferencesKey("drag_speed")
        val CLICK_DELAY = intPreferencesKey("click_delay")
        val ENABLE_BLINK_CLICK = booleanPreferencesKey("enable_blink_click")
        val ENABLE_DWELL_CLICK = booleanPreferencesKey("enable_dwell_click")

        // New properties
        val CURSOR_COLOR = intPreferencesKey("cursor_color")
        val DEAD_ZONE = intPreferencesKey("dead_zone")
        val PRECISION_MODE = booleanPreferencesKey("precision_mode")
        val TURBO_MODE = booleanPreferencesKey("turbo_mode")
        val SNAP_DISTANCE = intPreferencesKey("snap_distance")
        val BLINK_HOLD_TIME = intPreferencesKey("blink_hold_time")
        val GESTURE_COOLDOWN = intPreferencesKey("gesture_cooldown")
        val AUTO_RECENTER = booleanPreferencesKey("auto_recenter")
        val EDGE_SCROLL = booleanPreferencesKey("edge_scroll")
        val REEL_NAVIGATION = booleanPreferencesKey("reel_navigation")
        val VOICE_COMMANDS = booleanPreferencesKey("voice_commands")
        val PROGRESS_RING = booleanPreferencesKey("progress_ring")
        val VIBRATION = booleanPreferencesKey("vibration")
        val SOUND_FEEDBACK = booleanPreferencesKey("sound_feedback")
        val SPEED_PROFILE = intPreferencesKey("speed_profile")

        val CALIB_CENTER_X = floatPreferencesKey("calib_center_x")
        val CALIB_CENTER_Y = floatPreferencesKey("calib_center_y")
        val CALIB_LEFT_X = floatPreferencesKey("calib_left_x")
        val CALIB_LEFT_Y = floatPreferencesKey("calib_left_y")
        val CALIB_RIGHT_X = floatPreferencesKey("calib_right_x")
        val CALIB_RIGHT_Y = floatPreferencesKey("calib_right_y")
        val CALIB_UP_X = floatPreferencesKey("calib_up_x")
        val CALIB_UP_Y = floatPreferencesKey("calib_up_y")
        val CALIB_DOWN_X = floatPreferencesKey("calib_down_x")
        val CALIB_DOWN_Y = floatPreferencesKey("calib_down_y")
        val IS_CALIBRATED = booleanPreferencesKey("is_calibrated")
    }

    data class CalibrationData(
        val centerX: Float, val centerY: Float,
        val leftX: Float, val leftY: Float,
        val rightX: Float, val rightY: Float,
        val upX: Float, val upY: Float,
        val downX: Float, val downY: Float,
        val isCalibrated: Boolean
    )

    val darkThemeFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[DARK_THEME] ?: false
    }

    val cameraSelectionFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[CAMERA_SELECTION] ?: "Front"
    }

    val voiceLanguageFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[VOICE_LANGUAGE] ?: "en-US"
    }

    val cursorSpeedFlow: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[CURSOR_SPEED] ?: 1.0f
    }

    val cursorSmoothingFlow: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[CURSOR_SMOOTHING] ?: 0.5f
    }

    val cursorSizeFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[CURSOR_SIZE] ?: 40
    }

    val eyeTrackingActiveFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[EYE_TRACKING_ACTIVE] ?: false
    }

    val dwellTimeFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[DWELL_TIME] ?: 1000
    }

    val blinkSensitivityFlow: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[BLINK_SENSITIVITY] ?: 1.0f
    }

    val scrollSpeedFlow: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[SCROLL_SPEED] ?: 5.0f
    }

    val dragSpeedFlow: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[DRAG_SPEED] ?: 5.0f
    }

    val clickDelayFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[CLICK_DELAY] ?: 300
    }

    val enableBlinkClickFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ENABLE_BLINK_CLICK] ?: true
    }

    val enableDwellClickFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ENABLE_DWELL_CLICK] ?: true
    }

    val cursorColorFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[CURSOR_COLOR] ?: 0xFFFF0000.toInt()
    }

    val deadZoneFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[DEAD_ZONE] ?: 10
    }

    val precisionModeFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PRECISION_MODE] ?: true
    }

    val turboModeFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[TURBO_MODE] ?: true
    }

    val snapDistanceFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[SNAP_DISTANCE] ?: 80
    }

    val blinkHoldTimeFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[BLINK_HOLD_TIME] ?: 600
    }

    val gestureCooldownFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[GESTURE_COOLDOWN] ?: 800
    }

    val autoRecenterFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTO_RECENTER] ?: true
    }

    val edgeScrollFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[EDGE_SCROLL] ?: true
    }

    val reelNavigationFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[REEL_NAVIGATION] ?: true
    }

    val voiceCommandsFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[VOICE_COMMANDS] ?: true
    }

    val progressRingFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PROGRESS_RING] ?: true
    }

    val vibrationFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[VIBRATION] ?: true
    }

    val soundFeedbackFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SOUND_FEEDBACK] ?: true
    }

    val speedProfileFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[SPEED_PROFILE] ?: 1
    }

    val isCalibratedFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_CALIBRATED] ?: false
    }

    val calibrationDataFlow: Flow<CalibrationData> = context.dataStore.data.map { preferences ->
        CalibrationData(
            centerX = preferences[CALIB_CENTER_X] ?: 0.5f,
            centerY = preferences[CALIB_CENTER_Y] ?: 0.5f,
            leftX = preferences[CALIB_LEFT_X] ?: 0.45f,
            leftY = preferences[CALIB_LEFT_Y] ?: 0.5f,
            rightX = preferences[CALIB_RIGHT_X] ?: 0.55f,
            rightY = preferences[CALIB_RIGHT_Y] ?: 0.5f,
            upX = preferences[CALIB_UP_X] ?: 0.5f,
            upY = preferences[CALIB_UP_Y] ?: 0.45f,
            downX = preferences[CALIB_DOWN_X] ?: 0.5f,
            downY = preferences[CALIB_DOWN_Y] ?: 0.55f,
            isCalibrated = preferences[IS_CALIBRATED] ?: false
        )
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DARK_THEME] = enabled
        }
    }

    suspend fun setCameraSelection(camera: String) {
        context.dataStore.edit { preferences ->
            preferences[CAMERA_SELECTION] = camera
        }
    }

    suspend fun setVoiceLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[VOICE_LANGUAGE] = language
        }
    }

    suspend fun setCursorSpeed(speed: Float) {
        context.dataStore.edit { preferences ->
            preferences[CURSOR_SPEED] = speed
        }
    }

    suspend fun setCursorSmoothing(smoothing: Float) {
        context.dataStore.edit { preferences ->
            preferences[CURSOR_SMOOTHING] = smoothing
        }
    }

    suspend fun setCursorSize(size: Int) {
        context.dataStore.edit { preferences ->
            preferences[CURSOR_SIZE] = size
        }
    }

    suspend fun setEyeTrackingActive(active: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[EYE_TRACKING_ACTIVE] = active
        }
    }

    suspend fun setDwellTime(time: Int) {
        context.dataStore.edit { preferences ->
            preferences[DWELL_TIME] = time
        }
    }

    suspend fun setBlinkSensitivity(sensitivity: Float) {
        context.dataStore.edit { preferences ->
            preferences[BLINK_SENSITIVITY] = sensitivity
        }
    }

    suspend fun setScrollSpeed(speed: Float) {
        context.dataStore.edit { preferences ->
            preferences[SCROLL_SPEED] = speed
        }
    }

    suspend fun setDragSpeed(speed: Float) {
        context.dataStore.edit { preferences ->
            preferences[DRAG_SPEED] = speed
        }
    }

    suspend fun setClickDelay(delay: Int) {
        context.dataStore.edit { preferences ->
            preferences[CLICK_DELAY] = delay
        }
    }

    suspend fun setEnableBlinkClick(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ENABLE_BLINK_CLICK] = enabled
        }
    }

    suspend fun setEnableDwellClick(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ENABLE_DWELL_CLICK] = enabled
        }
    }

    suspend fun setCursorColor(color: Int) {
        context.dataStore.edit { preferences -> preferences[CURSOR_COLOR] = color }
    }
    suspend fun setDeadZone(zone: Int) {
        context.dataStore.edit { preferences -> preferences[DEAD_ZONE] = zone }
    }
    suspend fun setPrecisionMode(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[PRECISION_MODE] = enabled }
    }
    suspend fun setTurboMode(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[TURBO_MODE] = enabled }
    }
    suspend fun setSnapDistance(distance: Int) {
        context.dataStore.edit { preferences -> preferences[SNAP_DISTANCE] = distance }
    }
    suspend fun setBlinkHoldTime(time: Int) {
        context.dataStore.edit { preferences -> preferences[BLINK_HOLD_TIME] = time }
    }
    suspend fun setGestureCooldown(cooldown: Int) {
        context.dataStore.edit { preferences -> preferences[GESTURE_COOLDOWN] = cooldown }
    }
    suspend fun setAutoRecenter(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[AUTO_RECENTER] = enabled }
    }
    suspend fun setEdgeScroll(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[EDGE_SCROLL] = enabled }
    }
    suspend fun setReelNavigation(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[REEL_NAVIGATION] = enabled }
    }
    suspend fun setVoiceCommands(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[VOICE_COMMANDS] = enabled }
    }
    suspend fun setProgressRing(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[PROGRESS_RING] = enabled }
    }
    suspend fun setVibration(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[VIBRATION] = enabled }
    }
    suspend fun setSoundFeedback(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[SOUND_FEEDBACK] = enabled }
    }
    suspend fun setSpeedProfile(profile: Int) {
        context.dataStore.edit { preferences -> preferences[SPEED_PROFILE] = profile }
    }

    suspend fun saveCalibration(
        centerX: Float, centerY: Float,
        leftX: Float, leftY: Float,
        rightX: Float, rightY: Float,
        upX: Float, upY: Float,
        downX: Float, downY: Float
    ) {
        context.dataStore.edit { preferences ->
            preferences[CALIB_CENTER_X] = centerX
            preferences[CALIB_CENTER_Y] = centerY
            preferences[CALIB_LEFT_X] = leftX
            preferences[CALIB_LEFT_Y] = leftY
            preferences[CALIB_RIGHT_X] = rightX
            preferences[CALIB_RIGHT_Y] = rightY
            preferences[CALIB_UP_X] = upX
            preferences[CALIB_UP_Y] = upY
            preferences[CALIB_DOWN_X] = downX
            preferences[CALIB_DOWN_Y] = downY
            preferences[IS_CALIBRATED] = true
        }
    }

    suspend fun clearCalibration() {
        context.dataStore.edit { preferences ->
            preferences[IS_CALIBRATED] = false
            preferences.remove(CALIB_CENTER_X)
            preferences.remove(CALIB_CENTER_Y)
            preferences.remove(CALIB_LEFT_X)
            preferences.remove(CALIB_LEFT_Y)
            preferences.remove(CALIB_RIGHT_X)
            preferences.remove(CALIB_RIGHT_Y)
            preferences.remove(CALIB_UP_X)
            preferences.remove(CALIB_UP_Y)
            preferences.remove(CALIB_DOWN_X)
            preferences.remove(CALIB_DOWN_Y)
        }
    }

    suspend fun resetSettings() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
