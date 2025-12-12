package com.example.groot.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.groot.ui.theme.GrootTheme
import com.example.groot.user.EmailPreferences

class EmailManagementActivity : ComponentActivity() {

    private lateinit var emailPreferences: EmailPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        emailPreferences = EmailPreferences(this)

        setContent {
            GrootTheme {
                EmailManagementScreen()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun EmailManagementScreen() {
        var contacts by remember { mutableStateOf(emailPreferences.loadContacts()) }
        var showAddDialog by remember { mutableStateOf(false) }
        var showEditDialog by remember { mutableStateOf(false) }
        var selectedContact by remember { mutableStateOf<Pair<String, String>?>(null) }
        var showSenderDialog by remember { mutableStateOf(false) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Email Management") },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.Default.ArrowBack, "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF1A237E),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color(0xFFF5F5F5))
            ) {
                // Sender Account Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "📧 Your Email Account",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val senderEmail = emailPreferences.getSenderEmail()
                                Text(
                                    text = senderEmail ?: "Not configured",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (senderEmail != null) Color(0xFF4CAF50) else Color.Gray
                                )
                                if (senderEmail != null) {
                                    Text(
                                        text = "✅ Connected",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF4CAF50)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { showSenderDialog = true }
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit",
                                    tint = Color(0xFF1A237E)
                                )
                            }
                        }
                    }
                }

                // Add Contact Button
                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    )
                ) {
                    Icon(Icons.Default.Add, "Add")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Email Contact")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Contacts Header
                Text(
                    text = "📋 Saved Recipients (${contacts.size})",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Contacts List
                if (contacts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "📭",
                                fontSize = 48.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No contacts yet",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.Gray
                            )
                            Text(
                                text = "Add your first contact to start sending emails",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(contacts.entries.toList()) { entry ->
                            ContactCard(
                                name = entry.key,
                                email = entry.value,
                                onEdit = {
                                    selectedContact = Pair(entry.key, entry.value)
                                    showEditDialog = true
                                },
                                onDelete = {
                                    emailPreferences.removeContact(entry.key)
                                    contacts = emailPreferences.loadContacts()
                                    Toast.makeText(
                                        this@EmailManagementActivity,
                                        "Contact deleted",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                    }
                }
            }
        }

        // Add Dialog
        if (showAddDialog) {
            AddContactDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { name, email ->
                    emailPreferences.addContact(name, email)
                    contacts = emailPreferences.loadContacts()
                    showAddDialog = false
                    Toast.makeText(
                        this@EmailManagementActivity,
                        "✅ Contact added",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }

        // Edit Dialog
        if (showEditDialog && selectedContact != null) {
            EditContactDialog(
                currentName = selectedContact!!.first,
                currentEmail = selectedContact!!.second,
                onDismiss = {
                    showEditDialog = false
                    selectedContact = null
                },
                onConfirm = { newName, newEmail ->
                    // Remove old, add new
                    emailPreferences.removeContact(selectedContact!!.first)
                    emailPreferences.addContact(newName, newEmail)
                    contacts = emailPreferences.loadContacts()
                    showEditDialog = false
                    selectedContact = null
                    Toast.makeText(
                        this@EmailManagementActivity,
                        "✅ Contact updated",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }

        // Sender Edit Dialog
        if (showSenderDialog) {
            EditSenderDialog(
                currentEmail = emailPreferences.getSenderEmail() ?: "",
                onDismiss = { showSenderDialog = false },
                onConfirm = { email, password ->
                    emailPreferences.saveSenderCredentials(email, password)
                    showSenderDialog = false
                    Toast.makeText(
                        this@EmailManagementActivity,
                        "✅ Email account updated",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }
    }

    @Composable
    private fun ContactCard(
        name: String,
        email: String,
        onEdit: () -> Unit,
        onDelete: () -> Unit
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "Contact",
                        tint = Color(0xFF1A237E),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = name.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = email,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }

                Row {
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = Color(0xFF2196F3)
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFFF44336)
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun AddContactDialog(
        onDismiss: () -> Unit,
        onConfirm: (String, String) -> Unit
    ) {
        var name by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Add Email Contact") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        placeholder = { Text("e.g., Ram") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        placeholder = { Text("ram@example.com") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank() && email.isNotBlank()) {
                            onConfirm(name.trim(), email.trim())
                        }
                    },
                    enabled = name.isNotBlank() && email.isNotBlank()
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }

    @Composable
    private fun EditContactDialog(
        currentName: String,
        currentEmail: String,
        onDismiss: () -> Unit,
        onConfirm: (String, String) -> Unit
    ) {
        var name by remember { mutableStateOf(currentName) }
        var email by remember { mutableStateOf(currentEmail) }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Edit Contact") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank() && email.isNotBlank()) {
                            onConfirm(name.trim(), email.trim())
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }

    @Composable
    private fun EditSenderDialog(
        currentEmail: String,
        onDismiss: () -> Unit,
        onConfirm: (String, String) -> Unit
    ) {
        var email by remember { mutableStateOf(currentEmail) }
        var password by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Update Email Account") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "Enter your Gmail and App Password",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Gmail Address") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("App Password") },
                        placeholder = { Text("16-char password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (email.isNotBlank() && password.isNotBlank()) {
                            onConfirm(email.trim(), password.replace(" ", ""))
                        }
                    }
                ) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }
}