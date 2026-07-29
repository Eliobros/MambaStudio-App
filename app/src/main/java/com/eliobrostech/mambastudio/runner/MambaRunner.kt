package com.eliobrostech.mambastudio.runner

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Responsável por executar o binário MambaScript localmente no Android.
 *
 * O binário (mambas) é compilado com pkg para ARM64 e baixado
 * na primeira execução para o filesDir do app.
 */
class MambaRunner(private val context: Context) {

    companion object {
        private const val BINARY_NAME = "mambas"
        private const val DEFAULT_TIMEOUT = 15L
        private const val INSTALL_TIMEOUT = 60L
        private const val DOWNLOAD_URL = "https://mambascript-api.mozhost.shop/binario/mambas-arm64"
    }

    // ======================== BINÁRIO ========================

    /** Caminho do binário mambas no filesDir do app */
    private val binaryFile: File
        get() = File(context.filesDir, BINARY_NAME)

    /** Verifica se o binário já foi baixado */
    val isBinaryDownloaded: Boolean
        get() = binaryFile.exists()

    /**
     * Verifica se o binário mambas está funcional.
     * Executa "mambas --version" para testar.
     */
    fun verificar(): Boolean {
        return try {
            val process = ProcessBuilder(binaryFile.absolutePath, "--version")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor(3, TimeUnit.SECONDS)
            process.destroy()
            output.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    // ======================== DOWNLOAD ========================

    /**
     * Descarrega o binário mambas para ARM64 do servidor.
     * Guarda no filesDir e torna executável.
     */
    suspend fun downloadBinary(
        url: String = DOWNLOAD_URL,
        onProgress: ((Float) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val urlConnection = java.net.URL(url).openConnection()
            urlConnection.connect()
            val fileLength = urlConnection.contentLengthLong
            val inputStream = urlConnection.getInputStream()
            val outputFile = binaryFile

            // Cria diretório temporário para download
            val tempFile = File(context.cacheDir, "${BINARY_NAME}_download")
            tempFile.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytes = 0L
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytes += bytesRead
                    if (fileLength > 0) {
                        onProgress?.invoke(totalBytes.toFloat() / fileLength)
                    }
                }
            }
            inputStream.close()

            // Move para o destino e torna executável
            tempFile.copyTo(outputFile, overwrite = true)
            tempFile.delete()
            outputFile.setExecutable(true)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ======================== EXECUTAR CÓDIGO ========================

    /**
     * Executa um ficheiro MambaScript.
     *
     * @param scriptPath Caminho absoluto do ficheiro .ms
     * @param workingDir Diretório de trabalho (onde está o ficheiro)
     * @param timeoutSeconds Tempo máximo de execução
     * @return Result com stdout em caso de sucesso, ou erro em caso de falha
     */
    suspend fun executar(
        scriptPath: String,
        workingDir: String? = null,
        timeoutSeconds: Long = DEFAULT_TIMEOUT
    ): Result<String> = withContext(Dispatchers.IO) {
        // Verifica se o binário existe
        if (!isBinaryDownloaded) {
            return@withContext Result.failure(
                IOException("❌ MambaScript não baixado. Faça o download primeiro.")
            )
        }

        try {
            val processBuilder = ProcessBuilder(
                binaryFile.absolutePath,
                scriptPath
            )
            processBuilder.directory(File(workingDir ?: File(scriptPath).parent ?: "."))
            // Redireciona stderr para stdout para evitar deadlocks
            processBuilder.redirectErrorStream(true)

            val process = processBuilder.start()

            // Lê a saída completa (simples e sem risco de race condition)
            val output = try {
                process.inputStream.bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                ""
            }

            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)

            if (!finished) {
                process.destroyForcibly()
                return@withContext Result.failure(
                    IOException("⏳ Execução excedeu $timeoutSeconds segundos.")
                )
            }

            val exitCode = process.exitValue()
            val trimmedOutput = output.trimEnd()

            if (exitCode == 0) {
                Result.success(trimmedOutput)
            } else {
                Result.failure(IOException(trimmedOutput.ifEmpty { "❌ Erro desconhecido (código $exitCode)." }))
            }
        } catch (e: Exception) {
            val msg = when {
                e is IOException && e.message != null -> e.message!!
                else -> "❌ Erro ao executar: ${e.message}"
            }
            Result.failure(IOException(msg))
        }
    }

    // ======================== COMANDOS (instalar, listar, etc) ========================

    /**
     * Executa um comando do MambaScript (ex: "instalar criptografia").
     *
     * @param args Lista de argumentos (ex: ["instalar", "criptografia"])
     * @param workingDir Diretório de trabalho (normalmente a raiz dos projetos)
     * @param timeoutSeconds Tempo máximo (maior para instalações)
     * @return Result com stdout
     */
    suspend fun executarComando(
        args: List<String>,
        workingDir: String,
        timeoutSeconds: Long = INSTALL_TIMEOUT
    ): Result<String> = withContext(Dispatchers.IO) {
        // Verifica se o binário existe
        if (!isBinaryDownloaded) {
            return@withContext Result.failure(
                IOException("❌ MambaScript não baixado. Faça o download primeiro.")
            )
        }

        try {
            val command = mutableListOf(binaryFile.absolutePath)
            command.addAll(args)

            val processBuilder = ProcessBuilder(command)
            processBuilder.directory(File(workingDir))
            processBuilder.redirectErrorStream(true)

            val process = processBuilder.start()

            // Lê a saída completa (sem threads manuais)
            val output = try {
                process.inputStream.bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                ""
            }

            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)

            if (!finished) {
                process.destroyForcibly()
                return@withContext Result.failure(
                    IOException("⏳ Comando excedeu $timeoutSeconds segundos.")
                )
            }

            val trimmedOutput = output.trimEnd()
            if (process.exitValue() == 0) {
                Result.success(trimmedOutput)
            } else {
                Result.failure(IOException(trimmedOutput.ifEmpty { "❌ Comando falhou." }))
            }
        } catch (e: Exception) {
            Result.failure(IOException("❌ Erro no comando: ${e.message}"))
        }
    }

    /**
     * Atalho para instalar um pacote:
     *   mambas instalar <nomePacote>
     */
    suspend fun instalarPacote(
        nomePacote: String,
        workingDir: String = context.filesDir.absolutePath
    ): Result<String> {
        return executarComando(
            args = listOf("instalar", nomePacote),
            workingDir = workingDir,
            timeoutSeconds = INSTALL_TIMEOUT
        )
    }

    /**
     * Atalho para listar pacotes instalados:
     *   mambas listar
     */
    suspend fun listarPacotes(
        workingDir: String = context.filesDir.absolutePath
    ): Result<String> {
        return executarComando(
            args = listOf("listar"),
            workingDir = workingDir,
            timeoutSeconds = DEFAULT_TIMEOUT
        )
    }

    /**
     * Atalho para remover um pacote:
     *   mambas remover <nomePacote>
     */
    suspend fun removerPacote(
        nomePacote: String,
        workingDir: String = context.filesDir.absolutePath
    ): Result<String> {
        return executarComando(
            args = listOf("remover", nomePacote),
            workingDir = workingDir,
            timeoutSeconds = DEFAULT_TIMEOUT
        )
    }
}
