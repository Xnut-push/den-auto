package com.denauto

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Square

class ChessBoardOverlay(
    context: Context,
    private val onBoardMoved: (left: Float, top: Float, size: Float) -> Unit
) : View(context) {

    var boardLeft = 100f
    var boardTop = 200f
    var boardSize = 500f
    var flipped = false
    var locked = false

    private var dragging = false
    private var lastX = 0f; private var lastY = 0f
    private var resizing = false

    // Chess state
    var chessBoard = Board()
    var bestMoveFrom: Square? = null
    var bestMoveTo: Square? = null
    var isPlayerTurn = true

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#e8b84b")
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }
    private val lightSqPaint = Paint().apply { color = Color.parseColor("#66f0d9b5") }
    private val darkSqPaint = Paint().apply { color = Color.parseColor("#66b58863") }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CC4dde72")
        strokeWidth = 12f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val arrowFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CC4dde72")
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 14f
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#e8b84b")
        style = Paint.Style.FILL
    }
    private val lockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4dde72")
        style = Paint.Style.FILL
    }

    val sqSize get() = boardSize / 8f

    fun sqToScreen(sq: Square): PointF {
        val file = sq.file.ordinal
        val rank = sq.rank.ordinal
        val col = if(flipped) 7 - file else file
        val row = if(flipped) rank else 7 - rank
        return PointF(boardLeft + col * sqSize + sqSize / 2, boardTop + row * sqSize + sqSize / 2)
    }

    fun screenToSq(x: Float, y: Float): Square? {
        if(x < boardLeft || x > boardLeft + boardSize || y < boardTop || y > boardTop + boardSize) return null
        val col = ((x - boardLeft) / sqSize).toInt().coerceIn(0, 7)
        val row = ((y - boardTop) / sqSize).toInt().coerceIn(0, 7)
        val file = if(flipped) 7 - col else col
        val rank = if(flipped) row else 7 - row
        return try { Square.squareAt(rank * 8 + file) } catch(e: Exception) { null }
    }

    override fun onDraw(canvas: Canvas) {
        val sz = sqSize
        // Draw semi-transparent board squares
        for(row in 0..7) for(col in 0..7) {
            val isLight = (row + col) % 2 != 0
            val paint = if(isLight) lightSqPaint else darkSqPaint
            canvas.drawRect(boardLeft + col*sz, boardTop + row*sz, boardLeft + (col+1)*sz, boardTop + (row+1)*sz, paint)
        }

        // Draw border
        canvas.drawRect(boardLeft, boardTop, boardLeft + boardSize, boardTop + boardSize, borderPaint)

        // Draw pieces as emoji text
        val piecePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sz * 0.7f
            textAlign = Paint.Align.CENTER
        }
        for(sq in Square.values()) {
            if(sq == Square.NONE) continue
            val piece = chessBoard.getPiece(sq)
            if(piece == Piece.NONE) continue
            val pt = sqToScreen(sq)
            val glyph = pieceGlyph(piece)
            // Shadow
            piecePaint.color = Color.BLACK
            canvas.drawText(glyph, pt.x + 1f, pt.y - sz*0.3f + sz*0.5f + 1f, piecePaint)
            piecePaint.color = if(piece.pieceSide.value() == "WHITE") Color.WHITE else Color.parseColor("#222222")
            canvas.drawText(glyph, pt.x, pt.y - sz*0.3f + sz*0.5f, piecePaint)
        }

        // Draw best move arrow
        val from = bestMoveFrom; val to = bestMoveTo
        if(from != null && to != null) {
            val fp = sqToScreen(from); val tp = sqToScreen(to)
            val dx = tp.x - fp.x; val dy = tp.y - fp.y
            val len = Math.sqrt((dx*dx+dy*dy).toDouble()).toFloat()
            if(len > 2) {
                val trim = 20f
                val ex = tp.x - dx/len*trim; val ey = tp.y - dy/len*trim
                canvas.drawLine(fp.x, fp.y, ex, ey, arrowPaint)
                // Arrowhead
                val angle = Math.atan2(dy.toDouble(), dx.toDouble())
                val headLen = 24f; val headAngle = 0.5
                val path = Path().apply {
                    moveTo(tp.x, tp.y)
                    lineTo((tp.x - headLen * Math.cos(angle-headAngle)).toFloat(), (tp.y - headLen * Math.sin(angle-headAngle)).toFloat())
                    lineTo((tp.x - headLen * Math.cos(angle+headAngle)).toFloat(), (tp.y - headLen * Math.sin(angle+headAngle)).toFloat())
                    close()
                }
                canvas.drawPath(path, arrowFill)
            }
        }

        // Draw resize handle (bottom-right corner)
        if(!locked) {
            canvas.drawCircle(boardLeft + boardSize, boardTop + boardSize, 20f, handlePaint)
            canvas.drawCircle(boardLeft + boardSize/2, boardTop + boardSize + 30f, 15f, lockPaint)
            textPaint.textSize = 11f; textPaint.color = Color.parseColor("#0e0b08")
            canvas.drawText("FIJAR", boardLeft + boardSize/2, boardTop + boardSize + 35f, textPaint)
        } else {
            textPaint.textSize = 13f; textPaint.color = Color.parseColor("#4dde72")
            canvas.drawText(if(isPlayerTurn) "TU TURNO" else "ANALIZANDO...", boardLeft + boardSize/2, boardTop - 10f, textPaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x = e.x; val y = e.y
        when(e.action) {
            MotionEvent.ACTION_DOWN -> {
                // Check lock button
                val lockX = boardLeft + boardSize/2; val lockY = boardTop + boardSize + 30f
                if(!locked && Math.sqrt(((x-lockX)*(x-lockX)+(y-lockY)*(y-lockY)).toDouble()) < 30) {
                    locked = true; invalidate()
                    onBoardMoved(boardLeft, boardTop, boardSize)
                    return true
                }
                // Check resize handle
                val rx = boardLeft + boardSize; val ry = boardTop + boardSize
                if(!locked && Math.sqrt(((x-rx)*(x-rx)+(y-ry)*(y-ry)).toDouble()) < 30) {
                    resizing = true
                } else if(!locked) {
                    dragging = true
                }
                lastX = x; lastY = y
            }
            MotionEvent.ACTION_MOVE -> {
                if(locked) return false
                val dx = x - lastX; val dy = y - lastY
                if(resizing) {
                    boardSize = (boardSize + (dx + dy) / 2).coerceAtLeast(200f)
                } else if(dragging) {
                    boardLeft += dx; boardTop += dy
                }
                lastX = x; lastY = y
                invalidate()
            }
            MotionEvent.ACTION_UP -> { dragging = false; resizing = false }
        }
        return true
    }

    private fun pieceGlyph(piece: Piece): String = when(piece) {
        Piece.WHITE_PAWN -> "♙"; Piece.WHITE_KNIGHT -> "♘"; Piece.WHITE_BISHOP -> "♗"
        Piece.WHITE_ROOK -> "♖"; Piece.WHITE_QUEEN -> "♕"; Piece.WHITE_KING -> "♔"
        Piece.BLACK_PAWN -> "♟"; Piece.BLACK_KNIGHT -> "♞"; Piece.BLACK_BISHOP -> "♝"
        Piece.BLACK_ROOK -> "♜"; Piece.BLACK_QUEEN -> "♛"; Piece.BLACK_KING -> "♚"
        else -> ""
    }
}
