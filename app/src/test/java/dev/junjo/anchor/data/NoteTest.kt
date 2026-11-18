package dev.junjo.anchor.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteTest {
    @Test fun visibleIsNewestFirstWithoutDeleted() {
        val notes = listOf(
            Note("old", "Old", createdAt = 1, updatedAt = 1),
            Note("gone", "Gone", createdAt = 3, updatedAt = 3, deleted = true),
            Note("new", "New", createdAt = 2, updatedAt = 2),
        )
        assertEquals(listOf("new", "old"), notes.visible().map { it.id })
    }

    @Test fun jsonRoundTripKeepsLineBreaks() {
        val n = Note("a", "Shopping:\n- milk\n- \"good\" bread", createdAt = 5, updatedAt = 6)
        assertEquals(n, Note.fromJson(JSONObject(n.toJson().toString())))
        val deleted = n.copy(deleted = true)
        assertEquals(deleted, Note.fromJson(JSONObject(deleted.toJson().toString())))
    }
}
