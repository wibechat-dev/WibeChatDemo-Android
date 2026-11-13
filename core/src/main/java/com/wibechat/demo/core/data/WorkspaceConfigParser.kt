package com.wibechat.demo.core.data

/** The three values WibeChat gives each client. Any of them may be missing after parsing. */
data class WorkspaceConfig(
    val serverUrl: String? = null,
    val apiKey: String? = null,
    val clientId: String? = null,
) {
    val isComplete: Boolean get() = serverUrl != null && apiKey != null && clientId != null
    val foundAny: Boolean get() = serverUrl != null || apiKey != null || clientId != null
}

/**
 * Pulls the WibeChat config out of whatever the user pasted, e.g. the
 * onboarding message:
 *
 * ```
 * WIBECHAT_SERVER_URL=https://api.wibechat.com
 * WIBECHAT_API_KEY=lk_…
 * WIBECHAT_CLIENT_ID=13bb8ea1-e5a9-4718-ad13-a464f0831ce3
 * ```
 *
 * Also accepts `KEY: value`, quotes, `export KEY=…`, extra text around it,
 * and unlabelled values (recognised by shape: an http(s) URL, an `lk_` key,
 * a UUID).
 */
object WorkspaceConfigParser {

    private val serverKeys = setOf("WIBECHAT_SERVER_URL", "SERVER_URL", "WIBECHAT_API_URL", "API_URL", "BASE_URL")
    private val apiKeyKeys = setOf("WIBECHAT_API_KEY", "API_KEY", "WIBECHAT_LICENSE_KEY", "LICENSE_KEY")
    private val clientIdKeys = setOf("WIBECHAT_CLIENT_ID", "CLIENT_ID")

    private val labelled = Regex("""(?im)^\s*(?:export\s+)?([A-Z][A-Z0-9_]*)\s*[=:]\s*["'`]?([^\s"'`]+)""")
    private val url = Regex("""https?://[^\s"'`<>]+""", RegexOption.IGNORE_CASE)
    private val apiKey = Regex("""\blk_[A-Za-z0-9_\-]{8,}""")
    private val uuid = Regex("""\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b""")

    fun parse(text: String): WorkspaceConfig {
        var server: String? = null
        var key: String? = null
        var client: String? = null

        // 1. Labelled lines win.
        labelled.findAll(text).forEach { match ->
            val name = match.groupValues[1].uppercase()
            val value = match.groupValues[2].trim().trimEnd(',', ';')
            when (name) {
                in serverKeys -> server = server ?: value
                in apiKeyKeys -> key = key ?: value
                in clientIdKeys -> client = client ?: value
            }
        }

        // 2. Fill gaps by shape.
        server = server ?: url.find(text)?.value
        key = key ?: apiKey.find(text)?.value
        client = client ?: uuid.find(text)?.value

        return WorkspaceConfig(
            serverUrl = server?.trimEnd('/', '.', ',', ';'),
            apiKey = key,
            clientId = client?.lowercase(),
        )
    }
}
