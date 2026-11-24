package dev.junjo.anchor.trip

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import dev.junjo.anchor.QuickAddActivity
import dev.junjo.anchor.R
import dev.junjo.anchor.data.TodoStore
import dev.junjo.anchor.notify.Notifications
import dev.junjo.anchor.overlay.FloatingGoals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Active In&Out trip state, recent trip chips, and cue delivery (sound following ringer + short
 * buzz that still fires on silent).
 */
object Trips {
    private const val PREFS_STATE = "trip"
    private const val PREFS_RECENT = "trip_recent"

    private const val KEY_STATUS = "status"
    private const val KEY_NAME = "name"
    private const val KEY_MINUTES = "minutes"
    private const val KEY_STARTED = "started"
    private const val KEY_END = "end"
    private const val KEY_GOS = "gos"
    private const val KEY_HALFWAY = "halfway"

    private const val KEY_RECENT = "recent"
    private const val KEY_LAST_MINUTES = "last_minutes"
    private const val KEY_SEEN_TIP = "seen_tip"

    private lateinit var appContext: Context
    private lateinit var statePrefs: SharedPreferences
    private lateinit var recentPrefs: SharedPreferences

    private val _state = MutableStateFlow(TripState())
    val state: StateFlow<TripState> = _state.asStateFlow()

    private val _recent = MutableStateFlow<List<RecentTrip>>(emptyList())
    val recent: StateFlow<List<RecentTrip>> = _recent.asStateFlow()

    /** Incremented when the trip pill should flash three times (halfway, and after "another go"). */
    private val _flashSerial = MutableStateFlow(0)
    val flashSerial: StateFlow<Int> = _flashSerial.asStateFlow()

    val isActive: Boolean get() = _state.value.isActive

    fun init(context: Context) {
        if (::statePrefs.isInitialized) return
        appContext = context.applicationContext
        statePrefs = appContext.getSharedPreferences(PREFS_STATE, Context.MODE_PRIVATE)
        recentPrefs = appContext.getSharedPreferences(PREFS_RECENT, Context.MODE_PRIVATE)
        _state.value = loadState()
        _recent.value = TripPolicy.recentFromJson(recentPrefs.getString(KEY_RECENT, null))
    }

    fun lastMinutes(): Int = recentPrefs.getInt(KEY_LAST_MINUTES, TripPolicy.DEFAULT_MINUTES)
        .takeIf { it in TripPolicy.PRESET_MINUTES } ?: TripPolicy.DEFAULT_MINUTES

    fun seenTip(): Boolean = recentPrefs.getBoolean(KEY_SEEN_TIP, false)

