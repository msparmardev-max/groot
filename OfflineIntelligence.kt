package com.example.groot

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * Offline Intelligence System
 * Handles basic queries and tasks without server connection
 */
class OfflineIntelligence {

    companion object {
        private const val TAG = "OfflineIntelligence"
    }

    data class OfflineResponse(
        val reply: String,
        val action: String,
        val target: String,
        val handled: Boolean
    )

    /**
     * Try to handle the command offline with TaskAutomationManager support
     */
    fun handleOffline(command: String, taskManager: TaskAutomationManager? = null): OfflineResponse {
        Log.d(TAG, "Processing command: $command")
        Log.d(TAG, "actual taskManager: $taskManager")
        val lowerCommand = command.lowercase().trim()
        Log.i(TAG,"lowercommand : $lowerCommand")
        val isHindi = LanguageDetector.isHindiIntent(command)

        // CONTACT & CALL LOG FEATURES (NEW)
        when {
            // Context-aware pronouns
            (lowerCommand.contains("usko") || lowerCommand.contains("unko") ||
                    lowerCommand.contains("उसको") || lowerCommand.contains("उनको")) -> {
                return OfflineResponse(
                    reply = "",
                    action = "use_context",
                    target = command,
                    handled = true
                )
            }
                // Get specific contact number - WITH DISAMBIGUATION
                    (lowerCommand.contains("what") || lowerCommand.contains("kya") || lowerCommand.contains("क्या")) ||
                    (lowerCommand.contains("number") || lowerCommand.contains("contact") ||
                            lowerCommand.contains("नंबर") || lowerCommand.contains("संपर्क")) -> {
            val nameMatch = Regex("(?:of|for|का|के|ka)\\s+(.+?)(?:\\s+number|\\s+contact|\\s+नंबर|\\s+संपर्क|$)",
                RegexOption.IGNORE_CASE).find(command)

            if (nameMatch != null && taskManager != null) {
                val contactName = nameMatch.groupValues[1].trim()
                Log.d(TAG, "🔍 Searching for contact: $contactName")

                val searchResult = taskManager.searchContactWithDisambiguation(contactName.lowercase())
                Log.d(TAG, "📊 Search result: isSingleMatch=${searchResult.isSingleMatch}, multipleMatches=${searchResult.multipleMatches?.size}")

                if (searchResult.isSingleMatch && searchResult.singleContact != null) {
                    // Single match - return directly
                    Log.d(TAG, "✅ Single match, returning number")
                    return OfflineResponse(
                        reply = if (isHindi) "$contactName का नंबर है: ${searchResult.singleContact}"
                        else "$contactName's number is: ${searchResult.singleContact}",
                        action = "none",
                        target = "",
                        handled = true
                    )
                } else if (searchResult.multipleMatches != null && searchResult.multipleMatches.isNotEmpty()) {
                    // Multiple matches - let GrootService handle disambiguation
                    Log.d(TAG, "🔀 Multiple matches (${searchResult.multipleMatches.size}), action=get_number")
                    return OfflineResponse(
                        reply = "", // Will be set by GrootService
                        action = "get_number",
                        target = contactName,
                        handled = true
                    )
                } else {
                    // No match
                    return OfflineResponse(
                        reply = if (isHindi) "मुझे $contactName का संपर्क नहीं मिला"
                        else "I couldn't find contact for $contactName",
                        action = "none",
                        target = "",
                        handled = true
                    )
                }
            }
        }

            // Check last call / who called
            lowerCommand.contains("who called last ") ||
                    lowerCommand.contains("last call") ||
                    lowerCommand.contains("antim call") ||
                    lowerCommand.contains("aakhri call") ||
                    lowerCommand.contains("recent call") ||
                    lowerCommand.contains("किसने कॉल किया") ||
                    lowerCommand.contains("पिछली कॉल") ||
                    lowerCommand.contains("अंतिम कॉल") -> {
                if (taskManager != null) {
                    val lastNumber = taskManager.getLastCallInfo()
                    if (lastNumber != null) {
                        return OfflineResponse(
                            reply = if (isHindi) "अंतिम कॉल $lastNumber से थी"
                            else "Last call was from $lastNumber",
                            action = "none",
                            target = "",
                            handled = true
                        )
                    } else {
                        return OfflineResponse(
                            reply = if (isHindi) "कोई हाल की कॉल नहीं मिली"
                            else "No recent calls found",
                            action = "none",
                            target = "",
                            handled = true
                        )
                    }
                }
            }

            // Show all contacts

            lowerCommand.contains("show") || lowerCommand.contains("so") &&
                    (lowerCommand.contains("contact") || lowerCommand.contains("संपर्क")) -> {
                Log.i(TAG,"lowercommand for so my all contacts: $lowerCommand")
                if (taskManager != null) {
                    val contacts = taskManager.getAllPhoneContacts()
                    if (contacts.isNotEmpty()) {
                        val contactList = contacts.entries.take(5).joinToString("\n") { "${it.key}: ${it.value}" }
                        return OfflineResponse(
                            reply = if (isHindi) "आपके ${contacts.size} संपर्क हैं। पहले कुछ:\n$contactList"
                            else "You have ${contacts.size} contacts. First few:\n$contactList",
                            action = "none",
                            target = "",
                            handled = true
                        )
                    } else {
                        return OfflineResponse(
                            reply = if (isHindi) "कोई संपर्क नहीं मिले"
                            else "No contacts found",
                            action = "none",
                            target = "",
                            handled = true
                        )
                    }
                }
            }

            /// 1️⃣ Save last call as contact (English/Hindi)
            (lowerCommand.contains("save") || lowerCommand.contains("add") ||
                    lowerCommand.contains("सेव") || lowerCommand.contains("जोड़")) &&
                    (lowerCommand.contains("last call") || lowerCommand.contains("recent call") ||
                            lowerCommand.contains("पिछली कॉल") || lowerCommand.contains("अंतिम कॉल")) -> {

                // Flexible target for handleAddContact
                val targetText = command.trim() // full command
                return OfflineResponse(
                    reply = if (isHindi) "अंतिम कॉल को संपर्क में सहेज रहा हूँ"
                    else "Saving last call as contact",
                    action = "add_contact",
                    target = targetText,
                    handled = true
                )
            }

            // Add new contact - Enhanced pattern matching
                lowerCommand.contains("add contact") ||
                lowerCommand.contains("save contact") ||
                lowerCommand.contains("संपर्क जोड़ो") ||
                lowerCommand.contains("संपर्क सेव करो") ||
                lowerCommand.contains("sev karo") ||
                lowerCommand.contains("सेव करो") ||
                lowerCommand.contains("save kar") ||
                lowerCommand.contains("सेव कर") ||
                (lowerCommand.contains("save") && lowerCommand.contains("number")) ||
                (lowerCommand.contains("सेव") && lowerCommand.contains("नंबर")) ||
                (lowerCommand.matches(Regex(".*\\d{7,13}.*")) &&
                        (lowerCommand.contains("naam") || lowerCommand.contains("नाम") ||
                                lowerCommand.contains("save") || lowerCommand.contains("सेव")))-> {
                // Parse contact from command
                val parsed = ContactUtils.parseContactFromText(command) // use original 'command'
                val name = parsed.name
                val phone = parsed.phone

                if (name.isNotEmpty() && phone.isNotEmpty()) {
                    val target = "${name}:${phone}"  // Always name:number
                    return OfflineResponse(
                        reply = if (isHindi) "$name का नंबर $phone सेव कर रहा हूँ"
                        else "Saving contact $name with number $phone",
                        action = "add_contact",
                        target = target,
                        handled = true
                    )
                } else {
                    return OfflineResponse(
                        reply = if (isHindi) "कृपया नाम और नंबर दोनों बताइए ताकि contact save कर सकूँ"
                        else "Please provide both name and number to save the contact",
                        action = "none",
                        target = "",
                        handled = true
                    )
                }
            }
                    // Identity questions
            lowerCommand.contains("what is your name") ||
                    lowerCommand.contains("who are you") ||
                    lowerCommand.contains("your name") ||
                    lowerCommand.contains("तुम कौन हो") ||
                    lowerCommand.contains("तुम्हारा नाम") ||
                    lowerCommand.contains("नाम क्या है") ||
                    lowerCommand.contains("आपका नाम") -> {
                return OfflineResponse(
                    reply = if (isHindi) "मैं ग्रूट हूँ, आपका निजी AI सहायक। कॉल, मैसेज और दैनिक कार्यों में आपकी मदद करने के लिए यहाँ हूँ!"
                    else "I am Groot, your personal AI assistant. I'm here to help with calls, messages, and daily tasks!",
                    action = "none",
                    target = "",
                    handled = true
                )
            }

            // How are you
            lowerCommand.contains("how are you") ||
                    lowerCommand.contains("kaise ho") ||
                    lowerCommand.contains("कैसे हो") ||
                    lowerCommand.contains("तुम कैसे") ||
                    lowerCommand.contains("how r u") -> {
                return OfflineResponse(
                    reply = if (isHindi) "मैं बहुत अच्छा हूँ और आपकी मदद के लिए तैयार हूँ! आप आज कैसे हैं?"
                    else "I'm doing great and ready to assist! How can I help you today?",
                    action = "none",
                    target = "",
                    handled = true
                )
            }

            // Thank you responses
            lowerCommand.contains("thank") ||
                    lowerCommand.contains("thanks") ||
                    lowerCommand.contains("dhanyvad") ||
                    lowerCommand.contains("dhanyavad") ||
                    lowerCommand.contains("dhanyawad") ||
                    lowerCommand.contains("shukriya") ||
                    lowerCommand.contains("thankyou") ||
                    lowerCommand.contains("धन्यवाद") ||
                    lowerCommand.contains("शुक्रिया") -> {
                return OfflineResponse(
                    reply = ResponseVariations.getThankYouResponse(isHindi),
                    action = "prompt_continue",
                    target = "",
                    handled = true
                )
            }
            // Greetings
            // Greetings - WITH AUTO MIC RESTART
            lowerCommand.contains("hello") ||
                    lowerCommand.contains("hi") ||
                    lowerCommand.contains("hey") ||
                    lowerCommand.contains("namaste") ||
                    lowerCommand.contains("नमस्ते") -> {
                val greeting = ResponseVariations.getTimeBasedGreeting(isHindi)
                val intro = if (isHindi) "मैं Groot हूँ। कैसे मदद करूं?"
                else "I'm Groot. How can I help you?"

                return OfflineResponse(
                    reply = "$greeting! $intro",
                    action = "prompt_continue",
                    target = "",
                    handled = true
                )
            }

            lowerCommand.contains("good morning") ||
                    lowerCommand.contains("गुड मॉर्निंग") ||
                    lowerCommand.contains("सुप्रभात") -> {
                return OfflineResponse(
                    reply = if (isHindi) "सुप्रभात! आपके दिन की शानदार शुरुआत हो!"
                    else "Good morning! Hope you have a wonderful day ahead!",
                    action = "none",
                    target = "",
                    handled = true
                )
            }

            lowerCommand.contains("good night") ||
                    lowerCommand.contains("शुभ रात्रि") -> {
                return OfflineResponse(
                    reply = if (isHindi) "शुभ रात्रि! अच्छे सपने देखें!"
                    else "Good night! Sleep well and sweet dreams!",
                    action = "none",
                    target = "",
                    handled = true
                )
            }

            lowerCommand.contains("hello") ||
                    lowerCommand.contains("hi") ||
                    lowerCommand.contains("hey") ||
                    lowerCommand.contains("namaste") ||
                    lowerCommand.contains("नमस्ते") -> {
                val greeting = ResponseVariations.getTimeBasedGreeting(isHindi)
                val intro = if (isHindi) "मैं Groot हूँ। कैसे मदद करूं?"
                else "I'm Groot. How can I help you?"

                return OfflineResponse(
                    reply = "$greeting! $intro",
                    action = "prompt_continue",
                    target = "",
                    handled = true
                )
            }

            // Time queries
            lowerCommand.contains("time") ||
                    lowerCommand.contains("समय") ||
                    lowerCommand.contains("samay") -> {
                val currentTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                return OfflineResponse(
                    reply = if (isHindi) "वर्तमान समय $currentTime है"
                    else "The current time is $currentTime",
                    action = "time",
                    target = "",
                    handled = true
                )
            }

            // Date queries
            lowerCommand.contains("date") ||
                    lowerCommand.contains("तारीख") ||
                    lowerCommand.contains("today") ||
                    lowerCommand.contains("tarikh") -> {
                val currentDate = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault()).format(Date())
                return OfflineResponse(
                    reply = if (isHindi) "आज $currentDate है"
                    else "Today is $currentDate",
                    action = "date",
                    target = "",
                    handled = true
                )
            }

            // Day queries
            lowerCommand.contains("what day") ||
                    lowerCommand.contains("कौन सा दिन") ||
                    lowerCommand.contains("दिन क्या है") -> {
                val day = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date())
                return OfflineResponse(
                    reply = if (isHindi) "आज $day है"
                    else "Today is $day",
                    action = "none",
                    target = "",
                    handled = true
                )
            }

