package com.esteffeson.bolaclip;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.List;

public class OverlayService extends Service {

    private static final String CHANNEL_ID = "bolaclip_overlay_channel";
    private static final int NOTIFICATION_ID = 2026;

    private WindowManager windowManager;
    private View matrixBubbleView;
    private WindowManager.LayoutParams matrixParams;
    private final List<View> activeOverlays = new ArrayList<>();
    private final List<String> savedClips = new ArrayList<>();
    private int bubbleCounter = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundNotification();

        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        showMatrixBubble();
    }

    private void startForegroundNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "BolaClip Ativo",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Mantém as bolinhas e janelas do BolaClip sobrepostas");
            manager.createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("BolaClip")
                .setContentText("Bolinha matriz ativa sobre a tela")
                .setSmallIcon(R.drawable.ic_bolaclip_launcher)
                .setOngoing(true)
                .build();

        startForeground(NOTIFICATION_ID, notification);
    }

    private int createOverlayType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        }
        return WindowManager.LayoutParams.TYPE_PHONE;
    }

    private void showMatrixBubble() {
        if (matrixBubbleView != null || windowManager == null) return;

        TextView plusBubble = new TextView(this);
        plusBubble.setText("+");
        plusBubble.setTextColor(Color.parseColor("#4A3F35"));
        plusBubble.setTextSize(24f);
        plusBubble.setGravity(Gravity.CENTER);

        GradientDrawable bubbleBg = new GradientDrawable();
        bubbleBg.setShape(GradientDrawable.OVAL);
        bubbleBg.setColor(Color.parseColor("#FFD1DC"));
        bubbleBg.setStroke(3, Color.parseColor("#F4A7B9"));
        plusBubble.setBackground(bubbleBg);

        int sizePx = dp(52);
        matrixParams = new WindowManager.LayoutParams(
                sizePx,
                sizePx,
                createOverlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        matrixParams.gravity = Gravity.TOP | Gravity.START;
        matrixParams.x = dp(18);
        matrixParams.y = dp(420);

        final int touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        plusBubble.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private boolean isDragging;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = matrixParams.x;
                        initialY = matrixParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isDragging = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - initialTouchX);
                        int dy = (int) (event.getRawY() - initialTouchY);
                        if (Math.hypot(dx, dy) > touchSlop) {
                            isDragging = true;
                        }
                        if (isDragging) {
                            matrixParams.x = initialX + dx;
                            matrixParams.y = initialY + dy;
                            windowManager.updateViewLayout(plusBubble, matrixParams);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (!isDragging) {
                            spawnCandyBubble();
                        }
                        return true;
                }
                return false;
            }
        });

        matrixBubbleView = plusBubble;
        windowManager.addView(matrixBubbleView, matrixParams);
    }

    private void spawnCandyBubble() {
        if (windowManager == null) return;
        bubbleCounter++;
        final int idx = bubbleCounter;

        String[] pastelColors = {"#BAE1FF", "#BAFFC9", "#FFFFBA", "#E2CFEA"};
        String[] borderColors = {"#89C4F4", "#82E0AA", "#F7DC6F", "#C39BD3"};
        int colorIndex = idx % pastelColors.length;

        TextView bubble = new TextView(this);
        bubble.setText(String.valueOf(idx));
        bubble.setTextColor(Color.parseColor("#4A3F35"));
        bubble.setTextSize(17f);
        bubble.setGravity(Gravity.CENTER);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.parseColor(pastelColors[colorIndex]));
        bg.setStroke(3, Color.parseColor(borderColors[colorIndex]));
        bubble.setBackground(bg);

        int sizePx = dp(48);
        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                sizePx,
                sizePx,
                createOverlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(84) + ((idx * 18) % dp(120));
        params.y = dp(380) + ((idx * 20) % dp(140));

        final int touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        bubble.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private boolean isDragging;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isDragging = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - initialTouchX);
                        int dy = (int) (event.getRawY() - initialTouchY);
                        if (Math.hypot(dx, dy) > touchSlop) {
                            isDragging = true;
                        }
                        if (isDragging) {
                            params.x = initialX + dx;
                            params.y = initialY + dy;
                            windowManager.updateViewLayout(bubble, params);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (!isDragging) {
                            openBubbleWindow(idx, bubble);
                        }
                        return true;
                }
                return false;
            }
        });

        activeOverlays.add(bubble);
        windowManager.addView(bubble, params);
    }

    private void openBubbleWindow(int idx, final View ownerBubble) {
        if (windowManager == null) return;

        final FrameLayout container = new FrameLayout(this);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(dp(22));
        cardBg.setColor(Color.parseColor("#FFF9FB"));
        cardBg.setStroke(4, Color.parseColor("#F4A7B9"));
        card.setBackground(cardBg);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("BolaClip #" + idx);
        title.setTextColor(Color.parseColor("#4A3F35"));
        title.setTextSize(16f);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button closeBubbleAndWindowBtn = new Button(this);
        closeBubbleAndWindowBtn.setText("Fechar");
        closeBubbleAndWindowBtn.setTextSize(12f);
        closeBubbleAndWindowBtn.setTextColor(Color.parseColor("#4A3F35"));
        closeBubbleAndWindowBtn.setBackgroundColor(Color.parseColor("#FFD1DC"));

        header.addView(title);
        header.addView(closeBubbleAndWindowBtn);

        final EditText input = new EditText(this);
        input.setHint("Digite ou cole seu clip...");
        input.setTextColor(Color.parseColor("#4A3F35"));
        input.setHintTextColor(Color.parseColor("#9C8B94"));
        input.setMinLines(3);
        input.setGravity(Gravity.TOP | Gravity.START);

        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        inputParams.topMargin = dp(10);
        input.setLayoutParams(inputParams);

        Button saveBtn = new Button(this);
        saveBtn.setText("Salvar");
        saveBtn.setTextColor(Color.parseColor("#4A3F35"));
        saveBtn.setBackgroundColor(Color.parseColor("#BAFFC9"));

        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        saveParams.topMargin = dp(10);
        saveBtn.setLayoutParams(saveParams);

        card.addView(header);
        card.addView(input);
        card.addView(saveBtn);
        container.addView(card);

        final WindowManager.LayoutParams winParams = new WindowManager.LayoutParams(
                dp(300),
                WindowManager.LayoutParams.WRAP_CONTENT,
                createOverlayType(),
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        winParams.gravity = Gravity.CENTER;

        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = input.getText().toString().trim();
                if (!text.isEmpty()) {
                    savedClips.add(text);
                    input.setText("");
                }
            }
        });

        closeBubbleAndWindowBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                removeOverlayView(container);
                removeOverlayView(ownerBubble);
                checkIfAllClosed();
            }
        });

        activeOverlays.add(container);
        windowManager.addView(container, winParams);
    }

    private void removeOverlayView(View view) {
        if (view == null || windowManager == null) return;
        try {
            windowManager.removeView(view);
        } catch (Exception ignored) {
        }
        activeOverlays.remove(view);
    }

    private void checkIfAllClosed() {
        if (activeOverlays.isEmpty()) {
            removeOverlayView(matrixBubbleView);
            matrixBubbleView = null;
            stopForeground(true);
            stopSelf();
        }
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        List<View> copy = new ArrayList<>(activeOverlays);
        for (View view : copy) {
            removeOverlayView(view);
        }
        removeOverlayView(matrixBubbleView);
        matrixBubbleView = null;
        super.onDestroy();
    }
}
