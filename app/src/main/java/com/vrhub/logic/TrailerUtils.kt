package com.vrhub.logic

/**
 * Utilities for handling streaming trailer URLs (Story 11.2).
 *
 * The server (Story 11.1) resolves and serves a YouTube **watch URL** for a game's trailer.
 * The client never downloads the video — it streams it via the official YouTube IFrame Player,
 * which needs the bare 11-character video ID. These helpers extract that ID from the various
 * YouTube URL shapes without pulling in any networking or YouTube-internal parsing.
 */
object TrailerUtils {

    /**
     * YouTube video IDs are exactly 11 characters from the URL-safe Base64 alphabet
     * (A–Z, a–z, 0–9, '-', '_'). We anchor the match to that set so we never accept
     * a truncated or padded value.
     */
    private val VIDEO_ID_REGEX = Regex("^[A-Za-z0-9_-]{11}$")

    /**
     * Parses the 11-character YouTube video ID from a watch/share URL.
     *
     * Supported shapes:
     *  - https://www.youtube.com/watch?v=ID  (with any extra params, e.g. &t=30s, &list=...)
     *  - https://youtu.be/ID                 (with optional ?t= / trailing params)
     *  - https://www.youtube.com/embed/ID
     *  - https://www.youtube.com/shorts/ID
     *  - http/https, with or without "www.", and youtube-nocookie.com
     *
     * @param url the trailer URL served by the server (may be null/blank/garbage).
     * @return the 11-char video ID, or null if the URL is not a parseable YouTube link.
     */
    fun extractYouTubeId(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val trimmed = url.trim()

        // 1) youtu.be/<id>
        Regex("""youtu\.be/([A-Za-z0-9_-]{11})""").find(trimmed)?.let {
            return it.groupValues[1].takeIf(::isValidVideoId)
        }

        // 2) /watch?v=<id> — pull the v query param explicitly so other params don't interfere.
        Regex("""[?&]v=([A-Za-z0-9_-]{11})""").find(trimmed)?.let {
            return it.groupValues[1].takeIf(::isValidVideoId)
        }

        // 3) /embed/<id>, /shorts/<id>, /v/<id>
        Regex("""youtube(?:-nocookie)?\.com/(?:embed|shorts|v)/([A-Za-z0-9_-]{11})""").find(trimmed)?.let {
            return it.groupValues[1].takeIf(::isValidVideoId)
        }

        return null
    }

    /**
     * @return true if [id] is a syntactically valid YouTube video ID (exactly 11 url-safe chars).
     */
    fun isValidVideoId(id: String?): Boolean =
        id != null && VIDEO_ID_REGEX.matches(id)
}
