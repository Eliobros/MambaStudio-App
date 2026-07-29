package com.eliobrostech.mambastudio.storage

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val lastModified: Long = 0,
    val extension: String = ""
) {
    companion object {
        private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        fun fromFile(file: File): FileItem {
            return FileItem(
                name = file.name,
                path = file.absolutePath,
                isDirectory = file.isDirectory,
                size = if (file.isFile) file.length() else 0,
                lastModified = file.lastModified(),
                extension = if (file.isFile) file.extension.lowercase() else ""
            )
        }
    }

    val formattedSize: String
        get() = when {
            isDirectory -> ""
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${"%.1f".format(size / 1024.0)} KB"
            size < 1024 * 1024 * 1024 -> "${"%.1f".format(size / (1024.0 * 1024))} MB"
            else -> "${"%.1f".format(size / (1024.0 * 1024 * 1024))} GB"
        }

    val formattedDate: String
        get() = if (lastModified > 0) dateFormat.format(Date(lastModified)) else ""

    val isMambaScript: Boolean
        get() = extension == "ms"

    val icon: String
        get() = when {
            isDirectory -> "📁"
            extension == "ms" -> "🐍"
            extension == "txt" -> "📄"
            extension == "json" -> "📋"
            else -> "📄"
        }
}
