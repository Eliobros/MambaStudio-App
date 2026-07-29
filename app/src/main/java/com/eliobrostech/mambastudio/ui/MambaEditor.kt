package com.eliobrostech.mambastudio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MambaEditor(
    code: String,
    onCodeChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val lineCount = code.lines().size

    Row(modifier = modifier.verticalScroll(scrollState)) {
        // Coluna de Números de Linha
        Column(
            modifier = Modifier
                .width(40.dp)
                .padding(top = 8.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.End
        ) {
            for (i in 1..lineCount) {
                Text(
                    text = "$i ",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = Color.LightGray
                    )
                )
            }
        }

        // Campo de Texto do Editor
        BasicTextField(
            value = code,
            onValueChange = onCodeChange,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            ),
            visualTransformation = MambaSyntaxHighlighter(),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
        )
    }
}
