package org.acoustixaudio.casttobrowser.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SmbRepositoryTest {
    @Test
    fun normalizeConnection_trimsServerAndShare() {
        val connection = SmbUrlUtils.normalizeConnection(
            SmbConnection(
                server = "smb://nas.local/",
                share = "/Media/",
                username = " user ",
                password = "secret",
                domain = " workgroup "
            )
        )

        assertEquals("nas.local", connection.server)
        assertEquals("Media", connection.share)
        assertEquals("user", connection.username)
        assertEquals("workgroup", connection.domain)
    }

    @Test
    fun buildRootUrl_supportsOptionalShare() {
        assertEquals(
            "smb://nas.local/Media/",
            SmbUrlUtils.buildRootUrl(SmbConnection(server = "nas.local", share = "Media"))
        )
        assertEquals(
            "smb://nas.local/",
            SmbUrlUtils.buildRootUrl(SmbConnection(server = "nas.local"))
        )
    }
}
