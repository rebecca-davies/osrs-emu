package emu.server.game.world.map

import emu.cache.map.codec.MapTileDecoder
import emu.cache.map.model.MapSquare
import emu.game.map.Tile
import emu.server.game.config.CollisionLoadQueueConfig
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class CollisionLoadQueueTest {
    @Test
    fun `prepare makes collision available before character entry continues`() {
        val collision = collisionMap()
        CollisionLoadQueue(collision, config()).use { loads ->
            val tile = Tile(3_200, 3_200)

            loads.prepare(tile)

            assertEquals(0, collision.flagsAt(tile.x, tile.y, tile.plane))
        }
    }

    @Test
    fun `requests are non blocking deduplicated and bounded`() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val firstRead = AtomicBoolean(true)
        val collision =
            collisionMap { x, y ->
                if (firstRead.compareAndSet(true, false)) {
                    started.countDown()
                    release.await()
                }
                openSquare(x, y)
            }
        CollisionLoadQueue(collision, config(capacity = 1)).use { loads ->
            val first = Tile(3_200, 3_200)
            val second = Tile(3_328, 3_200)
            val rejected = Tile(3_456, 3_200)

            assertTrue(loads.request(first))
            assertTrue(started.await(1, TimeUnit.SECONDS))
            assertTrue(loads.request(first))
            assertTrue(loads.request(second))
            assertFalse(loads.request(rejected))

            release.countDown()
            assertTrue(awaitLoaded(collision, second))
        }
    }

    private fun config(capacity: Int = 4) =
        CollisionLoadQueueConfig(
            capacity = capacity,
            workerThreads = 1,
            shutdownTimeout = 1.seconds,
        )

    private fun collisionMap(
        mapSquare: (Int, Int) -> MapSquare? = { x, y -> openSquare(x, y) },
    ) = CacheCollisionMap(mapSquare, objectDefinition = { null })

    private fun awaitLoaded(collision: CacheCollisionMap, tile: Tile): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
        while (System.nanoTime() < deadline) {
            if (collision.flagsAt(tile.x, tile.y, tile.plane) == 0) return true
            Thread.yield()
        }
        return false
    }

    private companion object {
        fun openSquare(x: Int, y: Int): MapSquare =
            MapSquare(x, y, MapTileDecoder.decode(ByteArray(4 * 64 * 64 * 2 + 1)), emptyList())
    }
}
