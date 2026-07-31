package com.eliobrostech.mambastudio.runner

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Gerencia o runtime Node.js embutido no app para executar MambaScript localmente.
 *
 * O Node.js (via nodejs-mobile libnode.so) é iniciado numa thread nativa através de JNI.
 * A comunicação é feita via HTTP para localhost (servidor HTTP embutido no main.js).
 *
 * Usa um singleton estático (através do ApplicationContext) para evitar iniciar
 * Node.js múltiplas vezes.
 */
class NodeJsRunner(context: Context) {

    companion object {
        private const val TAG = "NodeJsRunner"
        private const val PORT_FILE = "node_port.txt"
        private const val TIMEOUT_MS = 15000L
        private const val START_TIMEOUT_MS = 10000L
        private const val POLL_INTERVAL_MS = 100L

        // Estado estático (partilhado entre instâncias)
        @Volatile
        private var staticNodeStarted = false

        @Volatile
        private var staticNodePort: Int = -1

        private val staticAppContext: AtomicBoolean = AtomicBoolean(false)

        // Carrega as bibliotecas nativas (libnode.so + native-lib.so)
        init {
            System.loadLibrary("node")
            System.loadLibrary("native-lib")
        }
    }

    // ======================== JNI NATIVE METHODS ========================

    /**
     * Start Node.js with arguments. This call BLOCKS the calling thread forever
     * (Node.js event loop never returns). Must be called from a background thread.
     */
    private external fun startNodeWithArguments(arguments: Array<String>): Int

    // ======================== ESTADO ========================

    private val appContext: Context = context.applicationContext

    val isReady: Boolean
        get() = staticNodeStarted && staticNodePort > 0

    // ======================== INICIALIZAÇÃO ========================

