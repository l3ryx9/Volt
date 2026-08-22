package com.voltai.doai.data.code

import com.voltai.doai.domain.interfaces.LanguageDetector
import com.voltai.doai.domain.models.CodeLanguage

class LanguageDetectorImpl : LanguageDetector {

    override fun detectLanguage(path: String): CodeLanguage? = CodeLanguage.fromPath(path)

    override fun detectLanguage(path: String?, content: String): CodeLanguage? {
        path?.let { CodeLanguage.fromPath(it)?.let { return it } }
        return CodeLanguage.fromContent(content)
    }
}
