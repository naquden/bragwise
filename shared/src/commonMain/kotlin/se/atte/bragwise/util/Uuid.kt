package se.atte.bragwise.util

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
fun randomUuid(): String = Uuid.random().toString()
