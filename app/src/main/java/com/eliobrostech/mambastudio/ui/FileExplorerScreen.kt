package com.eliobrostech.mambastudio.ui

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eliobrostech.mambastudio.storage.FileItem
import com.eliobrostech.mambastudio.storage.FileManager
import kotlinx.coroutines.launch

/**
 * Tela de explorador de ficheiros do MambaStudio.
 *
 * Funcionalidades:
 * - Navegação por pastas
 * - Criar ficheiros/pastas
 * - Renomear e apagar
 * - Clique em ficheiro .ms para abrir no editor
 * - Breadcrumb do caminho atual
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileExplorerScreen(
    onFileSelected: (FileItem) -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Estado do explorador
    var currentItems by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var currentPath by remember { mutableStateOf(FileManager.getCurrentPath()) }
    var isAtRoot by remember { mutableStateOf(FileManager.isAtRoot()) }

    // Estado dos dialogs
    var showCreateDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var createType by remember { mutableStateOf("file") } // "file" ou "folder"
    var selectedItem by remember { mutableStateOf<FileItem?>(null) }
    var dialogName by remember { mutableStateOf("") }

    // Carrega conteúdo da pasta atual
    fun refreshContents() {
        currentItems = FileManager.listContents()
        currentPath = FileManager.getCurrentPath()
        isAtRoot = FileManager.isAtRoot()
    }

    // Carrega na inicialização
    LaunchedEffect(Unit) {
        FileManager.init()
        refreshContents()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Explorador", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = formatPath(currentPath),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Voltar")
                    }
                },
                actions = {
                    // Botão voltar pasta
                    IconButton(
                        onClick = {
                            FileManager.navigateUp()
                            refreshContents()
                        },
                        enabled = !isAtRoot
                    ) {
                        Icon(Icons.Default.ArrowUpward, "Pasta anterior")
                    }
                    // Botão raiz
                    IconButton(onClick = {
                        FileManager.navigateToRoot()
                        refreshContents()
                    }) {
                        Icon(Icons.Default.Home, "Raiz")
                    }
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallFloatingActionButton(
                    onClick = { createType = "folder"; showCreateDialog = true },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Default.CreateNewFolder, "Nova Pasta")
                }
                FloatingActionButton(
                    onClick = { createType = "file"; showCreateDialog = true }
                ) {
                    Icon(Icons.Default.NoteAdd, "Novo Ficheiro")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Breadcrumb de navegação
            BreadcrumbBar(
                currentPath = currentPath,
                onNavigate = { path ->
                    FileManager.navigateToPath(path)
                    refreshContents()
                }
            )

            if (currentItems.isEmpty()) {
                // Estado vazio
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.FolderOff,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Pasta vazia",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Toque no + para criar um ficheiro",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                // Lista de ficheiros
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(currentItems, key = { it.path }) { item ->
                        FileItemRow(
                            item = item,
                            onClick = {
                                if (item.isDirectory) {
                                    FileManager.navigateTo(item)
                                    refreshContents()
                                } else {
                                    onFileSelected(item)
                                }
                            },
                            onRename = {
                                selectedItem = item
                                dialogName = item.name.removeSuffix(".ms")
                                showRenameDialog = true
                            },
                            onDelete = {
                                selectedItem = item
                                showDeleteDialog = true
                            }
                        )
                    }
                }
            }
        }
    }

    // ======================== DIALOGS ========================

    // Dialog criar ficheiro/pasta
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { dialogName = ""; showCreateDialog = false },
            title = {
                Text(if (createType == "file") "Novo Ficheiro" else "Nova Pasta")
            },
            text = {
                OutlinedTextField(
                    value = dialogName,
                    onValueChange = { dialogName = it },
                    label = { Text(if (createType == "file") "Nome do ficheiro" else "Nome da pasta") },
                    placeholder = { Text(if (createType == "file") "ex: meu_codigo" else "ex: projetos") },
                    suffix = if (createType == "file") {{ Text(".ms") }} else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = dialogName.trim()
                        if (name.isNotEmpty() && FileManager.isValidFileName(name)) {
                            val created = if (createType == "file") {
                                FileManager.createFile(name)
                            } else {
                                FileManager.createFolder(name)
                            }
                            if (created) {
                                refreshContents()
                                scope.launch {
                                    snackbarHostState.showSnackbar("${if (createType == "file") "Ficheiro" else "Pasta"} criado com sucesso!")
                                }
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar("❌ Já existe ou nome inválido.")
                                }
                            }
                            dialogName = ""
                            showCreateDialog = false
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar("❌ Nome inválido.")
                            }
                        }
                    }
                ) { Text("Criar") }
            },
            dismissButton = {
                TextButton(onClick = {
                    dialogName = ""
                    showCreateDialog = false
                }) { Text("Cancelar") }
            }
        )
    }

    // Dialog renomear
    if (showRenameDialog && selectedItem != null) {
        AlertDialog(
            onDismissRequest = { dialogName = ""; showRenameDialog = false },
            title = { Text("Renomear") },
            text = {
                OutlinedTextField(
                    value = dialogName,
                    onValueChange = { dialogName = it },
                    label = { Text("Novo nome") },
                    placeholder = { Text(selectedItem!!.name) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = dialogName.trim()
                        if (name.isNotEmpty() && FileManager.isValidFileName(name)) {
                            val success = FileManager.rename(selectedItem!!, name)
                            if (success) {
                                refreshContents()
                                scope.launch {
                                    snackbarHostState.showSnackbar("Renomeado com sucesso!")
                                }
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar("❌ Erro ao renomear.")
                                }
                            }
                            dialogName = ""
                            showRenameDialog = false
                        }
                    }
                ) { Text("Renomear") }
            },
            dismissButton = {
                TextButton(onClick = {
                    dialogName = ""
                    showRenameDialog = false
                }) { Text("Cancelar") }
            }
        )
    }

    // Dialog confirmar eliminação
    if (showDeleteDialog && selectedItem != null) {
        val isDir = selectedItem!!.isDirectory
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Apagar ${if (isDir) "pasta" else "ficheiro"}?") },
            text = {
                Column {
                    Text("Tens a certeza que queres apagar \"${selectedItem!!.name}\"?")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (isDir) "📁 Toda a pasta e o seu conteúdo serão apagados permanentemente."
                        else "📄 Este ficheiro será apagado permanentemente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Esta ação não pode ser desfeita.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val success = FileManager.delete(selectedItem!!)
                        if (success) {
                            refreshContents()
                            scope.launch {
                                snackbarHostState.showSnackbar("${selectedItem!!.name} apagado.")
                            }
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar("❌ Erro ao apagar.")
                            }
                        }
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Apagar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

// ======================== COMPONENTES ========================

/**
 * Linha individual para um ficheiro/pasta na lista.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileItemRow(
    item: FileItem,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            ),
        color = if (item.isMambaScript && !item.isDirectory)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ícone
            Text(
                text = item.icon,
                fontSize = 28.sp,
                modifier = Modifier.padding(end = 12.dp)
            )

            // Informações
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (item.isDirectory) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        if (item.isDirectory) append("Pasta")
                        else append(item.formattedSize)
                        if (item.formattedDate.isNotEmpty()) {
                            append(" • ${item.formattedDate}")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            // Menu de contexto
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Opções",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Renomear") },
                        onClick = {
                            showMenu = false
                            onRename()
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Apagar", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }
        }
    }
}

/**
 * Barra de breadcrumb para navegação rápida entre pastas.
 */
@Composable
fun BreadcrumbBar(
    currentPath: String,
    onNavigate: (String) -> Unit
) {
    val basePath = FileManager.getBasePath()

    if (!currentPath.startsWith(basePath)) return

    val relativePath = currentPath.removePrefix(basePath).trimStart('/')
    val parts = if (relativePath.isEmpty()) emptyList() else relativePath.split("/")

    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Raiz — apenas um label não clicável
            Text(
                "MambaStudio",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            // Partes do caminho
            var accumulatedPath = basePath
            for (part in parts) {
                if (part.isEmpty()) continue
                accumulatedPath = "$accumulatedPath/$part"
                Text(
                    text = "›",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
                TextButton(
                    onClick = { onNavigate(accumulatedPath) },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(part, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/**
 * Formata o caminho para exibição (encurta se muito longo).
 */
private fun formatPath(path: String): String {
    val base = FileManager.getBasePath()
    return if (path.startsWith(base)) {
        val rel = path.removePrefix(base).trimStart('/')
        if (rel.isEmpty()) "Raiz" else "files/$rel"
    } else path
}
