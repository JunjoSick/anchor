package dev.junjo.anchor.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistTest {
    @Test fun textToItemsRoundTripAndPlainLinesStayPlain() {
        val raw = "Shopping\n☐ Milk\n☑ Bread\n☐ Eggs"
        assertTrue(Checklist.isChecklist(raw))
        val parsed = Checklist.parse(raw)
        assertEquals(
            listOf(
                NoteLine.Plain("Shopping"),
                NoteLine.Item("Milk", checked = false),
                NoteLine.Item("Bread", checked = true),
                NoteLine.Item("Eggs", checked = false),
            ),
            parsed,
        )
        assertEquals(raw, Checklist.format(parsed))
        assertFalse(Checklist.isChecklist("Just a normal note\nwith two lines"))
    }

    @Test fun togglingDropsTickedItemsToBottomInOriginalOrder() {
        val initial = Checklist.parse("Shopping\n☐ Milk\n☐ Bread\n☐ Eggs")
        val afterFirst = Checklist.toggle(initial, 1) // tick Milk
        assertEquals(
            "Shopping\n☐ Bread\n☐ Eggs\n☑ Milk",
            Checklist.format(afterFirst),
        )
        // Reordering an existing mixed list preserves original order of ticked items.
        val mixed = Checklist.parse("Shopping\n☑ First\n☐ Open\n☑ Second")
        assertEquals(
            "Shopping\n☐ Open\n☑ First\n☑ Second",
            Checklist.format(Checklist.reorder(mixed)),
        )
    }

    @Test fun clearTickedRemovesOnlyCheckedItems() {
        val lines = Checklist.parse("Packing\n☐ Passport\n☑ Charger\n☐ Toothbrush\n☑ Socks")
        assertEquals(
            "Packing\n☐ Passport\n☐ Toothbrush",
            Checklist.format(Checklist.clearTicked(lines)),
        )
    }

    @Test fun emptyItemsAreDropped() {
        val lines = listOf(
            NoteLine.Item("Milk", false),
            NoteLine.Item("   ", false),
            NoteLine.Item("", true),
            NoteLine.Item("Eggs", true),
        )
        assertEquals("☐ Milk\n☑ Eggs", Checklist.format(lines))
        assertEquals(lines, Checklist.addItem(lines, "   "))
    }

    @Test fun summaryShowsFirstLineAndRemainingCount() {
        assertNull(Checklist.summary("Plain note"))
        val s = Checklist.summary("Shopping\n☐ Milk\n☐ Eggs\n☐ Apples\n☑ Bread\n☑ Butter")!!
        assertEquals("Shopping", s.headline)
        assertEquals("3 of 5 left", s.countText)

        val allDone = Checklist.summary("☑ One\n☑ Two")!!
        assertEquals("One", allDone.headline)
        assertEquals("All 2 ticked", allDone.countText)
    }

    @Test fun convertToAndFromChecklist() {
        assertEquals("☐ Milk\n☐ Bread", Checklist.toChecklist("Milk\n\nBread"))
        assertEquals("Milk\nBread", Checklist.toPlainText("☐ Milk\n☑ Bread"))
        assertEquals("Milk", Checklist.firstCleanLine("☐ Milk\n☐ Bread"))
        // formatEditing preserves trailing spaces and empty items while typing; format cleans them up.
        val editing = listOf(NoteLine.Item("Milk ", false), NoteLine.Item("", false))
        assertEquals("☐ Milk \n☐ ", Checklist.formatEditing(editing))
        assertEquals("☐ Milk", Checklist.format(editing))
    }
}
