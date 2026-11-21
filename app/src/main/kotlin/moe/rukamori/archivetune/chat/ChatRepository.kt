package moe.rukamori.archivetune.chat

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

internal data class ChatUser(val id: String, val username: String, val displayName: String)
internal data class ChatMember(val username: String, val displayName: String)
internal data class ChatConversation(
    val id: String,
    val kind: String,
    val title: String?,
    val members: List<ChatMember>,
)
internal data class ChatMessage(
    val id: String,
    val senderName: String,
    val body: String,
    val createdAt: String,
    val trackTitle: String? = null,
)

@Singleton
internal class ChatRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val client: OkHttpClient,
) {
    private val preferences = context.getSharedPreferences("independent_chat", Context.MODE_PRIVATE)
    private val baseUrl = BuildConfig.CHAT_API_BASE_URL.trimEnd('/')
    private var socket: WebSocket? = null

    fun currentToken(): String? = preferences.getString(TOKEN_KEY, null)
    fun clearToken() { preferences.edit().remove(TOKEN_KEY).apply(); socket?.cancel(); socket = null }

    suspend fun register(username: String, displayName: String, password: String): ChatUser =
        authenticate("/v1/auth/register", username, displayName, password)

    suspend fun login(username: String, password: String): ChatUser =
        authenticate("/v1/auth/login", username, username, password)

    suspend fun me(): ChatUser = request("/v1/me").getJSONObject("user").let(::parseUser)

    suspend fun conversations(): List<ChatConversation> = request("/v1/conversations").let { root ->
        val items = root.getJSONArray("conversations")
        buildList { for (index in 0 until items.length()) add(parseConversation(items.getJSONObject(index))) }
    }

    suspend fun messages(conversationId: String): List<ChatMessage> = request("/v1/conversations/$conversationId/messages").let { root ->
        val items = root.getJSONArray("messages")
        buildList { for (index in 0 until items.length()) add(parseMessage(items.getJSONObject(index))) }
    }

    suspend fun sendMessage(conversationId: String, body: String): ChatMessage =
        request("/v1/conversations/$conversationId/messages", "POST", JSONObject().put("body", body))
            .getJSONObject("message").let(::parseMessage)

    suspend fun createConversation(kind: String, title: String?, memberUsernames: List<String>): ChatConversation {
        val members = JSONArray().apply { memberUsernames.forEach { put(it) } }
        return request(
            "/v1/conversations",
            "POST",
            JSONObject().put("kind", kind).put("title", title).put("memberUsernames", members),
        ).getJSONObject("conversation").let(::parseConversation)
    }

    suspend fun addMember(conversationId: String, username: String): ChatConversation =
        request("/v1/conversations/$conversationId/members", "POST", JSONObject().put("username", username))
            .getJSONObject("conversation").let(::parseConversation)

    fun connect(onMessage: (ChatMessage) -> Unit) {
        val token = currentToken() ?: return
        socket?.cancel()
        val wsUrl = baseUrl.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://")
        val request = Request.Builder().url("$wsUrl/v1/ws?token=$token").build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val json = JSONObject(text)
                    if (json.optString("type") == "message") onMessage(parseMessage(json.getJSONObject("message")))
                }
            }
        })
    }

    private suspend fun authenticate(path: String, username: String, displayName: String, password: String): ChatUser =
        request(path, "POST", JSONObject().put("username", username).put("displayName", displayName).put("password", password)).let { root ->
            preferences.edit().putString(TOKEN_KEY, root.getString("token")).apply()
            parseUser(root.getJSONObject("user"))
        }

    private suspend fun request(path: String, method: String = "GET", payload: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(baseUrl + path)
            .header("Accept", "application/json")
        currentToken()?.let { builder.header("Authorization", "Bearer $it") }
        if (method != "GET") builder.method(method, (payload?.toString() ?: "{}").toRequestBody(JSON))
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching { JSONObject(text).optString("error") }.getOrNull().orEmpty()
                error(message.ifBlank { "HTTP ${response.code}" })
            }
            JSONObject(text.ifBlank { "{}" })
        }
    }

    private fun parseUser(json: JSONObject) = ChatUser(json.getString("id"), json.getString("username"), json.getString("displayName"))
    private fun parseConversation(json: JSONObject): ChatConversation {
        val items = json.optJSONArray("members")
        val members = buildList {
            if (items != null) for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                add(ChatMember(item.optString("username"), item.optString("displayName")))
            }
        }
        return ChatConversation(json.getString("id"), json.optString("kind", "private"), json.optString("title").ifBlank { null }, members)
    }
    private fun parseMessage(json: JSONObject): ChatMessage {
        val sender = json.optJSONObject("sender")
        val track = json.optJSONObject("track")
        return ChatMessage(json.getString("id"), sender?.optString("displayName").orEmpty().ifBlank { sender?.optString("username").orEmpty() }, json.optString("body"), json.optString("createdAt"), track?.optString("title")?.ifBlank { null })
    }

    companion object {
        private const val TOKEN_KEY = "token"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