            // Call commands
            lowerCommand.contains("call") ||
                    lowerCommand.contains("phone") ||
                    lowerCommand.contains("कॉल") ||
                    lowerCommand.contains("dial") -> {
                val contact = extractContact(lowerCommand)
                if (contact.isNotEmpty()) {
                    return OfflineResponse(
                        reply = if (isHindi) "$contact को कॉल कर रहा हूँ"
                        else "Calling $contact",
                        action = "call",
                        target = contact,
                        handled = true
                    )
                } else {
                    return OfflineResponse(
                        reply = if (isHindi) "किसे कॉल करना चाहेंगे?"
                        else "Who would you like me to call?",
                        action = "none",
                        target = "",
                        handled = true
                    )
                }
            }

            // SMS commands - WITH DISAMBIGUATION
            lowerCommand.contains("message") ||
                    lowerCommand.contains("ko message karo") ||
                    lowerCommand.contains("send") ||
                    lowerCommand.contains("text") ||
                    lowerCommand.contains("sms") ||
                    lowerCommand.contains("मैसेज") -> {
                        Log.i(TAG, "inside message logic :$command")

                val smsData = extractMessageImproved(command)
                Log.e(TAG,"smsData : $smsData")

                if (smsData.contact.isNotEmpty() && smsData.message.isNotEmpty()) {
                    return OfflineResponse(
                        reply = "", // Will be set by GrootService after disambiguation
                        action = "sms",
                        target = "${smsData.contact}:${smsData.message}",
                        handled = true
                    )
                } else if (smsData.contact.isNotEmpty() && smsData.message.isEmpty()) {
                    // Only contact, no message - ask for message
                    return OfflineResponse(
                        reply = if (isHindi) "${smsData.contact} को क्या मैसेज भेजना है?"
                        else "What message do you want to send to ${smsData.contact}?",
                        action = "pending_sms_message",  // NEW action
                        target = smsData.contact,
                        handled = true
                    )
                } else if (smsData.contact.isEmpty()) {
                    return OfflineResponse(
                        reply = if (isHindi) "किसे मैसेज भेजना है?"
                        else "Who do you want to message?",
                        action = "none",
                        target = "",
                        handled = true
                    )
                } else {
                    return OfflineResponse(
                        reply = if (isHindi) "क्या मैसेज भेजना है?"
                        else "What message do you want to send?",
                        action = "none",
                        target = "",
                        handled = true
                    )
                }
            }

