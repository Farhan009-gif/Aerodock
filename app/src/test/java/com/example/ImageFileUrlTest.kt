package com.example

import com.example.ui.components.isImageFileUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The chat renderer promotes a markdown link to an image preview when its URL
// points at an image file. Regression context: 2026-11-12, a Composio image
// result arrived wrapped as [snow-walk.jpg](signed S3 URL) and showed as a
// bare link instead of a picture.
class ImageFileUrlTest {

    @Test
    fun `plain image urls are images`() {
        assertTrue(isImageFileUrl("https://example.com/cat.jpg"))
        assertTrue(isImageFileUrl("https://example.com/cat.JPEG"))
        assertTrue(isImageFileUrl("https://example.com/a/b/photo.png"))
        assertTrue(isImageFileUrl("https://example.com/anim.webp"))
        assertTrue(isImageFileUrl("https://example.com/anim.gif"))
    }

    @Test
    fun `signed urls with query strings are images`() {
        // The exact shape Composio returned: .jpg followed by S3 signing params.
        assertTrue(
            isImageFileUrl(
                "https://temp.4d4f16c61d89ec64e760039c4ec50717.r2.cloudflarestorage.com" +
                    "/project_scope/ok/files/j927dlun6r3o.jpg?X-Amz-Algorithm=AWS4-HMAC-SHA256" +
                    "&X-Amz-Expires=3600&X-Amz-Signature=d38564afb"
            )
        )
        assertTrue(isImageFileUrl("https://example.com/pic.png#fragment"))
    }

    @Test
    fun `non image urls stay links`() {
        assertFalse(isImageFileUrl("https://example.com/article"))
        assertFalse(isImageFileUrl("https://example.com/file.pdf"))
        assertFalse(isImageFileUrl("https://example.com/page.html?next=cat.jpg"))
        assertFalse(isImageFileUrl("https://example.com/jpg"))
        assertFalse(isImageFileUrl(""))
    }
}
