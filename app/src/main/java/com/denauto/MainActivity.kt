package com.denauto

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusText = findViewById<TextView>(R.id.statusText)
        val startBtn = findViewById<Button>(R.id.startBtn)
        val accessBtn = findViewById<Button>(R.id.accessBtn)
        val colorToggle = findViewById<Switch>(R.id.colorToggle)
        val eloSlider = findViewById<SeekBar>(R.id.eloSlider)
        val eloLabel = findViewById<TextView>(R.id.eloLabel)

        val prefs = getSharedPreferences("denauto_prefs", Context.MODE_PRIVATE)
        colorToggle.isChecked = prefs.getBoolean("playAsBlack", false)
        eloSlider.progress = prefs.getInt("eloProgress", 45)

        val eloValues = (600..3600 step 100).toList()
        eloLabel.text = "ELO: ${eloValues.getOrElse(eloSlider.progress){1500}}"

        eloSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, u: Boolean) {
                val elo = eloValues.getOrElse(p){1500}
                eloLabel.text = "ELO: $elo"
                prefs.edit().putInt("eloProgress", p).putInt("elo", elo).apply()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        colorToggle.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("playAsBlack", checked).apply()
            colorToggle.text = if(checked) "Juego con ♚ Negras" else "Juego con ♔ Blancas"
        }
        colorToggle.text = if(colorToggle.isChecked) "Juego con ♚ Negras" else "Juego con ♔ Blancas"

        accessBtn.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        startBtn.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                statusText.text = "⚠ Activa permiso de overlay primero"
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
                return@setOnClickListener
            }
            if (!AutoClickService.isEnabled) {
                statusText.text = "⚠ Activa el servicio de accesibilidad primero"
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                return@setOnClickListener
            }
            startForegroundService(Intent(this, OverlayService::class.java))
            statusText.text = "✅ Overlay activo — posiciona el tablero"
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        val statusText = findViewById<TextView>(R.id.statusText)
        val overlayOk = Settings.canDrawOverlays(this)
        val accessOk = AutoClickService.isEnabled
        statusText.text = when {
            !overlayOk -> "⚠ Falta permiso de overlay"
            !accessOk -> "⚠ Activa el servicio de accesibilidad"
            else -> "✅ Todo listo — toca Iniciar"
        }
    }
}
