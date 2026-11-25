package com.esteffeson.bolaclip;

import android.app.KeyguardManager;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.util.concurrent.Executor;

public class MainActivity extends FragmentActivity {

    private static final int REQUEST_OVERLAY_PERMISSION = 1001;
    private static final int REQUEST_DEVICE_CREDENTIAL_LEGACY = 1002;

    private WebView webView;
    private boolean isAuthenticated = false;
    private boolean promptShowing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.setVisibility(View.INVISIBLE);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                ensureOverlayPermissionAndStartService();
            }
        });

        setContentView(webView);
        requestDeviceUnlock();
    }

    private void requestDeviceUnlock() {
        if (isAuthenticated || promptShowing) return;

        BiometricManager biometricManager = BiometricManager.from(this);
        int credentialAuth = BiometricManager.Authenticators.DEVICE_CREDENTIAL;
        int combinedAuth = BiometricManager.Authenticators.BIOMETRIC_STRONG
                | BiometricManager.Authenticators.BIOMETRIC_WEAK
                | BiometricManager.Authenticators.DEVICE_CREDENTIAL;

        int canAuth = biometricManager.canAuthenticate(combinedAuth);
        if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
            showBiometricPrompt(combinedAuth);
            return;
        }

        int canCredential = biometricManager.canAuthenticate(credentialAuth);
        if (canCredential == BiometricManager.BIOMETRIC_SUCCESS) {
            showBiometricPrompt(credentialAuth);
            return;
        }

        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (km != null && km.isKeyguardSecure()) {
            Intent intent = km.createConfirmDeviceCredentialIntent("BolaClip", "Digite a senha do celular para abrir");
            if (intent != null) {
                promptShowing = true;
                startActivityForResult(intent, REQUEST_DEVICE_CREDENTIAL_LEGACY);
                return;
            }
        }

        Toast.makeText(this, "Configure uma senha ou biometria no celular para usar o bloqueio", Toast.LENGTH_LONG).show();
        isAuthenticated = true;
        unlockApp();
    }

    private void showBiometricPrompt(int allowedAuthenticators) {
        promptShowing = true;
        Executor executor = ContextCompat.getMainExecutor(this);

        BiometricPrompt prompt = new BiometricPrompt(
                this,
                executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        promptShowing = false;
                        isAuthenticated = true;
                        unlockApp();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        promptShowing = false;
                        Toast.makeText(MainActivity.this, "Senha necessária: " + errString, Toast.LENGTH_SHORT).show();
                        finishAffinity();
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        Toast.makeText(MainActivity.this, "Senha ou biometria incorreta", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("BolaClip")
                .setSubtitle("Confirme a biometria ou senha do celular")
                .setAllowedAuthenticators(allowedAuthenticators)
                .build();

        prompt.authenticate(info);
    }

    private void unlockApp() {
        webView.setVisibility(View.VISIBLE);
        if (webView.getUrl() == null) {
            webView.loadUrl("file:///android_asset/bolaclip.html");
        } else {
            ensureOverlayPermissionAndStartService();
        }
    }

    private void ensureOverlayPermissionAndStartService() {
        if (!isAuthenticated) return;

        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            );
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
            return;
        }

        Intent serviceIntent = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_DEVICE_CREDENTIAL_LEGACY) {
            promptShowing = false;
            if (resultCode == RESULT_OK) {
                isAuthenticated = true;
                unlockApp();
            } else {
                finishAffinity();
            }
        } else if (requestCode == REQUEST_OVERLAY_PERMISSION && isAuthenticated) {
            if (Settings.canDrawOverlays(this)) {
                ensureOverlayPermissionAndStartService();
            } else {
                Toast.makeText(this, "Ative 'Aparecer sobre outros apps' para o BolaClip funcionar", Toast.LENGTH_LONG).show();
            }
        }
    }
}
