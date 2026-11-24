package dev.junjo.anchor.data

/**
 * Checklists stored as plain text inside a [Note]: each item starts with `☐ ` or `☑ `.
 * Lines without a box stay as plain lines (e.g. a title), so older app versions and JSON backups
 * always show readable text without any schema change.
 */
sealed class NoteLine {
    data class Plain(val text: String) : NoteLine()
    data class Item(val text: String, val checked: Boolean = false) : NoteLine()
}

data class ChecklistSummary(val headline: String, val remaining: Int, val total: Int) {
    val countText: String
        get() = if (remaining == 0) "All $total ticked" else "$remaining of $total left"
}

object Checklist {
    const val UNCHECKED = "☐ "
    const val CHECKED = "☑ "

    fun isChecklist(text: String): Boolean =
        text.lineSequence().any { it.startsWith(UNCHECKED) || it.startsWith(CHECKED) }

    fun parse(text: String): List<NoteLine> {
        if (text.isEmpty()) return emptyList()
        return text.split('\n').map { line ->
            when {
                line.startsWith(UNCHECKED) -> NoteLine.Item(line.removePrefix(UNCHECKED), checked = false)
                line.startsWith(CHECKED) -> NoteLine.Item(line.removePrefix(CHECKED), checked = true)
                else -> NoteLine.Plain(line)
            }
        }
    }

    /** Serializes lines back to text, trimming and dropping any empty checklist items. */
    fun format(lines: List<NoteLine>): String = lines.mapNotNull { line ->
        when (line) {
            is NoteLine.Plain -> line.text
            is NoteLine.Item -> {
                val clean = line.text.trim()
                if (clean.isEmpty()) null else (if (line.checked) CHECKED else UNCHECKED) + clean
            }
        }
    }.joinToString("\n")

    /**
     * Serializes lines verbatim for in-progress inline editing so trailing spaces and temporarily
     * emptied items don't jump under the user's cursor; [format] cleans them up on save.
     */
    fun formatEditing(lines: List<NoteLine>): String = lines.joinToString("\n") { line ->
        when (line) {
            is NoteLine.Plain -> line.text
            is NoteLine.Item -> (if (line.checked) CHECKED else UNCHECKED) + line.text
        }
    }

    /** Turns every non-blank plain line into an unchecked item. */
    fun toChecklist(text: String): String =
        text.split('\n')
            .mapNotNull { raw ->
                when {
                    raw.startsWith(UNCHECKED) || raw.startsWith(CHECKED) -> raw
                    raw.isBlank() -> null
                    else -> UNCHECKED + raw.trim()
                }
            }
            .joinToString("\n")

    /** Strips checklist boxes back into plain text lines. */
    fun toPlainText(text: String): String =
        text.split('\n').joinToString("\n") { line ->
            when {
                line.startsWith(UNCHECKED) -> line.removePrefix(UNCHECKED)
                line.startsWith(CHECKED) -> line.removePrefix(CHECKED)
                else -> line
            }
        }

    /** Moves ticked items to the bottom while keeping plain lines and original order intact. */
    fun reorder(lines: List<NoteLine>): List<NoteLine> {
        val plains = lines.filterIsInstance<NoteLine.Plain>()
        val items = lines.filterIsInstance<NoteLine.Item>()
        val (unchecked, checked) = items.partition { !it.checked }
        return plains + unchecked + checked
    }

    /** Toggles the item at [index] and drops ticked items to the bottom. */
    fun toggle(lines: List<NoteLine>, index: Int): List<NoteLine> {
        if (index !in lines.indices) return lines
        val item = lines[index] as? NoteLine.Item ?: return lines
        val updated = lines.toMutableList()
        updated[index] = item.copy(checked = !item.checked)
        return reorder(updated)
    }

    /** Appends a new unchecked item above any ticked items. Blank text is ignored. */
    fun addItem(lines: List<NoteLine>, text: String): List<NoteLine> {
        val clean = text.trim()
        if (clean.isEmpty()) return lines
        return reorder(lines + NoteLine.Item(clean, checked = false))
    }

    /** Removes all ticked items, keeping unchecked items and any plain lines. */
    fun clearTicked(lines: List<NoteLine>): List<NoteLine> =
        lines.filterNot { it is NoteLine.Item && it.checked }

    /** First non-blank line without any checkbox prefix, for "Make it a to-do". */
    fun firstCleanLine(text: String): String? =
        text.lineSequence()
            .map {
                when {
                    it.startsWith(UNCHECKED) -> it.removePrefix(UNCHECKED)
                    it.startsWith(CHECKED) -> it.removePrefix(CHECKED)
                    else -> it
                }.trim()
            }
            .firstOrNull { it.isNotEmpty() }

    /** Card preview for checklist notes, or null for plain notes. */
    fun summary(text: String): ChecklistSummary? {
        if (!isChecklist(text)) return null
        val parsed = parse(text)
        val items = parsed.filterIsInstance<NoteLine.Item>().filter { it.text.isNotBlank() }
        if (items.isEmpty()) return null
        val headline = firstCleanLine(text) ?: items.first().text
        val remaining = items.count { !it.checked }
        return ChecklistSummary(headline = headline, remaining = remaining, total = items.size)
    }
}