    /**
     * Inicia o Node.js numa thread de background.
     * Só executa uma vez — as chamadas seguintes são no-op.
     */
    suspend fun start(): Result<Unit> = withContext(Dispatchers.IO) {
        if (staticNodeStarted) {
            android.util.Log.i(TAG, "Node.js já iniciado (porta $staticNodePort)")
            return@withContext Result.success(Unit)
        }

        try {
            // 1. Copiar Node.js project dos assets para filesDir
            copyNodeProject()

            // 2. Limpar port file antigo
            val portFile = getPortFile()
            portFile.delete()

            // 3. Iniciar Node.js numa thread separada (bloqueante)
            Thread({
                try {
                    val nodeDir = getNodeDir()
                    val args = arrayOf(
                        "node",
                        File(nodeDir, "main.js").absolutePath,
                        appContext.filesDir.absolutePath
                    )
                    startNodeWithArguments(args)
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "❌ Node.js thread crashed: ${e.message}")
                }
            }, "NodeJsThread").also { it.isDaemon = true }.start()

            // 4. Aguardar o servidor HTTP ficar pronto (polling no port file)
            val startTime = System.currentTimeMillis()
            var portFound = false

            while (System.currentTimeMillis() - startTime < START_TIMEOUT_MS) {
                if (portFile.exists()) {
                    val portStr = portFile.readText().trim()
                    if (portStr.isNotEmpty()) {
                        staticNodePort = portStr.toInt()
                        staticNodeStarted = true
                        portFound = true
                        break
                    }
                }
                Thread.sleep(POLL_INTERVAL_MS)
            }

            if (!portFound) {
                return@withContext Result.failure(
                    Exception("⏳ Node.js não iniciou em $START_TIMEOUT_MS ms")
                )
            }

            android.util.Log.i(TAG, "✅ Node.js v18 pronto na porta $staticNodePort")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Erro ao iniciar Node.js: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Verifica se o servidor Node.js está a responder.
     */
    suspend fun verificar(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!isReady) return@withContext false
            val response = httpPost("http://127.0.0.1:$staticNodePort/verificar", "{}")
            response.contains("\"pronto\":true")
        } catch (e: Exception) {
            false
        }
    }

    // ======================== EXECUTAR CÓDIGO ========================

    /**
     * Executa um ficheiro .ms através do Node.js embutido.
     */
    suspend fun executar(
        scriptPath: String,
        workingDir: String? = null,
        timeoutSeconds: Long = 15
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!isReady) {
                return@withContext Result.failure(
                    Exception("❌ Node.js não iniciado. Aguarde um momento...")
                )
            }

            val body = buildJsonObject(
                "scriptPath" to scriptPath,
                "workingDir" to (workingDir ?: File(scriptPath).parent ?: ".")
            )

            val response = httpPost("http://127.0.0.1:$staticNodePort/executar", body)
            val result = parseJsonResponse(response)

            if (result["success"] == true) {
                Result.success(result["output"]?.toString() ?: "")
            } else {
                Result.failure(Exception(result["error"]?.toString() ?: "❌ Erro desconhecido"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("❌ Erro na execução: ${e.message}"))
        }
    }

    // ======================== COMANDOS ========================

    /**
     * Executa um comando da CLI mambas (instalar, listar, remover, etc.).
     */
    suspend fun executarComando(
        args: List<String>,
        workingDir: String = appContext.filesDir.absolutePath,
        timeoutSeconds: Long = 60
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!isReady) {
                return@withContext Result.failure(
                    Exception("❌ Node.js não iniciado. Aguarde um momento...")
                )
            }

            val body = buildJsonObject(
                "args" to args,
                "workingDir" to workingDir
            )

            val response = httpPost("http://127.0.0.1:$staticNodePort/comando", body)
            val result = parseJsonResponse(response)

            if (result["success"] == true) {
                Result.success(result["output"]?.toString() ?: "")
            } else {
                Result.failure(Exception(result["error"]?.toString() ?: "❌ Erro no comando"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("❌ Erro no comando: ${e.message}"))
        }
    }

    /** Atalho para instalar pacote */
    suspend fun instalarPacote(
    nomePacote: String,
    workingDir: String = appContext.filesDir.absolutePath
): Result<String> {
    return executarComando(args = listOf("instalar", nomePacote), workingDir = workingDir)
}


    /** Atalho para listar pacotes */
    suspend fun listarPacotes(): Result<String> {
        return executarComando(args = listOf("listar"))
    }

    /** Atalho para remover pacote */
    suspend fun removerPacote(
    nomePacote: String,
    workingDir: String = appContext.filesDir.absolutePath
): Result<String> {
    return executarComando(args = listOf("remover", nomePacote), workingDir = workingDir)
}

    // ======================== INTERNO ========================

    private fun getNodeDir(): File = File(appContext.filesDir, "nodejs-project")

    private fun getPortFile(): File = File(appContext.filesDir, PORT_FILE)

    /**
     * Copia o projeto Node.js dos assets para o filesDir do app.
     */
    private fun copyNodeProject() {
        val targetDir = getNodeDir()

        try {
            val packageInfo = appContext.packageManager.getPackageInfo(
                appContext.packageName, 0
            )
            val apkUpdateTime = packageInfo.lastUpdateTime

            val versionFile = File(targetDir, ".apk_version")
            if (targetDir.exists() && versionFile.exists()) {
                val savedTime = versionFile.readText().trim().toLongOrNull() ?: 0
                if (savedTime == apkUpdateTime) {
                    android.util.Log.i(TAG, "Node.js project já copiado (mesma versão do APK)")
                    return
                }
            }

            // Remover projeto antigo e copiar novo
            if (targetDir.exists()) {
                targetDir.deleteRecursively()
            }
            targetDir.mkdirs()

            copyAssetFolder("nodejs-project", targetDir.absolutePath)

            // Salvar timestamp da versão do APK
            versionFile.writeText(apkUpdateTime.toString())

            android.util.Log.i(TAG, "Node.js project copiado para ${targetDir.absolutePath}")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Erro ao copiar Node.js project: ${e.message}")
        }
    }

    private fun copyAssetFolder(assetPath: String, targetPath: String) {
        val assetManager = appContext.assets
        val files = try {
            assetManager.list(assetPath)
        } catch (e: Exception) {
            null
        }

        if (files.isNullOrEmpty()) {
            try {
                val targetFile = File(targetPath)
                targetFile.parentFile?.mkdirs()
                assetManager.open(assetPath).use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Erro ao copiar asset $assetPath: ${e.message}")
            }
        } else {
            val targetDir = File(targetPath)
            targetDir.mkdirs()
            for (file in files) {
                copyAssetFolder("$assetPath/$file", "$targetPath/$file")
            }
        }
    }

    // ======================== HTTP CLIENT ========================

    private fun httpPost(urlString: String, body: String): String {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.doOutput = true
        conn.connectTimeout = TIMEOUT_MS.toInt()
        conn.readTimeout = TIMEOUT_MS.toInt()

        try {
            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(body)
                writer.flush()
            }

            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) {
                conn.inputStream
            } else {
                conn.errorStream
            }

            return BufferedReader(InputStreamReader(stream, "utf-8")).use { reader ->
                reader.readText()
            }
        } finally {
            conn.disconnect()
        }
    }

    // ======================== JSON HELPERS ========================

    private fun buildJsonObject(vararg pairs: Pair<String, Any?>): String {
        val entries = pairs.joinToString(",") { (key, value) ->
            when (value) {
                is String -> "\"$key\":\"${escapeJson(value)}\""
                is Number -> "\"$key\":$value"
                is Boolean -> "\"$key\":$value"
                is List<*> -> "\"$key\":[${value.joinToString(",") { v ->
                    if (v is String) "\"${escapeJson(v)}\"" else "$v"
                }}]"
                null -> "\"$key\":null"
                else -> "\"$key\":\"${escapeJson(value.toString())}\""
            }
        }
        return "{$entries}"
    }

    private fun escapeJson(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseJsonResponse(json: String): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        try {
            val trimmed = json.trim()
            if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return map

            val content = trimmed.substring(1, trimmed.length - 1)
            var depth = 0
            var inString = false
            var currentKey: String? = null
            var currentValue = StringBuilder()
            var parsingKey = true

            for (ch in content) {
                when {
                    ch == '"' && !inString -> inString = true
                    ch == '"' && inString -> inString = false
                    ch == '{' || ch == '[' -> depth++
                    ch == '}' || ch == ']' -> depth--
                    ch == ':' && depth == 0 && !inString && parsingKey -> {
                        currentKey = currentValue.toString().trim().removeSurrounding("\"")
                        currentValue = StringBuilder()
                        parsingKey = false
                    }
                    ch == ',' && depth == 0 && !inString -> {
                        if (currentKey != null) {
                            map[currentKey] = parseJsonValue(currentValue.toString().trim())
                        }
                        currentValue = StringBuilder()
                        parsingKey = true
                    }
                    else -> currentValue.append(ch)
                }
            }
            if (currentKey != null) {
                map[currentKey] = parseJsonValue(currentValue.toString().trim())
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Erro ao parsear JSON: ${e.message}")
        }
        return map
    }

    private fun parseJsonValue(value: String): Any? {
        return when {
            value == "null" -> null
            value == "true" -> true
            value == "false" -> false
            value.startsWith("\"") -> value.removeSurrounding("\"")
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
            value.contains(".") -> value.toDoubleOrNull() ?: value
            else -> value.toIntOrNull() ?: value.toLongOrNull() ?: value
        }
    }
}
