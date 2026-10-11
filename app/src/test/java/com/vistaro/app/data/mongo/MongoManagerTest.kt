package com.vistaro.app.data.mongo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MongoManagerTest {

    @Test
    fun isConfigured_withEmptyUri_returnsFalse() {
        val manager = MongoManager(connectionUri = "")
        assertFalse(manager.isConfigured)
    }

    @Test
    fun isConfigured_withInvalidUri_returnsFalse() {
        val manager = MongoManager(connectionUri = "http://localhost:27017")
        assertFalse(manager.isConfigured)
    }

    @Test
    fun isConfigured_withValidSrvUri_returnsTrue() {
        val manager = MongoManager(connectionUri = "mongodb+srv://user:pass@cluster0.example.mongodb.net")
        assertTrue(manager.isConfigured)
    }
}
