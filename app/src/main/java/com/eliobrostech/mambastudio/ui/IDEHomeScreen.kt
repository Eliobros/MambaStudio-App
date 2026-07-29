package com.eliobrostech.mambastudio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eliobrostech.mambastudio.api.MambaApiService
import com.eliobrostech.mambastudio.api.MambaRequest
import com.eliobrostech.mambastudio.storage.StorageManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IDEHomeScreen() {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    var currentFileName by remember { mutableStateOf("novo_ficheiro.ms") }
    var code by remember { mutableStateOf("# Bem-vindo ao MambaStudio!\nescreva \"Olá, Mundo! 🐍\"") }
    var consoleOutput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var fileList by remember { mutableStateOf(StorageManager.listFiles()) }

    val apiService = remember { MambaApiService.create() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text("Meus Ficheiros", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
                HorizontalDivider()
                LazyColumn {
                    items(fileList) { fileName ->
                        NavigationDrawerItem(
                            label = { Text(fileName) },
                            selected = fileName == currentFileName,
                            onClick = {
                                scope.launch {
                                    val content = StorageManager.readFile(fileName)
                                    if (content != null) {
                                        currentFileName = fileName
                                        code = content
                                    }
                                    drawerState.close()
                                }
                            }
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(currentFileName, fontSize = 16.sp) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            if (StorageManager.saveFile(currentFileName, code)) {
                                fileList = StorageManager.listFiles()
                            }
                        }) {
                            Icon(Icons.Default.Save, contentDescription = "Salvar")
                        }
                        IconButton(
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    consoleOutput = "A executar..."
                                    try {
                                        val response = apiService.executarCodigo(MambaRequest(code))
                                        consoleOutput = response.saida ?: response.erro ?: "Sem resposta do servidor."
                                    } catch (e: Exception) {
                                        consoleOutput = "Erro de rede: ${e.message}"
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                            enabled = !isLoading
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Executar", tint = if (isLoading) Color.Gray else Color.Green)
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                // Editor
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    MambaEditor(
                        code = code,
                        onCodeChange = { code = it },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                // Console
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(Color(0xFF1E1E1E))
                        .padding(8.dp)
                ) {
                    Text("Console", color = Color.Gray, fontSize = 12.sp)
                    HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 4.dp))
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = consoleOutput,
                            color = if (consoleOutput.contains("Erro")) Color.Red else Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center).size(32.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            }
        }
    }
}