            // Open app commands
            lowerCommand.contains("open") &&
                    !lowerCommand.contains("can you open") -> {
                val app = extractAppName(lowerCommand)
                if (app.isNotEmpty()) {
                    return OfflineResponse(
                        reply = if (isHindi) "$app खोल रहा हूँ"
                        else "Opening $app",
                        action = "open_app",
                        target = app,
                        handled = true
                    )
                }
            }

            // WiFi controls
            lowerCommand.contains("wifi on") ||
                    lowerCommand.contains("turn on wifi") ||
                    lowerCommand.contains("enable wifi") -> {
                return OfflineResponse(
                    reply = if (isHindi) "WiFi चालू कर रहा हूँ"
                    else "Turning on WiFi",
                    action = "wifi",
                    target = "on",
                    handled = true
                )
            }

            lowerCommand.contains("wifi off") ||
                    lowerCommand.contains("turn off wifi") ||
                    lowerCommand.contains("disable wifi") -> {
                return OfflineResponse(
                    reply = if (isHindi) "WiFi बंद कर रहा हूँ"
                    else "Turning off WiFi",
                    action = "wifi",
                    target = "off",
                    handled = true
                )
            }

            // Mobile data controls
            lowerCommand.contains("mobile data on") ||
                    lowerCommand.contains("turn on mobile data") ||
                    lowerCommand.contains("enable mobile data") -> {
                return OfflineResponse(
                    reply = if (isHindi) "मोबाइल डेटा चालू कर रहा हूँ"
                    else "Turning on mobile data",
                    action = "mobile_data",
                    target = "on",
                    handled = true
                )
            }

