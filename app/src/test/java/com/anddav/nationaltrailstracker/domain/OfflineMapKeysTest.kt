package com.anddav.nationaltrailstracker.domain

import com.anddav.nationaltrailstracker.domain.map.OfflineMapKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfflineMapKeysTest {

    @Test
    fun `stage keys round-trip`() {
        val key = OfflineMapKeys.forStage("thames-path", 3)
        assertEquals("thames-path/stage-3", key)
        assertEquals("thames-path" to 3, OfflineMapKeys.parse(key))
    }

    @Test
    fun `whole-trail keys round-trip with a null stage`() {
        val key = OfflineMapKeys.forStage("south-west-coast-path", null)
        assertEquals("south-west-coast-path" to null, OfflineMapKeys.parse(key))
    }

    @Test
    fun `other keys are not ours`() {
        assertNull(OfflineMapKeys.parse("something else"))
        assertNull(OfflineMapKeys.parse("thames-path/stage-x"))
    }
}
