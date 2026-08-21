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
        val encoded = context.applicationContext.gameDataStore.data.first()[GameSnapshotKey] ?: return null
        return GameSnapshotCodec.decode(encoded)
    }

    suspend fun save(context: Context, snapshot: GameSnapshot) {
        context.applicationContext.gameDataStore.edit { preferences ->
            preferences[GameSnapshotKey] = GameSnapshotCodec.encode(snapshot)
        }
    }
}

object GameSnapshotCodec {
    fun encode(snapshot: GameSnapshot): String = listOf(
        snapshot.score,
        snapshot.bestScore,
        snapshot.hasAcknowledgedWin,
        snapshot.cells.joinToString(","),
    ).joinToString("|")

    fun decode(raw: String): GameSnapshot? = runCatching {
        val parts = raw.split("|")
        if (parts.size != 4) return null
        val cells = parts[3].split(",").map { it.toInt() }
        if (cells.size != BOARD_SIDE * BOARD_SIDE || cells.any { it < 0 }) return null
        GameSnapshot(
            cells = cells,
            score = parts[0].toInt().coerceAtLeast(0),
            bestScore = parts[1].toInt().coerceAtLeast(0),
            hasAcknowledgedWin = parts[2].toBooleanStrict(),
        )
    }.getOrNull()
}
