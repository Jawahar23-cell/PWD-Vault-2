package com.example.passwordvault

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import java.util.UUID

// === THEME COLORS ===
object VaultColors {
    val DarkBg = Color(0xFF0a0e27)
    val CardBg = Color(0xFF16213e)
    val CyanAccent = Color(0xFF00d4ff)
    val MagentaAccent = Color(0xFFff006e)
    val YellowAccent = Color(0xFFffbe0b)
    val PurpleAccent = Color(0xFF8338ec)
    val TextPrimary = Color(0xFFffffff)
    val TextSecondary = Color(0xFFb0b0b0)
    val ErrorRed = Color(0xFFff006e)
}

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = VaultColors.CyanAccent)) {
                Surface(Modifier.fillMaxSize().background(VaultColors.DarkBg)) {
                    VaultApp()
                }
            }
        }
    }
}

private fun toast(context: Context, msg: String) =
    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = android.content.ClipData.newPlainText("password", text)
    cm.setPrimaryClip(clip)
    toast(context, "Copied! (auto-clears in 30s)")
    
    Handler(Looper.getMainLooper()).postDelayed({
        val emptyClip = android.content.ClipData.newPlainText("", "")
        cm.setPrimaryClip(emptyClip)
    }, 30000)
}

@Composable
fun VaultApp() {
    val context = LocalContext.current
    val activity = context as FragmentActivity
    val crypto = remember { CryptoManager() }
    val store = remember { VaultStore(context) }
    val auth = remember { BiometricAuthenticator(activity) }

    var unlocked by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf(store.load()) }
    var lastActivityTime by remember { mutableStateOf(System.currentTimeMillis()) }

    // Auto-lock after 2 minutes of inactivity
    LaunchedEffect(unlocked) {
        if (unlocked) {
            while (true) {
                val elapsed = System.currentTimeMillis() - lastActivityTime
                if (elapsed > 120000) { // 2 minutes
                    unlocked = false
                    break
                }
                kotlinx.coroutines.delay(5000)
            }
        }
    }

    val updateActivity = {
        lastActivityTime = System.currentTimeMillis()
    }

    if (!unlocked) {
        UnlockScreen(
            isBiometricAvailable = auth.isBiometricAvailable(),
            onUnlock = {
                auth.authenticateBiometric(
                    title = "🔐 Unlock Vault",
                    subtitle = "Verify your fingerprint or face to access your passwords",
                    onSuccess = { unlocked = true },
                    onError = { msg -> toast(context, msg) }
                )
            }
        )
    } else {
        VaultScreen(
            entries = entries,
            onActivity = updateActivity,
            onAdd = { label, secret ->
                val cipher = crypto.encryptCipher()
                auth.authenticateBiometric(
                    title = "💾 Confirm Save",
                    subtitle = "Verify to encrypt \"$label\"",
                    cryptoObject = BiometricPrompt.CryptoObject(cipher),
                    onSuccess = { co ->
                        val c = co!!.cipher!!
                        val ct = c.doFinal(secret.toByteArray(Charsets.UTF_8))
                        val entry = VaultEntry(
                            id = UUID.randomUUID().toString(),
                            label = label,
                            ivBase64 = Base64.encodeToString(c.iv, Base64.NO_WRAP),
                            cipherBase64 = Base64.encodeToString(ct, Base64.NO_WRAP),
                            timestamp = System.currentTimeMillis()
                        )
                        entries = entries + entry
                        store.save(entries)
                        updateActivity()
                    },
                    onError = { msg -> toast(context, msg) }
                )
            },
            onReveal = { entry, onPlain ->
                // ---- NEW: PIN first, then biometric ----
                auth.authenticateDeviceCredential(
                    title = "🔑 Step 1: Verify PIN",
                    subtitle = "Enter your device PIN/pattern for \"${entry.label}\"",
                    onSuccess = {
                        // PIN success → now biometric
                        val iv = Base64.decode(entry.ivBase64, Base64.NO_WRAP)
                        val cipher = crypto.decryptCipher(iv)
                        auth.authenticateBiometric(
                            title = "👆 Step 2: Biometric Verify",
                            subtitle = "Fingerprint or face to decrypt",
                            cryptoObject = BiometricPrompt.CryptoObject(cipher),
                            onSuccess = { co ->
                                val ct = Base64.decode(entry.cipherBase64, Base64.NO_WRAP)
                                val plain = String(co!!.cipher!!.doFinal(ct), Charsets.UTF_8)
                                onPlain(plain)
                                updateActivity()
                            },
                            onError = { msg -> toast(context, msg) }
                        )
                    },
                    onError = { msg -> toast(context, msg) }
                )
            },
            onDelete = { entry ->
                // Require PIN to delete (prevents accidental loss)
                auth.authenticateDeviceCredential(
                    title = "🗑️ Confirm Delete",
                    subtitle = "Enter PIN to permanently delete \"${entry.label}\"",
                    onSuccess = {
                        entries = entries.filterNot { it.id == entry.id }
                        store.save(entries)
                        updateActivity()
                    },
                    onError = { msg -> toast(context, msg) }
                )
            },
            onLock = { unlocked = false }
        )
    }
}

