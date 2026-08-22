package com.voltai.doai.presentation.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.voltai.doai.domain.interfaces.EnvironmentStatus
import com.voltai.doai.domain.models.CommandResult
import com.voltai.doai.presentation.VoltColors

@Composable
fun ToolsScreen(
    viewModel: ToolsViewModel = viewModel()
) {
    val status by viewModel.status.collectAsState()
    val envStatus by viewModel.envStatus.collectAsState()
    val results by viewModel.results.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val testCommand by viewModel.testCommand.collectAsState()
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(VoltColors.Background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
            item {
                Text(
                    text = status,
                    color = if (busy) VoltColors.Accent else VoltColors.MutedText,
                    style = MaterialTheme.typography.caption
                )
            }
            item {
                EnvironmentStatusCard(
                    envStatus = envStatus,
                    busy = busy,
                    onRefresh = viewModel::refreshStatus
                )
            }

            item {
                Text(
                    text = "Environnement Linux",
                    color = VoltColors.Accent,
                    style = MaterialTheme.typography.subtitle2,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                ActionRow(
                    busy = busy,
                    onInit = viewModel::initializeEnvironment,
                    onBasicPackages = viewModel::installBasicPackages
                )
            }

            item {
                ActionRowUbuntu(
                    busy = busy,
                    onProot = viewModel::installProotDistro,
                    onUbuntu = viewModel::installUbuntu,
                    onSetup = viewModel::setupUbuntu,
                    onTestRuntime = { viewModel.testRuntime(appContext) },
                    onRepair = { viewModel.repairEnvironment(appContext) }
                )
            }

            item {
                TestCommandCard(
                    text = testCommand,
                    busy = busy,
                    onTextChange = viewModel::onTestCommandChange,
                    onExecute = viewModel::executeTestCommand,
                    onAnalyze = { viewModel.analyzeAndExecute(testCommand) }
                )
            }

            item {
                Text(
                    text = "Journal d'exécution",
                    color = VoltColors.Accent,
                    style = MaterialTheme.typography.subtitle2,
                    fontWeight = FontWeight.Bold
                )
            }

            if (results.isEmpty()) {
                item {
                    Text(
                        text = "Aucune commande exécutée",
                        color = Color.Gray,
                        style = MaterialTheme.typography.body2
                    )
                }
            }

            items(results) { result ->
                CommandResultCard(result = result)
            }
    }
}

@Composable
private fun EnvironmentStatusCard(
    envStatus: EnvironmentStatus,
    busy: Boolean,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = VoltColors.Input,
        elevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Statut de l'environnement",
                    color = Color.White,
                    style = MaterialTheme.typography.subtitle1,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onRefresh, enabled = !busy) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Actualiser",
                        tint = VoltColors.Accent
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            StatusRow(label = "Runtime Termux", value = envStatus.termuxInitialized)
            StatusRow(label = "Packages de base", value = envStatus.basicPackagesInstalled)
            StatusRow(label = "proot-distro", value = envStatus.prootDistroInstalled)
            StatusRow(label = "Ubuntu 24.04", value = envStatus.ubuntuInstalled)
            StatusRow(label = "Ubuntu prêt", value = envStatus.ubuntuReady)
        }
    }
}

@Composable
private fun StatusRow(label: String, value: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = Color.LightGray,
            style = MaterialTheme.typography.body2
        )
        Text(
            text = if (value) "✓ Installé" else "✗ Non détecté",
            color = if (value) VoltColors.Accent else VoltColors.MutedText,
            style = MaterialTheme.typography.body2,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ActionRow(
    busy: Boolean,
    onInit: () -> Unit,
    onBasicPackages: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onInit,
            enabled = !busy,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            border = ButtonDefaults.outlinedBorder.copy(
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(VoltColors.Accent, VoltColors.AccentBright)
                )
            ),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = VoltColors.Accent)
        ) {
            Text("Vérifier env.", color = VoltColors.Accent)
        }
        Button(
            onClick = onBasicPackages,
            enabled = !busy,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = VoltColors.Accent)
        ) {
            Text("Packages de base", color = Color.White)
        }
    }
}

@Composable
private fun ActionRowUbuntu(
    busy: Boolean,
    onProot: () -> Unit,
    onUbuntu: () -> Unit,
    onSetup: () -> Unit,
    onTestRuntime: () -> Unit,
    onRepair: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onProot,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = VoltColors.Accent)
        ) {
            Text("Installer proot-distro", color = Color.White)
        }
        Button(
            onClick = onUbuntu,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = VoltColors.Accent)
        ) {
            Text("Installer Ubuntu 24.04", color = Color.White)
        }
        OutlinedButton(
            onClick = onSetup,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = ButtonDefaults.outlinedBorder.copy(
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(VoltColors.Accent, VoltColors.AccentBright)
                )
            ),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = VoltColors.Accent)
        ) {
            Text("Configurer Ubuntu", color = VoltColors.Accent)
        }
        OutlinedButton(
            onClick = onTestRuntime,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = ButtonDefaults.outlinedBorder.copy(
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(VoltColors.AccentBright, VoltColors.Accent)
                )
            ),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = VoltColors.Accent)
        ) {
            Text("Tester le runtime embarqué", color = VoltColors.Accent)
        }
        Button(
            onClick = onRepair,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = Color(0xFFB45309),
                contentColor = Color.White
            )
        ) {
            Text("Réparer l'environnement", color = Color.White)
        }
    }
}

@Composable
private fun TestCommandCard(
    text: String,
    busy: Boolean,
    onTextChange: (String) -> Unit,
    onExecute: () -> Unit,
    onAnalyze: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = VoltColors.Input,
        elevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Test de commande",
                color = Color.White,
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Ex: pkg install python") },
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    backgroundColor = Color(0xFF1A1D26),
                    textColor = Color.White,
                    placeholderColor = Color.Gray,
                    focusedBorderColor = VoltColors.Accent,
                    unfocusedBorderColor = VoltColors.AccentBright,
                    cursorColor = VoltColors.Accent
                ),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onExecute,
                    enabled = !busy && text.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = VoltColors.Accent)
                ) {
                    Text("Exécuter", color = Color.White)
                }
                OutlinedButton(
                    onClick = onAnalyze,
                    enabled = !busy && text.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = ButtonDefaults.outlinedBorder.copy(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(VoltColors.Accent, VoltColors.AccentBright)
                        )
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VoltColors.Accent)
                ) {
                    Text("Analyser", color = VoltColors.Accent)
                }
            }
        }
    }
}

@Composable
private fun CommandResultCard(result: CommandResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        backgroundColor = VoltColors.Input,
        elevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "\$ ${result.command}",
                color = VoltColors.Accent,
                style = MaterialTheme.typography.body2,
                fontWeight = FontWeight.Bold
            )
            if (result.output.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = result.output,
                    color = Color.White,
                    style = MaterialTheme.typography.body2
                )
            }
            result.error?.let {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = it,
                    color = Color(0xFFFF6B6B),
                    style = MaterialTheme.typography.body2
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "[exit: ${result.exitCode}] - ${result.duration} ms",
                color = Color.Gray,
                style = MaterialTheme.typography.caption
            )
        }
    }
}
