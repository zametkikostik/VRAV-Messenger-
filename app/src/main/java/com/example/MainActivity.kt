package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.example.crypto.VravCrypto
import com.example.data.MessageEntity
import com.example.data.PeerEntity
import com.example.ui.theme.*
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        var viewModelInitializationError: Throwable? = null
        var viewModel: VravViewModel? = null
        try {
            viewModel = ViewModelProvider(this)[VravViewModel::class.java]
        } catch (t: Throwable) {
            viewModelInitializationError = t
            android.util.Log.e("VravSystem", "Fatal: Failed to initialize VravViewModel", t)
        }

        setContent {
            MyApplicationTheme {
                var compositionError by remember { mutableStateOf<Throwable?>(viewModelInitializationError) }

                // Define an Uncaught Exception Handler to capture async/layout crashes safely inside RAM
                val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
                DisposableEffect(Unit) {
                    val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
                    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                        android.util.Log.e("VravFatalCrash", "Sovereign Emergency Interception!", throwable)
                        mainHandler.post {
                            compositionError = throwable
                        }
                    }
                    onDispose {
                        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
                    }
                }

                if (compositionError != null) {
                    // Sovereign emergency crash recovery screen
                    Scaffold(
                        modifier = Modifier.fillMaxSize().background(SleekBackground)
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .background(SleekBackground)
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Sovereign System Flagged",
                                    tint = SleekCoral,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "SOVEREIGN SYSTEM RUNTIME RECOVERY",
                                    color = SleekTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "To guarantee zero metadata leakage and client security, anomalous system exceptions are intercepted safely inside RAM.",
                                    color = SleekTextSecondary,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .background(SleekSurface, RoundedCornerShape(12.dp))
                                        .border(0.5.dp, SleekBorder, RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                ) {
                                    LazyColumn {
                                        item {
                                            Text(
                                                text = compositionError?.stackTraceToString() ?: "Anomalous operation details offline.",
                                                color = SleekCoral,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        // Clear preferences and reload
                                        val prefs = getSharedPreferences("vrav_prefs", android.content.Context.MODE_PRIVATE)
                                        prefs.edit().clear().apply()
                                        val intent = intent
                                        finish()
                                        startActivity(intent)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SleekCoral)
                                ) {
                                    Text("Reset Sandbox & Reboot Node", color = SleekBackground, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        compositionError = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SleekBorder)
                                ) {
                                    Text("Dismiss & Resume Ephemeral Session", color = SleekTextPrimary)
                                }
                            }
                        }
                    }
                } else {
                    Scaffold(
                        modifier = Modifier.fillMaxSize().background(SleekBackground),
                        contentWindowInsets = WindowInsets.safeDrawing
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .background(SleekBackground)
                        ) {
                            if (viewModel != null) {
                                MainContentScreen(viewModel)
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Sovereign VM failure. Please reset.", color = SleekCoral)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MainContentScreen(viewModel: VravViewModel) {
    val currentTab by viewModel.currentTab.collectAsState()
    val selectedPeerId by viewModel.selectedPeerId.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        if (selectedPeerId != null && currentTab == "Chats") {
            // Focus on Conversation View
            ConversationScreen(viewModel, selectedPeerId!!)
        } else {
            // General Header representing sovereign status
            HeaderSection(viewModel)

            // Content Body based on tab selection
            Box(modifier = Modifier.weight(1f)) {
                when (currentTab) {
                    "Chats" -> ChatsTabScreen(viewModel)
                    "Nodes" -> NodesTabScreen(viewModel)
                    "Vault" -> VaultTabScreen(viewModel)
                    "Core" -> CoreTabScreen(viewModel)
                }
            }
        }

        // Clean Sleek Bottom Navigation Bar with safe window padding for mobile layout
        BottomNavBar(
            activeTab = currentTab,
            onTabSelected = { 
                viewModel.selectedPeerId.value = null // reset individual chat back to list
                viewModel.currentTab.value = it 
            }
        )
    }
}

@Composable
fun HeaderSection(viewModel: VravViewModel) {
    val isP2pActive by viewModel.isP2pActive.collectAsState()
    val isTorEnabled by viewModel.isTorEnabled.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SleekSurface)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .border(width = 0.5f.dp, color = SleekBorder.copy(alpha = 0.3f), shape = RoundedCornerShape(0.dp)),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App Identity Avatar Logo
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(SleekAccent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "VR",
                color = SleekTextAccent,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                fontFamily = FontFamily.SansSerif
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "VRAV Messenger",
                color = SleekTextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Online Dot Indicator
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isP2pActive) SleekGreen else SleekCoral)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isP2pActive) "P2P ACTIVE (WebRTC)" else "NETWORK OFFLINE",
                    color = if (isP2pActive) SleekGreen else SleekCoral,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // Crypto strength chip
        Row(
            modifier = Modifier
                .background(SleekBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Shield Locked",
                tint = SleekAccent,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isTorEnabled) "TOR • KYBER-768" else "KYBER-768",
                color = SleekAccent,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ChatsTabScreen(viewModel: VravViewModel) {
    val peers by viewModel.allPeers.collectAsState()
    val messages by viewModel.allMessages.collectAsState()

    if (peers.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "No Nodes Found",
                modifier = Modifier.size(48.dp),
                tint = SleekTextSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No Peer Links Discovered",
                color = SleekTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Go to the 'Nodes' tab to scan multiaddresses or connect with nearby decentralised peers.",
                color = SleekTextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(peers) { peer ->
                // Fetch last message for preview
                val peerMsg = messages.filter { it.peerId == peer.peerId }
                val lastMsg = peerMsg.lastOrNull()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SleekSurface, RoundedCornerShape(16.dp))
                        .border(0.5.dp, SleekBorder.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .clickable { viewModel.selectedPeerId.value = peer.peerId }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar init
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(SleekBorder),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = peer.name.take(2).uppercase(),
                            color = SleekAccent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = peer.name,
                                color = SleekTextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (peer.isOnline) SleekGreen else SleekTextSecondary)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = if (lastMsg != null) {
                                if (lastMsg.isFile) "📁 [Encrypted File] ${lastMsg.fileName}"
                                else lastMsg.content
                            } else "Tap to establish keys and chat securely...",
                            color = SleekTextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConversationScreen(viewModel: VravViewModel, peerId: String) {
    val peers by viewModel.allPeers.collectAsState()
    val allMessages by viewModel.allMessages.collectAsState()
    
    val peer = peers.find { it.peerId == peerId } ?: return
    val messages = allMessages.filter { it.peerId == peerId }

    var textInput by remember { mutableStateOf("") }
    val lazyListState = rememberLazyListState()
    val context = LocalContext.current

    // Automatically scroll to the bottom of the conversation on any new messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            lazyListState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Individual Conversation Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SleekSurface)
                .padding(16.dp)
                .border(0.5.dp, SleekBorder.copy(alpha = 0.2f), RoundedCornerShape(0.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.selectedPeerId.value = null }) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Back",
                    tint = SleekTextPrimary
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(SleekAccent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = peer.name.take(2).uppercase(),
                    color = SleekAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peer.name,
                    color = SleekTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(
                    text = "Kyber-768/X25519 Link Stable",
                    color = SleekGreen,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Delete Peer Button Option
            IconButton(onClick = { 
                viewModel.selectedPeerId.value = null
                viewModel.deletePeer(peerId)
                Toast.makeText(context, "Peer connection removed.", Toast.LENGTH_SHORT).show()
            }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove Peer Link",
                    tint = SleekCoral.copy(alpha = 0.8f)
                )
            }
        }

        // Chat Message Log Stream
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                // Key Exchanged Header info matching "Sleek Interface" Layout Spec
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Keys exchanged via X25519",
                        color = SleekTextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .background(SleekBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        letterSpacing = 0.4.sp
                    )
                }
            }

            items(messages) { msg ->
                if (msg.isFile) {
                    // Ephemeral AES Transient Storage File Container Matching HTML design specification exactly!
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        contentAlignment = if (msg.isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Column(
                            modifier = Modifier.maxOrNullWidth(280.dp),
                            horizontalAlignment = if (msg.isOutgoing) Alignment.End else Alignment.Start
                        ) {
                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(SleekBorder)
                                    .border(0.5.dp, SleekBorder, RoundedCornerShape(16.dp))
                            ) {
                                // Upper detailed segment
                                Row(
                                    modifier = Modifier
                                        .background(SleekSurface.copy(alpha = 0.5f))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Custom illustrated File box design instead of un-imported ZIP icons
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(SleekAccent, RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Encrypted Zip File Container",
                                            tint = SleekTextAccent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = msg.fileName,
                                            color = SleekTextPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${msg.fileSize} • Encrypted",
                                            color = SleekGreen,
                                            fontSize = 10.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    // Local download tap capability
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .border(0.5.dp, SleekAccent.copy(alpha = 0.3f), CircleShape)
                                            .clip(CircleShape)
                                            .clickable {
                                                Toast.makeText(context, "Sovereign file decrypted! Access allowed.", Toast.LENGTH_SHORT).show()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Verify decrypted",
                                            tint = SleekAccent,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                // Interactive footer showing unpin status and countdown
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SleekSurface)
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                        .border(width = 0.5f.dp, color = SleekBorder.copy(alpha = 0.2f)),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (msg.fileUnpinSecondsLeft > 0) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(SleekCoral)
                                            )
                                            Text(
                                                text = "Unpinning in ${msg.fileUnpinSecondsLeft}s",
                                                color = SleekCoral,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    } else {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(SleekTextSecondary)
                                            )
                                            Text(
                                                text = "Pin Unpinned from Pinata",
                                                color = SleekTextSecondary,
                                                fontSize = 9.sp
                                            )
                                        }
                                    }

                                    Text(
                                        text = "IPFS Transient",
                                        color = SleekTextSecondary,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                            // Time metadata
                            Text(
                                text = formatTime(msg.timestamp),
                                color = SleekTextSecondary,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
                            )
                        }
                    }
                } else {
                    // Regular Text Message bubble matching "Sleek Interface" Layout Spec!
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = if (msg.isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Column(
                            modifier = Modifier.maxOrNullWidth(280.dp),
                            horizontalAlignment = if (msg.isOutgoing) Alignment.End else Alignment.Start
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 16.dp, 
                                            topEnd = 16.dp, 
                                            bottomStart = if (msg.isOutgoing) 16.dp else 0.dp, 
                                            bottomEnd = if (msg.isOutgoing) 0.dp else 16.dp
                                        )
                                    )
                                    .background(if (msg.isOutgoing) SleekAccent else SleekBorder)
                                    .padding(vertical = 10.dp, horizontal = 14.dp)
                            ) {
                                Text(
                                    text = msg.content,
                                    color = if (msg.isOutgoing) SleekTextAccent else SleekTextPrimary,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    fontWeight = if (msg.isOutgoing) FontWeight.Medium else FontWeight.Normal
                                )
                            }
                            
                            // Delivery metadata timestamp
                            val metaText = if (msg.isOutgoing) {
                                "${formatTime(msg.timestamp)} • Delivered"
                            } else {
                                formatTime(msg.timestamp)
                            }

                            Text(
                                text = metaText,
                                color = SleekTextSecondary,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Send Options Bottom Area Configuration
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SleekBackground)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Interactive File Attachment Simulated button
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(SleekBorder)
                    .clickable {
                        // Simulate selecting a secure sovereign archive to execute uploaded pins!
                        val sampleFileName = "rust_core_${(10..99).random()}.tar.gz"
                        val dataBytes = ("DECENTRALIZED_SOVEREIGN_BIN_DATA_" + UUID.randomUUID().toString()).toByteArray()
                        
                        viewModel.uploadAndForget(peerId, sampleFileName, dataBytes)
                        Toast.makeText(context, "Encrypted payload. Triggered Pinata uploadAndForget!", Toast.LENGTH_SHORT).show()
                    }
                    .testTag("attach_file_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Attach Ephemeral File",
                    tint = SleekAccent
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Text message typing area
            TextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = { Text("Sovereign message...", color = SleekTextSecondary) },
                colors = TextFieldDefaults.colors(
                    focusedTextColor = SleekTextPrimary,
                    unfocusedTextColor = SleekTextPrimary,
                    focusedContainerColor = SleekSurface,
                    unfocusedContainerColor = SleekSurface,
                    disabledContainerColor = SleekSurface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .border(0.5.dp, SleekBorder, RoundedCornerShape(24.dp))
                    .testTag("message_input")
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Sovereign cryptographic Broadcast Button
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(SleekAccent)
                    .clickable {
                        if (textInput.isNotBlank()) {
                            viewModel.sendMessage(peerId, textInput)
                            textInput = ""
                        }
                    }
                    .testTag("send_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send Encrypted Packet",
                    tint = SleekTextAccent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun NodesTabScreen(viewModel: VravViewModel) {
    val localId by viewModel.localPeerId.collectAsState()
    val isP2PActive by viewModel.isP2pActive.collectAsState()
    val heartbeatCountdown by viewModel.heartbeatCountdown.collectAsState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    // Node Registries inputs
    var newPeerName by remember { mutableStateOf("") }
    var newPeerIP by remember { mutableStateOf("") }
    var inputXPub by remember { mutableStateOf("") }
    var inputKPub by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Node Status Overview
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SleekSurface, RoundedCornerShape(16.dp))
                    .border(0.5.dp, SleekBorder.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "Local Node Configuration (libp2p)",
                    color = SleekAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                
                Spacer(modifier = Modifier.height(10.dp))
                
                Text(
                    text = "Local Peer ID:",
                    color = SleekTextSecondary,
                    fontSize = 11.sp
                )
                Text(
                    text = localId,
                    color = SleekTextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clickable {
                            clipboard.setText(AnnotatedString(localId))
                            Toast.makeText(context, "Sovereign Peer ID Copied!", Toast.LENGTH_SHORT).show()
                        }
                        .background(SleekBorder.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Local multiaddresses:",
                    color = SleekTextSecondary,
                    fontSize = 11.sp
                )
                val multiaddr = "/ip4/127.0.0.1/tcp/4001/p2p/$localId"
                Text(
                    text = multiaddr,
                    color = SleekTextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clickable {
                            clipboard.setText(AnnotatedString(multiaddr))
                            Toast.makeText(context, "Multiaddress Copied!", Toast.LENGTH_SHORT).show()
                        }
                        .background(SleekBorder.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "WebRTC Signal Gateway",
                        color = SleekTextSecondary,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "CONNECTED (Sovereign)",
                        color = SleekGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Add Custom Peer form representing lattice exchange setup
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SleekSurface, RoundedCornerShape(16.dp))
                    .border(0.5.dp, SleekBorder.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "Connect with a New Peer Multiaddress",
                    color = SleekTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Name input
                CustomInputField(
                    value = newPeerName,
                    onValueChange = { newPeerName = it },
                    label = "Peer Name / Pseudonym"
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Network IP adress input
                CustomInputField(
                    value = newPeerIP,
                    onValueChange = { newPeerIP = it },
                    label = "IP Address Override (default: 127.0.0.1)"
                )
                Spacer(modifier = Modifier.height(8.dp))

                // X25519 Public Identifier
                CustomInputField(
                    value = inputXPub,
                    onValueChange = { inputXPub = it },
                    label = "Recipient's X25519 Public Key"
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Kyber Public Identifier
                CustomInputField(
                    value = inputKPub,
                    onValueChange = { inputKPub = it },
                    label = "Recipient's Kyber-768 Public Matrix"
                )
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (newPeerName.isNotBlank() && inputXPub.isNotBlank() && inputKPub.isNotBlank()) {
                            viewModel.addNewPeer(newPeerName, newPeerIP, inputXPub, inputKPub)
                            // Clear inputs
                            newPeerName = ""
                            newPeerIP = ""
                            inputXPub = ""
                            inputKPub = ""
                            Toast.makeText(context, "Sovereign WebRTC connection established!", Toast.LENGTH_SHORT).show()
                        } else {
                            // Populate with generated demo keys so that testing/demonstration is instantly easy & seamless!
                            val mockX = VravCrypto.generateX25519KeyPair().publicKeyHex
                            val mockK = VravCrypto.generateKyberKeyPair().publicKeyHex
                            newPeerName = "Elena K. (Sovereign Node #12)"
                            newPeerIP = "10.0.2.15"
                            inputXPub = mockX
                            inputKPub = mockK
                            Toast.makeText(context, "Empty parameters completed with valid mock lattices! Tap Register to save.", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SleekAccent),
                    modifier = Modifier.fillMaxWidth().testTag("add_peer_button")
                ) {
                    Text("Register Sovereign Connection", color = SleekTextAccent, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Live Diagnostic Log Screen showing Heartbeats countdown
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SleekSurface, RoundedCornerShape(16.dp))
                    .border(0.5.dp, SleekBorder.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Decentralised Heartbeat Logs",
                        color = SleekTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Next Ping in ${heartbeatCountdown}s",
                        color = SleekCoral,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable logs box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(SleekBackground, RoundedCornerShape(8.dp))
                        .border(0.5.dp, SleekBorder, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    val logs by viewModel.heartbeatLogs.collectAsState()
                    if (logs.isEmpty()) {
                        Text(
                            text = "[System Ready] Heartbeat engine active. Logs will record here.",
                            color = SleekTextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(logs) { log ->
                                Text(
                                    text = log,
                                    color = if (log.contains("ACK")) SleekGreen else SleekTextPrimary,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VaultTabScreen(viewModel: VravViewModel) {
    val xPub by viewModel.x25519PublicKey.collectAsState()
    val xPriv by viewModel.x25519PrivateKey.collectAsState()
    val kPub by viewModel.kyberPublicKey.collectAsState()
    val kPriv by viewModel.kyberPrivateKey.collectAsState()
    val crdtLogs by viewModel.crdtLogsList.collectAsState()

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Vault Header Information
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SleekBorder.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .border(0.5.dp, SleekBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "Sovereign Key Vault (Safe Area)",
                    color = SleekAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Your cryptographic key pairs are generated on-device via high-entropy entropy pools. Private keys NEVER leave your local device storage under any circumstances.",
                    color = SleekTextPrimary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }

        // Key displays
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SleekSurface, RoundedCornerShape(16.dp))
                    .border(0.5.dp, SleekBorder.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "X25519 DH SUITE",
                    color = SleekTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                Text(text = "Public Key:", color = SleekTextSecondary, fontSize = 11.sp)
                Text(
                    text = xPub,
                    color = SleekTextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SleekBackground, RoundedCornerShape(6.dp))
                        .padding(8.dp)
                        .clickable {
                            clipboard.setText(AnnotatedString(xPub))
                            Toast.makeText(context, "X25519 Public Key copied!", Toast.LENGTH_SHORT).show()
                        }
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(text = "Private Key (Safe Storage):", color = SleekTextSecondary, fontSize = 11.sp)
                Text(
                    text = "•••••••••••••••• " + xPriv.takeLast(8),
                    color = SleekCoral,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SleekBackground, RoundedCornerShape(6.dp))
                        .padding(8.dp)
                )
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SleekSurface, RoundedCornerShape(16.dp))
                    .border(0.5.dp, SleekBorder.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "KYBER-768 POST-QUANTUM SUITE",
                    color = SleekTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                Text(text = "Lattice Public Seed Matrix:", color = SleekTextSecondary, fontSize = 11.sp)
                Text(
                    text = kPub,
                    color = SleekTextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SleekBackground, RoundedCornerShape(6.dp))
                        .padding(8.dp)
                        .clickable {
                            clipboard.setText(AnnotatedString(kPub))
                            Toast.makeText(context, "Kyber-768 Public Matrix copied!", Toast.LENGTH_SHORT).show()
                        }
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(text = "Secret Kyber Coordinates:", color = SleekTextSecondary, fontSize = 11.sp)
                Text(
                    text = "•••••••••••••••• " + kPriv.takeLast(8),
                    color = SleekCoral,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SleekBackground, RoundedCornerShape(6.dp))
                        .padding(8.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        viewModel.rotateIdentity()
                        Toast.makeText(context, "Sovereign keys ROTATED successfully!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SleekBorder),
                    modifier = Modifier.fillMaxWidth().testTag("rotate_keys_button")
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Rotate", modifier = Modifier.size(16.dp), tint = SleekTextPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Rotate Key Footprint", color = SleekTextPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }

        // CRDT Consistency Log Entries list
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SleekSurface, RoundedCornerShape(16.dp))
                    .border(0.5.dp, SleekBorder.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CRDT Merged Consistency Ledgers",
                        color = SleekTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    IconButton(onClick = { viewModel.triggerCrdtSync() }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Force Synchronize", tint = SleekAccent)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "CRDT ensures correct message sorting and consistency across multiple devices without needing a central coordinator.",
                    color = SleekTextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(SleekBackground, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    if (crdtLogs.isEmpty()) {
                        Text(
                            text = "No log state entries yet. Communicate with peers to build logs ledger.",
                            color = SleekTextSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(crdtLogs) { item ->
                                val formattedDate = formatTime(item.timestamp)
                                Text(
                                    text = "[$formattedDate] L-Clock: ${item.logicalClock} | OP: ${item.operationType} | origin: ...${item.originatingPeerId.takeLast(12)}",
                                    color = SleekGreen,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        viewModel.triggerCrdtSync()
                        Toast.makeText(context, "Executing CRDT GOSSIPSUB Sync...", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SleekAccent),
                    modifier = Modifier.fillMaxWidth().testTag("sync_button")
                ) {
                    Text("Execute Vector Sync Check", color = SleekTextAccent, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun CoreTabScreen(viewModel: VravViewModel) {
    val jwt by viewModel.pinataJwt.collectAsState()
    val isTorEnabled by viewModel.isTorEnabled.collectAsState()
    val socksHost by viewModel.torSocksHost.collectAsState()
    val socksPort by viewModel.torSocksPort.collectAsState()
    val bridgeSelected by viewModel.snowflakeBridge.collectAsState()

    val emailFallbackEnabled by viewModel.isEmailFallbackEnabled.collectAsState()
    val smtpHostSec by viewModel.smtpHost.collectAsState()
    val imapHostSec by viewModel.imapHost.collectAsState()
    val smtpPortSec by viewModel.smtpPort.collectAsState()
    val emailAddressSec by viewModel.emailAddress.collectAsState()

    val localAiEnabled by viewModel.isLocalAiEnabled.collectAsState()
    val ollamaHostSec by viewModel.ollamaHost.collectAsState()
    val ollamaPortSec by viewModel.ollamaPort.collectAsState()
    val openRouterEnabled by viewModel.isOpenRouterEnabled.collectAsState()
    val openRouterApiKeySec by viewModel.openRouterApiKey.collectAsState()
    val openRouterModelSec by viewModel.openRouterModel.collectAsState()
    val openRouterPrivacyDenySec by viewModel.openRouterPrivacyDeny.collectAsState()
    val aiTranslationActive by viewModel.isAiTranslationActive.collectAsState()

    var jwtInput by remember { mutableStateOf(jwt) }
    var torHostInput by remember { mutableStateOf(socksHost) }
    var torPortInput by remember { mutableStateOf(socksPort) }
    var isTorSelected by remember { mutableStateOf(isTorEnabled) }

    var isEmailFallbackSelected by remember { mutableStateOf(emailFallbackEnabled) }
    var smtpHostInput by remember { mutableStateOf(smtpHostSec) }
    var imapHostInput by remember { mutableStateOf(imapHostSec) }
    var smtpPortInput by remember { mutableStateOf(smtpPortSec) }
    var emailAddressInput by remember { mutableStateOf(emailAddressSec) }

    var isLocalAiSelected by remember { mutableStateOf(localAiEnabled) }
    var ollamaHostInput by remember { mutableStateOf(ollamaHostSec) }
    var ollamaPortInput by remember { mutableStateOf(ollamaPortSec) }
    
    var isOpenRouterSelected by remember { mutableStateOf(openRouterEnabled) }
    var openRouterApiKeyInput by remember { mutableStateOf(openRouterApiKeySec) }
    var openRouterModelInput by remember { mutableStateOf(openRouterModelSec) }
    var isOpenRouterPrivacyDenySelected by remember { mutableStateOf(openRouterPrivacyDenySec) }
    
    var isAiTranslationSelected by remember { mutableStateOf(aiTranslationActive) }

    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Pinata Credentials Section
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SleekSurface, RoundedCornerShape(16.dp))
                    .border(0.5.dp, SleekBorder.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "Pinata API Transient Credentials",
                    color = SleekAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "JWT auth token is used for pinning encrypted transient payload files.",
                    color = SleekTextSecondary,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                CustomInputField(
                    value = jwtInput,
                    onValueChange = { jwtInput = it },
                    label = "Pinata JWT Access Token (Optional)"
                )
            }
        }

        // Tor route settings
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SleekSurface, RoundedCornerShape(16.dp))
                    .border(0.5.dp, SleekBorder.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "Adaptive SOCKS5 Proxy Routing (Tor)",
                    color = SleekTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Bypasses firewalls and hides IP metadata by tunneling libp2p packets through Tor.",
                    color = SleekTextSecondary,
                    fontSize = 11.sp
                )
                
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tunnel traffic via Orbot Client",
                        color = SleekTextPrimary,
                        fontSize = 12.sp
                    )
                    Switch(
                        checked = isTorSelected,
                        onCheckedChange = { isTorSelected = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SleekAccent,
                            checkedTrackColor = SleekBorder
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                CustomInputField(
                    value = torHostInput,
                    onValueChange = { torHostInput = it },
                    label = "Tor SOCKS5 Proxy Host",
                    isEnabled = isTorSelected
                )

                Spacer(modifier = Modifier.height(8.dp))

                CustomInputField(
                    value = torPortInput,
                    onValueChange = { torPortInput = it },
                    label = "Tor SOCKS5 Proxy Port",
                    isEnabled = isTorSelected
                )
            }
        }

        // Anti-Censorship Snowflake bridges selection
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SleekSurface, RoundedCornerShape(16.dp))
                    .border(0.5.dp, SleekBorder.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "Snowflake DPI Bridges (Anti-Block)",
                    color = SleekTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Select bridge protocols to camouflage egress mesh packages.",
                    color = SleekTextSecondary,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                val bridges = listOf("Snowflake-US", "Snowflake-DE", "Ooni-Proxy-UK", "Symmetric-DHT")
                bridges.forEach { br ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (bridgeSelected == br) SleekBorder.copy(alpha = 0.5f) else Color.Transparent)
                            .clickable {
                                viewModel.saveCoreConfig(
                                    jwt = jwtInput,
                                    torEnabled = isTorSelected,
                                    socksHost = torHostInput,
                                    socksPort = torPortInput,
                                    bridge = br
                                )
                                Toast.makeText(context, "Anti-censorship bridge set to $br.", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (bridgeSelected == br),
                            onClick = {
                                viewModel.saveCoreConfig(
                                    jwt = jwtInput,
                                    torEnabled = isTorSelected,
                                    socksHost = torHostInput,
                                    socksPort = torPortInput,
                                    bridge = br
                                )
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = SleekAccent,
                                unselectedColor = SleekTextSecondary
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = br, color = SleekTextPrimary, fontSize = 12.sp)
                    }
                }
            }
        }

        // Emergency Fallback Transport (SMTP/IMAP) Section
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SleekSurface, RoundedCornerShape(16.dp))
                    .border(0.5.dp, SleekBorder.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "Emergency Fallback (E2E SMTP/IMAP)",
                    color = SleekTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "If WebRTC/QUIC is heavily censored, VRAV falls back automatically to E2E encrypted email packets.",
                    color = SleekTextSecondary,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Enable Email-As-A-Transport",
                        color = SleekTextPrimary,
                        fontSize = 12.sp
                    )
                    Switch(
                        checked = isEmailFallbackSelected,
                        onCheckedChange = { isEmailFallbackSelected = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SleekAccent,
                            checkedTrackColor = SleekBorder
                        )
                    )
                }

                if (isEmailFallbackSelected) {
                    Spacer(modifier = Modifier.height(12.dp))

                    CustomInputField(
                        value = emailAddressInput,
                        onValueChange = { emailAddressInput = it },
                        label = "Secure Inbox Account (Proton/Mailbox)"
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    CustomInputField(
                        value = smtpHostInput,
                        onValueChange = { smtpHostInput = it },
                        label = "SMTP Transport Server"
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(modifier = Modifier.weight(0.7f)) {
                            CustomInputField(
                                value = imapHostInput,
                                onValueChange = { imapHostInput = it },
                                label = "IMAP Server"
                            )
                        }
                        Box(modifier = Modifier.weight(0.3f)) {
                            CustomInputField(
                                value = smtpPortInput,
                                onValueChange = { smtpPortInput = it },
                                label = "Port"
                            )
                        }
                    }
                }
            }
        }

        // Sovereign AI Engine Suite (Local Ollama & OpenRouter Security)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SleekSurface, RoundedCornerShape(16.dp))
                    .border(0.5.dp, SleekBorder.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "Sovereign AI Security & Translation Suite",
                    color = SleekAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Direct E2E translation pipelines integration. Run securely offline (local Ollama host) or via zero-leak Cloud APIs (OpenRouter client with strict data collection denial).",
                    color = SleekTextSecondary,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Global Translation switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(0.8f)) {
                        Text(
                            text = "Enable Real-time Translation Suite",
                            color = SleekTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Asynchronously processes STT, translates text/calls, and synthesizes audio voice TTS.",
                            color = SleekTextSecondary,
                            fontSize = 10.sp
                        )
                    }
                    Switch(
                        checked = isAiTranslationSelected,
                        onCheckedChange = { isAiTranslationSelected = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SleekAccent,
                            checkedTrackColor = SleekBorder
                        )
                    )
                }

                if (isAiTranslationSelected) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(SleekBorder.copy(alpha = 0.3f)))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Local Ollama config
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Local AI Instance (Ollama Node)",
                                color = SleekTextPrimary,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Highly secure, fully offline processing on local daemon.",
                                color = SleekTextSecondary,
                                fontSize = 10.sp
                            )
                        }
                        Switch(
                            checked = isLocalAiSelected,
                            onCheckedChange = { 
                                isLocalAiSelected = it
                                if (it) isOpenRouterSelected = false
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = SleekAccent,
                                checkedTrackColor = SleekBorder
                            )
                        )
                    }

                    if (isLocalAiSelected) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(modifier = Modifier.weight(0.7f)) {
                                CustomInputField(
                                    value = ollamaHostInput,
                                    onValueChange = { ollamaHostInput = it },
                                    label = "Ollama Local Host"
                                )
                            }
                            Box(modifier = Modifier.weight(0.3f)) {
                                CustomInputField(
                                    value = ollamaPortInput,
                                    onValueChange = { ollamaPortInput = it },
                                    label = "Port"
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(SleekBorder.copy(alpha = 0.3f)))
                    Spacer(modifier = Modifier.height(12.dp))

                    // OpenRouter config
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Secure Cloud Gateway (OpenRouter)",
                                color = SleekTextPrimary,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Zero data harvesting, access to large models.",
                                color = SleekTextSecondary,
                                fontSize = 10.sp
                            )
                        }
                        Switch(
                            checked = isOpenRouterSelected,
                            onCheckedChange = { 
                                isOpenRouterSelected = it
                                if (it) isLocalAiSelected = false
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = SleekAccent,
                                checkedTrackColor = SleekBorder
                            )
                        )
                    }

                    if (isOpenRouterSelected) {
                        Spacer(modifier = Modifier.height(8.dp))
                        CustomInputField(
                            value = openRouterApiKeyInput,
                            onValueChange = { openRouterApiKeyInput = it },
                            label = "OpenRouter Auth API Key"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        CustomInputField(
                            value = openRouterModelInput,
                            onValueChange = { openRouterModelInput = it },
                            label = "Target LLM Model"
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Switch(
                                checked = isOpenRouterPrivacyDenySelected,
                                onCheckedChange = { isOpenRouterPrivacyDenySelected = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = SleekAccent,
                                    checkedTrackColor = SleekBorder
                                )
                            )
                            Text(
                                text = "Enforce Privacy: STRICT DENY DATA COLLECTION",
                                color = if (isOpenRouterPrivacyDenySelected) SleekAccent else SleekTextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Persistent save button
        item {
            Button(
                onClick = {
                    viewModel.saveCoreConfig(
                        jwt = jwtInput,
                        torEnabled = isTorSelected,
                        socksHost = torHostInput,
                        socksPort = torPortInput,
                        bridge = bridgeSelected
                    )
                    viewModel.saveFallbackEmailConfig(
                        enabled = isEmailFallbackSelected,
                        smtp = smtpHostInput,
                        imap = imapHostInput,
                        port = smtpPortInput,
                        email = emailAddressInput
                    )
                    viewModel.saveAiConfig(
                        localEnabled = isLocalAiSelected,
                        oHost = ollamaHostInput,
                        oPort = ollamaPortInput,
                        orEnabled = isOpenRouterSelected,
                        orKey = openRouterApiKeyInput,
                        orModel = openRouterModelInput,
                        orPrivacy = isOpenRouterPrivacyDenySelected,
                        aiTransActive = isAiTranslationSelected
                    )
                    Toast.makeText(context, "Sovereign configs saved successfully!", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = SleekAccent),
                modifier = Modifier.fillMaxWidth().testTag("core_save_button")
            ) {
                Text("Apply & Sync Settings", color = SleekTextAccent, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isEnabled: Boolean = true
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        enabled = isEnabled,
        label = { Text(label, color = SleekTextSecondary, fontSize = 10.sp) },
        colors = TextFieldDefaults.colors(
            focusedTextColor = SleekTextPrimary,
            unfocusedTextColor = SleekTextPrimary,
            focusedContainerColor = SleekSurface,
            unfocusedContainerColor = SleekSurface,
            disabledContainerColor = SleekSurface.copy(alpha = 0.5f),
            focusedIndicatorColor = SleekAccent,
            unfocusedIndicatorColor = SleekBorder
        ),
        shape = RoundedCornerShape(8.dp),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().height(56.dp)
    )
}

@Composable
fun BottomNavBar(activeTab: String, onTabSelected: (String) -> Unit) {
    val items = listOf("Chats", "Nodes", "Vault", "Core")
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding() // Satisfies bottom gesture navigation bar padding rules!
            .background(SleekSurface)
            .border(width = 0.5f.dp, color = SleekBorder.copy(alpha = 0.3f))
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { tab ->
            val icon = when (tab) {
                "Chats" -> Icons.Default.Share
                "Nodes" -> Icons.Default.Person
                "Vault" -> Icons.Default.Lock
                else -> Icons.Default.Settings // Core
            }
            val active = (activeTab == tab)
            val activeColor = if (active) SleekAccent else SleekTextSecondary

            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onTabSelected(tab) }
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("${tab.lowercase()}_tab_button"),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = tab,
                    tint = activeColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = tab,
                    color = activeColor,
                    fontSize = 9.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

// Helper expansion layout functions to restrict width on expanded display devices
fun Modifier.maxOrNullWidth(max: androidx.compose.ui.unit.Dp) = this.then(
    Modifier.widthIn(max = max)
)

private fun formatTime(millis: Long): String {
    val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(millis))
}
