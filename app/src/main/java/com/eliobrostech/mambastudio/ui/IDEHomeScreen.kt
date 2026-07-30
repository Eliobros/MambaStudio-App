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
import com.eliobrostech.mambastudio.runner.NodeJsRunner
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

    // NodeJsRunner para execução local via Node.js embutido
    val runner = remember { NodeJsRunner(context) }
    var nodeReady by remember { mutableStateOf(false) }
    var isStartingNode by remember { mutableStateOf(false) }
    var showNodeDialog by remember { mutableStateOf(true) }

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

        // Inicia Node.js em background
        isStartingNode = true
        runner.start().onSuccess {
            nodeReady = true
            showNodeDialog = false
        }.onFailure { error ->
            android.util.Log.e("IDEHomeScreen", "❌ Node.js: ${error.message}")
            // Mesmo sem Node.js, o app funciona (apenas sem execução)
            showNodeDialog = false
        }
        isStartingNode = false
    }

    // Dialog de inicialização do Node.js
    if (showNodeDialog && isStartingNode) {
        AlertDialog(
            onDismissRequest = { /* Não pode dispensar enquanto carrega */ },
            icon = { CircularProgressIndicator(modifier = Modifier.size(24.dp)) },
            title = { Text("A iniciar motor...") },
            text = {
                Text(
                    "A preparar o motor MambaScript (Node.js embutido). Isto leva apenas alguns segundos.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {},
            dismissButton = {}
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

                // Estado do motor
                Text(
                    "Motor",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                NavigationDrawerItem(
                    icon = {
                        Icon(
                            if (nodeReady) Icons.Default.CheckCircle else Icons.Default.Sync,
                            null,
                            tint = if (nodeReady) Color(0xFF4CAF50) else Color(0xFFFFA000)
                        )
                    },
                    label = {
                        Text(
                            if (nodeReady) "Node.js v18 (embutido)" else if (isStartingNode) "A iniciar..." else "Pronto (offline)",
                            fontSize = 13.sp
                        )
                    },
                    selected = false,
                    onClick = {},
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
                    nodeReady = nodeReady,
                    onSave = {
                        scope.launch {
                            if (currentFileItem != null) {
                                FileManager.writeFile(currentFileItem!!, code)
                                allFiles = FileManager.listAllMambaFiles()
                            } else {
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

                            // Salva antes de executar
                            if (currentFileItem != null) {
                                FileManager.writeFile(currentFileItem!!, code)
                            } else {
                                FileManager.writeFileAtPath(FileManager.getDefaultFilePath(), code)
                            }

                            if (!nodeReady) {
                                consoleOutput = "❌ Motor Node.js não está pronto.\nA aguardar inicialização..."
                                isLoading = false
                                return@launch
                            }

                            // Execução via Node.js embutido 🚀
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
    nodeReady: Boolean,
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
                    // Indicador de modo local
                    if (nodeReady) {
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
