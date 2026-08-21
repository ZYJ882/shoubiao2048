package com.shoubiao2048.app

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.launch
import kotlin.math.abs

private object AppColors {
    val Background = Color(0xFF12100E)
    val Board = Color(0xFF2B2723)
    val TileEmpty = Color(0xFF3A342F)
    val Ink = Color(0xFFFFF8E7)
    val Muted = Color(0xFFC8BFB1)
    val Accent = Color(0xFFF4B942)
    val Outline = Color(0xFF74695D)
}

private enum class DialogKind { RESTART, WON, OVER }

@Composable
fun Wrist2048Screen() {
    MaterialTheme {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var game by remember { mutableStateOf(GameEngine.newGame()) }
        var previous by remember { mutableStateOf<GameSnapshot?>(null) }
        var dialog by remember { mutableStateOf<DialogKind?>(null) }

        LaunchedEffect(Unit) {
            GameStore.load(context)?.let { game = it }
        }

        fun save(snapshot: GameSnapshot) {
            scope.launch { GameStore.save(context, snapshot) }
        }

        fun newGame() {
            val next = GameEngine.newGame(game.bestScore)
            game = next
            previous = null
            dialog = null
            save(next)
        }

        fun move(direction: Direction) {
            if (dialog != null) return
            val moved = GameEngine.move(game.cells, direction)
            if (!moved.moved) return
            val cells = GameEngine.addRandomTile(moved.cells)
            val score = game.score + moved.scoreDelta
            val next = GameSnapshot(
                cells = cells,
                score = score,
                bestScore = maxOf(game.bestScore, score),
                hasAcknowledgedWin = game.hasAcknowledgedWin,
            )
            previous = game
            game = next
            save(next)
            dialog = when {
                !game.hasAcknowledgedWin && GameEngine.hasTargetTile(cells) -> DialogKind.WON
                !GameEngine.canMove(cells) -> DialogKind.OVER
                else -> null
            }
        }

        val configuration = LocalConfiguration.current
        val edgePadding = if (configuration.isScreenRound) 18.dp else 12.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.Background),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = edgePadding, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Header(game)
                Spacer(Modifier.height(7.dp))
                GameBoard(
                    cells = game.cells,
                    modifier = Modifier.weight(1f),
                    onMove = ::move,
                )
                Spacer(Modifier.height(7.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    GameButton(
                        label = "撤销",
                        enabled = previous != null && dialog == null,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            previous?.let { restored ->
                                game = restored
                                previous = null
                                save(restored)
                            }
                        },
                    )
                    GameButton(
                        label = "新局",
                        emphasized = true,
                        modifier = Modifier.weight(1f),
                        onClick = { dialog = DialogKind.RESTART },
                    )
                }
            }

            dialog?.let { activeDialog ->
                GameDialog(
                    kind = activeDialog,
                    onContinue = {
                        if (activeDialog == DialogKind.WON) {
                            val next = game.copy(hasAcknowledgedWin = true)
                            game = next
                            save(next)
                        }
                        dialog = null
                    },
                    onRestart = ::newGame,
                    onDismiss = { dialog = null },
                )
            }
        }
    }
}

@Composable
private fun Header(game: GameSnapshot) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text("2048", color = AppColors.Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("滑动合并数字", color = AppColors.Muted, fontSize = 10.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            ScoreBox("分数", game.score)
            ScoreBox("最高", game.bestScore)
        }
    }
}

@Composable
private fun ScoreBox(label: String, score: Int) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(AppColors.Board)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = AppColors.Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Text(score.toString(), color = AppColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GameBoard(cells: List<Int>, modifier: Modifier, onMove: (Direction) -> Unit) {
    var dragX by remember { mutableStateOf(0f) }
    var dragY by remember { mutableStateOf(0f) }
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val boardSide = minOf(maxWidth, maxHeight)
        Column(
            modifier = Modifier
                .size(boardSide)
                .clip(RoundedCornerShape(13.dp))
                .background(AppColors.Board)
                .padding(5.dp)
                .pointerInput(cells) {
                    detectDragGestures(
                        onDragStart = {
                            dragX = 0f
                            dragY = 0f
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            dragX += amount.x
                            dragY += amount.y
                        },
                        onDragEnd = {
                            val direction = swipeDirection(dragX, dragY)
                            if (direction != null) onMove(direction)
                        },
                    )
                },
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            repeat(BOARD_SIDE) { row ->
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    repeat(BOARD_SIDE) { column ->
                        Tile(
                            value = cells[row * BOARD_SIDE + column],
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Tile(value: Int, modifier: Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(tileColor(value)),
        contentAlignment = Alignment.Center,
    ) {
        if (value != 0) {
            Text(
                text = value.toString(),
                color = if (value <= 4) Color(0xFF312B26) else AppColors.Ink,
                fontSize = when {
                    value >= 1024 -> 14.sp
                    value >= 128 -> 17.sp
                    else -> 21.sp
                },
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun tileColor(value: Int): Color = when (value) {
    0 -> AppColors.TileEmpty
    2 -> Color(0xFFF3E8C8)
    4 -> Color(0xFFEFD49A)
    8 -> Color(0xFFEFB066)
    16 -> Color(0xFFE8874C)
    32 -> Color(0xFFD96545)
    64 -> Color(0xFFC9473E)
    128 -> Color(0xFFF5C64C)
    256 -> Color(0xFFF0B640)
    512 -> Color(0xFFE8A631)
    1024 -> Color(0xFFE28D29)
    2048 -> AppColors.Accent
    else -> Color(0xFFC96D2E)
}

private fun swipeDirection(dx: Float, dy: Float): Direction? {
    if (maxOf(abs(dx), abs(dy)) < 16f) return null
    return if (abs(dx) > abs(dy)) {
        if (dx > 0) Direction.RIGHT else Direction.LEFT
    } else {
        if (dy > 0) Direction.DOWN else Direction.UP
    }
}

@Composable
private fun GameButton(
    label: String,
    modifier: Modifier,
    enabled: Boolean = true,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(42.dp),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = if (emphasized) AppColors.Accent else AppColors.Board,
            contentColor = if (emphasized) Color(0xFF312B26) else AppColors.Ink,
        ),
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GameDialog(
    kind: DialogKind,
    onContinue: () -> Unit,
    onRestart: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = when (kind) {
        DialogKind.RESTART -> "开始新局？"
        DialogKind.WON -> "你达成 2048"
        DialogKind.OVER -> "本局结束"
    }
    val body = when (kind) {
        DialogKind.RESTART -> "当前棋局将被替换。"
        DialogKind.WON -> "继续挑战更高数字。"
        DialogKind.OVER -> "没有可以移动的方块。"
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC12100E))
            .padding(22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(AppColors.Board)
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, color = AppColors.Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(5.dp))
            Text(body, color = AppColors.Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                when (kind) {
                    DialogKind.RESTART -> {
                        GameButton("继续", Modifier.weight(1f), onClick = onDismiss)
                        GameButton("新局", Modifier.weight(1f), emphasized = true, onClick = onRestart)
                    }
                    DialogKind.WON -> {
                        GameButton("继续", Modifier.weight(1f), onClick = onContinue)
                        GameButton("新局", Modifier.weight(1f), emphasized = true, onClick = onRestart)
                    }
                    DialogKind.OVER -> GameButton("再来一局", Modifier.fillMaxWidth(), emphasized = true, onClick = onRestart)
                }
            }
        }
    }
}