    fun start(name: String, minutes: Int): Boolean {
        val clean = name.trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) return false
        val mins = minutes.coerceAtLeast(1)
        val now = System.currentTimeMillis()
        clearAlert()
        val updatedRecent = TripPolicy.recordRecent(_recent.value, clean, mins)
        _recent.value = updatedRecent
        recentPrefs.edit {
            putString(KEY_RECENT, TripPolicy.recentToJson(updatedRecent))
            putInt(KEY_LAST_MINUTES, mins)
            putBoolean(KEY_SEEN_TIP, true)
        }
        setState(
            TripState(
                name = clean,
                minutes = mins,
                startedAt = now,
                endAt = now + mins * TripPolicy.MINUTE,
                gos = 1,
                halfwayDone = false,
                status = TripStatus.RUNNING,
            ),
        )
        FloatingGoals.sync(appContext)
        return true
    }

    fun markHalfway() {
        val s = _state.value
        if (s.status != TripStatus.RUNNING || s.halfwayDone) return
        setState(s.copy(halfwayDone = true))
        _flashSerial.update { it + 1 }
        postSoundCue(Notifications.CH_TRIP_HALFWAY, "Halfway", s.name)
        buzzHalfway()
    }

    fun reachStop(playCue: Boolean) {
        val s = _state.value
        if (s.status != TripStatus.RUNNING) return
        setState(s.copy(status = TripStatus.STOP))
        if (playCue) {
            postSoundCue(Notifications.CH_TRIP_STOP, "Time's up", s.name)
            buzzStop()
        }
    }

    /** Ends the trip and clears its alert; the caller handles going home when appropriate. */
    fun done() {
        clearAlert()
        setState(TripState())
        FloatingGoals.sync(appContext)
    }

    /** Same as [done], for cancelling from the pill menu without leaving the current app. */
    fun cancel() = done()

    /** Starts the same trip again with the same length, and flashes the pill with the name. */
    fun again() {
        val s = _state.value
        if (!s.isActive) return
        clearAlert()
        setState(TripPolicy.again(s, System.currentTimeMillis()))
        _flashSerial.update { it + 1 }
    }

    /** Ends the trip and adds its name to your to-do list. */
    fun later() {
        val name = _state.value.name
        if (name.isNotBlank()) TodoStore.add(name)
        done()
    }

    /** Ends the trip and opens Focus's sheet prefilled with the trip's name. */
    fun toFocus() {
        val name = _state.value.name.takeIf { it.isNotBlank() }
        done()
        QuickAddActivity.open(appContext, QuickAddActivity.Kind.FOCUS, text = name)
    }

    /** If a running trip passed its end while the screen was off or after a restart, show STOP. */
    fun catchUp() {
        val before = _state.value
        val after = TripPolicy.afterRestart(before, System.currentTimeMillis())
        if (after != before) {
            clearAlert()
            setState(after)
        }
    }

    private fun postSoundCue(channel: String, title: String, text: String) {
        // Cancel first so a fresh post always plays the channel's sound, then auto-clear from shade.
        Notifications.cancel(appContext, Notifications.ID_TRIP_ALERT)
        val n = NotificationCompat.Builder(appContext, channel)
            .setSmallIcon(R.drawable.ic_trip)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setTimeoutAfter(4_000)
            .build()
        Notifications.post(appContext, Notifications.ID_TRIP_ALERT, n)
    }

    private fun clearAlert() {
        Notifications.cancel(appContext, Notifications.ID_TRIP_ALERT)
    }

    /** One short buzz at halfway, firing even when the phone is on silent. */
    private fun buzzHalfway() = buzz(VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE))

    /** Double buzz at time's up, firing even when the phone is on silent. */
    private fun buzzStop() = buzz(VibrationEffect.createWaveform(longArrayOf(0, 180, 110, 220), -1))

    private fun buzz(effect: VibrationEffect) {
        runCatching {
            val vibrator = appContext.getSystemService(VibratorManager::class.java).defaultVibrator
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= 33) {
                val attrs = VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_ALARM)
                    .build()
                vibrator.vibrate(effect, attrs)
            } else {
                @Suppress("DEPRECATION")
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, attrs)
            }
        }
    }

    private fun setState(s: TripState) {
        _state.value = s
        statePrefs.edit {
            putString(KEY_STATUS, s.status.name)
            putString(KEY_NAME, s.name)
            putInt(KEY_MINUTES, s.minutes)
            putLong(KEY_STARTED, s.startedAt)
            putLong(KEY_END, s.endAt)
            putInt(KEY_GOS, s.gos)
            putBoolean(KEY_HALFWAY, s.halfwayDone)
        }
    }

    private fun loadState() = TripState(
        name = statePrefs.getString(KEY_NAME, "") ?: "",
        minutes = statePrefs.getInt(KEY_MINUTES, TripPolicy.DEFAULT_MINUTES),
        startedAt = statePrefs.getLong(KEY_STARTED, 0),
        endAt = statePrefs.getLong(KEY_END, 0),
        gos = statePrefs.getInt(KEY_GOS, 1).coerceAtLeast(1),
        halfwayDone = statePrefs.getBoolean(KEY_HALFWAY, false),
        status = runCatching {
            TripStatus.valueOf(statePrefs.getString(KEY_STATUS, null) ?: "IDLE")
        }.getOrDefault(TripStatus.IDLE),
    )
}
