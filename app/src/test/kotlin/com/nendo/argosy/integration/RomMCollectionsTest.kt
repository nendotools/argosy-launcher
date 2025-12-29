package com.nendo.argosy.integration

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RomMCollectionsTest : RomMIntegrationTest() {

    @Before
    fun auth() {
        authenticate()
    }

    @Test
    fun `getCollections returns list`() = runBlocking {
        val response = api.getCollections()

        assertTrue("getCollections should succeed", response.isSuccessful)
        val collections = response.body()
        assertNotNull("Collections list should not be null", collections)
    }

    @Test
    fun `collection includes expected fields`() = runBlocking {
        val response = api.getCollections()
        assertTrue("getCollections should succeed", response.isSuccessful)
        val collections = response.body()!!

        if (collections.isNotEmpty()) {
            val collection = collections.first()
            assertTrue("Collection should have positive ID", collection.id > 0)
            assertTrue("Collection name should not be empty", collection.name.isNotEmpty())
            assertNotNull("Collection should have romIds list", collection.romIds)
        }
    }

    @Test
    fun `favorites collection query works`(): Unit = runBlocking {
        val response = api.getCollections(isFavorite = true)

        assertTrue("getCollections with isFavorite should succeed", response.isSuccessful)
        val collections = response.body()
        assertNotNull("Collections list should not be null", collections)

        collections?.forEach { collection ->
            assertTrue("All returned collections should be favorites", collection.isFavorite)
        }
    }
}
