package com.voltai.doai.presentation.files

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.voltai.doai.domain.models.ArchiveProgress
import com.voltai.doai.domain.models.FileEntry
import com.voltai.doai.presentation.VoltColors
import java.io.File

@Composable
fun FilesScreen(viewModel: FilesViewModel = viewModel()) {
    val state = viewModel.state

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VoltColors.Background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            FilesHeader(
                currentPath = state.currentPath,
                onUp = { viewModel.navigateUp() },
                onWorkspace = { viewModel.loadWorkspace() },
                onRoot = { viewModel.goToRoot() },
                onDownloads = { viewModel.goToDownloads() },
                onReset = { viewModel.resetWorkspace() }
            )

            SearchBar(
                query = state.searchQuery,
                onQueryChange = { viewModel.onSearchQueryChange(it) },
                onSearch = { viewModel.search() }
            )

            state.progress?.let { progress ->
                ArchiveProgressView(progress)
            }

            if (state.searchResults.isNotEmpty()) {
                Text(
                    text = "Résultats (${state.searchResults.size})",
                    color = VoltColors.Accent,
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                val entries = if (state.searchResults.isNotEmpty()) state.searchResults else state.entries
                items(entries, key = { it.path }) { entry ->
                    FileRow(
                        entry = entry,
                        onClick = {
                            if (state.searchResults.isEmpty()) viewModel.openFile(entry.path) else viewModel.navigateTo(entry.path)
                        },
                        onExtract = { viewModel.extractArchive(entry.path) },
                        onCreateArchive = { viewModel.createArchive(entry.path) },
                        onDelete = { viewModel.deleteFile(entry.path) }
                    )
                }
            }
        }

        state.message?.let { message ->
            LaunchedEffect(message) {
                kotlinx.coroutines.delay(3500)
                viewModel.clearMessage()
            }
            Text(
                text = message,
                color = Color.White,
                style = MaterialTheme.typography.body2,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(VoltColors.ElevatedSurface, MaterialTheme.shapes.medium)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }

        state.fileContent?.let { content ->
            FileContentDialog(
                content = content,
                onDismiss = { viewModel.closeFile() }
            )
        }
    }
}

@Composable
private fun FilesHeader(
    currentPath: String,
    onUp: () -> Unit,
    onWorkspace: () -> Unit,
    onRoot: () -> Unit,
    onDownloads: () -> Unit,
    onReset: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = currentPath,
                color = VoltColors.MutedText,
                style = MaterialTheme.typography.caption,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )
            IconButton(onClick = onUp) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Dossier parent", tint = Color.White)
            }
            IconButton(onClick = onWorkspace) {
                Icon(Icons.Default.Home, contentDescription = "Workspace", tint = Color.White)
            }
            IconButton(onClick = onRoot) {
                Icon(Icons.Default.Folder, contentDescription = "Racine", tint = Color.White)
            }
            IconButton(onClick = onDownloads) {
                Icon(Icons.Default.Download, contentDescription = "Téléchargements", tint = Color.White)
            }
            IconButton(onClick = onReset) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Réinitialiser", tint = Color.White)
            }
        }
        Divider(color = VoltColors.Divider)
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Rechercher un fichier...", color = Color.Gray) },
            singleLine = true,
            modifier = Modifier.weight(1f),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = VoltColors.Accent,
                unfocusedBorderColor = VoltColors.Divider,
                cursorColor = VoltColors.Accent,
                textColor = Color.White,
                backgroundColor = VoltColors.Input
            )
        )
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = onSearch,
            colors = ButtonDefaults.buttonColors(
                backgroundColor = VoltColors.Accent,
                contentColor = VoltColors.Background
            )
        ) {
            Icon(Icons.Default.Search, contentDescription = "Rechercher", modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Chercher")
        }
    }
}

@Composable
private fun ArchiveProgressView(progress: ArchiveProgress) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(VoltColors.Input)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (progress.operation == "EXTRACT") "Extraction..." else "Création...",
                color = VoltColors.Accent,
                style = MaterialTheme.typography.body2
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (progress.isIndeterminate) "..." else "${progress.processedFiles}/${progress.totalFiles} fichiers",
                color = Color.Gray,
                style = MaterialTheme.typography.caption
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = if (progress.isIndeterminate) 0f else progress.percentage / 100f,
            modifier = Modifier.fillMaxWidth(),
            color = VoltColors.Accent,
            backgroundColor = VoltColors.ElevatedSurface
        )
        Text(
            text = progress.currentFile,
            color = Color.Gray,
            style = MaterialTheme.typography.caption,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (progress.remainingMs > 0 && !progress.isIndeterminate) {
            Text(
                text = "${progress.percentage.toInt()}% - reste ${progress.remainingMs / 1000}s",
                color = Color.Gray,
                style = MaterialTheme.typography.caption
            )
        }
    }
}

@Composable
private fun FileRow(
    entry: FileEntry,
    onClick: () -> Unit,
    onExtract: () -> Unit,
    onCreateArchive: () -> Unit,
    onDelete: () -> Unit
) {
    val isArchive = entry.extension in setOf("apk", "aab", "zip", "jar", "rar", "7z", "tar", "gz", "tgz", "bz2")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (entry.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
            contentDescription = null,
            tint = if (entry.isDirectory) VoltColors.AccentBright else VoltColors.Accent
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                color = Color.White,
                style = MaterialTheme.typography.body2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!entry.isDirectory) {
                Text(
                    text = if (entry.extension.isNotEmpty()) "${entry.extension.uppercase()} · ${entry.displaySize}" else entry.displaySize,
                    color = Color.Gray,
                    style = MaterialTheme.typography.caption
                )
            }
        }
        if (isArchive) {
            IconButton(onClick = onExtract) {
                Icon(Icons.Default.Unarchive, contentDescription = "Extraire", tint = VoltColors.Accent)
            }
        }
        if (entry.isDirectory && !entry.path.endsWith("/workspace") && entry.path.contains("/workspace")) {
            IconButton(onClick = onCreateArchive) {
                Icon(Icons.Default.Archive, contentDescription = "Créer une archive", tint = VoltColors.Accent)
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = Color.Red.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun FileContentDialog(content: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Contenu du fichier", color = Color.White) },
        text = {
            Box(
                modifier = Modifier
                    .height(320.dp)
                    .background(VoltColors.Input)
                    .padding(8.dp)
            ) {
                Text(
                    text = content,
                    color = VoltColors.Text,
                    style = MaterialTheme.typography.body2,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 24
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer", color = VoltColors.Accent)
            }
        },
        backgroundColor = VoltColors.Surface
    )
}
