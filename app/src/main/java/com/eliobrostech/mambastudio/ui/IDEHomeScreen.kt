package com.eliobrostech.mambastudio.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eliobrostech.mambastudio.runner.MambaRunner
import com.eliobrostech.mambastudio.storage.FileItem
import com.eliobrostech.mambastudio.storage.FileManager
import kotlinx.coroutines.launch

// Telas disponíveis no drawer
enum class AppScreen(val label: String, val icon: @Composable () -> Unit) {
    EDITOR("Editor", { Icon(Icons.Default.Code, "Editor") }),
    EXPLORER("Explorador", { Icon(Icons.Default.Folder, "Explorador") }),
    PACOTES("Pacotes", { Icon(Icons.Default.Inventory2, "Pacotes") })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IDEHomeScreen() {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Estado de navegação
    var currentScreen by remember { mutableStateOf(AppScreen.EDITOR) }

    // Estado do editor
    var currentFileItem by remember { mutableStateOf<FileItem?>(null) }
    var code by remember { mutableStateOf("") }
    var consoleOutput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    // MambaRunner para execução local
    val runner = remember { MambaRunner(context) }
    var binaryReady by remember { mutableStateOf(runner.isBinaryDownloaded) }
    var showBinaryDialog by remember { mutableStateOf(!runner.isBinaryDownloaded) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var isDownloading by remember { mutableStateOf(false) }

    // Estado do drawer (lista de ficheiros)
    var allFiles by remember { mutableStateOf<List<FileItem>>(emptyList()) }

    // Inicialização
    LaunchedEffect(Unit) {
        FileManager.init()
        // Carrega o ficheiro padrão
        val defaultFile = FileManager.getDefaultFileItem()
        currentFileItem = defaultFile
        val content = FileManager.readFile(defaultFile)
        if (content != null) code = content
        // Carrega lista de ficheiros para o drawer
        allFiles = FileManager.listAllMambaFiles()
    }

    // Dialog de download do binário (mostra na 1ª execução)
    if (showBinaryDialog) {
        AlertDialog(
            onDismissRequest = { /* Não pode dispensar */ },
            icon = { Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("MambaScript Engine") },
            text = {
                Column {
                    Text(
                        "Para executar código MambaScript offline, precisas descarregar o motor de execução (~15MB).",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    if (isDownloading) {
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${(downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            isDownloading = true
                            downloadProgress = 0f
                            runner.downloadBinary(
                                onProgress = { progress ->
                                    downloadProgress = progress
                                }
                            ).onSuccess {
                                binaryReady = true
                                showBinaryDialog = false
                                Toast.makeText(context, "✅ Motor MambaScript instalado!", Toast.LENGTH_SHORT).show()
                            }.onFailure { error ->
                                Toast.makeText(context, "❌ Erro: ${error.message}", Toast.LENGTH_LONG).show()
                                showBinaryDialog = false
                            }
                            isDownloading = false
                        }
                    },
                    enabled = !isDownloading
                ) { Text("Descarregar (15MB)") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBinaryDialog = false
                    Toast.makeText(context, "Podes descarregar depois no menu", Toast.LENGTH_SHORT).show()
                }) { Text("Agora não") }
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(280.dp)) {
                // Cabeçalho
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "🐍 MambaStudio",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "IDE MambaScript",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }

                // Navegação entre telas
                Text(
                    "Navegação",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                AppScreen.entries.forEach { screen ->
                    NavigationDrawerItem(
                        icon = { screen.icon() },
                        label = { Text(screen.label) },
                        selected = currentScreen == screen,
                        onClick = {
                            currentScreen = screen
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Estado do binário
                Text(
                    "Motor",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                NavigationDrawerItem(
                    icon = {
                        Icon(
                            if (binaryReady) Icons.Default.CheckCircle else Icons.Default.Download,
                            null,
                            tint = if (binaryReady) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                        )
                    },
                    label = {
                        Text(
                            if (binaryReady) "MambaScript pronto" else "Descarregar motor",
                            fontSize = 13.sp
                        )
                    },
                    selected = false,
                    onClick = {
                        if (!binaryReady) {
                            showBinaryDialog = true
                        }
                    },
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                // Ficheiros
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    "Ficheiros Recentes",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                if (currentScreen == AppScreen.EDITOR && allFiles.isNotEmpty()) {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(allFiles.take(30)) { fileItem ->
                            NavigationDrawerItem(
                                icon = { Text(fileItem.icon, fontSize = 16.sp) },
                                label = {
                                    Column {
                                        Text(
                                            fileItem.name,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            FileManager.getRelativePath(fileItem),
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                },
                                selected = fileItem.path == currentFileItem?.path,
                                onClick = {
                                    scope.launch {
                                        val content = FileManager.readFile(fileItem)
                                        if (content != null) {
                                            currentFileItem = fileItem
                                            code = content
                                        }
                                        drawerState.close()
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    ) {
        // Conteúdo principal baseado na tela selecionada
        when (currentScreen) {
            AppScreen.EDITOR -> {
                EditorView(
                    currentFileItem = currentFileItem,
                    code = code,
                    onCodeChange = { code = it },
                    consoleOutput = consoleOutput,
                    isLoading = isLoading,
                    binaryReady = binaryReady || runner.isBinaryDownloaded,
                    onSave = {
                        scope.launch {
                            if (currentFileItem != null) {
                                FileManager.writeFile(currentFileItem!!, code)
                                // Atualiza a lista de ficheiros se o nome mudou
                                allFiles = FileManager.listAllMambaFiles()
                            } else {
                                // Salva como novo ficheiro
                                val name = "index.ms"
                                FileManager.saveFile(name, code)
                                val savedFile = FileItem.fromFile(
                                    java.io.File(FileManager.getCurrentPath(), name)
                                )
                                currentFileItem = savedFile
                                allFiles = FileManager.listAllMambaFiles()
                            }
                        }
                    },
                    onClearConsole = { consoleOutput = "" },
                    onRun = {
                        scope.launch {
                            isLoading = true
                            consoleOutput = "A executar..."

                            // Salva antes de executar (apenas no caminho exato)
                            if (currentFileItem != null) {
                                FileManager.writeFile(currentFileItem!!, code)
                            } else {
                                FileManager.writeFileAtPath(FileManager.getDefaultFilePath(), code)
                            }

                            if (!binaryReady) {
                                consoleOutput = "❌ Motor MambaScript não descarregado.\nVai ao menu → Motor para descarregar."
                                isLoading = false
                                return@launch
                            }

                            // Execução LOCAL 🚀
                            val scriptPath = currentFileItem?.path
                                ?: FileManager.getDefaultFilePath()

                            val result = runner.executar(
                                scriptPath = scriptPath,
                                workingDir = FileManager.getCurrentPath()
                            )

                            result.onSuccess { output ->
                                consoleOutput = output
                            }.onFailure { error ->
                                consoleOutput = "❌ ${error.message}"
                            }

                            isLoading = false
                        }
                    },
                    onOpenDrawer = { scope.launch { drawerState.open() } }
                )
            }

            AppScreen.EXPLORER -> {
                FileExplorerScreen(
                    onFileSelected = { fileItem ->
                        scope.launch {
                            val content = FileManager.readFile(fileItem)
                            if (content != null) {
                                currentFileItem = fileItem
                                code = content
                                currentScreen = AppScreen.EDITOR
                                allFiles = FileManager.listAllMambaFiles()
                            }
                        }
                    },
                    onNavigateBack = {
                        currentScreen = AppScreen.EDITOR
                    }
                )
            }

            AppScreen.PACOTES -> {
                PackageManagerScreen(
                    onNavigateBack = {
                        currentScreen = AppScreen.EDITOR
                    }
                )
            }
        }
    }
}

// ======================== TELA DO EDITOR ========================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorView(
    currentFileItem: FileItem?,
    code: String,
    onCodeChange: (String) -> Unit,
    consoleOutput: String,
    isLoading: Boolean,
    binaryReady: Boolean,
    onSave: () -> Unit,
    onRun: () -> Unit,
    onClearConsole: () -> Unit,
    onOpenDrawer: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            currentFileItem?.name ?: "index.ms",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (currentFileItem != null) {
                            val relativePath = FileManager.getRelativePath(currentFileItem!!)
                            if (relativePath.isNotEmpty()) {
                                Text(
                                    relativePath,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, "Abrir menu")
                    }
                },
                actions = {
                    // Indicador de modo (local/API)                        if (binaryReady) {
                            Icon(
                                Icons.Default.OfflineBolt,
                                contentDescription = "Modo offline",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                    // Salvar
                    IconButton(onClick = onSave) {
                        Icon(Icons.Default.Save, "Salvar")
                    }

                    // Executar
                    IconButton(
                        onClick = onRun,
                        enabled = !isLoading
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Executar",
                            tint = if (isLoading) Color.Gray else Color(0xFF4CAF50),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            // Editor de código
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                MambaEditor(
                    code = code,
                    onCodeChange = onCodeChange,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Console
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                color = Color(0xFF1E1E1E)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Console",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                        if (consoleOutput.isNotEmpty()) {
                            TextButton(
                                onClick = onClearConsole,
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Icon(Icons.Default.Clear, null, modifier = Modifier.size(12.dp), tint = Color.Gray)
                                Spacer(Modifier.width(2.dp))
                                Text("Limpar", fontSize = 10.sp, color = Color.Gray)
                            }
                        }
                    }
                    HorizontalDivider(
                        color = Color.DarkGray,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = consoleOutput,
                            color = if (consoleOutput.contains("❌") || consoleOutput.contains("Erro"))
                                Color(0xFFFF5252) else Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(24.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
