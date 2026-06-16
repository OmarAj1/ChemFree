package com.example.data

data class UserSettings(
    val isVegan: Boolean = false,
    val isGlutenFree: Boolean = false,
    val flagDyes: Boolean = false,
    val flagTitaniumDioxide: Boolean = false,
    val flagCarrageenan: Boolean = false,
    val flagPfas: Boolean = false,
    // Allergies
    val allergySoy: Boolean = false,
    val allergyDairy: Boolean = false,
    val allergyWheat: Boolean = false,
    val allergyNuts: Boolean = false,
    val allergyCorn: Boolean = false
)
