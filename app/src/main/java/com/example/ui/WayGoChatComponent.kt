package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChatMessage
import com.example.ui.theme.*
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

@Composable
fun WayGoChatDialog(
    tripId: String,
    currentRole: String, // "PASSENGER" or "DRIVER"
    currentUserId: String,
    currentUserName: String,
    viewModel: WayGoViewModel,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val messages by viewModel.chatMessages.collectAsState()
    var rawText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Start listening on Firestore / Fallback on creation
    LaunchedEffect(tripId) {
        viewModel.startChatSession(tripId)
    }

    // Auto scroll to latest message when it grows
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Quick replies presets to optimize on-the-road interaction
    val presetReplies = when (currentRole) {
        "DRIVER" -> listOf(
            "Salam! On my way.",
            "I have arrived at your pickup point.",
            "Stuck in Westfield traffic, please wait.",
            "Okay, see you shortly!"
        )
        else -> listOf( // PASSENGER
            "Salam Alaikum!",
            "I'm waiting by the roadside.",
            "Please follow the exact GPS marker.",
            "Okay, coming out now."
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(decorFitsSystemWindows = false),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 550.dp)
            .imePadding()
            .testTag("ride_chat_dialog"),
        shape = RoundedCornerShape(20.dp),
        containerColor = Color.White,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(BrandBluePrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = "Chat",
                            tint = BrandBluePrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Live Ride Chat",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandBlueDark
                        )
                        Text(
                            text = "Connected to ${if (currentRole == "PASSENGER") "your Driver" else "your Passenger"}",
                            fontSize = 10.sp,
                            color = SuccessGreen,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = NeutralGray)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Chat conversation area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(BrandBlueLight.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(8.dp)
                ) {
                    if (messages.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(BrandBluePrimary.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ChatBubbleOutline,
                                        contentDescription = "No messages",
                                        tint = BrandBluePrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    "No messages yet",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandBlueDark
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Send a message or tap one of the quick presets below.",
                                    fontSize = 11.sp,
                                    color = NeutralGray,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 6.dp)
                        ) {
                            items(messages) { msg ->
                                val isMe = msg.senderRole == currentRole

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                                ) {
                                    Column(
                                        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
                                        modifier = Modifier.widthIn(max = 240.dp)
                                    ) {
                                        // Sender name
                                        Text(
                                            text = if (isMe) "You" else msg.senderName,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NeutralGray,
                                            modifier = Modifier.padding(horizontal = 6.dp)
                                        )

                                        // Speech bubble
                                        Box(
                                            modifier = Modifier
                                                .clip(
                                                    RoundedCornerShape(
                                                        topStart = 14.dp,
                                                        topEnd = 14.dp,
                                                        bottomStart = if (isMe) 14.dp else 2.dp,
                                                        bottomEnd = if (isMe) 2.dp else 14.dp
                                                    )
                                                )
                                                .background(
                                                    if (isMe) BrandBluePrimary else Color.White
                                                )
                                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = msg.message,
                                                fontSize = 12.sp,
                                                color = if (isMe) Color.White else BrandBlueDark,
                                                lineHeight = 16.sp
                                            )
                                        }

                                        // Formatted Timestamp representation
                                        val timeStr = remember(msg.timestamp) {
                                            val date = java.util.Date(msg.timestamp)
                                            val format = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                                            format.format(date)
                                        }
                                        Text(
                                            text = timeStr,
                                            fontSize = 8.sp,
                                            color = NeutralGray.copy(alpha = 0.8f),
                                            modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quick presets list
                Text(
                    text = "Quick Dispatch presets:",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandBlueSecondary,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    presetReplies.forEach { txt ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(BrandBlueLight)
                                .clickable {
                                    viewModel.sendChatMessage(
                                        tripId = tripId,
                                        senderId = currentUserId,
                                        senderName = currentUserName,
                                        senderRole = currentRole,
                                        text = txt
                                    )
                                }
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = txt,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrandBluePrimary,
                                textAlign = TextAlign.Center,
                                lineHeight = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Input Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = rawText,
                        onValueChange = { rawText = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_input_field"),
                        placeholder = { Text("Write a secure message...", fontSize = 11.sp) },
                        maxLines = 2,
                        shape = RoundedCornerShape(12.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(color = BrandBlueDark),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = BrandBlueDark,
                            unfocusedTextColor = BrandBlueDark,
                            focusedBorderColor = BrandBluePrimary,
                            unfocusedBorderColor = Color.LightGray.copy(alpha = 0.6f),
                            focusedContainerColor = PureWhite,
                            unfocusedContainerColor = PureWhite,
                            focusedPlaceholderColor = NeutralGray.copy(alpha = 0.6f),
                            unfocusedPlaceholderColor = NeutralGray.copy(alpha = 0.6f)
                        ),
                        trailingIcon = {
                            if (rawText.isNotEmpty()) {
                                IconButton(onClick = { rawText = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "clear", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    )

                    IconButton(
                        onClick = {
                            if (rawText.isNotBlank()) {
                                viewModel.sendChatMessage(
                                    tripId = tripId,
                                    senderId = currentUserId,
                                    senderName = currentUserName,
                                    senderRole = currentRole,
                                    text = rawText.trim()
                                )
                                rawText = ""
                            }
                        },
                        modifier = Modifier
                            .testTag("chat_send_button")
                            .size(42.dp)
                            .background(BrandBluePrimary, CircleShape),
                        enabled = rawText.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = BrandBluePrimary, fontWeight = FontWeight.Bold)
            }
        }
    )
}
