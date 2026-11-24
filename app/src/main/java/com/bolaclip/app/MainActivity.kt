package com.bolaclip.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 48)
            setBackgroundColor(0xFFFFF6FA.toInt())
        }

        val title = TextView(this).apply {
            text = "🍬 BolaClip"
            textSize = 26f
            setTextColor(0xFF5C2644.toInt())
        }

        val subtitle = TextView(this).apply {
            text = "Bolinhas flutuantes sobre outros apps (até 30 salvas)\n1 clique copia • 2 cliques edita • 3 cliques salva\nCriador: ${AppConfig.CREATOR_NAME}"
            textSize = 14f
            setTextColor(0xFF6E3B57.toInt())
            setPadding(0, 16, 0, 32)
        }

        val btnPermission = Button(this).apply {
            text = "1. Permitir 'Aparecer sobre outros apps'"
            setBackgroundColor(0xFFFF9EC7.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener { requestOverlayPermission() }
        }

        val btnStartBubbles = Button(this).apply {
            text = "2. Ativar Bolinhas Flutuantes"
            setBackgroundColor(0xFF8AD8C8.toInt())
            setTextColor(0xFF1F473F.toInt())
            setOnClickListener { startOverlayAction(FloatingBubbleOverlayService.ACTION_SHOW_BUBBLES) }
        }

        val btnAddBubble = Button(this).apply {
            text = "3. Nova Bolinha (máx. 30)"
            setBackgroundColor(0xFFFFD670.toInt())
            setTextColor(0xFF4D3700.toInt())
            setOnClickListener { startOverlayAction(FloatingBubbleOverlayService.ACTION_ADD_BUBBLE) }
        }

        val btnHideBubbles = Button(this).apply {
            text = "Ocultar Bolinhas"
            setBackgroundColor(0xFFFFE3EC.toInt())
            setTextColor(0xFF8A2446.toInt())
            setOnClickListener { startOverlayAction(FloatingBubbleOverlayService.ACTION_HIDE_ALL) }
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(btnPermission)
        root.addView(btnStartBubbles)
        root.addView(btnAddBubble)
        root.addView(btnHideBubbles)
        setContentView(root)
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } else {
            Toast.makeText(this, "Permissão de sobreposição já ativa!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startOverlayAction(action: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Primeiro permita 'Aparecer sobre outros apps'.", Toast.LENGTH_SHORT).show()
            requestOverlayPermission()
            return
        }
        val intent = Intent(this, FloatingBubbleOverlayService::class.java).apply {
            this.action = action
        }
        ContextCompat.startForegroundService(this, intent)
    }
}
