package moe.rukamori.archivetune.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.chat.ChatConversation
import moe.rukamori.archivetune.chat.ChatUiState
import moe.rukamori.archivetune.chat.ChatViewModel
import moe.rukamori.archivetune.models.ParsedIntentAction
import moe.rukamori.archivetune.ui.PlayerViewModel
import moe.rukamori.archivetune.ui.state.PlayerUiState

@Composable
internal fun ChatScreen(navController: NavController, viewModel: ChatViewModel = hiltViewModel()) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val playerState = playerViewModel.uiState.collectAsStateWithLifecycle().value
    val clipboard = LocalClipboardManager.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (state.loggedIn) {
                        Text(if (state.selected == null) "@${state.username}" else conversationTitle(state.selected, state.username), fontWeight = FontWeight.SemiBold)
                    } else Text(stringResource(R.string.independent_chat_title))
                },
                navigationIcon = {
                    IconButton(onClick = { if (state.selected != null) viewModel.clearSelection() else navController.navigateUp() }) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (state.loggedIn) {
                        if (state.selected == null) {
                            IconButton(onClick = { clipboard.setText(AnnotatedString("@${state.username}")) }) {
                                Icon(painterResource(R.drawable.copy), contentDescription = stringResource(R.string.independent_chat_copy))
                            }
                        }
                        IconButton(onClick = viewModel::openActionsMenu) {
                            Icon(painterResource(R.drawable.more_vert), contentDescription = stringResource(R.string.independent_chat_actions))
                        }
                        DropdownMenu(expanded = state.showActionsMenu, onDismissRequest = viewModel::closeActionsMenu) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.independent_chat_new_private)) }, onClick = { viewModel.closeActionsMenu(); viewModel.openCreateDialog(false) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.independent_chat_new_group)) }, onClick = { viewModel.closeActionsMenu(); viewModel.openCreateDialog(true) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.independent_chat_logout), color = MaterialTheme.colorScheme.error) }, onClick = { viewModel.closeActionsMenu(); viewModel.logout() })
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (!state.loggedIn) LoginContent(state, viewModel, Modifier.padding(padding))
        else ChatContent(state, viewModel, playerViewModel, playerState, Modifier.padding(padding))
    }
}

