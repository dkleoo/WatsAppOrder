package com.example.watsapporderservices.data.prompt

object PromptLoader {
    fun load(path: String, fallback: String): String =
        PromptLoader::class.java.classLoader.getResourceAsStream(path)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?.takeIf { it.isNotBlank() }
            ?: fallback
}
