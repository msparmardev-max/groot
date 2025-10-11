package com.example.groot

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

data class LLMRequest(
    val text: String,
    val context: String = "mobile_assistant"
)

data class LLMResponse(
    val reply: String,
    val action: String,
    val target: String,
    val emotion: String,
    val confidence: Double
)

data class HealthResponse(
    val status: String,
    val ollama: String
)

interface LLMService {
    @GET("health")
    suspend fun healthCheck(): Response<HealthResponse>

    @POST("query")
    suspend fun processCommand(@Body request: LLMRequest): Response<LLMResponse>
}