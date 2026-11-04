package in.samwad.learning;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.graphics.Insets;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
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
import org.json.JSONObject;
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
    private boolean isAdminApp;
    private String roleHome;
    private String roleLogin;
    private String guardedOrigin;

    // Navigation control is an app-shell UX restriction, NOT a replacement for
    // server-side authorization and row-level access policies.
    private String destinationFor(Uri uri) {
        if (!isHttps(uri) || trustedHost == null ||
                !trustedHost.equalsIgnoreCase(uri.getHost())) return null;
        String path = uri.getPath();
        if (path == null || path.isEmpty()) path = "/";
        if (isAllowedPath(path)) return null;
        return guardedOrigin + (path.startsWith("/admin") || path.startsWith("/student")
                ? roleLogin : roleHome);
    }

    private boolean isAllowedPath(String path) {
        if (isAdminApp) {
            return path.equals("/admin") || path.startsWith("/admin/")
                    || isGenericAuthPath(path);
        }
        return path.equals("/dashboard") || path.equals("/learning-path")
                || path.equals("/my-progress")
                || path.equals("/classes") || path.startsWith("/classes/")
                || path.startsWith("/learn/")
                || path.equals("/student") || path.startsWith("/student/")
                || isGenericAuthPath(path);
    }

    private boolean isGenericAuthPath(String path) {
        // Platform login redirects sometimes use these unprefixed callback routes.
        return path.equals("/login") || path.equals("/signup")
                || path.equals("/callback") || path.equals("/forgot-password")
                || path.equals("/reset-password") || path.equals("/verify-email")
                || path.equals("/accept-invite") || path.equals("/change-password")
                || path.equals("/access-denied");
    }

    private void redirectIfOutsideRole(String url) {
        if (webView == null) return;
        Uri uri = Uri.parse(url);
        String destination = destinationFor(uri);
        if (destination != null && !destination.equals(url)) {
            webView.loadUrl(destination);
        }
    }

    private void installClientRouteGuard() {
        if (webView == null) return;
        // React Router uses pushState, so WebViewClient alone cannot block those
        // transitions. The website's own auth/access checks still decide access.
        String js = "(function(){"
                + "if(window.__samwadRoleGuardInstalled)return;"
                + "window.__samwadRoleGuardInstalled=true;"
                + "var admin=" + (isAdminApp ? "true" : "false") + ";"
                + "var home=" + JSONObject.quote(isAdminApp ? "/admin" : "/dashboard") + ";"
                + "var login=" + JSONObject.quote(isAdminApp ? "/admin/login" : "/student/login") + ";"
                + "function allowed(p){"
                + "var generic=['/login','/signup','/callback','/forgot-password','/reset-password','/verify-email','/accept-invite','/change-password','/access-denied'];"
                + "if(generic.indexOf(p)>=0)return true;"
                + "if(admin)return p==='/admin'||p.indexOf('/admin/')===0;"
                + "return p==='/dashboard'||p==='/learning-path'||p==='/my-progress'||p==='/classes'||p.indexOf('/classes/')===0||p.indexOf('/learn/')===0||p==='/student'||p.indexOf('/student/')===0;"
                + "}"
                + "function target(p){return (p.indexOf('/admin')===0||p.indexOf('/student')===0)?login:home;}"
                + "function enforce(){var p=location.pathname;if(!allowed(p))location.replace(target(p));}"
                + "['pushState','replaceState'].forEach(function(method){"
                + "var original=history[method];"
                + "history[method]=function(){var args=Array.prototype.slice.call(arguments);"
                + "if(args.length>2&&args[2]!=null){try{var u=new URL(args[2],location.href);"
                + "if(u.origin===location.origin&&!allowed(u.pathname)){args[2]=target(u.pathname);}}catch(e){}}"
                + "var result=original.apply(this,args);enforce();return result;};});"
                + "addEventListener('popstate',enforce);addEventListener('hashchange',enforce);"
                + "document.addEventListener('click',function(e){"
                + "var a=e.target.closest?e.target.closest('a[href]'):null;if(!a)return;"
                + "try{var u=new URL(a.href,location.href);"
                + "if(u.origin===location.origin&&!allowed(u.pathname)){e.preventDefault();location.assign(target(u.pathname));}}catch(err){}"
                + "},true);enforce();})();";
        webView.evaluateJavascript(js, null);
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Uri startUri = Uri.parse(BuildConfig.START_URL);
        trustedHost = startUri.getHost();
        guardedOrigin = "https://" + trustedHost;
        isAdminApp = BuildConfig.IS_ADMIN_APP;
        roleHome = isAdminApp ? "/admin" : "/dashboard";
        roleLogin = isAdminApp ? "/admin/login" : "/student/login";
        getWindow().setStatusBarColor(0xff09120f);
        getWindow().setNavigationBarColor(0xff09120f);

        root = new FrameLayout(this);
        // Android 15 (target 35) draws behind system bars by default. Give WebView
        // real status- and navigation-bar insets so the LMS stays tappable.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                Insets safe = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout());
                if (fullScreenView == null) {
                    root.setPadding(safe.left, safe.top, safe.right, safe.bottom);
                }
                return WindowInsets.CONSUMED;
            });
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER;
        }
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
        // Allow the hosted HTML5 / embedded player to start after its own play control.
        // Do not force an unmuted autoplay from the webpage.
        settings.setMediaPlaybackRequiresUserGesture(false);
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
                if (trustedHost != null && trustedHost.equalsIgnoreCase(uri.getHost())) {
                    String redirect = destinationFor(uri);
                    if (redirect != null) {
                        view.loadUrl(redirect);
                        return true;
                    }
                    return false;
                }
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
                if (trustedHost != null && trustedHost.equalsIgnoreCase(Uri.parse(url).getHost())) {
                    redirectIfOutsideRole(url);
                    installClientRouteGuard();
                }
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowInsetsController controller = getWindow().getInsetsController();
                    if (controller != null) controller.hide(WindowInsets.Type.systemBars());
                    root.setPadding(0, 0, 0, 0);
                }
                root.addView(view, new FrameLayout.LayoutParams(-1, -1));
            }
            @Override public void onHideCustomView() { closeFullScreen(); }
        });
        if (savedInstanceState == null) webView.loadUrl(BuildConfig.START_URL);
        else {
            webView.restoreState(savedInstanceState);
            // Restored navigation history must also stay inside the assigned portal.
            webView.post(() -> redirectIfOutsideRole(webView.getUrl()));
        }
    }

    private boolean isHttps(Uri uri) {
        return uri != null && "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
    }

    private void closeFullScreen() {
        if (fullScreenView == null) return;
        root.removeView(fullScreenView);
        fullScreenView = null;
        webView.setVisibility(View.VISIBLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) controller.show(WindowInsets.Type.systemBars());
            root.requestApplyInsets();
        }
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