            lowerCommand.contains("mobile data off") ||
                    lowerCommand.contains("turn off mobile data") ||
                    lowerCommand.contains("disable mobile data") -> {
                return OfflineResponse(
                    reply = if (isHindi) "मोबाइल डेटा बंद कर रहा हूँ"
                    else "Turning off mobile data",
                    action = "mobile_data",
                    target = "off",
                    handled = true
                )
            }

            // Hotspot controls
            lowerCommand.contains("hotspot on") ||
                    lowerCommand.contains("turn on hotspot") ||
                    lowerCommand.contains("enable hotspot") -> {
                return OfflineResponse(
                    reply = if (isHindi) "हॉटस्पॉट चालू कर रहा हूँ"
                    else "Turning on hotspot",
                    action = "hotspot",
                    target = "on",
                    handled = true
                )
            }

            lowerCommand.contains("hotspot off") ||
                    lowerCommand.contains("turn off hotspot") ||
                    lowerCommand.contains("disable hotspot") -> {
                return OfflineResponse(
                    reply = if (isHindi) "हॉटस्पॉट बंद कर रहा हूँ"
                    else "Turning off hotspot",
                    action = "hotspot",
                    target = "off",
                    handled = true
                )
            }

            // Bluetooth controls
            lowerCommand.contains("bluetooth on") ||
                    lowerCommand.contains("turn on bluetooth") ||
                    lowerCommand.contains("enable bluetooth") -> {
                return OfflineResponse(
                    reply = if (isHindi) "ब्लूटूथ चालू कर रहा हूँ"
                    else "Turning on Bluetooth",
                    action = "bluetooth",
                    target = "on",
                    handled = true
                )
            }

