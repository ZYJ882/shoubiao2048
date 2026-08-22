package com.shoubiao2048.app

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private object AppColors {
    val Background = Color(0xFF12100E)
    val Board = Color(0xFF2B2723)
    val TileEmpty = Color(0xFF3A342F)
    val Ink = Color(0xFFFFF8E7)
    val Muted = Color(0xFFC8BFB1)
    val Accent = Color(0xFFF4B942)
}

private val BoardShape = RoundedCornerShape(13.dp)
private val TileShape = RoundedCornerShape(6.dp)
private val CardShape = RoundedCornerShape(7.dp)
private val DialogShape = RoundedCornerShape(16.dp)
private const val TILE_MOTION_DURATION_MS = 132
private const val MERGE_DURATION_MS = 160
private const val SPAWN_DURATION_MS = 120
private const val DIALOG_FADE_DURATION_MS = 200
private const val BUTTON_PRESS_DURATION_MS = 80
private const val BUTTON_PRESSED_SCALE = 0.96f
private val BoardPadding = 5.dp
private val BoardGap = 5.dp

private enum class DialogKind { RESTART, WON, OVER }

private data class MergePulse(val value: Int, val targetIndex: Int)

private data class BoardAnimation(
    val id: Int,
    val cellsAfterMove: IntArray,
    val motions: Array<TileMotion>,
    val mergePulses: Array<MergePulse>,
    val spawnedIndex: Int,
    val durationMs: Int,
)

