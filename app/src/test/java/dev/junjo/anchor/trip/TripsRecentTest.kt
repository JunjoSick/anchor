package dev.junjo.anchor.trip

import org.junit.Assert.assertEquals
import org.junit.Test

class TripsRecentTest {
    @Test fun newestFirstAndCappedAtFive() {
        var list = emptyList<RecentTrip>()
        for (i in 1..7) {
            list = TripPolicy.recordRecent(list, "Trip $i", minutes = 2)
        }
        assertEquals(
            listOf("Trip 7", "Trip 6", "Trip 5", "Trip 4", "Trip 3"),
            list.map { it.name },
        )
    }

    @Test fun repeatedNameMovesToFrontWithUpdatedMinutes() {
        var list = listOf(
            RecentTrip("Check weather", 1),
            RecentTrip("Check Marco's stories", 2),
            RecentTrip("Reply to Sam", 5),
        )
        list = TripPolicy.recordRecent(list, "  check marco's stories  ", minutes = 5)
        assertEquals(
            listOf(
                RecentTrip("check marco's stories", 5),
                RecentTrip("Check weather", 1),
                RecentTrip("Reply to Sam", 5),
            ),
            list,
        )
    }

    @Test fun blankNamesAreNeverSaved() {
        val existing = listOf(RecentTrip("Reply to Sam", 2))
        assertEquals(existing, TripPolicy.recordRecent(existing, "", 2))
        assertEquals(existing, TripPolicy.recordRecent(existing, "   \n\t ", 5))
    }

    @Test fun jsonRoundTripAndOlderFileDefaults() {
        val items = listOf(
            RecentTrip("Check Marco's stories", 2),
            RecentTrip("Pay bill", 5),
        )
        assertEquals(items, TripPolicy.recentFromJson(TripPolicy.recentToJson(items)))

        // Older entry missing optional "minutes" loads with the default length.
        val older = """[{"name":"Old trip"},{"name":"   "},{"name":"Valid","minutes":1}]"""
        assertEquals(
            listOf(RecentTrip("Old trip", TripPolicy.DEFAULT_MINUTES), RecentTrip("Valid", 1)),
            TripPolicy.recentFromJson(older),
        )
    }
}
