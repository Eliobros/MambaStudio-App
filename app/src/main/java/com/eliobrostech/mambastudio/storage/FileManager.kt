package com.eliobrostech.mambastudio.storage

import android.os.Environment
import java.io.File
import kotlinx.coroutines.yield

object FileManager {

    private const val FILES_DIR = "MambaStudio/files"
    private const val DEFAULT_FILE = "index.ms"

    private val BASE_DIR = File(Environment.getExternalStorageDirectory(), FILES_DIR)
    private var currentDir: File = BASE_DIR

    // ======================== INICIALIZAÇÃO ========================

    fun init(): Boolean {
        return ensureDirExists(BASE_DIR) && ensureDefaultFile()
    }

    private fun ensureDirExists(dir: File): Boolean {
        return if (!dir.exists()) dir.mkdirs() else true
    }

    // ======================== NAVEGAÇÃO ========================

    fun getCurrentPath(): String = currentDir.absolutePath

    fun getBasePath(): String = BASE_DIR.absolutePath

    fun isAtRoot(): Boolean = currentDir == BASE_DIR

    /**
     * Navega para uma pasta.
     * Retorna false se a pasta não existir, não for um diretório
     * ou estiver fora da árvore do MambaStudio.
     */
    fun navigateTo(folderItem: FileItem): Boolean {
        val dir = File(folderItem.path)
        return if (dir.exists() && dir.isDirectory && dir.absolutePath.startsWith(BASE_DIR.absolutePath)) {
            currentDir = dir
            true
        } else false
    }

    /**
     * Navega para um caminho absoluto.
     */
    fun navigateToPath(path: String): Boolean {
        val dir = File(path)
        return if (dir.exists() && dir.isDirectory && dir.absolutePath.startsWith(BASE_DIR.absolutePath)) {
            currentDir = dir
            true
        } else false
    }

    /**
     * Volta para a pasta anterior (pai).
     * Retorna false se já estiver na raiz.
     */
    fun navigateUp(): Boolean {
        return if (!isAtRoot()) {
            currentDir = currentDir.parentFile ?: BASE_DIR
            true
        } else false
    }

    /**
     * Volta diretamente para a raiz.
     */
    fun navigateToRoot() {
        currentDir = BASE_DIR
    }

    // ======================== LISTAR CONTEÚDOS ========================

    /**
     * Lista todos os ficheiros e pastas na pasta atual,
     * ordenados com pastas primeiro, depois por nome.
     */
    fun listContents(): List<FileItem> {
        val files = currentDir.listFiles() ?: return emptyList()
        return files
            .map { FileItem.fromFile(it) }
            .sortedWith(compareBy<FileItem> { !it.isDirectory }.thenBy { it.name.lowercase() })
    }

    /**
     * Lista apenas ficheiros (não pastas) com extensão .ms
     */
    fun listMambaFiles(): List<FileItem> {
        return listContents().filter { it.isMambaScript }
    }

    /**
     * Lista apenas pastas.
     */
    fun listFolders(): List<FileItem> {
        return listContents().filter { it.isDirectory }
    }

    /**
     * Lista todos os ficheiros .ms de forma recursiva (para o drawer lateral).
     * É uma função suspensa para não bloquear a UI em árvores grandes.
     */
    suspend fun listAllMambaFiles(): List<FileItem> {
        val result = mutableListOf<FileItem>()
        listAllMambaFilesRecursive(BASE_DIR, result)
        return result.sortedBy { it.name.lowercase() }
    }