@Composable
private fun UnlockScreen(isBiometricAvailable: Boolean, onUnlock: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(VaultColors.DarkBg)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🔐", fontSize = 80.sp)
        Spacer(Modifier.height(24.dp))
        Text(
            "Password Vault",
            style = MaterialTheme.typography.headlineLarge,
            color = VaultColors.TextPrimary,
            fontSize = 32.sp
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Biometric-Locked",
            style = MaterialTheme.typography.bodyMedium,
            color = VaultColors.CyanAccent
        )
        Spacer(Modifier.height(40.dp))

        Text(
            if (isBiometricAvailable) "Locked. Tap to authenticate." else "Biometric not available.",
            style = MaterialTheme.typography.bodyMedium,
            color = VaultColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Spacer(Modifier.height(48.dp))

        Button(
            onClick = onUnlock,
            enabled = isBiometricAvailable,
            modifier = Modifier
                .height(56.dp)
                .width(200.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = VaultColors.CyanAccent,
                contentColor = VaultColors.DarkBg
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("Unlock", fontSize = 18.sp, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultScreen(
    entries: List<VaultEntry>,
    onActivity: () -> Unit,
    onAdd: (label: String, secret: String) -> Unit,
    onReveal: (VaultEntry, (String) -> Unit) -> Unit,
    onDelete: (VaultEntry) -> Unit,
    onLock: () -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    
    val context = LocalContext.current

    var revealLabel by remember { mutableStateOf<String?>(null) }
    var revealValue by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<VaultEntry?>(null) }
    var searchText by remember { mutableStateOf("") }

    val filtered = entries.filter { it.label.contains(searchText, ignoreCase = true) }
        .sortedByDescending { it.timestamp }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("🔐 My Passwords", color = VaultColors.TextPrimary, fontSize = 20.sp)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VaultColors.CardBg,
                    titleContentColor = VaultColors.TextPrimary
                ),
                actions = {
                    TextButton(
                        onClick = onLock,
                        colors = ButtonDefaults.textButtonColors(contentColor = VaultColors.ErrorRed)
                    ) {
                        Text("Lock", fontSize = 14.sp)
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Add Password", color = VaultColors.DarkBg) },
                icon = { Text("➕") },
                onClick = { showAdd = true; onActivity() },
                containerColor = VaultColors.PurpleAccent,
                contentColor = VaultColors.DarkBg,
                modifier = Modifier.padding(16.dp)
            )
        },
        containerColor = VaultColors.DarkBg
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(VaultColors.DarkBg)
        ) {
            // Search bar
            SearchBar(
                searchText = searchText,
                onSearchChange = { searchText = it; onActivity() },
                modifier = Modifier.padding(12.dp)
            )

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (searchText.isEmpty()) "No passwords yet. Tap Add to store one."
                        else "No matches found.",
                        color = VaultColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .background(VaultColors.DarkBg),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    items(filtered, key = { it.id }) { entry ->
                        PasswordCard(
                            entry = entry,
                            onReveal = { onReveal(entry, it) },
                            onDelete = { onDelete(entry); onActivity() },
                            onActivity = onActivity,
                            revealLabel = revealLabel,
                            revealValue = revealValue,
                            onRevealShow = { label, value ->
                                revealLabel = label
                                revealValue = value
                                onActivity()
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddDialog(
            onDismiss = { showAdd = false; onActivity() },
            onConfirm = { label, secret -> showAdd = false; onAdd(label, secret) },
            onActivity = onActivity
        )
    }

    val localContext = LocalContext.current

    if (revealValue != null) {
        RevealDialog(
            label = revealLabel ?: "Password",
            value = revealValue!!,
            onDismiss = { revealValue = null; revealLabel = null; onActivity() },
            onCopy = {copyToClipboard(context, revealValue!!)}
        )
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null; onActivity() },
            containerColor = VaultColors.CardBg,
            title = { Text("Delete entry?", color = VaultColors.ErrorRed) },
            text = { Text("Remove \"${entry.label}\" permanently?", color = VaultColors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = { onDelete(entry); pendingDelete = null }) {
                    Text("Delete", color = VaultColors.ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null; onActivity() }) {
                    Text("Cancel", color = VaultColors.CyanAccent)
                }
            }
        )
    }
}

@Composable
private fun SearchBar(
    searchText: String,
    onSearchChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = searchText,
        onValueChange = onSearchChange,
        label = { Text("Search passwords...", color = VaultColors.TextSecondary) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = VaultColors.CyanAccent) },
        trailingIcon = {
            if (searchText.isNotEmpty()) {
                IconButton(onClick = { onSearchChange("") }) {
                    Icon(Icons.Default.Close, null, tint = VaultColors.CyanAccent)
                }
            }
        },
        modifier = modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = VaultColors.TextPrimary,
            unfocusedTextColor = VaultColors.TextSecondary,
            focusedBorderColor = VaultColors.CyanAccent,
            unfocusedBorderColor = VaultColors.TextSecondary
        ),
        singleLine = true
    )
}

