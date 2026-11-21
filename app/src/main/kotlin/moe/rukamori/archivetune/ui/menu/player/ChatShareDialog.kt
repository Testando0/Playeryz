package moe.rukamori.archivetune.ui.menu.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.chat.ChatConversation
import moe.rukamori.archivetune.chat.ChatViewModel

@Composable
internal fun ChatShareDialog(
    title: String,
    artist: String,
    album: String?,
    trackId: String,
    coverUrl: String?,
    onDismiss: () -> Unit,
) {
    val viewModel: ChatViewModel = hiltViewModel()
    val state = viewModel.state.collectAsStateWithLifecycle().value
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    val conversations = state.conversations
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.independent_chat_send_song_title)) },
        text = {
            Column {
                Text(stringResource(R.string.independent_chat_send_song_description, title), modifier = Modifier.padding(bottom = 8.dp))
                if (!state.loggedIn) {
                    Text(stringResource(R.string.independent_chat_login_to_send_song))
                } else if (conversations.isEmpty()) {
                    Text(stringResource(R.string.independent_chat_no_conversations))
                } else {
                    LazyColumn {
                        items(conversations, key = { it.id }) { conversation ->
                            val checked = conversation.id in selectedIds
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        selectedIds = if (it && selectedIds.size < 5) selectedIds + conversation.id else selectedIds - conversation.id
                                    },
                                    enabled = checked || selectedIds.size < 5,
                                )
                                Text(conversation.displayTitle(state.username), modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.sendTrackToConversations(selectedIds.toList(), title, artist, album, trackId, coverUrl)
                    onDismiss()
                },
                enabled = state.loggedIn && selectedIds.isNotEmpty() && trackId.isNotBlank(),
            ) { Text(stringResource(R.string.independent_chat_send)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun ChatConversation.displayTitle(selfUsername: String): String {
    title?.takeIf { it.isNotBlank() }?.let { return it }
    return members.firstOrNull { it.username != selfUsername }?.let { "@${it.username}" } ?: "Conversation"
}
