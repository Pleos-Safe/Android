package io.issac.pleosandroid.core.cardui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import io.issac.pleosandroid.core.domain.cards.AlbumSnapshot

@Composable
fun CardAlbumContent(
    album: AlbumSnapshot?,
    loadFailed: Boolean,
    message: String?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier,
    content: @Composable (AlbumSnapshot) -> Unit,
) {
    if (album == null) {
        CollectionStatusScreen(
            if (loadFailed) "카드 보관함을 불러오지 못했어요." else "카드 보관함을 불러오고 있어요",
            modifier,
            onRetry = onRetry.takeIf { loadFailed },
        )
        return
    }
    key(album.generation) { content(album) }
    if (message != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            text = { Text(message) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("확인") } },
        )
    }
}
