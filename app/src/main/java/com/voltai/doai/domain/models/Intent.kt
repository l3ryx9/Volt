package com.voltai.doai.domain.models

import com.voltai.doai.domain.interfaces.Complexity

data class Intent(
    val action: String,
    val target: String,
    val tools: List<String>,
    val files: List<String>,
    val complexity: Complexity,
    val confidence: Float
)