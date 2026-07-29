package com.eliobrostech.mambastudio.api

import kotlinx.serialization.Serializable

@Serializable
data class MambaRequest(
    val codigo: String
)

@Serializable
data class MambaResponse(
    val saida: String? = null,
    val erro: String? = null
)
