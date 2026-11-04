package in.samwad.learning;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.net.http.SslError;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

/**
 * Samwad Android online LMS shell. No secrets embedded.
 * Server-side authorisation remains the responsibility of the hosted LMS.
 * Login, SSO, callbacks and downloads require real-device verification.
 */
public final class MainActivity extends Activity {
    private static final int FILE_PICKER = 41;
    private WebView webView;
    private ProgressBar loading;
    private ValueCallback<Uri[]> fileCallback;
    private View fullScreenView;
    private WebChromeClient.CustomViewCallback fullScreenCallback;
    private FrameLayout root;
    private String trustedHost;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        trustedHost = Uri.parse(BuildConfig.START_URL).getHost();
        getWindow().setStatusBarColor(0xff09120f);
        getWindow().setNavigationBarColor(0xff09120f);

        root = new FrameLayout(this);
        webView = new WebView(this);
        loading = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        loading.setMax(100);
        root.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        root.addView(loading, new FrameLayout.LayoutParams(-1, 5));
        setContentView(root);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true); // Hosted React application requires JavaScript.
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);
        // Do not enable third-party cookies by default. Some SSO providers may require
        // a system-browser based sign-in integration, to be tested before public launch.
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);
        WebView.setWebContentsDebuggingEnabled(false);

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, length) -> {
            Uri download = Uri.parse(url);
            if (isHttps(download)) openExternally(download);
            else Toast.makeText(this, "Only secure downloads are supported", Toast.LENGTH_LONG).show();
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                Uri uri = request.getUrl();
                if (!isHttps(uri)) {
                    if ("mailto".equals(uri.getScheme()) || "tel".equals(uri.getScheme())) openExternally(uri);
                    return true;
                }
                // Never navigate arbitrary external websites inside a privileged WebView.
                if (trustedHost != null && trustedHost.equalsIgnoreCase(uri.getHost())) return false;
                openExternally(uri);
                return true;
            }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel(); // Never bypass TLS validation.
                Toast.makeText(MainActivity.this, "Secure connection failed", Toast.LENGTH_LONG).show();
            }
            @Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
                loading.setVisibility(View.VISIBLE);
            }
            @Override public void onPageFinished(WebView view, String url) {
                loading.setVisibility(View.GONE);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int progress) {
                loading.setProgress(progress);
            }
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), FILE_PICKER);
                    return true;
                } catch (ActivityNotFoundException ex) {
                    fileCallback = null;
                    callback.onReceiveValue(null);
                    return false;
                }
            }
            @Override public void onShowCustomView(View view, CustomViewCallback callback) {
                if (fullScreenView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                fullScreenView = view;
                fullScreenCallback = callback;
                webView.setVisibility(View.GONE);
                root.addView(view, new FrameLayout.LayoutParams(-1, -1));
            }
            @Override public void onHideCustomView() { closeFullScreen(); }
        });
        if (savedInstanceState == null) webView.loadUrl(BuildConfig.START_URL);
        else webView.restoreState(savedInstanceState);
    }

    private boolean isHttps(Uri uri) {
        return uri != null && "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
    }

    private void closeFullScreen() {
        if (fullScreenView == null) return;
        root.removeView(fullScreenView);
        fullScreenView = null;
        webView.setVisibility(View.VISIBLE);
        if (fullScreenCallback != null) {
            fullScreenCallback.onCustomViewHidden();
            fullScreenCallback = null;
        }
    }

    private void openExternally(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception ex) {
            Toast.makeText(this, "No compatible app installed", Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_PICKER && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            fileCallback = null;
        }
    }

    @Override public void onBackPressed() {
        if (fullScreenView != null) { closeFullScreen(); return; }
        if (webView != null && webView.canGoBack()) { webView.goBack(); return; }
        new AlertDialog.Builder(this)
            .setMessage("Close Samwad?")
            .setNegativeButton("Stay", null)
            .setPositiveButton("Close", (dialog, which) -> finish())
            .show();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        webView.saveState(state);
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() {
        if (fileCallback != null) { fileCallback.onReceiveValue(null); fileCallback = null; }
        if (webView != null) {
            root.removeView(webView);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
