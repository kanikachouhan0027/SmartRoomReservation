package com.smartroom.reserve.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Talks to Google's Gemini API using a plain HTTPS POST request.
 * No extra library is needed: HttpURLConnection and org.json are built into Android.
 *
 * Request  ->  {"systemInstruction": {...}, "contents": [...], "generationConfig": {...}}
 * Response <-  {"candidates": [{"content": {"parts": [{"text": "..."}]}}]}
 */
class GeminiClient(private val apiKey: String, private val model: String) {

    /** One message in the conversation. role is "user" or "model". */
    data class Turn(val role: String, val text: String)

    suspend fun generate(
        systemPrompt: String,
        turns: List<Turn>,
        jsonOutput: Boolean
    ): String = withContext(Dispatchers.IO) {
        // 1. Build the request body
        val contents = JSONArray()
        for (t in normalise(turns)) {
            contents.put(
                JSONObject()
                    .put("role", t.role)
                    .put("parts", JSONArray().put(JSONObject().put("text", t.text)))
            )
        }
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
            .put("contents", contents)
        val config = JSONObject().put("temperature", 0.3)
        if (jsonOutput) config.put("responseMimeType", "application/json")
        body.put("generationConfig", config)

        // 2. Send it
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 20_000
            conn.readTimeout = 60_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-goog-api-key", apiKey)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            // 3. Read the answer
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw AiException(friendlyError(code, text))
            extractText(text)
        } catch (e: AiException) {
            throw e
        } catch (e: java.io.IOException) {
            throw AiException("No internet connection, or Google's server could not be reached.")
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Gemini expects the conversation to start with the user and to alternate user / model.
     * So we drop leading bot messages and join two messages in a row from the same side.
     */
    private fun normalise(turns: List<Turn>): List<Turn> {
        val out = mutableListOf<Turn>()
        for (t in turns.dropWhile { it.role != "user" }) {
            val last = out.lastOrNull()
            if (last != null && last.role == t.role) out[out.size - 1] = Turn(t.role, last.text + "\n" + t.text)
            else out.add(t)
        }
        return out
    }

    /** Takes the model's text out of the JSON response (skipping any "thought" parts). */
    private fun extractText(responseJson: String): String {
        val root = JSONObject(responseJson)
        val candidates = root.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            throw AiException("The AI did not return an answer. Please rephrase and try again.")
        }
        val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
            ?: throw AiException("The AI returned an empty answer.")
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val p = parts.getJSONObject(i)
            if (!p.optBoolean("thought", false)) sb.append(p.optString("text"))
        }
        return sb.toString().trim()
    }

    private fun friendlyError(code: Int, body: String): String {
        val serverMsg = runCatching {
            JSONObject(body).getJSONObject("error").getString("message")
        }.getOrDefault("")
        return when (code) {
            400 -> if (serverMsg.contains("API key", true)) "Your Gemini API key is not valid. Check it in Profile → AI settings."
                   else "The AI could not understand the request ($serverMsg)."
            401, 403 -> "Your Gemini API key is not valid or not allowed. Check it in Profile → AI settings."
            404 -> "AI model \"$model\" was not found. Change the model name in Profile → AI settings."
            429 -> "Free AI limit reached for now. Please wait a minute and try again."
            else -> "AI service error ($code). Please try again."
        }
    }
}

class AiException(message: String) : Exception(message)
