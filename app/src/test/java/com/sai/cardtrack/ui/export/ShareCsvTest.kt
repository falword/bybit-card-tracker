package com.sai.cardtrack.ui.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ShareCsvTest {

    @Test
    fun `cacheRelativePath uses export folder and table filename`() {
        assertEquals("export/cardtrack.csv", ShareCsv.cacheRelativePath(ShareCsv.TABLE_FILENAME))
    }

    @Test
    fun `cacheRelativePath rejects slash in filename`() {
        assertThrows(IllegalArgumentException::class.java) {
            ShareCsv.cacheRelativePath("nested/cardtrack.csv")
        }
    }

    @Test
    fun `cacheRelativePath rejects parent traversal in filename`() {
        assertThrows(IllegalArgumentException::class.java) {
            ShareCsv.cacheRelativePath("..secret.csv")
        }
    }
}
