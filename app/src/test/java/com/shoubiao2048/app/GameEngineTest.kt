package com.shoubiao2048.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {
    @Test
    fun `a row merges once per tile when moved left`() {
        val result = GameEngine.move(intArrayOf(*List(16) { if (it < 4) 2 else 0 }.toIntArray()), Direction.LEFT)
        assertEquals(listOf(4, 4, 0, 0), result.cells.take(4).toList())
        assertEquals(8, result.scoreDelta)
        assertTrue(result.moved)
    }

    @Test
    fun `a vertical pair moves and merges downward`() {
        val cells = intArrayOf(* (listOf(2, 0, 0, 0, 2, 0, 0, 0) + List(8) { 0 }).toIntArray())
        val result = GameEngine.move(cells, Direction.DOWN)
        assertEquals(4, result.cells[12])
        assertEquals(4, result.scoreDelta)
    }

    @Test
    fun `locked board has no valid moves`() {
        val board = intArrayOf(2, 4, 2, 4, 4, 2, 4, 2, 2, 4, 2, 4, 4, 2, 4, 2)
        assertFalse(GameEngine.canMove(board))
    }

    @Test
    fun `snapshot codec retains a valid local save`() {
        val snapshot = GameSnapshot(intArrayOf(*List(16) { if (it == 0) 2 else 0 }.toIntArray()), 12, 64, true)
        val restored = GameSnapshotCodec.decode(GameSnapshotCodec.encode(snapshot))
        requireNotNull(restored)
        assertTrue(snapshot.cells.contentEquals(restored.cells))
        assertEquals(snapshot.score, restored.score)
        assertEquals(snapshot.bestScore, restored.bestScore)
        assertEquals(snapshot.hasAcknowledgedWin, restored.hasAcknowledgedWin)
    }

    @Test
    fun `snapshot codec rejects malformed or unsafe persisted state`() {
        assertNull(GameSnapshotCodec.decode("0|0|false|2,2,2"))
        assertNull(GameSnapshotCodec.decode("0|0|false|3,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0"))
        assertNull(GameSnapshotCodec.decode("-1|0|false|2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0"))
        assertNull(GameSnapshotCodec.decode("10|5|false|2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0"))
        assertNull(GameSnapshotCodec.decode("0|0|false|" + "2,".repeat(300)))
    }
}
