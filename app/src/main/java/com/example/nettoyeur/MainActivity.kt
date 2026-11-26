package com.example.nettoyeur

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NettoyeurScreen()
                }
            }
        }
    }
}

@Composable
fun NettoyeurScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var folders by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var groups by remember { mutableStateOf<List<DuplicateGroup>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<Uri>>(emptySet()) }
    var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Ajoutez un ou plusieurs dossiers à analyser.") }

    // Sélecteur de dossier Android : l'application ne voit que les dossiers choisis.
    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null && uri !in folders) {
            folders = folders + uri
        }
    }

    fun scan() {
        scope.launch {
            busy = true
            status = "Analyse en cours…"
            groups = emptyList()
            selected = emptySet()

            val found = withContext(Dispatchers.IO) {
                val files = DuplicateScanner.collect(context, folders)
                DuplicateScanner.findDuplicates(context, files)
            }

            groups = found
            // Par défaut, on coche tous les doublons SAUF le premier de chaque groupe.
            selected = found.flatMap { g -> g.files.drop(1).map { it.uri } }.toSet()
            status = if (found.isEmpty()) {
                "Aucun doublon trouvé."
            } else {
                "${found.size} groupe(s) de doublons trouvé(s). Vérifiez la sélection avant de supprimer."
            }
            busy = false
        }
    }

    fun toggle(group: DuplicateGroup, uri: Uri) {
        if (uri in selected) {
            selected = selected - uri
        } else {
            // Garde toujours au moins un fichier non coché dans chaque groupe.
            val stillKept = group.files.count { it.uri !in selected && it.uri != uri }
            if (stillKept == 0) {
                status = "Au moins un fichier par groupe doit être conservé."
            } else {
                selected = selected + uri
            }
        }
    }

    fun deleteSelected() {
        scope.launch {
            busy = true
            val toDelete = selected.toList()
            val deleted = withContext(Dispatchers.IO) {
                toDelete.filter { DuplicateScanner.delete(context, it) }.toSet()
            }
            groups = groups
                .map { g -> g.copy(files = g.files.filter { it.uri !in deleted }) }
                .filter { it.files.size > 1 }
            selected = selected - deleted
            status = "${deleted.size} fichier(s) supprimé(s), ${toDelete.size - deleted.size} échec(s)."
            busy = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Nettoyeur de doublons",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(8.dp))
        Text(text = status, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { pickFolder.launch(null) },
                enabled = !busy
            ) { Text("Ajouter un dossier") }

            Button(
                onClick = { scan() },
                enabled = !busy && folders.isNotEmpty()
            ) { Text("Analyser") }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = "Dossiers choisis : ${folders.size}",
            style = MaterialTheme.typography.bodySmall
        )

        if (busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        }

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            groups.forEach { group ->
                item(key = "header-${group.hash}") {
                    Text(
                        text = "${group.files.size} fichiers identiques, " +
                            formatSize(group.files.first().size),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }
                items(group.files, key = { it.uri.toString() }) { file ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = file.uri in selected,
                            onCheckedChange = { toggle(group, file.uri) },
                            enabled = !busy
                        )
                        Column {
                            Text(text = file.name, style = MaterialTheme.typography.bodyLarge)
                            Text(text = file.path, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        if (selected.isNotEmpty()) {
            Button(
                onClick = { confirmDelete = true },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Supprimer ${selected.size} fichier(s) sélectionné(s)")
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Confirmer la suppression") },
            text = {
                Text("${selected.size} fichier(s) seront supprimés définitivement. Cette action est irréversible.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    deleteSelected()
                }) { Text("Supprimer") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Annuler") }
            }
        )
    }
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f Mo".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.0f Ko".format(bytes / 1024.0)
    else -> "$bytes o"
}
