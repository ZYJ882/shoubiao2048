package com.shoubiao2048.app

import kotlin.random.Random

const val BOARD_SIDE = 4
private const val CELL_COUNT = BOARD_SIDE * BOARD_SIDE

enum class Direction { UP, DOWN, LEFT, RIGHT }

data class GameSnapshot(
    val cells: List<Int>,
    val score: Int,
    val bestScore: Int,
    val hasAcknowledgedWin: Boolean = false,
) {
    init {
        require(cells.size == CELL_COUNT) { "A 2048 board must contain 16 cells." }
    }
}

data class MoveResult(
    val cells: List<Int>,
    val scoreDelta: Int,
    val moved: Boolean,
)

object GameEngine {
    fun newGame(bestScore: Int = 0, random: Random = Random.Default): GameSnapshot {
        val first = addRandomTile(List(CELL_COUNT) { 0 }, random)
        return GameSnapshot(addRandomTile(first, random), score = 0, bestScore = bestScore)
    }

    fun addRandomTile(cells: List<Int>, random: Random = Random.Default): List<Int> {
        val emptyCells = cells.indices.filter { cells[it] == 0 }
        if (emptyCells.isEmpty()) return cells
        val target = emptyCells[random.nextInt(emptyCells.size)]
        return cells.toMutableList().also { it[target] = if (random.nextInt(10) == 0) 4 else 2 }
    }

    fun move(cells: List<Int>, direction: Direction): MoveResult {
        val result = MutableList(CELL_COUNT) { 0 }
        var scoreDelta = 0

        repeat(BOARD_SIDE) { line ->
            val indexes = lineIndexes(direction, line)
            val compacted = indexes.map { cells[it] }.filter { it != 0 }
            val merged = mutableListOf<Int>()
            var position = 0
            while (position < compacted.size) {
                val current = compacted[position]
                if (position + 1 < compacted.size && current == compacted[position + 1]) {
                    val value = current * 2
                    merged += value
                    scoreDelta += value
                    position += 2
                } else {
                    merged += current
                    position += 1
                }
            }
            indexes.forEachIndexed { positionInLine, cellIndex ->
                result[cellIndex] = merged.getOrElse(positionInLine) { 0 }
            }
        }
        return MoveResult(result, scoreDelta, result != cells)
    }

    fun canMove(cells: List<Int>): Boolean {
        if (cells.any { it == 0 }) return true
        for (row in 0 until BOARD_SIDE) {
            for (column in 0 until BOARD_SIDE) {
                val index = row * BOARD_SIDE + column
                val value = cells[index]
                if (column < BOARD_SIDE - 1 && value == cells[index + 1]) return true
                if (row < BOARD_SIDE - 1 && value == cells[index + BOARD_SIDE]) return true
            }
        }
        return false
    }

    fun hasTargetTile(cells: List<Int>): Boolean = cells.any { it >= 2048 }

    private fun lineIndexes(direction: Direction, line: Int): List<Int> = List(BOARD_SIDE) { offset ->
        val position = if (direction == Direction.RIGHT || direction == Direction.DOWN) BOARD_SIDE - 1 - offset else offset
        if (direction == Direction.LEFT || direction == Direction.RIGHT) line * BOARD_SIDE + position else position * BOARD_SIDE + line
    }
}
