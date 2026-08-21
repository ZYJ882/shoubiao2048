package com.shoubiao2048.app

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.gameDataStore by preferencesDataStore(name = "wrist_2048")
private val GameSnapshotKey = stringPreferencesKey("snapshot")

object GameStore {
    suspend fun load(context: Context): GameSnapshot? {
        return runCatching {
            val encoded = context.applicationContext.gameDataStore.data.first()[GameSnapshotKey] ?: return null
            GameSnapshotCodec.decode(encoded)
        }.getOrNull()
    }

    suspend fun save(context: Context, snapshot: GameSnapshot) {
        context.applicationContext.gameDataStore.edit { preferences ->
            preferences[GameSnapshotKey] = GameSnapshotCodec.encode(snapshot)
        }
    }
}

object GameSnapshotCodec {
    private const val MAX_SNAPSHOT_CHARS = 512
    private const val MAX_SCORE = 1_000_000_000
    private const val MAX_TILE_VALUE = 1 shl 20

    fun encode(snapshot: GameSnapshot): String = listOf(
        snapshot.score,
        snapshot.bestScore,
        snapshot.hasAcknowledgedWin,
        snapshot.cells.joinToString(","),
    ).joinToString("|")

    fun decode(raw: String): GameSnapshot? = runCatching {
        if (raw.length > MAX_SNAPSHOT_CHARS) return null
        val parts = raw.split('|', limit = 4)
        if (parts.size != 4) return null
        val score = parts[0].toInt()
        val bestScore = parts[1].toInt()
        if (score !in 0..MAX_SCORE || bestScore !in score..MAX_SCORE) return null

        val values = parts[3].split(',')
        if (values.size != BOARD_SIDE * BOARD_SIDE) return null
        val cells = IntArray(values.size) { index -> values[index].toInt() }
        if (cells.any { !isValidTile(it) }) return null
        GameSnapshot(
            cells = cells,
            score = score,
            bestScore = bestScore,
            hasAcknowledgedWin = parts[2].toBooleanStrict(),
        )
    }.getOrNull()

    private fun isValidTile(value: Int): Boolean {
        return value == 0 || (value in 2..MAX_TILE_VALUE && (value and (value - 1)) == 0)
    }
}
