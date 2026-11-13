package com.wibechat.demo.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceConfigParserTest {

    private val key = "lk_abc123def456_ExampleKeyValue0123456789"
    private val client = "13bb8ea1-e5a9-4718-ad13-a464f0831ce3"

    @Test
    fun `onboarding message`() {
        val config = WorkspaceConfigParser.parse(
            """
            WIBECHAT_SERVER_URL=https://api.wibechat.com
            WIBECHAT_API_KEY=$key
            WIBECHAT_CLIENT_ID=$client
            """.trimIndent(),
        )
        assertEquals("https://api.wibechat.com", config.serverUrl)
        assertEquals(key, config.apiKey)
        assertEquals(client, config.clientId)
        assertTrue(config.isComplete)
    }

    @Test
    fun `colons, quotes, export and surrounding chat text`() {
        val config = WorkspaceConfigParser.parse(
            """
            Hi! Here are your details:
            export WIBECHAT_SERVER_URL="https://api.wibechat.com/"
            WIBECHAT_API_KEY: '$key'
            CLIENT_ID = ${client.uppercase()}
            Thanks
            """.trimIndent(),
        )
        assertEquals("https://api.wibechat.com", config.serverUrl)
        assertEquals(key, config.apiKey)
        assertEquals(client, config.clientId)
    }

    @Test
    fun `unlabelled values are recognised by shape`() {
        val config = WorkspaceConfigParser.parse("https://api.wibechat.com $key $client")
        assertEquals("https://api.wibechat.com", config.serverUrl)
        assertEquals(key, config.apiKey)
        assertEquals(client, config.clientId)
    }

    @Test
    fun `partial and empty input`() {
        val partial = WorkspaceConfigParser.parse("WIBECHAT_API_KEY=$key")
        assertNull(partial.serverUrl)
        assertEquals(key, partial.apiKey)
        assertFalse(partial.isComplete)
        assertTrue(partial.foundAny)

        assertFalse(WorkspaceConfigParser.parse("hello there").foundAny)
    }
}