@Composable
private fun PasswordCard(
    entry: VaultEntry,
    onReveal: ((String) -> Unit) -> Unit,
    onDelete: () -> Unit,
    onActivity: () -> Unit,
    revealLabel: String?,
    revealValue: String?,
    onRevealShow: (String, String) -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = VaultColors.CardBg)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        entry.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = VaultColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Updated: ${entry.getFormattedTime()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = VaultColors.TextSecondary,
                        fontSize = 11.sp
                    )
                }
                Button(
                    onClick = {
                        onReveal { plain ->
                            onRevealShow(entry.label, plain)
                        }
                        onActivity()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VaultColors.CyanAccent),
                    modifier = Modifier
                        .height(40.dp)
                        .padding(end = 8.dp)
                ) {
                    Text("👁️ Reveal", color = VaultColors.DarkBg, fontSize = 12.sp)
                }
                IconButton(onClick = { onDelete() }) {
                    Icon(Icons.Default.Close, null, tint = VaultColors.ErrorRed, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun RevealDialog(
    label: String,
    value: String,
    onDismiss: () -> Unit,
    onCopy: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultColors.CardBg,
        title = {
            Text(
                "🔓 $label",
                color = VaultColors.CyanAccent,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column {
                SelectionText(value)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onCopy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VaultColors.PurpleAccent)
                ) {
                    Icon(Icons.Default.ContentCopy, null, tint = VaultColors.DarkBg, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Copy to Clipboard", color = VaultColors.DarkBg)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = VaultColors.CyanAccent)
            }
        }
    )
}

@Composable
private fun SelectionText(value: String) {
    androidx.compose.foundation.text.selection.SelectionContainer {
        Text(
            value,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
            color = VaultColors.TextPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .background(VaultColors.DarkBg, shape = MaterialTheme.shapes.small)
                .padding(12.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddDialog(
    onDismiss: () -> Unit,
    onConfirm: (label: String, secret: String) -> Unit,
    onActivity: () -> Unit
) {
    var label by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var generatedPassword by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultColors.CardBg,
        title = {
            Text("➕ Add New Password", color = VaultColors.PurpleAccent)
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it; onActivity() },
                    label = { Text("Label (e.g. Gmail)", color = VaultColors.TextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = VaultColors.TextPrimary,
                        unfocusedTextColor = VaultColors.TextSecondary,
                        focusedBorderColor = VaultColors.CyanAccent
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = secret,
                    onValueChange = { secret = it; onActivity() },
                    label = { Text("Password", color = VaultColors.TextSecondary) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                null,
                                tint = VaultColors.CyanAccent
                            )
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = VaultColors.TextPrimary,
                        unfocusedTextColor = VaultColors.TextSecondary,
                        focusedBorderColor = VaultColors.CyanAccent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                // Password strength indicator
                if (secret.isNotEmpty()) {
                    val strength = PasswordUtils.assessStrength(secret)
                    val (color, label) = PasswordUtils.getStrengthDisplay(strength)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Strength: ", color = VaultColors.TextSecondary, fontSize = 12.sp)
                        Text(
                            label,
                            color = Color(android.graphics.Color.parseColor("#$color")),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // Generate button
                Button(
                    onClick = {
                        generatedPassword = PasswordUtils.generatePassword(16)
                        secret = generatedPassword
                        onActivity()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VaultColors.YellowAccent)
                ) {
                    Text("🎲 Generate", color = VaultColors.DarkBg, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = label.isNotBlank() && secret.isNotEmpty(),
                onClick = { onConfirm(label.trim(), secret); onActivity() }
            ) {
                Text("Save", color = VaultColors.CyanAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) {
                Text("Cancel", color = VaultColors.ErrorRed)
            }
        }
    )
}
