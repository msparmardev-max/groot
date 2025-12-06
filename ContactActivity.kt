package com.example.groot.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.groot.birthday.BirthdayManager
import com.example.groot.birthday.Contact
import com.example.groot.ui.theme.GrootTheme
import kotlinx.coroutines.launch
import java.util.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll


class ContactActivity : ComponentActivity() {

    private lateinit var birthdayManager: BirthdayManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        birthdayManager = BirthdayManager(this)

        setContent {
            GrootTheme {
                ContactManagementScreen()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ContactManagementScreen() {
        var contacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
        var isLoading by remember { mutableStateOf(false) }
        var searchQuery by remember { mutableStateOf("") }
        var showStatsDialog by remember { mutableStateOf(false) }
        var selectedContact by remember { mutableStateOf<Contact?>(null) }
        var showBirthdayDialog by remember { mutableStateOf(false) }
        var showAddContactDialog by remember { mutableStateOf(false) }

        // Load contacts on first launch
        LaunchedEffect(Unit) {
            loadContacts { contacts = it }
        }

        // Filter contacts based on search
        val filteredContacts = remember(contacts, searchQuery) {
            if (searchQuery.isEmpty()) {
                contacts
            } else {
                contacts.filter { contact ->
                    contact.name.contains(searchQuery, ignoreCase = true) ||
                            contact.phoneNumber.contains(searchQuery)
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("📱 Contact Manager") },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.Default.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showStatsDialog = true }) {
                            Icon(Icons.Default.Info, "Statistics")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF1A237E),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
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
                // Statistics Card
                StatisticsCard(contacts)

                Spacer(modifier = Modifier.height(8.dp))

                // Action Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            isLoading = true
                            lifecycleScope.launch {
                                val count = birthdayManager.scanAndSaveContacts()
                                loadContacts { contacts = it }
                                isLoading = false
                                Toast.makeText(
                                    this@ContactActivity,
                                    "✅ Scanned $count contacts",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        ),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, "Scan", modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Scan", fontSize = 14.sp)
                    }

                    Button(
                        onClick = {
                            lifecycleScope.launch {
                                val upcoming = birthdayManager.getUpcomingBirthdays()
                                contacts = upcoming
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2196F3)
                        )
                    ) {
                        Icon(Icons.Default.DateRange, "Upcoming", modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Upcoming", fontSize = 14.sp)
                    }

                    Button(
                        onClick = {
                            loadContacts { contacts = it }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF9800)
                        )
                    ) {
                        Icon(Icons.Default.List, "All", modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("All", fontSize = 14.sp)
                    }

                    Button(
                        onClick = { showAddContactDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF9C27B0)
                        )
                    ) {
                        Icon(Icons.Default.Add, "Add", modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add", fontSize = 14.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    placeholder = { Text("🔍 Search contacts...") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF1A237E),
                        unfocusedBorderColor = Color.Gray
                    ),
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, "Clear")
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Contacts Header
                Text(
                    text = "📋 Contacts (${filteredContacts.size})",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Contacts List
                if (filteredContacts.isEmpty() && !isLoading) {
                    EmptyState(
                        hasContacts = contacts.isNotEmpty(),
                        searchQuery = searchQuery
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredContacts) { contact ->
                            ContactCard(
                                contact = contact,
                                onSetBirthday = {
                                    selectedContact = contact
                                    showBirthdayDialog = true
                                },
                                onDelete = {
                                    lifecycleScope.launch {
                                        birthdayManager.deleteContact(contact.contactId)
                                        loadContacts { contacts = it }
                                        Toast.makeText(
                                            this@ContactActivity,
                                            "🗑️ Contact deleted",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                        }
                        // Add bottom padding
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }
            }
        }

        // Statistics Dialog
        if (showStatsDialog) {
            StatisticsDialog(
                contacts = contacts,
                onDismiss = { showStatsDialog = false }
            )
        }

        // Birthday Dialog
        if (showBirthdayDialog && selectedContact != null) {
            BirthdayInputDialog(
                contact = selectedContact!!,
                onDismiss = {
                    showBirthdayDialog = false
                    selectedContact = null
                },
                onConfirm = { contact, birthday ->
                    lifecycleScope.launch {
                        val success = birthdayManager.setContactBirthday(contact.name, birthday)
                        if (success) {
                            loadContacts { contacts = it }
                            Toast.makeText(
                                this@ContactActivity,
                                "✅ Birthday saved for ${contact.name}",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                this@ContactActivity,
                                "❌ Failed to save birthday",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        showBirthdayDialog = false
                        selectedContact = null
                    }
                }
            )
        }

        // Add Contact Dialog
        if (showAddContactDialog) {
            AddContactDialog(
                onDismiss = {
                    showAddContactDialog = false
                },
                onConfirm = { name, phoneNumber, birthday ->
                    lifecycleScope.launch {
                        val newContact = Contact(
                            contactId = java.util.UUID.randomUUID().toString(),
                            name = name,
                            phoneNumber = phoneNumber,
                            dateOfBirth = birthday,
                            isAutoWishEnabled = true
                        )

                        val success = birthdayManager.addContact(newContact)

                        if (success) {
                            loadContacts { contacts = it }
                            Toast.makeText(
                                this@ContactActivity,
                                "✅ Contact added: $name",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                this@ContactActivity,
                                "❌ Failed to add contact",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        showAddContactDialog = false
                    }
                }
            )
        }
    }

    @Composable
    private fun StatisticsCard(contacts: List<Contact>) {
        val totalContacts = contacts.size
        val withBirthdays = contacts.count { it.dateOfBirth != null }
        val withoutBirthdays = totalContacts - withBirthdays
        val percentage = if (totalContacts > 0) (withBirthdays * 100) / totalContacts else 0

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1A237E)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "📊 Statistics",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatItem("📱 Total", totalContacts.toString(), Color.White)
                    StatItem("🎂 With Birthday", withBirthdays.toString(), Color(0xFF4CAF50))
                    StatItem("❌ Missing", withoutBirthdays.toString(), Color(0xFFFF9800))
                    StatItem("📈 Progress", "$percentage%", Color(0xFF2196F3))
                }
            }
        }
    }

    @Composable
    private fun StatItem(label: String, value: String, color: Color) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFB0BEC5)
            )
        }
    }

    @Composable
    private fun ContactCard(
        contact: Contact,
        onSetBirthday: () -> Unit,
        onDelete: () -> Unit
    ) {
        val isBirthdayToday = contact.isBirthdayToday()
        val cardColor = if (isBirthdayToday) Color(0xFFFFF3E0) else Color.White

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(containerColor = cardColor)
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
                                text = contact.name + if (isBirthdayToday) " 🎉" else "",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "📞 ${contact.phoneNumber}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }

                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFFF44336)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Birthday Section
                if (contact.dateOfBirth != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "🎂 Birthday",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                            Text(
                                text = contact.getFormattedBirthday() ?: contact.dateOfBirth!!,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = Color(0xFF4CAF50)
                            )
                            if (contact.getAge() != null) {
                                Text(
                                    text = "Age: ${contact.getAge()} years",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }

                        Button(
                            onClick = onSetBirthday,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2196F3)
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit", fontSize = 12.sp)
                        }
                    }
                } else {
                    Button(
                        onClick = onSetBirthday,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF9800)
                        )
                    ) {
                        Icon(Icons.Default.Add, "Add Birthday")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Birthday")
                    }
                }

                if (isBirthdayToday) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "🎈 Birthday Today!",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color(0xFFFF5722)
                    )
                }
            }
        }
    }

    @Composable
    private fun EmptyState(hasContacts: Boolean, searchQuery: String) {
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
                    text = if (searchQuery.isNotEmpty()) "🔍" else "📭",
                    fontSize = 64.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (searchQuery.isNotEmpty()) {
                        "No contacts found"
                    } else if (hasContacts) {
                        "No contacts with birthdays"
                    } else {
                        "No contacts yet"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (searchQuery.isNotEmpty()) {
                        "Try a different search"
                    } else if (hasContacts) {
                        "Tap 'All' to see all contacts"
                    } else {
                        "Tap 'Scan Contacts' to import from phone"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }
        }
    }

    @Composable
    private fun BirthdayInputDialog(
        contact: Contact,
        onDismiss: () -> Unit,
        onConfirm: (Contact, String) -> Unit
    ) {
        var day by remember { mutableStateOf("") }
        var month by remember { mutableStateOf("") }
        var year by remember { mutableStateOf("") }

        // Pre-fill if birthday exists
        LaunchedEffect(contact.dateOfBirth) {
            contact.dateOfBirth?.let { dob ->
                val parts = dob.split("/")
                if (parts.size == 3) {
                    day = parts[0]
                    month = parts[1]
                    year = parts[2]
                }
            }
        }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("🎂 Set Birthday for ${contact.name}") },
            text = {
                Column {
                    Text(
                        text = "Enter birthday date:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Day
                        OutlinedTextField(
                            value = day,
                            onValueChange = {
                                if (it.length <= 2 && it.all { char -> char.isDigit() }) {
                                    day = it
                                }
                            },
                            label = { Text("DD") },
                            placeholder = { Text("01") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        // Month
                        OutlinedTextField(
                            value = month,
                            onValueChange = {
                                if (it.length <= 2 && it.all { char -> char.isDigit() }) {
                                    month = it
                                }
                            },
                            label = { Text("MM") },
                            placeholder = { Text("08") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        // Year
                        OutlinedTextField(
                            value = year,
                            onValueChange = {
                                if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                    year = it
                                }
                            },
                            label = { Text("YYYY") },
                            placeholder = { Text("1995") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1.5f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Date Picker Button
                    OutlinedButton(
                        onClick = {
                            showDatePicker(contact) { selectedDate ->
                                val parts = selectedDate.split("/")
                                if (parts.size == 3) {
                                    day = parts[0]
                                    month = parts[1]
                                    year = parts[2]
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DateRange, "Pick Date")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Or Pick from Calendar")
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Format: DD/MM/YYYY (e.g., 01/08/1995)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (day.isNotBlank() && month.isNotBlank() && year.isNotBlank()) {
                            val birthday = String.format(
                                "%02d/%02d/%04d",
                                day.toInt(),
                                month.toInt(),
                                year.toInt()
                            )
                            onConfirm(contact, birthday)
                        }
                    },
                    enabled = day.isNotBlank() && month.isNotBlank() && year.isNotBlank()
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
    private fun StatisticsDialog(
        contacts: List<Contact>,
        onDismiss: () -> Unit
    ) {
        val totalContacts = contacts.size
        val withBirthdays = contacts.count { it.dateOfBirth != null }
        val withoutBirthdays = totalContacts - withBirthdays

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("📊 Detailed Statistics") },
            text = {
                Column {
                    StatRow("Total Contacts", totalContacts.toString(), "📱")
                    StatRow("With Birthdays", withBirthdays.toString(), "🎂")
                    StatRow("Without Birthdays", withoutBirthdays.toString(), "❌")

                    if (totalContacts > 0) {
                        val percentage = (withBirthdays * 100) / totalContacts
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = percentage / 100f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = Color(0xFF4CAF50)
                        )
                        Text(
                            text = "$percentage% Complete",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        )
    }

    @Composable
    private fun StatRow(label: String, value: String, icon: String) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = icon, fontSize = 20.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = Color(0xFF1A237E)
            )
        }
    }

    private fun showDatePicker(contact: Contact, onDateSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()

        // Pre-fill with existing birthday if available
        contact.dateOfBirth?.let { dob ->
            val parts = dob.split("/")
            if (parts.size == 3) {
                calendar.set(Calendar.DAY_OF_MONTH, parts[0].toInt())
                calendar.set(Calendar.MONTH, parts[1].toInt() - 1)
                calendar.set(Calendar.YEAR, parts[2].toInt())
            }
        }

        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val birthday = String.format("%02d/%02d/%04d", dayOfMonth, month + 1, year)
                onDateSelected(birthday)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun loadContacts(onLoaded: (List<Contact>) -> Unit) {
        lifecycleScope.launch {
            val allContacts = birthdayManager.getAllBirthdays()
            onLoaded(allContacts)
        }
    }

    @Composable
    private fun AddContactDialog(
        onDismiss: () -> Unit,
        onConfirm: (name: String, phoneNumber: String, birthday: String?) -> Unit
    ) {
        var name by remember { mutableStateOf("") }
        var phoneNumber by remember { mutableStateOf("") }
        var day by remember { mutableStateOf("") }
        var month by remember { mutableStateOf("") }
        var year by remember { mutableStateOf("") }
        var includeBirthday by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("➕ Add New Contact") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Create a new contact in Groot",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Name Field
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name *") },
                        placeholder = { Text("e.g., Rajesh Kumar") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(Icons.Default.Person, "Name")
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Phone Number Field
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = {
                            // Only allow digits
                            if (it.all { char -> char.isDigit() } && it.length <= 10) {
                                phoneNumber = it
                            }
                        },
                        label = { Text("Phone Number *") },
                        placeholder = { Text("9876543210") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(Icons.Default.Phone, "Phone")
                        },
                        prefix = { Text("+91 ") }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Divider()

                    Spacer(modifier = Modifier.height(12.dp))

                    // Birthday Toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = includeBirthday,
                            onCheckedChange = { includeBirthday = it }
                        )
                        Text(
                            text = "🎂 Add Birthday (Optional)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    // Birthday Fields (conditional)
                    AnimatedVisibility(visible = includeBirthday) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Birthday Date:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = day,
                                    onValueChange = {
                                        if (it.length <= 2 && it.all { char -> char.isDigit() }) {
                                            val dayInt = it.toIntOrNull() ?: 0
                                            if (dayInt in 0..31) {
                                                day = it
                                            }
                                        }
                                    },
                                    label = { Text("DD") },
                                    placeholder = { Text("01") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )

                                OutlinedTextField(
                                    value = month,
                                    onValueChange = {
                                        if (it.length <= 2 && it.all { char -> char.isDigit() }) {
                                            val monthInt = it.toIntOrNull() ?: 0
                                            if (monthInt in 0..12) {
                                                month = it
                                            }
                                        }
                                    },
                                    label = { Text("MM") },
                                    placeholder = { Text("08") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )

                                OutlinedTextField(
                                    value = year,
                                    onValueChange = {
                                        if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                            year = it
                                        }
                                    },
                                    label = { Text("YYYY") },
                                    placeholder = { Text("1995") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1.5f)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Format: DD/MM/YYYY",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "* Required fields",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFF44336)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank() && phoneNumber.isNotBlank()) {
                            val birthday = if (includeBirthday &&
                                day.isNotBlank() &&
                                month.isNotBlank() &&
                                year.isNotBlank()) {
                                String.format(
                                    "%02d/%02d/%04d",
                                    day.toInt(),
                                    month.toInt(),
                                    year.toInt()
                                )
                            } else {
                                null
                            }

                            onConfirm(name.trim(), "+91$phoneNumber", birthday)
                        }
                    },
                    enabled = name.isNotBlank() && phoneNumber.isNotBlank() && phoneNumber.length == 10
                ) {
                    Icon(Icons.Default.Check, "Add")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Contact")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add delete method to BirthdayManager (if not exists)
    private suspend fun BirthdayManager.deleteContact(contactId: String) {
        // You'll need to add this method to BirthdayManager.kt
        // For now, you can leave it or implement it
    }
}