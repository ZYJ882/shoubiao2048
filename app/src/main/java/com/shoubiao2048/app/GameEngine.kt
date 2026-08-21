package com.shoubiao2048.app

import kotlin.random.Random

const val BOARD_SIDE = 4
private const val CELL_COUNT = BOARD_SIDE * BOARD_SIDE

enum class Direction { UP, DOWN, LEFT, RIGHT }

data class GameSnapshot(
    val cells: IntArray,
    val score: Int,
    val bestScore: Int,
    val hasAcknowledgedWin: Boolean = false,
) {
    init {
        require(cells.size == CELL_COUNT) { "A 2048 board must contain 16 cells." }
    }
}

data class MoveResult(
    val cells: IntArray,
    val scoreDelta: Int,
    val moved: Boolean,
)

object GameEngine {
    private val lineIndexes = Array(Direction.entries.size * BOARD_SIDE) { slot ->
        val direction = Direction.entries[slot / BOARD_SIDE]
        val line = slot % BOARD_SIDE
        IntArray(BOARD_SIDE) { offset ->
            val position = if (direction == Direction.RIGHT || direction == Direction.DOWN) {
                BOARD_SIDE - 1 - offset
            } else {
                offset
            }
            if (direction == Direction.LEFT || direction == Direction.RIGHT) {
                line * BOARD_SIDE + position
            } else {
                position * BOARD_SIDE + line
            }
        }
    }
    
    fun newGame(bestScore: Int = 0, random: Random = Random.Default): GameSnapshot {
        val first = addRandomTile(IntArray(CELL_COUNT), random)
        return GameSnapshot(addRandomTile(first, random), score = 0, bestScore = bestScore)
    }

    fun addRandomTile(cells: IntArray, random: Random = Random.Default): IntArray {
        var emptyCount = 0
        for (i in cells.indices) {
            if (cells[i] == 0) emptyCount++
        }
        if (emptyCount == 0) return cells
        
        var remaining = random.nextInt(emptyCount)
        var targetIndex = 0
        for (index in cells.indices) {
            if (cells[index] != 0) continue
            if (remaining == 0) {
                targetIndex = index
                break
            }
            remaining--
        }
        
        return cells.copyOf().apply { 
            this[targetIndex] = if (random.nextInt(10) == 0) 4 else 2 
        }
    }

    fun move(cells: IntArray, direction: Direction): MoveResult {
        val result = IntArray(CELL_COUNT)
        var scoreDelta = 0

        for (line in 0 until BOARD_SIDE) {
            val indexes = lineIndexes[direction.ordinal * BOARD_SIDE + line]
            
            // Compact and merge in one pass
            var writePos = 0
            var lastValue = 0
            var canMerge = false
            
            for (offset in indexes.indices) {
                val cellIndex = indexes[offset]
                val value = cells[cellIndex]
                if (value == 0) continue
                
                if (canMerge && lastValue == value) {
                    val merged = value * 2
                    result[indexes[writePos - 1]] = merged
                    scoreDelta += merged
                    canMerge = false
                } else {
                    result[indexes[writePos]] = value
                    lastValue = value
                    writePos++
                    canMerge = true
                }
            }
            
            // Fill remaining with zeros
            for (i in writePos until BOARD_SIDE) {
                result[indexes[i]] = 0
            }
        }
        
        val moved = !cells.contentEquals(result)
        return MoveResult(result, scoreDelta, moved)
    }

    fun canMove(cells: IntArray): Boolean {
        // Check for empty cells first (fast path)
        for (cell in cells) {
            if (cell == 0) return true
        }
        
        // Check adjacent cells
        for (row in 0 until BOARD_SIDE) {
            for (column in 0 until BOARD_SIDE - 1) {
                val index = row * BOARD_SIDE + column
                if (cells[index] == cells[index + 1]) return true
            }
        }
        
        for (column in 0 until BOARD_SIDE) {
            for (row in 0 until BOARD_SIDE - 1) {
                val index = row * BOARD_SIDE + column
                if (cells[index] == cells[index + BOARD_SIDE]) return true
            }
        }
        
        return false
    }

    fun hasTargetTile(cells: IntArray): Boolean {
        for (cell in cells) {
            if (cell >= 2048) return true
        }
        return false
    }

}
