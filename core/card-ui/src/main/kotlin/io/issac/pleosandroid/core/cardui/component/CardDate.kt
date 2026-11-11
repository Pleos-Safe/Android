package io.issac.pleosandroid.core.cardui.component

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormat = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.KOREA)
fun dateOf(at: Instant): String = dateFormat.format(at.atZone(ZoneId.systemDefault()))
