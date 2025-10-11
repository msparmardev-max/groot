package com.example.groot

import java.util.Calendar

object ResponseVariations {

    fun getRandomPrompt(isHindi: Boolean): String {
        val hindiPrompts = listOf(
            "और कुछ मदद चाहिए?",
            "कुछ और काम है?",
            "मैं और क्या कर सकता हूं?"
        )

        val englishPrompts = listOf(
            "Can I help with anything else?",
            "What else can I do for you?",
            "Anything else?"
        )

        return if (isHindi) hindiPrompts.random() else englishPrompts.random()
    }

    fun getTimeBasedGreeting(isHindi: Boolean): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        return when {
            hour < 12 -> if (isHindi) "सुप्रभात" else "Good morning"
            hour < 17 -> if (isHindi) "नमस्ते" else "Good afternoon"
            hour < 21 -> if (isHindi) "शुभ संध्या" else "Good evening"
            else -> if (isHindi) "नमस्ते" else "Good evening"
        }
    }

    fun getPositiveAck(isHindi: Boolean): String {
        return if (isHindi) {
            listOf("ठीक है", "जी हां", "बिल्कुल").random()
        } else {
            listOf("Okay", "Sure", "Absolutely").random()
        }
    }

    fun getThankYouResponse(isHindi: Boolean): String {
        return if (isHindi) {
            listOf(
                "कोई बात नहीं!",
                "खुशी हुई मदद करके!",
                "स्वागत है!"
            ).random()
        } else {
            listOf(
                "You're welcome!",
                "Happy to help!",
                "No problem!"
            ).random()
        }
    }

    fun getConfusionResponse(isHindi: Boolean): String {
        return if (isHindi) {
            listOf(
                "क्षमा करें, समझ नहीं आया",
                "फिर से बताइए?",
                "मैंने सही से नहीं सुना"
            ).random()
        } else {
            listOf(
                "Sorry, didn't catch that",
                "Could you repeat that?",
                "I didn't quite get that"
            ).random()
        }
    }
}