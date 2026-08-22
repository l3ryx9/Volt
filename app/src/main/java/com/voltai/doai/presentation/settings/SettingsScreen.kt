package com.voltai.doai.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.voltai.doai.presentation.VoltColors

@Composable
fun SettingsScreen(
    onOpenQwenConnect: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val url by viewModel.url.collectAsState()
    val testing by viewModel.testing.collectAsState()
    val testResult by viewModel.testResult.collectAsState()
    val loggingOut by viewModel.loggingOut.collectAsState()
    val logoutResult by viewModel.logoutResult.collectAsState()
    val githubUsername by viewModel.githubUsername.collectAsState()
    val githubToken by viewModel.githubToken.collectAsState()
    val repoUrl by viewModel.repoUrl.collectAsState()
    val testingGithub by viewModel.testingGithub.collectAsState()
    val githubTestResult by viewModel.githubTestResult.collectAsState()
    val cloning by viewModel.cloning.collectAsState()
    val cloneResult by viewModel.cloneResult.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoltColors.Background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Serveur Qwen distant (Google Colab)",
            color = VoltColors.Accent,
            style = MaterialTheme.typography.subtitle1
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = url,
            onValueChange = viewModel::setUrl,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("URL publique du tunnel Colab") },
            placeholder = { Text("https://xxxx-xxxx.trycloudflare.com") },
            singleLine = true,
            colors = TextFieldDefaults.outlinedTextFieldColors(
                backgroundColor = VoltColors.Input,
                textColor = VoltColors.Text,
                placeholderColor = VoltColors.MutedText,
                focusedBorderColor = VoltColors.Accent,
                unfocusedBorderColor = VoltColors.AccentBright,
                cursorColor = VoltColors.Accent
            ),
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = viewModel::testConnection,
            enabled = !testing && url.isNotBlank(),
            colors = ButtonDefaults.buttonColors(backgroundColor = VoltColors.Accent),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(
                if (testing) "Vérification..." else "Tester la connexion",
                color = VoltColors.Background
            )
        }

        if (testResult != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                backgroundColor = VoltColors.Input,
                elevation = 2.dp
            ) {
                Text(
                    text = testResult.orEmpty(),
                    color = if (testResult?.startsWith("✓") == true) VoltColors.AccentBright else VoltColors.Error,
                    style = MaterialTheme.typography.caption,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onOpenQwenConnect,
            colors = ButtonDefaults.buttonColors(backgroundColor = VoltColors.Accent),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Qwen Connect — connexion auto au serveur", color = VoltColors.Background)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = viewModel::logout,
            enabled = !loggingOut,
            colors = ButtonDefaults.buttonColors(backgroundColor = VoltColors.Input),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(
                if (loggingOut) "Déconnexion..." else "Se déconnecter (Google/Colab)",
                color = VoltColors.Error
            )
        }

        if (logoutResult != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                backgroundColor = VoltColors.Input,
                elevation = 2.dp
            ) {
                Text(
                    text = logoutResult.orEmpty(),
                    color = if (logoutResult?.startsWith("✓") == true) VoltColors.AccentBright else VoltColors.Error,
                    style = MaterialTheme.typography.caption,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "GitHub",
            color = VoltColors.Accent,
            style = MaterialTheme.typography.subtitle1
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = githubUsername,
            onValueChange = viewModel::setGithubUsername,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Nom d'utilisateur GitHub") },
            singleLine = true,
            colors = TextFieldDefaults.outlinedTextFieldColors(
                backgroundColor = VoltColors.Input,
                textColor = Color.White,
                placeholderColor = Color.Gray,
                focusedBorderColor = VoltColors.Accent,
                unfocusedBorderColor = VoltColors.AccentBright,
                cursorColor = VoltColors.Accent
            ),
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = githubToken,
            onValueChange = viewModel::setGithubToken,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Token GitHub (classic, repo scope)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                backgroundColor = VoltColors.Input,
                textColor = Color.White,
                placeholderColor = Color.Gray,
                focusedBorderColor = VoltColors.Accent,
                unfocusedBorderColor = VoltColors.AccentBright,
                cursorColor = VoltColors.Accent
            ),
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = viewModel::testGithubConnection,
            enabled = !testingGithub,
            colors = ButtonDefaults.buttonColors(backgroundColor = VoltColors.Accent),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(if (testingGithub) "Vérification..." else "Tester la connexion GitHub", color = Color.White)
        }

        if (githubTestResult != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                backgroundColor = VoltColors.Input,
                elevation = 2.dp
            ) {
                Text(
                    text = githubTestResult.orEmpty(),
                    color = if (githubTestResult?.startsWith("✓") == true) VoltColors.AccentBright else VoltColors.Error,
                    style = MaterialTheme.typography.caption,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = repoUrl,
            onValueChange = viewModel::setRepoUrl,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("URL du dépôt à cloner") },
            placeholder = { Text("https://github.com/owner/repo") },
            singleLine = true,
            colors = TextFieldDefaults.outlinedTextFieldColors(
                backgroundColor = VoltColors.Input,
                textColor = VoltColors.Text,
                placeholderColor = VoltColors.MutedText,
                focusedBorderColor = VoltColors.Accent,
                unfocusedBorderColor = VoltColors.AccentBright,
                cursorColor = VoltColors.Accent
            ),
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = viewModel::cloneRepo,
            enabled = !cloning && repoUrl.isNotBlank(),
            colors = ButtonDefaults.buttonColors(backgroundColor = VoltColors.Accent),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(if (cloning) "Clonage..." else "Cloner le dépôt", color = VoltColors.Background)
        }

        if (cloneResult != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                backgroundColor = VoltColors.Input,
                elevation = 2.dp
            ) {
                Text(
                    text = cloneResult.orEmpty(),
                    color = if (cloneResult?.startsWith("✓") == true) VoltColors.AccentBright else VoltColors.Error,
                    style = MaterialTheme.typography.caption,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}