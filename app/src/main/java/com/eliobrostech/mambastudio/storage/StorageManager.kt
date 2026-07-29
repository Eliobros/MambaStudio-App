package com.eliobrostech.mambastudio.storage

import android.os.Environment
import java.io.File

/**
 * Gerenciador de ficheiros legado.
 * Mantido para compatibilidade — delegando para o novo FileManager.
 *
 * NOTA: StorageManager salva em "MambaStudio/" (raiz direta).
 * FileManager salva em "MambaStudio/files/" (com subpasta).
 */
@Deprecated("Usar FileManager para novas funcionalidades")
object StorageManager {
    private val rootDir = File(Environment.getExternalStorageDirectory(), "MambaStudio")

    fun ensureDirectoryExists(): Boolean {
        if (!rootDir.exists()) {
            return rootDir.mkdirs()
        }
        return true
    }

    fun saveFile(fileName: String, content: String): Boolean {
        return try {
            ensureDirectoryExists()
            val file = File(rootDir, if (fileName.endsWith(".ms")) fileName else "$fileName.ms")
            file.writeText(content)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun readFile(fileName: String): String? {
        return try {
            val file = File(rootDir, fileName)
            if (file.exists()) file.readText() else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun listFiles(): List<String> {
        ensureDirectoryExists()
        return rootDir.listFiles { _, name -> name.endsWith(".ms") }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
    }
    
    fun getRootPath(): String = rootDir.absolutePath
}
