package com.eliobrostech.mambastudio.runner

import android.content.Context

/**
 * ⚠️ DEPRECATED — Substituído por [NodeJsRunner].
 *
 * O antigo MambaRunner tentava executar o binário compilado com `pkg`,
 * que não funciona em Android (glibc vs bionic).
 *
 * O [NodeJsRunner] usa nodejs-mobile (libnode.so) para executar o motor
 * MambaScript diretamente através do Node.js embutido no app.
 */
@Deprecated("Usar NodeJsRunner para execução local via Node.js embutido")
@Suppress("unused")
class MambaRunner(context: Context) {
    // Esta classe foi mantida apenas para referência histórica.
    // Toda a funcionalidade foi migrada para NodeJsRunner.
}