    private suspend fun listAllMambaFilesRecursive(dir: File, result: MutableList<FileItem>) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                listAllMambaFilesRecursive(file, result)
            } else if (file.extension.lowercase() == "ms") {
                result.add(FileItem.fromFile(file))
            }
            // Pequena pausa para não travar a UI em pastas grandes
            if (result.size % 50 == 0) kotlinx.coroutines.yield()
        }
    }

    // ======================== CRIAR ========================

    /**
     * Cria um novo ficheiro .ms na pasta atual.
     * Se o nome não terminar em .ms, adiciona automaticamente.
     */
    fun createFile(name: String): Boolean {
        val fileName = if (name.endsWith(".ms")) name else "$name.ms"
        return try {
            val file = File(currentDir, fileName)
            if (file.exists()) return false
            file.createNewFile()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Cria uma nova pasta na pasta atual.
     */
    fun createFolder(name: String): Boolean {
        return try {
            val dir = File(currentDir, name)
            if (dir.exists()) return false
            dir.mkdir()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ======================== RENOMEAR ========================

    /**
     * Renomeia um ficheiro ou pasta.
     * O novo nome pode incluir ou não a extensão .ms (para ficheiros).
     */
    fun rename(item: FileItem, newName: String): Boolean {
        val oldFile = File(item.path)
        if (!oldFile.exists()) return false

        val finalName = if (!item.isDirectory && !newName.endsWith(".ms")) "$newName.ms" else newName
        val newFile = File(oldFile.parent, finalName)

        return try {
            if (newFile.exists()) return false
            oldFile.renameTo(newFile)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ======================== APAGAR ========================

    /**
     * Apaga um ficheiro ou pasta (recursivamente para pastas).
     */
    fun delete(item: FileItem): Boolean {
        return try {
            val file = File(item.path)
            if (!file.exists()) return false
            deleteRecursive(file)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun deleteRecursive(file: File): Boolean {
        return if (file.isDirectory) {
            file.listFiles()?.all { deleteRecursive(it) } == true && file.delete()
        } else {
            file.delete()
        }
    }

    // ======================== LER / ESCREVER ========================

    /**
     * Lê o conteúdo de um ficheiro.
     */
    fun readFile(item: FileItem): String? {
        return try {
            val file = File(item.path)
            if (file.exists() && file.isFile) file.readText() else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Lê o conteúdo de um ficheiro pelo caminho.
     */
    fun readFileAtPath(path: String): String? {
        return try {
            val file = File(path)
            if (file.exists() && file.isFile) file.readText() else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Escreve/sobrescreve o conteúdo de um ficheiro.
     */
    fun writeFile(item: FileItem, content: String): Boolean {
        return try {
            val file = File(item.path)
            val parent = file.parentFile ?: return false
            ensureDirExists(parent)
            file.writeText(content)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Escreve/sobrescreve o conteúdo de um ficheiro pelo caminho.
     */
    fun writeFileAtPath(path: String, content: String): Boolean {
        return try {
            val file = File(path)
            val parent = file.parentFile ?: return false
            ensureDirExists(parent)
            file.writeText(content)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Salva um ficheiro na pasta atual.
     */
    fun saveFile(fileName: String, content: String): Boolean {
        return try {
            val name = if (fileName.endsWith(".ms")) fileName else "$fileName.ms"
            val file = File(currentDir, name)
            val parent = file.parentFile ?: return false
            ensureDirExists(parent)
            file.writeText(content)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ======================== FICHEIRO PADRÃO ========================

    /**
     * Garante que o ficheiro index.ms existe na raiz.
     */
    fun ensureDefaultFile(): Boolean {
        val defaultFile = File(BASE_DIR, DEFAULT_FILE)
        return if (!defaultFile.exists()) {
            try {
                defaultFile.writeText(DEFAULT_CONTENT)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        } else true
    }

    fun getDefaultFilePath(): String = File(BASE_DIR, DEFAULT_FILE).absolutePath

    fun getDefaultFileItem(): FileItem {
        ensureDefaultFile()
        return FileItem.fromFile(File(BASE_DIR, DEFAULT_FILE))
    }

    // ======================== UTILITÁRIOS ========================

    /**
     * Verifica se um nome é válido para ficheiro/pasta (sem caracteres proibidos).
     */
    fun isValidFileName(name: String): Boolean {
        if (name.isBlank()) return false
        val forbidden = listOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
        return name.none { it in forbidden }
    }

    /**
     * Obtém o caminho relativo de um ficheiro em relação à raiz.
     */
    fun getRelativePath(item: FileItem): String {
        return item.path.removePrefix(BASE_DIR.absolutePath).removePrefix("/")
    }

    // ======================== CONTEÚDO PADRÃO ========================

    private val DEFAULT_CONTENT = """
        |# 🐍 Bem-vindo ao MambaStudio!
        |# Escreve o teu código MambaScript aqui.
        |
        |escreva "Olá, Mundo! 🚀"
    """.trimMargin()
}