@Composable
private fun LoginContent(state: ChatUiState, viewModel: ChatViewModel, modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(if (state.registerMode) R.string.independent_chat_create_account else R.string.independent_chat_sign_in), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(state.username, viewModel::updateUsername, label = { Text(stringResource(R.string.independent_chat_username)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (state.registerMode) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(state.displayName, viewModel::updateDisplayName, label = { Text(stringResource(R.string.independent_chat_display_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(state.password, viewModel::updatePassword, label = { Text(stringResource(R.string.independent_chat_password)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done), modifier = Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
        Spacer(Modifier.height(16.dp))
        Button(onClick = viewModel::authenticate, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
            if (state.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text(stringResource(if (state.registerMode) R.string.independent_chat_register else R.string.independent_chat_login))
        }
        TextButton(onClick = viewModel::toggleRegister) { Text(stringResource(if (state.registerMode) R.string.independent_chat_have_account else R.string.independent_chat_need_account)) }
    }
}

@Composable
private fun ChatContent(state: ChatUiState, viewModel: ChatViewModel, playerViewModel: PlayerViewModel, playerState: PlayerUiState, modifier: Modifier) {
    if (state.selected == null) ConversationList(state, viewModel, modifier)
    else ConversationDetail(state, viewModel, playerViewModel, playerState, modifier)
    if (state.showCreateDialog) CreateConversationDialog(state, viewModel)
    if (state.showAddMemberDialog) AddMemberDialog(state, viewModel)
}

@Composable
private fun ConversationList(state: ChatUiState, viewModel: ChatViewModel, modifier: Modifier) {
    Column(modifier.fillMaxSize()) {
        Text(stringResource(R.string.independent_chat_recent), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
        if (state.conversations.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.independent_chat_empty_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.independent_chat_no_conversations), modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Text(stringResource(R.string.independent_chat_recent), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.conversations, key = { it.id }) { conversation -> ConversationRow(conversation, state.username, state.unreadByConversation[conversation.id] ?: 0) { viewModel.selectConversation(conversation) } }
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
    }
}

@Composable
private fun ConversationDetail(state: ChatUiState, viewModel: ChatViewModel, playerViewModel: PlayerViewModel, playerState: PlayerUiState, modifier: Modifier) {
    val conversation = state.selected ?: return
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(conversationTitle(conversation, state.username), Modifier.size(42.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(conversationTitle(conversation, state.username), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(if (conversation.kind == "group") "${conversation.members.size} members" else stringResource(R.string.independent_chat_private), style = MaterialTheme.typography.bodySmall)
            }
            if (conversation.kind == "group") TextButton(onClick = viewModel::openAddMemberDialog) { Text(stringResource(R.string.independent_chat_add_member)) }
        }
        HorizontalDivider()
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp), reverseLayout = false) {
            items(state.messages, key = { it.id }) { message ->
                val mine = message.senderUsername == state.username
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                    Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                        Text(if (mine) stringResource(R.string.independent_chat_you) else message.senderName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Surface(shape = RoundedCornerShape(18.dp), color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Column(Modifier.padding(horizontal = 15.dp, vertical = 10.dp)) {
                                if (message.body.isNotBlank()) Text(message.body)
                                message.track?.let { track ->
                                    Row(Modifier.clickable { playerViewModel.handleDeepLinkAction(ParsedIntentAction.YouTubeVideo(track.id, null)) }, verticalAlignment = Alignment.CenterVertically) {
                                        Icon(painterResource(R.drawable.music_note), contentDescription = null, modifier = Modifier.size(22.dp))
                                        Column(Modifier.padding(start = 8.dp)) {
                                            Text(track.title, fontWeight = FontWeight.SemiBold)
                                            track.artist?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                            Text(stringResource(R.string.independent_chat_play_track), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.sendCurrentTrack(playerState.title, playerState.artist, playerState.album, playerState.trackUrl, playerState.coverUrl) }, enabled = playerState.trackUrl.isNotBlank()) {
                Icon(painterResource(R.drawable.music_note), contentDescription = stringResource(R.string.independent_chat_share_music))
            }
            OutlinedTextField(value = state.draft, onValueChange = viewModel::updateDraft, placeholder = { Text(stringResource(R.string.independent_chat_message_hint)) }, singleLine = true, shape = RoundedCornerShape(24.dp), modifier = Modifier.weight(1f))
            TextButton(onClick = viewModel::send, enabled = state.draft.isNotBlank(), modifier = Modifier.padding(start = 4.dp)) { Text(stringResource(R.string.independent_chat_send)) }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
    }
}

@Composable
private fun CreateConversationDialog(state: ChatUiState, viewModel: ChatViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::closeCreateDialog,
        title = { Text(stringResource(if (state.createGroup) R.string.independent_chat_new_group else R.string.independent_chat_new_private)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.createGroup) OutlinedTextField(state.createTitle, viewModel::updateCreateTitle, label = { Text(stringResource(R.string.independent_chat_group_name)) }, singleLine = true)
                OutlinedTextField(state.createMembers, viewModel::updateCreateMembers, label = { Text(stringResource(R.string.independent_chat_friend_username)) }, supportingText = { Text(stringResource(if (state.createGroup) R.string.independent_chat_group_members_hint else R.string.independent_chat_private_member_hint)) })
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { Button(onClick = viewModel::createConversation, enabled = !state.loading) { Text(stringResource(R.string.independent_chat_create)) } },
        dismissButton = { TextButton(onClick = viewModel::closeCreateDialog) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun AddMemberDialog(state: ChatUiState, viewModel: ChatViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::closeAddMemberDialog,
        title = { Text(stringResource(R.string.independent_chat_add_member)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.independent_chat_add_member_description))
                OutlinedTextField(state.addMemberUsername, viewModel::updateAddMemberUsername, label = { Text(stringResource(R.string.independent_chat_friend_username)) }, singleLine = true)
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { Button(onClick = viewModel::addMember, enabled = !state.loading) { Text(stringResource(R.string.independent_chat_add)) } },
        dismissButton = { TextButton(onClick = viewModel::closeAddMemberDialog) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ConversationRow(conversation: ChatConversation, selfUsername: String, unreadCount: Int, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(conversationTitle(conversation, selfUsername), Modifier.size(50.dp))
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(conversationTitle(conversation, selfUsername), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(conversation.lastMessagePreview ?: if (conversation.kind == "group") stringResource(R.string.independent_chat_group) else stringResource(R.string.independent_chat_private), style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            if (unreadCount > 0) Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp)) { Box(contentAlignment = Alignment.Center) { Text(unreadCount.coerceAtMost(99).toString(), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall) } }
        }
    }
}

@Composable
private fun Avatar(label: String, modifier: Modifier) {
    BoxAvatar(label.take(1).uppercase(), modifier)
}

@Composable
private fun BoxAvatar(letter: String, modifier: Modifier) {
    Box(modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
        Text(letter, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

private fun conversationTitle(conversation: ChatConversation, selfUsername: String = ""): String {
    if (!conversation.title.isNullOrBlank()) return conversation.title
    val other = conversation.members.firstOrNull { it.username != selfUsername }
    return other?.displayName?.ifBlank { "@${other.username}" } ?: other?.username?.let { "@$it" } ?: "Conversation"
}
