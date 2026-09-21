package cn.edu.scnu.schedule;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.webkit.CookieManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import android.view.MotionEvent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class CourseSyncEngine {
    public interface Callback {
        void onStatus(String message);
        void onSuccess(String semester, List<Course> courses);
        void onError(String message);
    }

    private static final String PORTAL_URL = "https://gs.scnu.edu.cn/gsapp/sys/yjsemaphome/portal/index.do";
    private static final String IFRAME_ID = "iframeContent_wdkbappxskcb";
    private static final long TIMEOUT_MS = 120_000L;

    private final Context context;
    private final WebView webView;
    private final Callback callback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean finished = new AtomicBoolean(false);
    private String account;
    private String password;
    private String currentUrl = "";
    private long loginClickedAt;
    private boolean loginSubmitted;
    private boolean confirmClicked;
    private boolean appClicked;
    private boolean polling;

    private final Runnable timeoutRunnable = () -> fail("同步超时，请检查网络；如出现验证码，请打开网页验证后重试。");

    public CourseSyncEngine(Context context, WebView webView, Callback callback) {
        this.context = context.getApplicationContext();
        this.webView = webView;
        this.callback = callback;
    }

    @SuppressLint("SetJavaScriptEnabled")
    public void start() {
        account = AppPrefs.account(context);
        password = AppPrefs.password(context);
        if (account.isEmpty() || password.isEmpty()) {
            fail("尚未设置账号或密码");
            return;
        }

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setSafeBrowsingEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setUserAgentString(settings.getUserAgentString() + " SCNU-Schedule-App/1.4");
        CookieManager.getInstance().setAcceptCookie(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                handler.postDelayed(() -> handlePage(url), 650L);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame()) {
                    fail("网络连接失败（" + error.getErrorCode() + "），请检查网络后重试。");
                }
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request != null && request.isForMainFrame() && response != null) {
                    fail("学校服务返回错误（HTTP " + response.getStatusCode() + "），请稍后重试。");
                }
            }
        });

        callback.onStatus("正在打开学校统一认证……");
        handler.postDelayed(timeoutRunnable, TIMEOUT_MS);
        webView.loadUrl(PORTAL_URL);
    }

    public void stop() {
        finished.set(true);
        polling = false;
        password = "";
        handler.removeCallbacksAndMessages(null);
    }

    private void handlePage(String url) {
        if (finished.get()) return;
        if (url == null) return;
        currentUrl = url;

        if (url.contains("sso.scnu.edu.cn") && url.contains("login.html")) {
            fillLogin();
        } else if (url.contains("openapi") && url.contains("auth")) {
            clickConfirm();
        } else if (url.contains("gs.scnu.edu.cn")) {
            clickCourseApp();
        }
    }

    private void fillLogin() {
        if (finished.get() || loginSubmitted) return;
        loginSubmitted = true;
        String js = "(function(){"
                + "var a=document.getElementById('account');"
                + "var p=document.getElementById('password');"
                + "var b=document.getElementById('btn-password-login');"
                + "var c=document.getElementById('password-random-code');"
                + "if(!a||!p||!b)return 'wait';"
                + "if(c&&c.offsetParent!==null&&!c.value)return 'captcha';"
                + "var setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;"
                + "setter.call(a," + JSONObject.quote(account) + ");"
                + "a.dispatchEvent(new Event('input',{bubbles:true}));"
                + "setter.call(p," + JSONObject.quote(password) + ");"
                + "p.dispatchEvent(new Event('input',{bubbles:true}));"
                + "b.click();return 'clicked';"
                + "})()";
        eval(js, value -> {
            String result = decode(value);
            if ("captcha".equals(result)) {
                callback.onStatus("检测到验证码，请打开“网页验证”完成登录。");
                loginSubmitted = false;
                handler.postDelayed(this::fillLogin, 2500L);
            } else if ("wait".equals(result)) {
                loginSubmitted = false;
                handler.postDelayed(this::fillLogin, 1200L);
            } else if ("clicked".equals(result)) {
                callback.onStatus("正在登录学校账号……");
                loginClickedAt = System.currentTimeMillis();
                handler.postDelayed(this::checkLoginFailure, 2500L);
            }
        });
    }

    private void checkLoginFailure() {
        if (finished.get() || !loginSubmitted) return;
        if (currentUrl == null || !currentUrl.contains("sso.scnu.edu.cn") || !currentUrl.contains("login.html")) {
            return;
        }
        String js = "(function(){"
                + "var selectors=['.login-error','.error-tip','#errorMsg','.el-message__content','.layui-layer-content','.ant-message-notice-content'];"
                + "for(var i=0;i<selectors.length;i++){var e=document.querySelector(selectors[i]);"
                + "if(e&&e.offsetParent!==null){var t=(e.innerText||e.textContent||'').trim();if(t)return t;}}"
                + "return '';})()";
        eval(js, value -> {
            if (finished.get() || !loginSubmitted) return;
            String error = decode(value).trim();
            if (!error.isEmpty()) {
                if (error.length() > 80) error = error.substring(0, 80);
                fail("登录失败：" + error);
                return;
            }
            if (System.currentTimeMillis() - loginClickedAt < 12_000L) {
                handler.postDelayed(this::checkLoginFailure, 1500L);
            } else {
                fail("登录未成功，请检查账号、密码、验证码或学校系统状态后重试。");
            }
        });
    }

    private void clickConfirm() {
        if (finished.get() || confirmClicked) return;
        confirmClicked = true;
        callback.onStatus("正在确认进入研究生系统……");
        String js = "(function(){"
                + "function visible(e){return e&&e.getClientRects().length>0;}"
                + "function docs(){var out=[document];function walk(d){var fs=d.querySelectorAll('iframe,frame');"
                + "for(var i=0;i<fs.length;i++){try{var cd=fs[i].contentDocument;if(cd){out.push(cd);walk(cd);}}catch(e){}}}walk(document);return out;}"
                + "function frameOffset(d){var x=0,y=0;try{var w=d.defaultView;while(w&&w.frameElement){var r=w.frameElement.getBoundingClientRect();x+=r.left;y+=r.top;w=w.parent;}}catch(e){}return{x:x,y:y};}"
                + "function pick(d){var exact=d.querySelector('span.login-check-comfirm');if(visible(exact))return exact;"
                + "var nodes=d.querySelectorAll('button,a,input,span,div');for(var i=0;i<nodes.length;i++){var e=nodes[i];"
                + "var t=(e.innerText||e.textContent||e.value||'').replace(/\\s+/g,'').trim();"
                + "if(t==='确定登录'||t==='确认登录'||t==='确定登陆'||t==='确认登陆'){"
                + "var target=e.closest('button,a,[role=\"button\"],.btn,.button')||e;if(visible(target))return target;}}return null;}"
                + "var list=docs();for(var i=0;i<list.length;i++){var target=pick(list[i]);if(target){"
                + "target.scrollIntoView({block:'center',inline:'center'});var r=target.getBoundingClientRect();var o=frameOffset(list[i]);"
                + "return JSON.stringify({found:true,x:r.left+r.width/2+o.x,y:r.top+r.height/2+o.y});}}"
                + "return JSON.stringify({found:false});})()";
        eval(js, value -> {
            String decoded = decode(value);
            try {
                JSONObject result = new JSONObject(decoded);
                if (result.optBoolean("found")) {
                    callback.onStatus("正在自动点击“确定登录”……");
                    dispatchWebViewTap((float) result.optDouble("x"), (float) result.optDouble("y"));
                    handler.postDelayed(() -> {
                        if (!finished.get() && currentUrl != null && currentUrl.contains("openapi")) {
                            confirmClicked = false;
                            clickConfirm();
                        }
                    }, 5000L);
                } else {
                    confirmClicked = false;
                    handler.postDelayed(this::clickConfirm, 900L);
                }
            } catch (Exception ignored) {
                confirmClicked = false;
                handler.postDelayed(this::clickConfirm, 900L);
            }
        });
    }

    private void dispatchWebViewTap(float webX, float webY) {
        float scale = webView.getScale();
        if (scale <= 0f || Float.isNaN(scale)) scale = 1f;
        float x = webX * scale;
        float y = webY * scale;
        webView.requestFocus();
        long downTime = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0);
        webView.dispatchTouchEvent(down);
        down.recycle();
        webView.postDelayed(() -> {
            long upTime = SystemClock.uptimeMillis();
            MotionEvent up = MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_UP, x, y, 0);
            webView.dispatchTouchEvent(up);
            up.recycle();
        }, 80L);
    }
    private void clickCourseApp() {
        if (finished.get() || appClicked) return;
        appClicked = true;
        callback.onStatus("正在打开学生课程表……");
        String js = "(function(){"
                + "var e=document.querySelector('.home-app-container[data-app=\"wdkbapp\"][data-menu=\"xskcb\"]');"
                + "if(!e)e=document.querySelector('[data-app=\"wdkbapp\"] .lmenu-level-2-content');"
                + "if(!e){var list=document.querySelectorAll('[data-app=\"wdkbapp\"] .clickAreaoMo');"
                + "for(var i=0;i<list.length;i++){if((list[i].innerText||list[i].textContent||'').trim()==='学生课程表'){e=list[i];break;}}}"
                + "if(!e)return 'wait';e.click();return 'clicked';})()";
        eval(js, value -> {
            String result = decode(value);
            if ("clicked".equals(result)) {
                handler.postDelayed(this::pollCoursePage, 900L);
            } else {
                appClicked = false;
                handler.postDelayed(this::clickCourseApp, 1100L);
            }
        });
    }

    private void pollCoursePage() {
        if (finished.get() || polling) return;
        polling = true;
        callback.onStatus("正在读取课表数据……");
        String js = "(function(){"
                + "var f=document.getElementById('" + IFRAME_ID + "');"
                + "if(!f)return JSON.stringify({status:'waiting',where:'iframe'});"
                + "var d;try{d=f.contentDocument||f.contentWindow.document;}catch(e){return JSON.stringify({status:'error',message:'无法访问课表框架'});}"
                + "var t=d.querySelector('table.zero-grid');"
                + "if(!t)return JSON.stringify({status:'waiting',where:'table'});"
                + "var headers=[].slice.call(t.querySelectorAll('thead tr:last-child th')).map(function(x){return (x.innerText||x.textContent||'').trim();});"
                + "var rows=[].slice.call(t.querySelectorAll('tbody tr')).map(function(tr){return [].slice.call(tr.querySelectorAll('th,td')).map(function(x){return (x.innerText||x.textContent||'').trim();});});"
                + "if(rows.length===0)return JSON.stringify({status:'waiting',where:'rows'});"
                + "var sel=d.getElementById('myXnxqSelect');"
                + "var semester=sel&&sel.options[sel.selectedIndex]?sel.options[sel.selectedIndex].text:'';"
                + "return JSON.stringify({status:'ready',semester:semester,headers:headers,rows:rows});"
                + "})()";
        eval(js, value -> {
            polling = false;
            if (finished.get()) return;
            String decoded = decode(value);
            try {
                JSONObject result = new JSONObject(decoded);
                String status = result.optString("status");
                if ("ready".equals(status)) {
                    JSONArray headers = result.optJSONArray("headers");
                    JSONArray rows = result.optJSONArray("rows");
                    List<Course> courses = CourseParser.parse(headers, rows);
                    if (courses.isEmpty()) {
                        fail("已读取到课表页面，但没有解析到课程数据。");
                    } else {
                        finished.set(true);
                        password = "";
                        handler.removeCallbacksAndMessages(null);
                        callback.onSuccess(result.optString("semester", ""), courses);
                    }
                } else if ("error".equals(status)) {
                    fail(result.optString("message", "读取课表失败"));
                } else {
                    handler.postDelayed(this::pollCoursePage, 1400L);
                }
            } catch (Exception e) {
                handler.postDelayed(this::pollCoursePage, 1400L);
            }
        });
    }

    private void eval(String script, android.webkit.ValueCallback<String> callback) {
        try {
            webView.evaluateJavascript(script, callback);
        } catch (Exception e) {
            fail("浏览器执行失败：" + e.getMessage());
        }
    }

    private String decode(String value) {
        if (value == null) return "";
        try {
            return new JSONArray("[" + value + "]").getString(0);
        } catch (Exception ignored) {
            return value.replace("\\\"", "\"").replace("\\n", "\n");
        }
    }

    private void fail(String message) {
        if (!finished.compareAndSet(false, true)) return;
        password = "";
        handler.removeCallbacksAndMessages(null);
        callback.onError(message);
    }
}





