package com.eliobrostech.mambastudio.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

class MambaSyntaxHighlighter : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        return TransformedText(
            highlightMambaCode(text.text),
            OffsetMapping.Identity
        )
    }

    private fun highlightMambaCode(code: String): AnnotatedString {
        val keywords = setOf(
            "variavel", "funcao", "se", "senao", "fim", "enquanto", "para", 
            "cada", "em", "de", "ate", "importar", "retorna", "tentar", 
            "pegar", "escolha", "caso", "padrao", "parar", "continuar", 
            "escreva", "ler", "hoje"
        )
        val operators = setOf(
            "mais", "menos", "vezes", "dividido", "igual", "maior", 
            "menor", "maiorIgual", "menorIgual", "e", "ou", "nao"
        )
        val literals = setOf("verdadeiro", "falso", "nulo")

        return buildAnnotatedString {
            append(code)
            
            // Regex para capturar palavras, strings e comentários
            val regex = Regex("""(#.*)|("[^"]*")|('[^']*')|(\b\w+\b)""")
            
            regex.findAll(code).forEach { match ->
                val value = match.value
                val range = match.range
                
                when {
                    value.startsWith("#") -> {
                        addStyle(SpanStyle(color = Color.Gray), range.first, range.last + 1)
                    }
                    value.startsWith("\"") || value.startsWith("'") -> {
                        addStyle(SpanStyle(color = Color(0xFFE67E22)), range.first, range.last + 1)
                    }
                    value in keywords -> {
                        addStyle(SpanStyle(color = Color(0xFF27AE60), fontWeight = FontWeight.Bold), range.first, range.last + 1)
                    }
                    value in operators -> {
                        addStyle(SpanStyle(color = Color(0xFF2980B9)), range.first, range.last + 1)
                    }
                    value in literals -> {
                        addStyle(SpanStyle(color = Color(0xFF8E44AD)), range.first, range.last + 1)
                    }
                    value.toDoubleOrNull() != null -> {
                        addStyle(SpanStyle(color = Color(0xFFD35400)), range.first, range.last + 1)
                    }
                }
            }
        }
    }
}
