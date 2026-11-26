package com.example.nettoyeur

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.security.MessageDigest

/** Un fichier trouvé dans un dossier choisi par l'utilisateur. */
data class FileItem(
    val uri: Uri,
    val name: String,
    val size: Long,
    val path: String
)

/** Un groupe de fichiers dont le contenu est identique. */
data class DuplicateGroup(
    val hash: String,
    val files: List<FileItem>
)

/**
 * Analyse les dossiers choisis via le sélecteur Android (Storage Access Framework).
 * Ne supprime rien seul : la suppression est déclenchée par l'interface après confirmation.
 */
object DuplicateScanner {

    /** Liste tous les fichiers de tous les dossiers choisis, sous-dossiers inclus. */
    fun collect(context: Context, roots: List<Uri>): List<FileItem> {
        val result = mutableListOf<FileItem>()
        for (root in roots) {
            val dir = DocumentFile.fromTreeUri(context, root) ?: continue
            walk(dir, dir.name ?: "dossier", result)
        }
        return result
    }

    private fun walk(dir: DocumentFile, path: String, out: MutableList<FileItem>) {
        for (child in dir.listFiles()) {
            val name = child.name ?: continue
            val childPath = "$path/$name"
            when {
                child.isDirectory -> walk(child, childPath, out)
                child.isFile -> out.add(FileItem(child.uri, name, child.length(), childPath))
            }
        }
    }

    /**
     * Repère les doublons en deux étapes :
     * 1. on ne calcule l'empreinte que pour les fichiers ayant la même taille (rapide) ;
     * 2. on regroupe les fichiers dont l'empreinte SHA-256 est identique (contenu identique).
     */
    fun findDuplicates(context: Context, files: List<FileItem>): List<DuplicateGroup> {
        val candidates = files
            .filter { it.size > 0 }
            .groupBy { it.size }
            .filterValues { it.size > 1 }
            .values
            .flatten()

        val byHash = mutableMapOf<String, MutableList<FileItem>>()
        for (item in candidates) {
            val hash = sha256(context, item.uri) ?: continue
            byHash.getOrPut(hash) { mutableListOf() }.add(item)
        }

        return byHash
            .filterValues { it.size > 1 }
            .map { (hash, list) -> DuplicateGroup(hash, list.sortedBy { it.path }) }
            .sortedByDescending { it.files.first().size * (it.files.size - 1) }
    }

    /** Empreinte SHA-256 d'un fichier, ou null si le fichier ne peut pas être lu. */
    private fun sha256(context: Context, uri: Uri): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            } ?: return null
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }

    /** Supprime un fichier. Retourne true si la suppression a réussi. */
    fun delete(context: Context, uri: Uri): Boolean {
        return try {
            DocumentFile.fromSingleUri(context, uri)?.delete() ?: false
        } catch (e: Exception) {
            false
        }
    }
}
