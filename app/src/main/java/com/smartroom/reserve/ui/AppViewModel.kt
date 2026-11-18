package com.smartroom.reserve.ui

import android.app.Application
import android.content.Context
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartroom.reserve.data.AdminStats
import com.smartroom.reserve.data.Booking
import com.smartroom.reserve.data.DbHelper
import com.smartroom.reserve.data.Repository
import com.smartroom.reserve.data.Room
import com.smartroom.reserve.data.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.smartroom.reserve.ai.AiAnswer
import com.smartroom.reserve.ai.AiAssistant
import com.smartroom.reserve.ai.AiException
import com.smartroom.reserve.ai.AiRequest
import com.smartroom.reserve.ai.GeminiClient
import com.smartroom.reserve.util.BookingRules
import com.smartroom.reserve.util.TimeUtils
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * MVVM: the screens observe these StateFlows and call these functions; all data access
 * goes through [Repository].
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository(DbHelper(app))
    private val prefs = app.getSharedPreferences("session", Context.MODE_PRIVATE)

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    private val _rooms = MutableStateFlow<List<Room>>(emptyList())
    val rooms: StateFlow<List<Room>> = _rooms.asStateFlow()

    private val _busyRooms = MutableStateFlow<Set<Long>>(emptySet())
    val busyRooms: StateFlow<Set<Long>> = _busyRooms.asStateFlow()

    private val _myBookings = MutableStateFlow<List<Booking>>(emptyList())
    val myBookings: StateFlow<List<Booking>> = _myBookings.asStateFlow()

    private val _allBookings = MutableStateFlow<List<Booking>>(emptyList())
    val allBookings: StateFlow<List<Booking>> = _allBookings.asStateFlow()

    private val _stats = MutableStateFlow<AdminStats?>(null)
    val stats: StateFlow<AdminStats?> = _stats.asStateFlow()

    /** One-off messages shown in a snackbar. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            val id = prefs.getLong(KEY_USER, -1L)
            if (id > 0) _user.value = repo.getUser(id)
            reload()
            _ready.value = true
        }
    }

    private fun now() = System.currentTimeMillis()

    fun refresh() {
        viewModelScope.launch { reload() }
    }

    private suspend fun reload() {
        val now = now()
        _rooms.value = repo.getRooms()
        _busyRooms.value = repo.busyRoomIds(now)
        val u = _user.value
        if (u != null) {
            _myBookings.value = repo.userBookings(u.id)
            if (u.isAdmin) {
                _allBookings.value = repo.allBookings()
                _stats.value = repo.adminStats(now)
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    // ---------- auth ----------

    fun login(email: String, password: String, onError: (String) -> Unit) {
        if (email.isBlank() || password.isBlank()) {
            onError("Enter your email and password"); return
        }
        viewModelScope.launch {
            val u = repo.login(email, password)
            if (u == null) onError("Incorrect email or password") else signIn(u)
        }
    }

    fun register(name: String, email: String, password: String, confirm: String, onError: (String) -> Unit) {
        val error = when {
            name.isBlank() -> "Enter your name"
            !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Enter a valid email address"
            password.length < 6 -> "Password must be at least 6 characters"
            password != confirm -> "Passwords do not match"
            else -> null
        }
        if (error != null) {
            onError(error); return
        }
        viewModelScope.launch {
            val result = repo.register(name, email, password)
            val u = result.getOrNull()
            if (u == null) onError(result.exceptionOrNull()?.message ?: "Registration failed")
            else {
                signIn(u)
                _message.value = "Welcome, ${u.name}!"
            }
        }
    }

    private suspend fun signIn(u: User) {
        prefs.edit().putLong(KEY_USER, u.id).apply()
        _user.value = u
        reload()
    }

    fun logout() {
        prefs.edit().remove(KEY_USER).apply()
        _user.value = null
        _myBookings.value = emptyList()
        _allBookings.value = emptyList()
        _stats.value = null
        _insights.value = null
        clearChat()
    }

    // ---------- rooms & schedule ----------

    suspend fun getRoom(id: Long): Room? = repo.getRoom(id)

    suspend fun schedule(roomId: Long, date: LocalDate): List<Booking> = repo.roomSchedule(roomId, date)

    suspend fun isRoomFree(roomId: Long, start: Long, end: Long): Boolean = repo.isRoomFree(roomId, start, end)

    suspend fun findRooms(start: Long, end: Long, attendees: Int, amenities: Set<String>): List<Room> =
        repo.findAvailableRooms(start, end, attendees, amenities)

    // ---------- bookings ----------

    fun book(
        roomId: Long, start: Long, end: Long, purpose: String, attendees: Int,
        onResult: (String?) -> Unit
    ) {
        val u = _user.value ?: return onResult("Please log in again")
        viewModelScope.launch {
            val err = repo.createBooking(u.id, roomId, start, end, purpose, attendees, now())
            if (err == null) {
                reload()
                _message.value = "Room booked successfully!"
            }
            onResult(err)
        }
    }

    fun cancel(booking: Booking) = runAction("Booking cancelled") { repo.cancelBooking(booking.id) }

    // ---------- admin ----------

    fun addRoom(
        name: String, building: String, floor: Int, capacity: Int,
        amenities: List<String>, description: String
    ) = runAction("Room added") { repo.addRoom(name, building, floor, capacity, amenities, description) }

    fun deleteRoom(room: Room) = runAction("${room.name} removed") { repo.deleteRoom(room.id, now()) }

    private fun runAction(success: String, action: suspend () -> String?) {
        viewModelScope.launch {
            val err = action()
            _message.value = err ?: success
            reload()
        }
    }


    // ================= AI (Google Gemini) =================

    /** One bubble in the AI chat. Bot answers can carry suggested rooms. */
    data class ChatMessage(
        val fromUser: Boolean,
        val text: String,
        val rooms: List<Room> = emptyList(),
        val request: AiRequest? = null,
        val isError: Boolean = false
    )

    private val aiPrefs = app.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)

    private val _aiKey = MutableStateFlow(aiPrefs.getString(KEY_AI_KEY, "") ?: "")
    val aiKey: StateFlow<String> = _aiKey.asStateFlow()

    private val _aiModel = MutableStateFlow(aiPrefs.getString(KEY_AI_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL)
    val aiModel: StateFlow<String> = _aiModel.asStateFlow()

    private val _chat = MutableStateFlow(
        listOf(
            ChatMessage(
                false,
                "Hi! I'm your Room Assistant. Tell me what you need, for example:\n" +
                    "\"Book a room for 6 people tomorrow at 3 pm with a projector\""
            )
        )
    )
    val chat: StateFlow<List<ChatMessage>> = _chat.asStateFlow()

    private val _aiBusy = MutableStateFlow(false)
    val aiBusy: StateFlow<Boolean> = _aiBusy.asStateFlow()

    private val _insights = MutableStateFlow<String?>(null)
    val insights: StateFlow<String?> = _insights.asStateFlow()

    fun saveAiSettings(key: String, model: String) {
        val m = model.trim().ifBlank { DEFAULT_MODEL }
        aiPrefs.edit().putString(KEY_AI_KEY, key.trim()).putString(KEY_AI_MODEL, m).apply()
        _aiKey.value = key.trim()
        _aiModel.value = m
        _message.value = "AI settings saved"
    }

    private fun client(): GeminiClient? =
        if (_aiKey.value.isBlank()) null else GeminiClient(_aiKey.value, _aiModel.value)

    /** User sends a sentence to the AI assistant. */
    fun askAssistant(text: String) {
        val question = text.trim()
        if (question.isEmpty() || _aiBusy.value) return
        val u = _user.value ?: return
        _chat.value = _chat.value + ChatMessage(true, question)

        val gemini = client()
        if (gemini == null) {
            _chat.value = _chat.value + ChatMessage(
                false, "AI is not set up yet. Add your free Gemini API key in Profile → AI settings.", isError = true
            )
            return
        }
        _aiBusy.value = true
        viewModelScope.launch {
            val reply = try {
                // last few messages give the AI context ("make it 5 pm instead")
                val history = _chat.value.filter { !it.isError }.takeLast(8)
                    .map { GeminiClient.Turn(if (it.fromUser) "user" else "model", it.text) }
                val system = AiAssistant.systemPrompt(LocalDateTime.now(), _rooms.value, _myBookings.value, u.name)
                val raw = gemini.generate(system, history, jsonOutput = true)
                val answer: AiAnswer = AiAssistant.parse(raw)
                if (answer.request == null) ChatMessage(false, answer.reply)
                else suggestRooms(answer.reply, answer.request)
            } catch (e: AiException) {
                ChatMessage(false, e.message ?: "AI error", isError = true)
            } catch (e: Exception) {
                ChatMessage(false, "Sorry, I could not understand the AI's answer. Please try again.", isError = true)
            }
            _chat.value = _chat.value + reply
            _aiBusy.value = false
        }
    }

    /** The AI understood the request; now OUR code checks the rules and finds free rooms. */
    private suspend fun suggestRooms(reply: String, r: AiRequest): ChatMessage {
        val start = TimeUtils.toMillis(r.date, r.start)
        val end = TimeUtils.toMillis(r.date, r.end)
        val when_ = "${TimeUtils.dateLabel(r.date)}, ${TimeUtils.rangeLabel(start, end)}"
        BookingRules.validate(start, end, r.attendees, Int.MAX_VALUE, now())?.let { rule ->
            return ChatMessage(false, "$reply\n\nI can't book $when_: $rule.", isError = true)
        }
        val rooms = repo.findAvailableRooms(start, end, r.attendees, r.amenities)
        if (rooms.isEmpty()) {
            return ChatMessage(false, "$reply\n\nSorry, no room is free for ${r.attendees} people on $when_. Try another time.")
        }
        val needs = if (r.amenities.isEmpty()) "" else " with ${r.amenities.joinToString(", ")}"
        return ChatMessage(
            false,
            "$reply\n\nFree rooms for ${r.attendees} people$needs on $when_ (best fit first):",
            rooms = rooms.take(3),
            request = r
        )
    }

    fun clearChat() {
        _chat.value = _chat.value.take(1)
    }

    /** Admin: ask the AI to read the booking data and give insights. */
    fun generateInsights() {
        val gemini = client()
        if (gemini == null) {
            _insights.value = "Add your Gemini API key in Profile → AI settings first."
            return
        }
        _aiBusy.value = true
        _insights.value = null
        viewModelScope.launch {
            _insights.value = try {
                val data = repo.usageReport(now())
                gemini.generate(AiAssistant.INSIGHTS_PROMPT, listOf(GeminiClient.Turn("user", data)), jsonOutput = false)
            } catch (e: AiException) {
                e.message
            }
            _aiBusy.value = false
        }
    }

    companion object {
        private const val KEY_USER = "user_id"
        private const val KEY_AI_KEY = "gemini_key"
        private const val KEY_AI_MODEL = "gemini_model"
        const val DEFAULT_MODEL = "gemini-3.8-flash"
    }
}
