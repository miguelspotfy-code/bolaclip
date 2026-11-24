package com.bolaclip.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

data class BubbleItem(
    val id: String,
    var label: String,
    var content: String,
    var x: Int,
    var y: Int
)

class FloatingBubbleOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private val bubbleViews = mutableMapOf<String, View>()
    private val bubbles = mutableListOf<BubbleItem>()

    private var activeDialogView: View? = null
    private var activeDevPanelView: View? = null
    private var activeCreditsView: View? = null

    private val tapHandler = Handler(Looper.getMainLooper())
    private val tapClickCounts = mutableMapOf<String, Int>()
    private val tapRunnables = mutableMapOf<String, Runnable>()

    companion object {
        const val ACTION_SHOW_BUBBLES = "com.bolaclip.app.ACTION_SHOW_BUBBLES"
        const val ACTION_ADD_BUBBLE = "com.bolaclip.app.ACTION_ADD_BUBBLE"
        const val ACTION_HIDE_ALL = "com.bolaclip.app.ACTION_HIDE_ALL"
        const val CHANNEL_ID = "bolaclip_overlay_channel"
        const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        loadBubblesFromStorage()
        if (bubbles.isEmpty()) {
            bubbles.add(
                BubbleItem(
                    id = "bubble_1",
                    label = "CLIP",
                    content = "Exemplo de texto rápido do BolaClip. 1 clique copia, 2 cliques edita.",
                    x = 80,
                    y = 260
                )
            )
            saveBubblesToStorage()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ADD_BUBBLE -> createNewBubble()
            ACTION_HIDE_ALL -> removeAllOverlays()
            ACTION_SHOW_BUBBLES, null -> renderAllBubbles()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun overlayLayoutParams(
        width: Int,
        height: Int,
        x: Int,
        y: Int,
        focusable: Boolean
    ): WindowManager.LayoutParams {
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val flags = if (focusable) {
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }

        return WindowManager.LayoutParams(
            width,
            height,
            overlayType,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
    }

    private fun createNewBubble() {
        if (bubbles.size >= AppConfig.MAX_BUBBLES) {
            Toast.makeText(
                this,
                "Limite de ${AppConfig.MAX_BUBBLES} bolinhas salvas atingido.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val index = bubbles.size + 1
        val item = BubbleItem(
            id = "bubble_${System.currentTimeMillis()}_$index",
            label = "B$index".take(AppConfig.MAX_BUBBLE_LABEL_CHARS),
            content = "",
            x = 100 + (index * 20) % 220,
            y = 240 + (index * 35) % 360
        )
        bubbles.add(item)
        saveBubblesToStorage()
        renderBubble(item)
        openBubbleDialog(item)
    }

    private fun renderAllBubbles() {
        bubbles.forEach { renderBubble(it) }
    }

    private fun renderBubble(item: BubbleItem) {
        bubbleViews[item.id]?.let { existing ->
            try {
                windowManager.removeView(existing)
            } catch (_: Exception) {
            }
        }

        val inflater = LayoutInflater.from(this)
        val bubbleView = inflater.inflate(R.layout.layout_bubble, null)
        val tvLabel = bubbleView.findViewById<TextView>(R.id.tvBubbleLabel)
        tvLabel.text = item.label.take(AppConfig.MAX_BUBBLE_LABEL_CHARS).ifEmpty { "CLIP" }

        val params = overlayLayoutParams(
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            x = item.x,
            y = item.y,
            focusable = false
        )

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var moved = false

        bubbleView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) {
                        moved = true
                        params.x = initialX + dx
                        params.y = initialY + dy
                        item.x = params.x
                        item.y = params.y
                        try {
                            windowManager.updateViewLayout(v, params)
                        } catch (_: Exception) {
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        handleBubbleClickGesture(item)
                    } else {
                        saveBubblesToStorage()
                    }
                    true
                }

                else -> false
            }
        }

        try {
            windowManager.addView(bubbleView, params)
            bubbleViews[item.id] = bubbleView
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Ative 'Aparecer sobre outros apps' para exibir as bolinhas.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun handleBubbleClickGesture(item: BubbleItem) {
        val currentCount = (tapClickCounts[item.id] ?: 0) + 1
        tapClickCounts[item.id] = currentCount

        tapRunnables[item.id]?.let { tapHandler.removeCallbacks(it) }

        val runnable = Runnable {
            val clicks = tapClickCounts[item.id] ?: 0
            tapClickCounts[item.id] = 0
            when (clicks) {
                1 -> copyBubbleContent(item)
                2 -> openBubbleDialog(item)
                else -> saveBubbleQuick(item)
            }
        }
        tapRunnables[item.id] = runnable
        tapHandler.postDelayed(runnable, 280L)
    }

    private fun copyBubbleContent(item: BubbleItem) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(item.label, item.content))
        Toast.makeText(this, "Copiado [${item.label}]", Toast.LENGTH_SHORT).show()
    }

    private fun saveBubbleQuick(item: BubbleItem) {
        saveBubblesToStorage()
        Toast.makeText(this, "Salvo [${item.label}] (${bubbles.size}/${AppConfig.MAX_BUBBLES})", Toast.LENGTH_SHORT).show()
    }

    private fun openBubbleDialog(item: BubbleItem) {
        closeActiveDialog()

        val inflater = LayoutInflater.from(this)
        val dialogView = inflater.inflate(R.layout.layout_bubble_dialog, null)

        val etShortName = dialogView.findViewById<EditText>(R.id.etBubbleShortName)
        val etContent = dialogView.findViewById<EditText>(R.id.etBubbleContent)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSaveBubble)
        val btnCopy = dialogView.findViewById<Button>(R.id.btnCopyBubble)
        val btnDelete = dialogView.findViewById<Button>(R.id.btnDeleteBubble)
        val btnCredits = dialogView.findViewById<Button>(R.id.btnOpenCredits)
        val btnClose = dialogView.findViewById<Button>(R.id.btnCloseDialog)

        etShortName.setText(item.label.take(AppConfig.MAX_BUBBLE_LABEL_CHARS))
        etContent.setText(item.content)

        btnSave.setOnClickListener {
            val newLabel = etShortName.text.toString().trim().take(AppConfig.MAX_BUBBLE_LABEL_CHARS)
            val newContent = etContent.text.toString()

            if (newContent.trim() == AppConfig.DEV_PANEL_TRIGGER || newLabel == AppConfig.DEV_PANEL_TRIGGER) {
                closeActiveDialog()
                openDevPanel()
                return@setOnClickListener
            }

            item.label = if (newLabel.isEmpty()) "CLIP" else newLabel
            item.content = newContent
            saveBubblesToStorage()
            renderBubble(item)
            Toast.makeText(this, "Bolinha salva!", Toast.LENGTH_SHORT).show()
            closeActiveDialog()
        }

        btnCopy.setOnClickListener {
            val textToCopy = etContent.text.toString()
            item.content = textToCopy
            copyBubbleContent(item)
        }

        btnDelete.setOnClickListener {
            bubbleViews[item.id]?.let {
                try {
                    windowManager.removeView(it)
                } catch (_: Exception) {
                }
            }
            bubbleViews.remove(item.id)
            bubbles.removeAll { it.id == item.id }
            saveBubblesToStorage()
            Toast.makeText(this, "Bolinha removida.", Toast.LENGTH_SHORT).show()
            closeActiveDialog()
        }

        btnCredits.setOnClickListener {
            closeActiveDialog()
            openCreditsDialog()
        }

        btnClose.setOnClickListener {
            closeActiveDialog()
        }

        val params = overlayLayoutParams(
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            x = 40,
            y = 140,
            focusable = true
        )

        try {
            windowManager.addView(dialogView, params)
            activeDialogView = dialogView
        } catch (_: Exception) {
        }
    }

    private fun openDevPanel() {
        closeDevPanel()
        val prefs = getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE)
        val inflater = LayoutInflater.from(this)
        val panelView = inflater.inflate(R.layout.layout_dev_panel, null)

        val etUrl = panelView.findViewById<EditText>(R.id.etSupabaseUrl)
        val etKey = panelView.findViewById<EditText>(R.id.etSupabaseKey)
        val etToken = panelView.findViewById<EditText>(R.id.etGithubToken)
        val rgMode = panelView.findViewById<RadioGroup>(R.id.rgCodeExportMode)
        val etRecipient = panelView.findViewById<EditText>(R.id.etRecipientEmail)
        val etAddon = panelView.findViewById<EditText>(R.id.etAddonPayload)
        val btnSendEmail = panelView.findViewById<Button>(R.id.btnSendCodeEmail)
        val btnSave = panelView.findViewById<Button>(R.id.btnSaveDevConfig)
        val btnTestAddon = panelView.findViewById<Button>(R.id.btnTestAddon)
        val btnDeploy = panelView.findViewById<Button>(R.id.btnDeployUpdate)
        val btnClose = panelView.findViewById<Button>(R.id.btnCloseDevPanel)

        etUrl.setText(prefs.getString(AppConfig.KEY_SUPABASE_URL, AppConfig.DEFAULT_SUPABASE_URL))
        etKey.setText(prefs.getString(AppConfig.KEY_SUPABASE_KEY, AppConfig.DEFAULT_SUPABASE_PUBLISHABLE_KEY))
        etToken.setText(prefs.getString(AppConfig.KEY_GITHUB_TOKEN, AppConfig.DEFAULT_GITHUB_TOKEN))

        btnSendEmail.setOnClickListener {
            val isAddendum = rgMode.checkedRadioButtonId == R.id.rbAddendumCode
            val modeLabel = if (isAddendum) "Código com Adendos" else "Código Original"
            val recipient = etRecipient.text.toString().trim()
            val addendumNote = etAddon.text.toString().trim()

            val emailBody = buildString {
                append("Olá,\n")
            append("Segue a entrega referente ao BolaClip ($modelLabel).\n")
            if (isAddendum && addendumNote.isNotEmpty()) {
                append("Adendos incluídos:\n$addendumNote\n")
            }
            append("Criador: Esteffeson Matheus Barquete\n")
            append("WhatsApp: 54993291661\n")
            append("E-mail: barqueteesteffeson@gmail.com\n")
        }

            val emailIntent = Intent(Intent.ACTION_SEND).apply {
                type = "message/rfc822"
                if (recipient.isNotEmpty()) {
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
                }
                putExtra(Intent.EXTRA_SUBJECT, "Entrega BolaClip - $modeLabel")
                putExtra(Intent.EXTRA_TEXT, emailBody)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            try {
                startActivity(Intent.createChooser(emailIntent, "Enviar por e-mail").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (_: Exception) {
                Toast.makeText(this, "Nenhum app de e-mail disponível no dispositivo.", Toast.LENGTH_SHORT).show()
            }
        }

        btnSave.setOnClickListener {
            prefs.edit()
                .putString(AppConfig.KEY_SUPABASE_URL, etUrl.text.toString().trim())
                .putString(AppConfig.KEY_SUPABASE_KEY, etKey.text.toString().trim())
                .putString(AppConfig.KEY_GITHUB_TOKEN, etToken.text.toString().trim())
                .apply()
            Toast.makeText(this, "Configurações salvas no dispositivo!", Toast.LENGTH_SHORT).show()
        }

        btnTestAddon.setOnClickListener {
            val payload = etAddon.text.toString().trim().ifEmpty { "ADENDO_TESTE" }
            Toast.makeText(this, "Adendo clonado/testado: $payload", Toast.LENGTH_SHORT).show()
        }

        btnDeploy.setOnClickListener {
            saveBubblesToStorage()
            Toast.makeText(this, "Atualização preparada para sincronização!", Toast.LENGTH_SHORT).show()
        }

        btnClose.setOnClickListener {
            closeDevPanel()
        }

        val params = overlayLayoutParams(
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            x = 24,
            y = 90,
            focusable = true
        )

        try {
            windowManager.addView(panelView, params)
            activeDevPanelView = panelView
        } catch (_: Exception) {
        }
    }

    private fun openCreditsDialog() {
        closeCreditsDialog()
        val inflater = LayoutInflater.from(this)
        val creditsView = inflater.inflate(R.layout.layout_credits_dialog, null)
        val btnClose = creditsView.findViewById<Button>(R.id.btnCloseCredits)
        btnClose.setOnClickListener { closeCreditsDialog() }

        val params = overlayLayoutParams(
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            x = 45,
            y = 180,
            focusable = true
        )

        try {
            windowManager.addView(creditsView, params)
            activeCreditsView = creditsView
        } catch (_: Exception) {
        }
    }

    private fun closeActiveDialog() {
        activeDialogView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        activeDialogView = null
    }

    private fun closeDevPanel() {
        activeDevPanelView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        activeDevPanelView = null
    }

    private fun closeCreditsDialog() {
        activeCreditsView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        activeCreditsView = null
    }

    private fun removeAllOverlays() {
        closeActiveDialog()
        closeDevPanel()
        closeCreditsDialog()
        bubbleViews.values.forEach { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {
            }
        }
        bubbleViews.clear()
    }

    private fun saveBubblesToStorage() {
        val prefs = getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray()
        for (b in bubbles.take(AppConfig.MAX_BUBBLES)) {
            val obj = JSONObject()
            obj.put("id", b.id)
            obj.put("label", b.label)
            obj.put("content", b.content)
            obj.put("x", b.x)
            obj.put("y", b.y)
            arr.put(obj)
        }
        prefs.edit().putString(AppConfig.KEY_BUBBLES_JSON, arr.toString()).apply()
    }

    private fun loadBubblesFromStorage() {
        bubbles.clear()
        val prefs = getSharedPreferences(AppConfig.PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(AppConfig.KEY_BUBBLES_JSON, null) ?: return
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                if (bubbles.size >= AppConfig.MAX_BUBBLES) break
                val obj = arr.getJSONObject(i)
                bubbles.add(
                    BubbleItem(
                        id = obj.optString("id", "bubble_$i"),
                        label = obj.optString("label", "CLIP").take(AppConfig.MAX_BUBBLE_LABEL_CHARS),
                        content = obj.optString("content", ""),
                        x = obj.optInt("x", 80),
                        y = obj.optInt("y", 240)
                    )
                )
            }
        } catch (_: Exception) {
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.overlay_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        removeAllOverlays()
        super.onDestroy()
    }
}
