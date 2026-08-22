package com.voltai.doai.domain.interfaces

import com.voltai.doai.domain.models.Intent

interface IntentAnalyzer {
    fun analyzeRequest(request: String): Intent
    fun detectAction(request: String): String
    fun detectTarget(request: String): String
    fun detectTools(request: String): List<String>
    fun detectFiles(request: String): List<String>
    fun estimateComplexity(request: String): Complexity
}

enum class Complexity {
    SIMPLE,
    MEDIUM,
    COMPLEX,
    EXPERT
}