            lowerCommand.contains("bluetooth off") ||
                    lowerCommand.contains("turn off bluetooth") ||
                    lowerCommand.contains("disable bluetooth") -> {
                return OfflineResponse(
                    reply = if (isHindi) "ब्लूटूथ बंद कर रहा हूँ"
                    else "Turning off Bluetooth",
                    action = "bluetooth",
                    target = "off",
                    handled = true
                )
            }

            // Settings
            lowerCommand.contains("open settings") ||
                    lowerCommand.contains("settings") -> {
                return OfflineResponse(
                    reply = if (isHindi) "सेटिंग्स खोल रहा हूँ"
                    else "Opening settings",
                    action = "settings",
                    target = "",
                    handled = true
                )
            }

            // Help/Capabilities
            lowerCommand.contains("what can you do") ||
                    lowerCommand.contains("help") ||
                    lowerCommand.contains("commands") ||
                    lowerCommand.contains("मदद") -> {
                return OfflineResponse(
                    reply = if (isHindi) "मैं कॉल करने, मैसेज भेजने, ऐप्स खोलने, समय और तारीख बताने, WiFi/डेटा/हॉटस्पॉट नियंत्रित करने, गणनाएँ करने, संपर्क सहेजने, अंतिम कॉल देखने और बहुत कुछ कर सकता हूँ!"
                    else "I can make calls, send messages, open apps, check time/date, control WiFi/data/hotspot, do calculations, save contacts, check last call and much more!",
                    action = "none",
                    target = "",
                    handled = true
                )
            }

