package com.example.compilerx

import java.io.Serializable

data class Question(
    val text: String = "",
    val options: List<String> = listOf(),
    val correctIdx: Int = 0
) : Serializable

data class UserAnswer(
    val question: Question,
    val selectedIdx: Int,
    val isCorrect: Boolean
) : Serializable