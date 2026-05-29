package org.acoustixaudio.casttobrowser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavXmlParserTest {
    @Test
    fun parseDirectoryListing_filtersCurrentDirectoryAndKeepsMedia() {
        val xml = """
            <?xml version="1.0" encoding="utf-8" ?>
            <D:multistatus xmlns:D="DAV:">
              <D:response>
                <D:href>/remote.php/dav/files/demo/Photos/</D:href>
                <D:propstat>
                  <D:prop>
                    <D:displayname>Photos</D:displayname>
                    <D:resourcetype><D:collection/></D:resourcetype>
                  </D:prop>
                </D:propstat>
              </D:response>
              <D:response>
                <D:href>/remote.php/dav/files/demo/Photos/Trips/</D:href>
                <D:propstat>
                  <D:prop>
                    <D:displayname>Trips</D:displayname>
                    <D:getlastmodified>Tue, 21 May 2024 18:30:00 GMT</D:getlastmodified>
                    <D:resourcetype><D:collection/></D:resourcetype>
                  </D:prop>
                </D:propstat>
              </D:response>
              <D:response>
                <D:href>/remote.php/dav/files/demo/Photos/image.jpg</D:href>
                <D:propstat>
                  <D:prop>
                    <D:displayname>image.jpg</D:displayname>
                    <D:getcontentlength>1024</D:getcontentlength>
                    <D:getcontenttype>image/jpeg</D:getcontenttype>
                    <D:getlastmodified>Tue, 21 May 2024 18:30:00 GMT</D:getlastmodified>
                    <D:resourcetype />
                  </D:prop>
                </D:propstat>
              </D:response>
              <D:response>
                <D:href>/remote.php/dav/files/demo/Photos/readme.txt</D:href>
                <D:propstat>
                  <D:prop>
                    <D:displayname>readme.txt</D:displayname>
                    <D:getcontentlength>12</D:getcontentlength>
                    <D:getcontenttype>text/plain</D:getcontenttype>
                    <D:resourcetype />
                  </D:prop>
                </D:propstat>
              </D:response>
            </D:multistatus>
        """.trimIndent()

        val entries = WebDavXmlParser.parseDirectoryListing(
            baseUrl = "https://example.com/remote.php/dav/files/demo/Photos/",
            currentDirectoryUrl = "https://example.com/remote.php/dav/files/demo/Photos/",
            xml = xml
        )

        assertEquals(2, entries.size)
        assertEquals("Trips", entries[0].name)
        assertTrue(entries[0].isDirectory)
        assertEquals("image.jpg", entries[1].name)
        assertEquals(MediaType.IMAGE, entries[1].mediaType)
        assertEquals(1024L, entries[1].size)
        assertEquals("https://example.com/remote.php/dav/files/demo/Photos/image.jpg", entries[1].url)
    }

    @Test
    fun normalize_addsSchemeAndTrailingSlash() {
        assertEquals(
            "http://nas.local:8080/library/",
            WebDavUrlUtils.normalize("nas.local:8080/library")
        )
    }
}