@Composable
fun Wrist2048Screen() {
    MaterialTheme {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var game by remember { mutableStateOf(GameEngine.newGame()) }
        var previous by remember { mutableStateOf<GameSnapshot?>(null) }
        var dialog by remember { mutableStateOf<DialogKind?>(null) }
        var pendingDialog by remember { mutableStateOf<DialogKind?>(null) }
        var boardAnimation by remember { mutableStateOf<BoardAnimation?>(null) }
        var queuedDirection by remember { mutableStateOf<Direction?>(null) }
        var animationId by remember { mutableStateOf(0) }

        LaunchedEffect(Unit) {
            GameStore.load(context)?.let { game = it }
        }

        fun save(snapshot: GameSnapshot) {
            scope.launch { GameStore.save(context, snapshot) }
        }

        fun move(direction: Direction) {
            if (dialog != null) return
            if (boardAnimation != null) {
                queuedDirection = direction
                return
            }
            val moved = GameEngine.move(game.cells, direction, captureMotions = true)
            if (!moved.moved) return
            val cells = GameEngine.addRandomTile(moved.cells)
            val score = game.score + moved.scoreDelta
            val bestScore = maxOf(game.bestScore, score)
            val hasAcknowledgedWin = game.hasAcknowledgedWin
            val pulses = mergePulses(moved.motions)
            
            val next = GameSnapshot(
                cells = cells,
                score = score,
                bestScore = bestScore,
                hasAcknowledgedWin = hasAcknowledgedWin,
            )
            previous = game
            game = next
            animationId++
            boardAnimation = BoardAnimation(
                id = animationId,
                cellsAfterMove = moved.cells,
                motions = moved.motions,
                mergePulses = pulses,
                spawnedIndex = findSpawnedIndex(moved.cells, cells),
                durationMs = if (pulses.isEmpty()) {
                    TILE_MOTION_DURATION_MS
                } else {
                    TILE_MOTION_DURATION_MS + MERGE_DURATION_MS
                },
            )
            save(next)
            
            pendingDialog = if (!hasAcknowledgedWin && GameEngine.hasTargetTile(cells)) {
                DialogKind.WON
            } else if (!GameEngine.canMove(cells)) {
                DialogKind.OVER
            } else null
        }

        LaunchedEffect(boardAnimation?.id) {
            val activeAnimation = boardAnimation
            if (activeAnimation != null) {
                delay(activeAnimation.durationMs.toLong())
                boardAnimation = null
                pendingDialog?.let { nextDialog ->
                    dialog = nextDialog
                    pendingDialog = null
                } ?: queuedDirection?.let { nextDirection ->
                    queuedDirection = null
                    move(nextDirection)
                }
            }
        }

        fun newGame() {
            val next = GameEngine.newGame(game.bestScore)
            game = next
            previous = null
            dialog = null
            pendingDialog = null
            boardAnimation = null
            queuedDirection = null
            save(next)
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
                    animation = boardAnimation,
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
                        enabled = previous != null && dialog == null && boardAnimation == null,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            previous?.let { restored ->
                                game = restored
                                previous = null
                                pendingDialog = null
                                boardAnimation = null
                                queuedDirection = null
                                save(restored)
                            }
                        },
                    )
                    GameButton(
                        label = "新局",
                        enabled = dialog == null && boardAnimation == null,
                        emphasized = true,
                        modifier = Modifier.weight(1f),
                        onClick = { dialog = DialogKind.RESTART },
                    )
                }
            }

            AnimatedContent(
                targetState = dialog,
                transitionSpec = {
                    fadeIn(tween(DIALOG_FADE_DURATION_MS, easing = FastOutSlowInEasing)) togetherWith
                        fadeOut(tween(DIALOG_FADE_DURATION_MS, easing = FastOutSlowInEasing))
                },
                modifier = Modifier.fillMaxSize(),
                label = "game_dialog",
            ) { activeDialog ->
                activeDialog?.let { currentDialog ->
                    GameDialog(
                        kind = currentDialog,
                        onContinue = {
                            if (currentDialog == DialogKind.WON) {
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
}

@Composable
private fun Header(game: GameSnapshot) {
    val shownScore by animateIntAsState(
        targetValue = game.score,
        animationSpec = tween(TILE_MOTION_DURATION_MS, easing = FastOutSlowInEasing),
        label = "score",
    )
    val shownBestScore by animateIntAsState(
        targetValue = game.bestScore,
        animationSpec = tween(TILE_MOTION_DURATION_MS, easing = FastOutSlowInEasing),
        label = "best_score",
    )
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
            ScoreBox("分数", shownScore)
            ScoreBox("最高", shownBestScore)
        }
    }
}

@Composable
private fun ScoreBox(label: String, score: Int) {
    Column(
        modifier = Modifier
            .clip(CardShape)
            .background(AppColors.Board)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = AppColors.Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Text(score.toString(), color = AppColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GameBoard(
    cells: IntArray,
    animation: BoardAnimation?,
    modifier: Modifier,
    onMove: (Direction) -> Unit,
) {
    val dragOffset = remember { FloatArray(2) }
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val boardSide = minOf(maxWidth, maxHeight)
        Box(
            modifier = Modifier
                .size(boardSide)
                .clip(BoardShape)
                .background(AppColors.Board)
                .padding(BoardPadding)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            dragOffset[0] = 0f
                            dragOffset[1] = 0f
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            dragOffset[0] += amount.x
                            dragOffset[1] += amount.y
                        },
                        onDragEnd = {
                            val direction = swipeDirection(dragOffset[0], dragOffset[1])
                            if (direction != null) onMove(direction)
                        },
                    )
                },
        ) {
            val baseCells = animation?.cellsAfterMove ?: cells
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(BoardGap),
            ) {
                repeat(BOARD_SIDE) { row ->
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(BoardGap),
                    ) {
                        repeat(BOARD_SIDE) { column ->
                            val index = row * BOARD_SIDE + column
                            Tile(
                                value = baseCells[index],
                                hidden = animation != null && hidesBaseTile(index, animation),
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
            }

            animation?.let { activeAnimation ->
                AnimatedTileLayer(activeAnimation, boardSide)
            }
        }
    }
}

@Composable
private fun AnimatedTileLayer(animation: BoardAnimation, boardSide: Dp) {
    val progress = remember(animation.id) { Animatable(0f) }
    LaunchedEffect(animation.id) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(animation.durationMs, easing = FastOutSlowInEasing),
        )
    }

    val tileSide = (boardSide - (BoardPadding * 2) - (BoardGap * (BOARD_SIDE - 1))) / BOARD_SIDE
    val cellStep = tileSide + BoardGap
    animation.motions.forEach { motion ->
        MovingTile(motion, progress.value, animation.durationMs, tileSide, cellStep)
    }
    animation.mergePulses.forEach { pulse ->
        MergeResultTile(pulse, progress.value, animation.durationMs, tileSide, cellStep)
    }
    if (animation.spawnedIndex >= 0) {
        SpawnedTile(animation.cellsAfterMove, animation.spawnedIndex, progress.value, animation.durationMs, tileSide, cellStep)
    }
}

@Composable
private fun MovingTile(motion: TileMotion, progress: Float, durationMs: Int, tileSide: Dp, cellStep: Dp) {
    val fromRow = motion.fromIndex / BOARD_SIDE
    val fromColumn = motion.fromIndex % BOARD_SIDE
    val toRow = motion.toIndex / BOARD_SIDE
    val toColumn = motion.toIndex % BOARD_SIDE
    val movementProgress = (progress * durationMs / TILE_MOTION_DURATION_MS).coerceIn(0f, 1f)
    val mergeFade = if (motion.merges) ((movementProgress - 0.88f) / 0.12f).coerceIn(0f, 1f) else 0f
    val x = cellStep * (fromColumn + (toColumn - fromColumn) * movementProgress)
    val y = cellStep * (fromRow + (toRow - fromRow) * movementProgress)

    Tile(
        value = motion.value,
        modifier = Modifier
            .offset(x = x, y = y)
            .size(tileSide)
            .graphicsLayer(alpha = 1f - mergeFade),
    )
}

@Composable
private fun MergeResultTile(pulse: MergePulse, progress: Float, durationMs: Int, tileSide: Dp, cellStep: Dp) {
    val mergeStart = TILE_MOTION_DURATION_MS.toFloat() / durationMs
    val mergeProgress = ((progress - mergeStart) / (1f - mergeStart)).coerceIn(0f, 1f)
    val row = pulse.targetIndex / BOARD_SIDE
    val column = pulse.targetIndex % BOARD_SIDE
    val easedProgress = FastOutSlowInEasing.transform(mergeProgress)
    val scale = if (easedProgress < 0.18f) {
        1f - (0.06f * (easedProgress / 0.18f))
    } else if (easedProgress < 0.66f) {
        0.94f + (0.14f * ((easedProgress - 0.18f) / 0.48f))
    } else {
        1.08f - (0.08f * ((easedProgress - 0.66f) / 0.34f))
    }

    Tile(
        value = pulse.value,
        modifier = Modifier
            .offset(x = cellStep * column, y = cellStep * row)
            .size(tileSide)
            .graphicsLayer(alpha = mergeProgress, scaleX = scale, scaleY = scale),
    )
}

@Composable
private fun SpawnedTile(cellsAfterMove: IntArray, index: Int, progress: Float, durationMs: Int, tileSide: Dp, cellStep: Dp) {
    val spawnStartMs = maxOf(0, TILE_MOTION_DURATION_MS - SPAWN_DURATION_MS)
    val spawnStart = spawnStartMs.toFloat() / durationMs
    val spawnEnd = (spawnStartMs + SPAWN_DURATION_MS).toFloat() / durationMs
    val spawnProgress = ((progress - spawnStart) / (spawnEnd - spawnStart)).coerceIn(0f, 1f)
    val row = index / BOARD_SIDE
    val column = index % BOARD_SIDE
    val scale = 0.80f + (0.20f * FastOutSlowInEasing.transform(spawnProgress))

    Tile(
        value = cellsAfterMove[index],
        modifier = Modifier
            .offset(x = cellStep * column, y = cellStep * row)
            .size(tileSide)
            .graphicsLayer(alpha = spawnProgress, scaleX = scale, scaleY = scale),
    )
}

@Composable
private fun Tile(value: Int, modifier: Modifier, hidden: Boolean = false) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(TileShape)
            .background(tileColor(value))
            .graphicsLayer(alpha = if (hidden) 0f else 1f),
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

private fun hidesBaseTile(index: Int, animation: BoardAnimation): Boolean {
    if (animation.spawnedIndex == index) return true
    for (motion in animation.motions) {
        if (motion.fromIndex == index || motion.toIndex == index) return true
    }
    return false
}

private fun findSpawnedIndex(cellsAfterMove: IntArray, cellsWithSpawn: IntArray): Int {
    for (index in cellsAfterMove.indices) {
        if (cellsAfterMove[index] == 0 && cellsWithSpawn[index] != 0) return index
    }
    return -1
}

private fun mergePulses(motions: Array<TileMotion>): Array<MergePulse> {
    val candidates = arrayOfNulls<MergePulse>(motions.size / 2)
    var count = 0
    for (motion in motions) {
        if (!motion.merges) continue
        var duplicate = false
        for (candidateIndex in 0 until count) {
            if (candidates[candidateIndex]!!.targetIndex == motion.toIndex) {
                duplicate = true
                break
            }
        }
        if (!duplicate) candidates[count++] = MergePulse(motion.value * 2, motion.toIndex)
    }
    return Array(count) { candidates[it]!! }
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
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed) BUTTON_PRESSED_SCALE else 1f,
        animationSpec = tween(BUTTON_PRESS_DURATION_MS, easing = FastOutSlowInEasing),
        label = "button_scale",
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(42.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale),
        interactionSource = interactionSource,
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
                .clip(DialogShape)
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
