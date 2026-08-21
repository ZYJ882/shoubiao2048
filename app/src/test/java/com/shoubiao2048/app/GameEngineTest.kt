package com.shoubiao2048.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {
    @Test
    fun `a row merges once per tile when moved left`() {
        val result = GameEngine.move(List(16) { if (it < 4) 2 else 0 }, Direction.LEFT)
        assertEquals(listOf(4, 4, 0, 0), result.cells.take(4))
        assertEquals(8, result.scoreDelta)
        assertTrue(result.moved)
    }

    @Test
    fun `a vertical pair moves and merges downward`() {
        val cells = listOf(2, 0, 0, 0, 2, 0, 0, 0) + List(8) { 0 }
        val result = GameEngine.move(cells, Direction.DOWN)
        assertEquals(4, result.cells[12])
        assertEquals(4, result.scoreDelta)
    }

    @Test
    fun `locked board has no valid moves`() {
        val board = listOf(2, 4, 2, 4, 4, 2, 4, 2, 2, 4, 2, 4, 4, 2, 4, 2)
        assertFalse(GameEngine.canMove(board))
    }

    @Test
    fun `snapshot codec retains a valid local save`() {
        val snapshot = GameSnapshot(List(16) { if (it == 0) 2 else 0 }, 12, 64, true)
        assertEquals(snapshot, GameSnapshotCodec.decode(GameSnapshotCodec.encode(snapshot)))
    }
}
