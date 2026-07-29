package com.eliobrostech.mambastudio.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eliobrostech.mambastudio.runner.MambaRunner
import com.eliobrostech.mambastudio.storage.FileManager
import kotlinx.coroutines.launch
import java.io.File

/**
 * Tela de gestão de pacotes MambaScript.
 *
 * Permite:
 * - Ver pacotes instalados
 * - Instalar novos pacotes do registry
 * - Remover pacotes instalados
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackageManagerScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val runner = remember { MambaRunner(context) }

    // Estado
    var installedPackages by remember { mutableStateOf<List<InstalledPackage>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showInstallDialog by remember { mutableStateOf(false) }
    var installPackageName by remember { mutableStateOf("") }

    // Carrega pacotes instalados
    fun refreshPackages() {
        installedPackages = scanInstalledPackages()
    }

    LaunchedEffect(Unit) {
        refreshPackages()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Gestor de Pacotes", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Voltar")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                refreshPackages()
                                isLoading = false
                            }
                        }
                    ) {
                        Icon(Icons.Default.Refresh, "Atualizar")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showInstallDialog = true },
                icon = { Icon(Icons.Default.Download, "Instalar") },
                text = { Text("Instalar Pacote") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Barra de pesquisa
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filtrar pacotes instalados...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, "Limpar")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Corpo
            val filteredPackages = installedPackages.filter {
                searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true)
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("A carregar...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (filteredPackages.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Inventory2,
                            null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            if (searchQuery.isNotBlank()) "Nenhum pacote encontrado"
                            else "Nenhum pacote instalado",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            if (searchQuery.isNotBlank()) "Tente outro termo de pesquisa"
                            else "Toque em \"Instalar Pacote\" para começar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item {
                        Text(
                            "${filteredPackages.size} pacote(s) instalado(s)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    items(filteredPackages, key = { it.name }) { pkg ->
                        InstalledPackageCard(
                            pkg = pkg,
                            onRemove = {
                                scope.launch {
                                    isLoading = true
                                    val result = runner.removerPacote(
                                        nomePacote = pkg.name,
                                        workingDir = FileManager.getBasePath()
                                    )
                                    isLoading = false
                                    result.onSuccess {
                                        refreshPackages()
                                        snackbarHostState.showSnackbar("${pkg.name} removido!")
                                    }.onFailure { error ->
                                        snackbarHostState.showSnackbar("❌ ${error.message}")
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // ======================== DIALOG INSTALAR ========================

    if (showInstallDialog) {
        AlertDialog(
            onDismissRequest = { showInstallDialog = false; installPackageName = "" },
            title = { Text("Instalar Pacote") },
            text = {
                Column {
                    Text(
                        "Digite o nome do pacote que deseja instalar do registry:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = installPackageName,
                        onValueChange = { installPackageName = it },
                        label = { Text("Nome do pacote") },
                        placeholder = { Text("ex: criptografia") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "O pacote será baixado de:\nhabibo-mambascript-registry.mozhost.shop",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = installPackageName.trim()
                        if (name.isNotEmpty()) {
                            scope.launch {
                                showInstallDialog = false
                                isLoading = true
                                outputMessage = null
                                val result = runner.instalarPacote(
                                    nomePacote = name,
                                    workingDir = FileManager.getBasePath()
                                )
                                isLoading = false
                                result.onSuccess { output ->
                                    refreshPackages()
                                    snackbarHostState.showSnackbar("✅ $name instalado com sucesso!")
                                }.onFailure { error ->
                                    snackbarHostState.showSnackbar("❌ ${error.message}")
                                }
                                installPackageName = ""
                            }
                        }
                    },
                    enabled = installPackageName.isNotBlank()
                ) { Text("Instalar") }
            },
            dismissButton = {
                TextButton(onClick = {
                    installPackageName = ""
                    showInstallDialog = false
                }) { Text("Cancelar") }
            }
        )
    }


}

// ======================== MODELO ========================

data class InstalledPackage(
    val name: String,
    val version: String = "",
    val path: String = ""
)

// ======================== COMPONENTES ========================

@Composable
fun InstalledPackageCard(
    pkg: InstalledPackage,
    onRemove: () -> Unit
) {
    var showRemoveConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ícone
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Inventory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pkg.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                if (pkg.version.isNotBlank()) {
                    Text(
                        text = "v${pkg.version}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Botão remover
            IconButton(
                onClick = { showRemoveConfirm = true },
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            ) {
                Icon(Icons.Default.Delete, "Remover pacote")
            }
        }
    }

    // Dialog confirmação de remoção
    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Remover ${pkg.name}?") },
            text = { Text("O pacote \"${pkg.name}\" será apagado do teu dispositivo.") },
            confirmButton = {
                Button(
                    onClick = {
                        showRemoveConfirm = false
                        onRemove()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Remover") }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) { Text("Cancelar") }
            }
        )
    }
}

// ======================== SCAN DE PACOTES ========================

/**
 * Escaneia a pasta modulos_mambas/ e lista os pacotes instalados.
 * Lê as subpastas diretamente — simples e sem parsing frágil de JSON.
 */
private fun scanInstalledPackages(): List<InstalledPackage> {
    val modulosDir = File(FileManager.getBasePath(), "modulos_mambas")
    val dirs = modulosDir.listFiles { f -> f.isDirectory } ?: return emptyList()

    return dirs.map { dir ->
        // Tenta ler versão de um package.json ou mamba.json dentro do módulo
        val version = lerVersaoDoModulo(dir)
        InstalledPackage(
            name = dir.name,
            version = version,
            path = dir.absolutePath
        )
    }.sortedBy { it.name.lowercase() }
}

/**
 * Tenta ler a versão de um módulo a partir do package.json ou mamba.json.
 */
private fun lerVersaoDoModulo(moduloDir: File): String {
    // Procura package.json
    val packageJson = File(moduloDir, "package.json")
    if (packageJson.exists()) {
        try {
            val content = packageJson.readText()
            // Procura "version": "x.y.z" no texto
            val regex = "\"version\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            val match = regex.find(content)
            if (match != null) return match.groupValues[1]
        } catch (_: Exception) {}
    }
    // Procura mamba.json
    val mambaJson = File(moduloDir, "mamba.json")
    if (mambaJson.exists()) {
        try {
            val content = mambaJson.readText()
            val regex = "\"versao\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            val match = regex.find(content)
            if (match != null) return match.groupValues[1]
        } catch (_: Exception) {}
    }
    return ""
}
