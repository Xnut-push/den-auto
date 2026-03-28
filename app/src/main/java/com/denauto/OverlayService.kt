package com.denauto

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.*
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move

class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private var boardOverlay: ChessBoardOverlay? = null
    private var statusView: View? = null
    private var fabView: View? = null

    private val handler = Handler(Looper.getMainLooper())
    private var autoMode = false
    private var analyzing = false
    private var stockfishThread: StockfishEngine? = null

    companion object {
        const val CHANNEL_ID = "denauto_channel"
        const val NOTIF_ID = 1
    }

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        stockfishThread = StockfishEngine()
        stockfishThread?.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showBoardOverlay()
        showFab()
        return START_STICKY
    }

    private fun overlayParams(w: Int, h: Int, gravity: Int = Gravity.TOP or Gravity.START) =
        WindowManager.LayoutParams(w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT).apply { this.gravity = gravity }

    private fun showBoardOverlay() {
        val prefs = getSharedPreferences("denauto_prefs", Context.MODE_PRIVATE)
        val overlay = ChessBoardOverlay(this) { left, top, size ->
            // Board locked — start auto mode
            updateStatus("🔍 Tablero fijado. Toca ▶ para iniciar.")
        }
        overlay.flipped = prefs.getBoolean("playAsBlack", false)
        boardOverlay = overlay

        val metrics = resources.displayMetrics
        val params = overlayParams(metrics.widthPixels, metrics.heightPixels)
        wm.addView(overlay, params)
    }

    private fun showFab() {
        val fab = TextView(this).apply {
            text = "▶"; textSize = 24f; gravity = Gravity.CENTER
            setBackgroundColor(android.graphics.Color.parseColor("#CC0e0b08"))
            setTextColor(android.graphics.Color.parseColor("#4dde72"))
            setPadding(20, 20, 20, 20)
        }
        fabView = fab
        val params = overlayParams(80, 80, Gravity.BOTTOM or Gravity.END).apply { x = 20; y = 120 }
        fab.setOnClickListener {
            autoMode = !autoMode
            fab.text = if(autoMode) "⏸" else "▶"
            fab.setTextColor(android.graphics.Color.parseColor(if(autoMode) "#f0c030" else "#4dde72"))
            if(autoMode) startAnalysisLoop()
        }
        wm.addView(fab, params)
    }

    private fun startAnalysisLoop() {
        if(!autoMode || analyzing) return
        val overlay = boardOverlay ?: return
        if(!overlay.locked) { updateStatus("⚠ Fija el tablero primero"); autoMode = false; return }

        analyzing = true
        updateStatus("⚙ Analizando posición...")

        val prefs = getSharedPreferences("denauto_prefs", Context.MODE_PRIVATE)
        val elo = prefs.getInt("elo", 1500)
        val playAsBlack = prefs.getBoolean("playAsBlack", false)
        val playerSide = if(playAsBlack) Side.BLACK else Side.WHITE
        val board = overlay.chessBoard

        // Check if it's player's turn
        if(board.sideToMove != playerSide) {
            updateStatus("⏳ Turno del rival...")
            analyzing = false
            handler.postDelayed({ if(autoMode) startAnalysisLoop() }, 1000)
            return
        }

        val fen = board.fen
        Thread {
            val move = stockfishThread?.getBestMove(fen, elo) ?: run {
                handler.post { updateStatus("❌ Sin movimiento"); analyzing = false }
                return@Thread
            }
            handler.post {
                if(!autoMode) { analyzing = false; return@post }
                // Parse move
                val fromStr = move.substring(0, 2)
                val toStr = move.substring(2, 4)
                try {
                    val fromSq = Square.fromValue(fromStr.uppercase())
                    val toSq = Square.fromValue(toStr.uppercase())
                    overlay.bestMoveFrom = fromSq
                    overlay.bestMoveTo = toSq
                    overlay.invalidate()
                    updateStatus("♟ Jugando: $move")

                    // Apply move on internal board
                    val mv = Move(fromSq, toSq)
                    board.doMove(mv)
                    overlay.invalidate()

                    // Auto click
                    val fromPt = overlay.sqToScreen(fromSq)
                    val toPt = overlay.sqToScreen(toSq)
                    AutoClickService.tapSequence(fromPt.x, fromPt.y, toPt.x, toPt.y, 350) {
                        handler.postDelayed({
                            analyzing = false
                            if(autoMode) startAnalysisLoop()
                        }, 800)
                    }
                } catch(e: Exception) {
                    updateStatus("❌ Error: ${e.message}")
                    analyzing = false
                }
            }
        }.start()
    }

    private fun updateStatus(text: String) {
        handler.post { (statusView as? TextView)?.text = text }
    }

    override fun onDestroy() {
        super.onDestroy()
        listOf(boardOverlay, statusView, fabView).forEach {
            it?.let { v -> try { wm.removeView(v) } catch(_: Exception) {} }
        }
        stockfishThread?.quit()
    }

    private fun createNotificationChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "Den Auto", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Den Auto activo")
            .setContentText("Bot de ajedrez en ejecución")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
}