            // Jokes
            lowerCommand.contains("joke") ||
                    lowerCommand.contains("funny") -> {
                val jokes = listOf(
                    if (isHindi) "कंप्यूटर ने ब्रेक क्यों माँगा? क्योंकि वह क्रैश कर रहा था!"
                    else "Why did the computer take a break? Because it was crashing!",
                    if (isHindi) "स्मार्टफोन स्कूल क्यों गया? अपनी रिसेप्शन सुधारने के लिए!"
                    else "Why did the smartphone go to school? To improve its reception!",
                    if (isHindi) "मैं आलसी नहीं हूँ, मैं बस एनर्जी-सेविंग मोड में हूँ!"
                    else "I'm not lazy, I'm just in energy-saving mode!"
                )
                return OfflineResponse(
                    reply = jokes.random(),
                    action = "none",
                    target = "",
                    handled = true
                )
            }

            // Creator
            lowerCommand.contains("who made you") ||
                    lowerCommand.contains("who created you") ||
                    lowerCommand.contains("your creator") -> {
                return OfflineResponse(
                    reply = if (isHindi) "मुझे आपके लिए एक निजी AI सहायक के रूप में बनाया गया था!"
                    else "I was created to be your personal AI assistant by my sir MP singh!",
                    action = "none",
                    target = "",
                    handled = true
                )
            }

            // Calculations
            lowerCommand.contains("calculate") ||
                    lowerCommand.matches(Regex(".*[0-9+\\-*/].*")) -> {
                val result = calculate(lowerCommand)
                if (result != null) {
                    return OfflineResponse(
                        reply = if (isHindi) "परिणाम: $result"
                        else "Result: $result",
                        action = "none",
                        target = "",
                        handled = true
                    )
                }
            }

            // Basic Q&A
            lowerCommand.contains("capital") ||
                    lowerCommand.contains("city") ||
                    lowerCommand.contains("country") ||
                    lowerCommand.contains("राजधानी") -> {
                val qaMap = mapOf(
                    "capital of france" to "Paris",
                    "capital of india" to "New Delhi",
                    "capital of usa" to "Washington, D.C.",
                    "फ्रांस की राजधानी" to "पेरिस",
                    "भारत की राजधानी" to "नई दिल्ली",
                    "अमेरिका की राजधानी" to "वाशिंगटन, डी.सी."
                )
                qaMap.entries.find { lowerCommand.contains(it.key) }?.let {
                    return OfflineResponse(
                        reply = if (isHindi) "${it.key}: ${it.value}"
                        else "The ${it.key} is ${it.value}",
                        action = "none",
                        target = "",
                        handled = true
                    )
                }
            }

