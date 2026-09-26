package com.sai.cardtrack.ui.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object ShareCsv {
    const val MIME = "text/csv"
    const val TABLE_FILENAME = "cardtrack.csv"
    const val KOINLY_FILENAME = "cardtrack-koinly.csv"

    fun cacheRelativePath(filename: String): String {
        require(!filename.contains("/") && !filename.contains("..")) {
            "filename must not contain '/' or '..'"
        }
        return "export/$filename"
    }

    fun writeAndIntent(context: Context, content: String, filename: String): Intent {
        val relative = cacheRelativePath(filename)
        val file = File(context.cacheDir, relative)
        file.parentFile?.mkdirs()
        file.writeText(content)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.export",
            file
        )
        return intent(uri, filename)
    }

    fun intent(uri: Uri, filename: String): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri(filename, uri)
        }
    }
}
