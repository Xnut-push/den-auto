package com.denauto

import android.os.HandlerThread
import java.io.*

class StockfishEngine : HandlerThread("Stockfish") {

    private var process: Process? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null

    override fun start() {
        super.start()
        try {
            // Use chesslib for move generation instead of native Stockfish binary
            // Since we can't bundle a binary in debug APK without NDK, use Java engine
            isReady = true
        } catch(e: Exception) {
            e.printStackTrace()
        }
    }

    @Volatile var isReady = false

    fun getBestMove(fen: String, elo: Int): String? {
        return try {
            val board = com.github.bhlangonijr.chesslib.Board()
            board.loadFromFen(fen)
            val moves = board.legalMoves()
            if(moves.isEmpty()) return null

            // Score moves using simple evaluation
            val scored = moves.map { move ->
                val copy = com.github.bhlangonijr.chesslib.Board()
                copy.loadFromFen(fen)
                copy.doMove(move)
                val score = evaluate(copy, board.sideToMove)
                Pair(move, score)
            }.sortedByDescending { it.second }

            // Add randomness based on ELO (lower ELO = more random)
            val topN = when {
                elo < 800 -> scored.size.coerceAtLeast(1)
                elo < 1200 -> (scored.size / 2).coerceAtLeast(1)
                elo < 1800 -> (scored.size / 3).coerceAtLeast(1)
                elo < 2400 -> 2.coerceAtLeast(1)
                else -> 1
            }
            val chosen = scored.take(topN).random()
            val move = chosen.first
            "${move.from.value().lowercase()}${move.to.value().lowercase()}${
                if(move.promotion != com.github.bhlangonijr.chesslib.Piece.NONE)
                    move.promotion.fenSymbol.lowercase() else ""
            }"
        } catch(e: Exception) { null }
    }

    private fun evaluate(board: com.github.bhlangonijr.chesslib.Board, side: com.github.bhlangonijr.chesslib.Side): Int {
        val pieceValues = mapOf(
            com.github.bhlangonijr.chesslib.PieceType.PAWN to 100,
            com.github.bhlangonijr.chesslib.PieceType.KNIGHT to 320,
            com.github.bhlangonijr.chesslib.PieceType.BISHOP to 330,
            com.github.bhlangonijr.chesslib.PieceType.ROOK to 500,
            com.github.bhlangonijr.chesslib.PieceType.QUEEN to 900,
            com.github.bhlangonijr.chesslib.PieceType.KING to 20000
        )
        var score = 0
        for(sq in com.github.bhlangonijr.chesslib.Square.values()) {
            if(sq == com.github.bhlangonijr.chesslib.Square.NONE) continue
            val piece = board.getPiece(sq)
            if(piece == com.github.bhlangonijr.chesslib.Piece.NONE) continue
            val value = pieceValues[piece.pieceType] ?: 0
            score += if(piece.pieceSide == side) value else -value
        }
        // Mobility bonus
        score += board.legalMoves().size * 5
        return score
    }

    fun quit() {
        process?.destroy()
        quitSafely()
    }
}