            // Capabilities specific
            lowerCommand.contains("can you") -> {
                return when {
                    lowerCommand.contains("call") -> OfflineResponse(
                        reply = if (isHindi) "हाँ, मैं आपके लिए कॉल कर सकता हूँ! बस 'कॉल' के बाद संपर्क का नाम बताएँ।"
                        else "Yes, I can make calls for you! Just say 'Call' followed by the contact name.",
                        action = "none",
                        target = "",
                        handled = true
                    )
                    lowerCommand.contains("message") || lowerCommand.contains("text") -> OfflineResponse(
                        reply = if (isHindi) "हाँ, मैं मैसेज भेज सकता हूँ! बस 'मैसेज' के बाद संपर्क और संदेश बताएँ।"
                        else "Yes, I can send messages! Just say 'Message' followed by contact and message.",
                        action = "none",
                        target = "",
                        handled = true
                    )
                    else -> OfflineResponse(
                        reply = if (isHindi) "मैं बहुत कुछ कर सकता हूँ! कॉल, ऐप्स खोलने, या समय पूछने की कोशिश करें।"
                        else "I can do many things! Try asking me to make calls, open apps, or check the time.",
                        action = "none",
                        target = "",
                        handled = true
                    )
                }
            }
        }

        // If we couldn't handle it offline
        return OfflineResponse(
            reply = if (isHindi) "मुझे यह ऑफलाइन समझने में दिक्कत हो रही है। क्या आप इसे दोहरा सकते हैं?"
            else "I'm having trouble understanding that offline. Can you try again?",
            action = "none",
            target = "",
            handled = false
        )
    }

    private fun extractContact(command: String): String {
        val words = command.split(Regex("\\s+"))
        val callIndex = words.indexOfFirst {
            it.contains("call") || it.contains("phone") ||
                    it.contains("dial") || it.contains("कॉल")
        }

        if (callIndex != -1 && callIndex + 1 < words.size) {
            return words.subList(callIndex + 1, words.size)
                .joinToString(" ")
                .trim()
        }
        return ""
    }

    data class SMSData(val contact: String, val message: String)

    private fun extractMessageImproved(command: String): SMSData {
        val lowerCmd = command.lowercase()

        // Pattern 1: "message/send to [name] saying/that [message]"
        val pattern1 = Regex(
            "(?:message|send|text|sms|मैसेज)\\s+(?:to)?\\s*([\\p{L}\\s]+?)\\s+(?:saying|that|कि|बोलो)\\s+(.+)",
            RegexOption.IGNORE_CASE
        )
        pattern1.find(command)?.let { match ->
            val contact = match.groupValues[1].trim()
            val message = match.groupValues[2].trim()
            if (contact.isNotEmpty() && message.isNotEmpty()) {
                Log.i(TAG,"pattern1")
                return SMSData(contact, message)
            }
        }

        // Pattern 2: "send [message] to [name]"
        val pattern2 = Regex(
            "(?:send|भेजो)\\s+(.+?)\\s+(?:to|ko|को)\\s+([\\p{L}\\s]+)",
            RegexOption.IGNORE_CASE
        )
        pattern2.find(command)?.let { match ->
            val message = match.groupValues[1].trim()
            val contact = match.groupValues[2].trim()
            if (contact.isNotEmpty() && message.isNotEmpty()) {
                Log.i(TAG,"pattern2")
                return SMSData(contact, message)
            }
        }

        // Pattern 3: Old colon format "message contact: text"
        val parts = command.split(":", limit = 2)
        if (parts.size == 2) {
            val contact = parts[0]
                .replace(Regex(".*?(message|send|text|sms|मैसेज)"), "")
                .trim()
            val message = parts[1].trim()
            if (contact.isNotEmpty() && message.isNotEmpty()) {
                Log.i(TAG,"pattern3")
                return SMSData(contact, message)
            }
        }

        // Pattern 4: Simple "message [name] [message]" or just "message [name]"
        /*val pattern4 = Regex(
            "(?:message|send|text|मैसेज)\\s+(?:to|को)?\\s*([\\p{L}]+)(?:\\s+(.+))?",
            RegexOption.IGNORE_CASE
        )
        pattern4.find(command)?.let { match ->
            val contact = match.groupValues[1].trim()
            val message = match.groupValues.getOrNull(2)?.trim() ?: ""

            if (contact.isNotEmpty()) {
                Log.i(TAG,"pattern4")
                return SMSData(contact, message)  // Message can be empty
            }
        }*/
        val cleanedCommand = command.replaceFirst("(?i)^groot\\s+".toRegex(), "")

        val pattern5 = Regex(
            "([\\p{L}\\s]+?)\\s*(?:ko|को|isko|इसको)?\\s*(?:ek|एक)?\\s*(?:message|msg|मैसेज)\\s*(?:bhej[\\w]*|send|kardo|कर[\\w]*|do|दो|karo|करो)?",
            RegexOption.IGNORE_CASE
        )

        pattern5.find(cleanedCommand)?.let { match ->
            var contact = match.groupValues[1].trim()

            // Special case: "isko / इसको"
            if (contact.equals("isko", ignoreCase = true) ||
                contact.equals("इसको", ignoreCase = true)) {
                contact = "last_call_contact" // placeholder ya apka logic
            }

            if (contact.isNotEmpty()) {
                Log.i(TAG,"pattern5")
                return SMSData(contact, "")  // message text optional
            }
        }

        return SMSData("", "")
    }

    private fun extractAppName(command: String): String {
        val words = command.split(Regex("\\s+"))
        val openIndex = words.indexOfFirst { it.contains("open") }

        if (openIndex != -1 && openIndex + 1 < words.size) {
            return words.subList(openIndex + 1, words.size).joinToString(" ")
        }
        return ""
    }

    private fun calculate(command: String): String? {
        try {
            val expression = command.replace(Regex(".*?(calculate|what is|equals)"), "")
                .replace(Regex("[^0-9+\\-*/(). ]"), "")
                .trim()
            if (expression.isNotEmpty()) {
                val result = evaluateExpression(expression)
                return if (result % 1 == 0.0) result.toInt().toString() else result.toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Calculation failed", e)
        }
        return null
    }

    private fun evaluateExpression(expression: String): Double {
        return parseExpression(expression)
    }

    private fun parseExpression(expression: String): Double {
        var idx = 0
        var value = parseTerm(expression, idx)
        idx = value.second
        while (idx < expression.length) {
            when (expression[idx]) {
                '+' -> { idx++; value = Pair(value.first + parseTerm(expression, idx).first, parseTerm(expression, idx).second); idx = value.second }
                '-' -> { idx++; value = Pair(value.first - parseTerm(expression, idx).first, parseTerm(expression, idx).second); idx = value.second }
                else -> break
            }
        }
        return value.first
    }

    private fun parseTerm(expression: String, startIdx: Int): Pair<Double, Int> {
        var idx = startIdx
        var value = parseFactor(expression, idx)
        idx = value.second
        while (idx < expression.length) {
            when (expression[idx]) {
                '*' -> { idx++; value = Pair(value.first * parseFactor(expression, idx).first, parseFactor(expression, idx).second); idx = value.second }
                '/' -> { idx++; value = Pair(value.first / parseFactor(expression, idx).first, parseFactor(expression, idx).second); idx = value.second }
                else -> break
            }
        }
        return value
    }

    private fun parseFactor(expression: String, startIdx: Int): Pair<Double, Int> {
        var idx = startIdx
        var value: Double

        if (expression[idx] == '(') {
            idx++
            value = parseExpression(expression.substring(idx))
            idx += value.toString().length + 1
            if (idx < expression.length && expression[idx] == ')') idx++
        } else {
            val start = idx
            while (idx < expression.length && (expression[idx].isDigit() || expression[idx] == '.')) idx++
            value = expression.substring(start, idx).toDoubleOrNull() ?: 0.0
        }
        return Pair(value, idx)
    }

    fun getTimeBasedGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 0..11 -> "Good morning! I'm Groot, your AI assistant. How may I help you?"
            in 12..16 -> "Good afternoon! I'm Groot, ready to assist you!"
            in 17..20 -> "Good evening! I'm Groot, how can I help you today?"
            else -> "Hello! I'm Groot, your AI assistant. What can I do for you?"
        }
    }
    /**
     * Check if the query can be handled offline
     */
    fun canHandleOffline(command: String): Boolean {
        val lowerCommand = command.lowercase().trim()

        val offlineKeywords = listOf(
            "name", "who are you", "how are you", "hello", "hi",
            "time", "date", "day", "call", "open", "wifi",
            "mobile data", "hotspot", "bluetooth", "settings",
            "thank", "joke", "help", "can you", "good morning",
            "good night", "namaste", "what can you do", "calculate",
            "capital", "message", "send", "text", "sms"
        )
        return offlineKeywords.any { lowerCommand.contains(it) }
    }
}