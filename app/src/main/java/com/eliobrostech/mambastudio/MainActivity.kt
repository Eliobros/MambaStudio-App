package com.eliobrostech.mambastudio

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eliobrostech.mambastudio.ui.IDEHomeScreen
import com.eliobrostech.mambastudio.ui.theme.MozhostTheme

class MainActivity : ComponentActivity() {
    private var hasStoragePermission by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        registarHandlerCrash()
        escreverDiagnostico()
        checkPermissions()

        setContent {
            MozhostTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    if (hasStoragePermission) {
                        IDEHomeScreen()
                    } else {
                        PermissionScreen(
                            onRequestPermission = { requestStoragePermission() }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    /**
     * Escreve um ficheiro de diagnóstico (modelo, Android, tamanho de página)
     * para o armazenamento partilhado e interno. Sobrevive mesmo a crash nativo.
     */
    private fun escreverDiagnostico() {
        try {
            val pageSize = android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE)
            val diag = buildString {
                appendLine("modelo=${Build.MODEL}")
                appendLine("marca=${Build.MANUFACTURER}")
                appendLine("android=${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("tamanho_pagina=$pageSize bytes")
                appendLine("if tamanho_pagina=4096 -> paginas de 4KB (normal)")
                appendLine("if tamanho_pagina=16384 -> paginas de 16KB (crash libnode.so!)")
            }
            File(filesDir, "diagnostico.txt").writeText(diag)
            try {
                val sharedDir = File(Environment.getExternalStorageDirectory(), "MambaStudio/files")
                if (sharedDir.exists() || sharedDir.mkdirs()) {
                    File(sharedDir, "diagnostico.txt").writeText(diag)
                }
            } catch (_: Exception) {}
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Erro ao escrever diagnostico: ${e.message}")
        }
    }

    /**
     * Captura crashes de Java para um ficheiro (para diagnóstico) e
     * re-encaminha para o handler padrão do Android.
     */
    private fun registarHandlerCrash() {
        try {
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    val trace = android.util.Log.getStackTraceString(throwable)
                    val diag = "thread=${thread.name}\n$trace"
                    File(filesDir, "crash_java.txt").writeText(diag)
                    try {
                        val sharedDir = File(Environment.getExternalStorageDirectory(), "MambaStudio/files")
                        if (sharedDir.exists() || sharedDir.mkdirs()) {
                            File(sharedDir, "crash_java.txt").writeText(diag)
                        }
                    } catch (_: Exception) {}
                } catch (_: Exception) {}
                defaultHandler?.uncaughtException(thread, throwable)
            }
        } catch (_: Exception) {}
    }

    private fun checkPermissions() {
        hasStoragePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            // Simplificado para este exemplo, assumindo que MANAGE_EXTERNAL_STORAGE cobre o necessário
            true 
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.addCategory("android.intent.category.DEFAULT")
                intent.data = Uri.parse("package:${packageName}")
                startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent()
                intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                startActivity(intent)
            }
        }
    }
}

@Composable
fun PermissionScreen(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "MambaStudio precisa de acesso ao armazenamento para salvar seus códigos.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRequestPermission) {
            Text("Conceder Permissão")
        }
    }
}
