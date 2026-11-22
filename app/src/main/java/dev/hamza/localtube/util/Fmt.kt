package dev.hamza.localtube.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Human-readable values for the UI and the notifications. No library needed for this. */
object Fmt {

    private val dateOnly: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)
    private val dayOfWeek: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.US)

    fun bytes(value: Long): String {
        if (value <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = value.toDouble()
        var index = 0
        while (size >= 1024 && index < units.lastIndex) {
            size /= 1024
            index++
        }
        return if (index == 0) "${value} B" else String.format(Locale.US, "%.1f %s", size, units[index])
    }

    /** 12:05 / 1:02:03. Live or unknown durations render as a dash. */
    fun duration(seconds: Long): String {
        if (seconds <= 0) return "--:--"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%d:%02d", m, s)
        }
    }

    fun eta(seconds: Long): String = when {
        seconds < 0 -> "calculating"
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m %02ds".format(seconds % 60)
        else -> "${seconds / 3600}h %02dm".format((seconds % 3600) / 60)
    }

    fun percent(value: Float): String = when {
        value <= 0f -> "0%"
        value >= 100f -> "100%"
        value >= 10f -> "${value.toInt()}%"
        else -> String.format(Locale.US, "%.1f%%", value)
    }

    fun dateBucket(timestamp: Long): DateBucket {
        val day = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        val today = LocalDate.now()
        return when {
            day == today -> DateBucket.Today
            day == today.minusDays(1) -> DateBucket.Yesterday
            day.isAfter(today.minusWeeks(1)) -> DateBucket.ThisWeek
            day.isAfter(today.withDayOfMonth(1).minusMonths(1)) -> DateBucket.ThisMonth
            day.isAfter(today.minusYears(1)) -> DateBucket.Label(dayOfWeek.format(day))
            else -> DateBucket.Label(dateOnly.format(day))
        }
    }

    fun dateTime(timestamp: Long): String {
        val day = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
        return day.format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.US))
    }
}

sealed class DateBucket {
    abstract val label: String

    object Today : DateBucket() {
        override val label = "Today"
    }

    object Yesterday : DateBucket() {
        override val label = "Yesterday"
    }

    object ThisWeek : DateBucket() {
        override val label = "This week"
    }

    object ThisMonth : DateBucket() {
        override val label = "This month"
    }

    data class Label(override val label: String) : DateBucket()
}

/** Ordered grouping used by the library list. */
data class DateGroup<T>(val bucket: DateBucket, val items: List<T>)

fun <T> List<T>.groupByBucket(selector: (T) -> Long): List<DateGroup<T>> {
    val order = linkedMapOf<String, DateGroup<T>>()
    forEach { item ->
        val bucket = Fmt.dateBucket(selector(item))
        val key = bucket.label
        order[key] = (order[key]?.let { DateGroup(it.bucket, it.items + item) })
            ?: DateGroup(bucket, listOf(item))
    }
    return order.values.toList()
}
