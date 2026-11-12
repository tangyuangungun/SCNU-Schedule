package cn.edu.scnu.schedule;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;

import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.webkit.WebView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQUEST_CALENDAR = 4101;
    private static final int REQUEST_NOTIFICATIONS = 4102;
    private static final int TAB_AGENDA = 0;
    private static final int TAB_WEEK = 1;
    private static final int TAB_MORE = 2;
    private static final int GRID_PERIODS = 11;
    private static final int GRID_ROW_HEIGHT_DP = 58;
    private static final String RELEASES_API = "https://api.github.com/repos/tangyuangungun/SCNU-Schedule/releases/latest";
    private static final String RELEASES_PAGE = "https://github.com/tangyuangungun/SCNU-Schedule/releases";
    private static final String QQ_CHANNEL_URL = "https://pd.qq.com/s/cj959avp3?b=9";
    private static final String QQ_GROUP_URL = "https://qun.qq.com/universal-share/share?ac=1&authKey=WF102ovLFRm46gvBN9rNWDjKOJM4Tr54tunl8QHXlvVkqd9knhopsHV4rl3N14cG&busi_data=eyJncm91cENvZGUiOiIxMTI2NDgzNzEyIiwidG9rZW4iOiJQWVh5ei9odnhyc05ib0JwU1NnR1VxK01mTFk4LzlGTC82SWpUb2lJMXBzNEtEaWtNaVpyQi9PSTlPWlpmbUpDIiwidWluIjoiMTkzMzg1NTYzNSJ9&data=2fm9q0Kfjm2W6J63LNm-aDGLnJp0vpajPbgWKleqBJ0OwNn_JnMd_gUZX_XUeN66EJxhLntcM40SQnoAhzsa5A&svctype=4&tempid=h5_group_info";

    private boolean dark;
    private FrameLayout root;
    private FrameLayout pageHost;
    private LinearLayout bottomNav;
    private WebView webView;
    private CourseSyncEngine engine;
    private TextView statusView;
    private ProgressBar progress;
    private Button loginButton;
    private boolean syncing;
    private int activeTab = TAB_WEEK;
    private int selectedWeek = 1;
    private String selectedSemesterKey = "";
    private SemesterCalendar.Semester semester;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private GestureDetector gestureDetector;
    private boolean switchingTab;
    private View updateBadge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dark = AppPrefs.darkMode(this);
        gestureDetector = new GestureDetector(this, new SwipeGestureListener());
        if (savedInstanceState != null) {
            activeTab = savedInstanceState.getInt("activeTab", TAB_WEEK);
            selectedWeek = savedInstanceState.getInt("selectedWeek", 1);
            selectedSemesterKey = savedInstanceState.getString("selectedSemesterKey", "");
        }
        configureWindow();
        root = new FrameLayout(this);
        root.setBackgroundColor(bg());
        pageHost = new FrameLayout(this);
        root.addView(pageHost, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        createWebView();
        setContentView(root);
        installSystemInsets();
        NotificationHelper.ensureChannels(this);
        SyncScheduler.schedule(this, AppPrefs.updateFrequency(this));
        if (AppPrefs.privacyAccepted(this)) {
            UpdateCheckScheduler.schedule(this);
            render();
            checkForUpdates(false);
            maybeRunAutomaticSync();
        } else {
            showPrivacyDialog(true);
        }
        ScheduleWidgetProvider.updateAll(this);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("activeTab", activeTab);
        outState.putInt("selectedWeek", selectedWeek);
        outState.putString("selectedSemesterKey", selectedSemesterKey);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (engine != null) engine.stop();
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (webView.getTranslationX() == 0f) {
            hideWebView();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (gestureDetector != null) gestureDetector.onTouchEvent(event);
        return super.dispatchTouchEvent(event);
    }

    private class SwipeGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent event) {
            return true;
        }

        @Override
        public boolean onFling(MotionEvent first, MotionEvent second, float velocityX, float velocityY) {
            if (first == null || second == null || webView == null || webView.getTranslationX() == 0f) return false;
            if (!AppPrefs.isLoggedIn(MainActivity.this) || AppPrefs.courses(MainActivity.this).isEmpty()) return false;
            float deltaX = second.getX() - first.getX();
            float deltaY = second.getY() - first.getY();
            if (Math.abs(deltaX) < dp(72) || Math.abs(deltaX) < Math.abs(deltaY) * 1.35f) return false;
            int nextTab = deltaX < 0 ? activeTab + 1 : activeTab - 1;
            if (nextTab < TAB_AGENDA || nextTab > TAB_MORE || nextTab == activeTab) return false;
            switchTabAnimated(nextTab);
            return true;
        }
    }
    private void maybeRunAutomaticSync() {
        if (syncing || !AppPrefs.isLoggedIn(this) || !AppPrefs.hasStoredPassword(this)) return;
        String frequency = AppPrefs.updateFrequency(this);
        if (AppPrefs.FREQ_MANUAL.equals(frequency)) return;
        long last = AppPrefs.lastUpdate(this);
        if (last > 0 && System.currentTimeMillis() - last < SyncScheduler.intervalMs(frequency)) return;
        pageHost.postDelayed(() -> {
            if (!syncing && AppPrefs.isLoggedIn(this)) startSync();
        }, 700L);
    }
    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(bg());
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
        } else {
            flags |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        }
        if (dark) {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        } else {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        window.getDecorView().setSystemUiVisibility(flags);
    }

    private void installSystemInsets() {
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top;
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            if (view.getPaddingTop() != top || view.getPaddingBottom() != bottom) {
                view.setPadding(0, top, 0, bottom);
            }
            return insets;
        });
        root.requestApplyInsets();
    }

    private void createWebView() {
        webView = new WebView(this);
        webView.setAlpha(0f);
        webView.setTranslationX(10000f);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.BOTTOM);
        root.addView(webView, params);
    }

    private void render() {
        pageHost.removeAllViews();
        if (bottomNav != null) {
            root.removeView(bottomNav);
            bottomNav = null;
        }
        hideWebView();

        boolean hasSchedule = AppPrefs.isLoggedIn(this) && !AppPrefs.courses(this).isEmpty();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scroll.setSmoothScrollingEnabled(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(14), dp(18), hasSchedule ? dp(92) : dp(34));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        pageHost.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        addHeader(content);
        if (hasSchedule) {
            prepareSemester();
            if (activeTab == TAB_AGENDA) addAgendaView(content);
            else if (activeTab == TAB_MORE) addMoreView(content);
            else addWeekView(content);
            addBottomNavigation();
        } else {
            addLoginScreen(content);
        }
    }

    private void prepareSemester() {
        LocalDate today = LocalDate.now();
        SemesterCalendar.Semester current = SemesterCalendar.resolve(today);
        if (!current.key().equals(selectedSemesterKey)) {
            semester = current;
            selectedSemesterKey = current.key();
            selectedWeek = current.weekFor(today);
        } else {
            semester = current;
        }
        selectedWeek = Math.max(1, Math.min(semester.totalWeeks, selectedWeek));
    }

    private void addHeader(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("华师课表", 28, textColor(), Typeface.BOLD);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        TextView subtitle = text("SCNU Schedule", 12, mutedColor(), Typeface.BOLD);
        subtitle.setLetterSpacing(0.12f);
        titleBox.addView(title);
        titleBox.addView(subtitle);
        row.addView(titleBox, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button theme = secondaryButton(dark ? "浅色模式" : "深色模式");
        theme.setOnClickListener(v -> {
            AppPrefs.setDarkMode(this, !dark);
            recreate();
        });
        row.addView(theme, new LinearLayout.LayoutParams(dp(96), dp(42)));
        parent.addView(row);
        parent.addView(space(14));
    }

    private void addLoginScreen(LinearLayout parent) {
        loginButton = null;
        TextView lead = text("登录研究生系统", 24, textColor(), Typeface.BOLD);
        TextView desc = text("输入统一身份认证账号。账号和密码加密保存在本机，课程表仅在本机处理。",
                14, mutedColor(), Typeface.NORMAL);
        desc.setLineSpacing(0, 1.25f);
        parent.addView(lead);
        parent.addView(desc);
        parent.addView(space(18));

        LinearLayout card = card();
        EditText account = input("登录账号", InputType.TYPE_CLASS_TEXT);
        account.setText(AppPrefs.account(this));
        EditText password = input("登录密码", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setText(AppPrefs.password(this));
        CheckBox remember = new CheckBox(this);
        remember.setText("加密保存登录信息，用于自动更新；不勾选则仅本次运行有效");
        remember.setTextColor(mutedColor());
        remember.setTextSize(13);
        remember.setChecked(AppPrefs.remember(this));
        remember.setEnabled(true);
        remember.setButtonTintList(android.content.res.ColorStateList.valueOf(primary()));

        card.addView(label("账号"));
        card.addView(account);
        card.addView(space(10));
        card.addView(label("密码"));
        card.addView(password);
        card.addView(remember);

        Button login = primaryButton("登录并获取课表");
        loginButton = login;
        login.setOnClickListener(v -> {
            String a = account.getText().toString().trim();
            String p = password.getText().toString();
            if (a.isEmpty() || p.isEmpty()) {
                showLoginError("登录失败：请输入账号和密码后重试。");
                if (a.isEmpty()) account.requestFocus(); else password.requestFocus();
                return;
            }
            AppPrefs.saveCredentials(this, a, p, remember.isChecked());
            startSync();
        });
        card.addView(space(6));
        card.addView(login);

        statusView = text("", 13, mutedColor(), Typeface.NORMAL);
        statusView.setPadding(0, dp(12), 0, 0);
        progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        card.addView(statusView);
        card.addView(progress);
        parent.addView(card);

        Button web = secondaryButton("显示网页验证 / 处理验证码");
        web.setOnClickListener(v -> showWebVerification());
        parent.addView(space(12));
        parent.addView(web, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        TextView note = text("提示：如果登入失败或学校页面出现验证码，请打开网页验证完成一次登录；后续首次登录成功后可自动更新。",
                12, mutedColor(), Typeface.NORMAL);
        note.setPadding(dp(4), dp(12), dp(4), 0);
        parent.addView(note);
    }

    private void showPrivacyDialog(boolean firstRun) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        String[] headings = {"账号与密码", "本机数据", "权限用途", "第三方服务", "删除数据"};
        String[] bodies = {
                "账号和密码仅用于登录华南师范大学研究生系统。勾选保存后会加密存放在本机，不会发送到开发者服务器。",
                "课表、提醒、组件和界面设置仅保存在本机应用私有空间。",
                "网络用于同步课表；通知用于上课提醒；日历用于导入课程；精确闹钟仅在你主动启用闹钟提醒时使用。",
                "应用不包含广告、统计或用户画像 SDK。",
                "可通过退出登录、清除应用数据或卸载应用删除本地数据。"
        };
        for (int i = 0; i < headings.length; i++) {
            TextView heading = text((i + 1) + ". " + headings[i], 14, primary(), Typeface.BOLD);
            TextView body = text(bodies[i], 13, mutedColor(), Typeface.NORMAL);
            body.setLineSpacing(0, 1.28f);
            body.setPadding(0, dp(4), 0, dp(12));
            content.addView(heading);
            content.addView(body);
        }
        TextView footer = text("完整说明请查看项目中的 PRIVACY.md。", 12, mutedColor(), Typeface.NORMAL);
        footer.setLineSpacing(0, 1.2f);
        content.addView(footer);
        if (firstRun) {
            showStyledDialog("隐私与权限说明", content, "同意并继续", () -> {
                AppPrefs.setPrivacyAccepted(this, true);
                UpdateCheckScheduler.schedule(this);
                render();
                checkForUpdates(false);
            }, "不同意并退出", this::finish, false);
        } else {
            showStyledDialog("隐私与权限说明", content, "我知道了", null, null, null, true);
        }
    }

    private void showSponsorDialog() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        TextView thanks = text("感谢你的支持，请作者喝一杯奶茶。", 14, textColor(), Typeface.BOLD);
        thanks.setGravity(Gravity.CENTER);
        content.addView(thanks);
        content.addView(space(10));
        ImageView qr = new ImageView(this);
        qr.setAdjustViewBounds(true);
        qr.setScaleType(ImageView.ScaleType.FIT_CENTER);
        qr.setImageResource(R.drawable.sponsor_qr);
        qr.setBackground(roundedStroke(cardColor(), 16, dividerColor()));
        qr.setPadding(dp(6), dp(6), dp(6), dp(6));
        content.addView(qr, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(space(10));
        TextView note = text("微信扫码即可支持，金额随意。", 12, mutedColor(), Typeface.NORMAL);
        note.setGravity(Gravity.CENTER);
        content.addView(note);
        showStyledDialog("打赏作者", content, "关闭", null, null, null, true);
    }

    private Dialog showStyledDialog(String title, View content, String positiveText,
                                    Runnable positiveAction, String negativeText,
                                    Runnable negativeAction, boolean cancelable) {
        return showStyledDialog(title, content, positiveText, positiveAction,
                negativeText, negativeAction, null, null, cancelable);
    }

    private Dialog showStyledDialog(String title, View content, String positiveText,
                                    Runnable positiveAction, String negativeText,
                                    Runnable negativeAction, String neutralText,
                                    Runnable neutralAction, boolean cancelable) {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(18), dp(20), dp(16));
        panel.setBackground(roundedStroke(cardColor(), 24, dividerColor()));
        panel.setElevation(dp(16));

        TextView heading = text(title, 21, textColor(), Typeface.BOLD);
        panel.addView(heading);
        panel.addView(space(12));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(space(14));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        if (neutralText != null) {
            Button neutral = secondaryButton(neutralText);
            neutral.setOnClickListener(v -> {
                if (neutralAction != null) neutralAction.run();
                dialog.dismiss();
            });
            actions.addView(neutral, new LinearLayout.LayoutParams(0, dp(44), 1f));
            actions.addView(horizontalSpace(8));
        }
        if (negativeText != null) {
            Button negative = secondaryButton(negativeText);
            negative.setOnClickListener(v -> {
                if (negativeAction != null) negativeAction.run();
                dialog.dismiss();
            });
            actions.addView(negative, new LinearLayout.LayoutParams(0, dp(44), 1f));
            actions.addView(horizontalSpace(8));
        }
        Button positive = primaryButton(positiveText);
        positive.setOnClickListener(v -> {
            if (positiveAction != null) positiveAction.run();
            dialog.dismiss();
        });
        actions.addView(positive, new LinearLayout.LayoutParams(0, dp(44), 1f));
        panel.addView(actions);

        dialog.setContentView(panel, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog.setCancelable(cancelable);
        dialog.setCanceledOnTouchOutside(cancelable);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = Math.round(getResources().getDisplayMetrics().widthPixels * 0.92f);
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        return dialog;
    }
    private void showStyledMessageDialog(String title, String message, String positiveText,
                                         Runnable positiveAction, String negativeText,
                                         Runnable negativeAction, boolean cancelable) {
        TextView body = text(message, 13, mutedColor(), Typeface.NORMAL);
        body.setLineSpacing(0, 1.3f);
        showStyledDialog(title, body, positiveText, positiveAction,
                negativeText, negativeAction, cancelable);
    }
    private void showLoginError(String message) {
        if (statusView != null) {
            statusView.setTextColor(errorColor());
            statusView.setText(message);
        }
        if (loginButton != null) {
            loginButton.setEnabled(true);
            loginButton.setText("重新登录");
        }
    }

    private void addBottomNavigation() {
        bottomNav = new LinearLayout(this);
        bottomNav.setOrientation(LinearLayout.HORIZONTAL);
        bottomNav.setGravity(Gravity.CENTER);
        bottomNav.setPadding(dp(10), dp(5), dp(10), dp(4));
        GradientDrawable background = roundedStroke(cardColor(), 0, dividerColor());
        background.setStroke(dp(1), dividerColor());
        bottomNav.setBackground(background);
        bottomNav.setElevation(dp(10));

        String[] labels = {"日程", "周程", "更多"};
        for (int i = 0; i < labels.length; i++) {
            final int tab = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(8), dp(5), dp(8), dp(2));
            TextView label = text(labels[i], 14, activeTab == tab ? primary() : mutedColor(),
                    activeTab == tab ? Typeface.BOLD : Typeface.NORMAL);
            label.setGravity(Gravity.CENTER);
            FrameLayout labelHost = new FrameLayout(this);
            labelHost.addView(label, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
            if (tab == TAB_MORE) {
                updateBadge = new View(this);
                FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(dp(8), dp(8), Gravity.TOP | Gravity.END);
                badgeParams.topMargin = dp(1);
                badgeParams.rightMargin = dp(1);
                updateBadge.setLayoutParams(badgeParams);
                updateBadge.setBackground(rounded(Color.parseColor("#E5484D"), 4));
                labelHost.addView(updateBadge);
            }
            View indicator = new View(this);
            LinearLayout.LayoutParams indicatorParams = new LinearLayout.LayoutParams(dp(22), dp(3));
            indicatorParams.topMargin = dp(5);
            indicator.setLayoutParams(indicatorParams);
            indicator.setBackground(rounded(activeTab == tab ? primary() : Color.TRANSPARENT, 2));
            item.addView(labelHost, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            item.addView(indicator);
            item.setOnClickListener(v -> switchTabAnimated(tab));
            bottomNav.addView(item, new LinearLayout.LayoutParams(0, dp(56), 1f));
        }
        root.addView(bottomNav, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(66), Gravity.BOTTOM));
        bottomNav.bringToFront();
        refreshUpdateBadge();
    }

    private void refreshUpdateBadge() {
        if (updateBadge == null) return;
        updateBadge.setVisibility(UpdateChecker.hasCachedUpdate(this, versionName())
                ? View.VISIBLE : View.GONE);
    }
    private void switchTabAnimated(int nextTab) {
        if (switchingTab || nextTab == activeTab) return;
        switchingTab = true;
        float direction = nextTab > activeTab ? 1f : -1f;
        pageHost.animate()
                .alpha(0f)
                .translationX(-direction * dp(28))
                .setDuration(130L)
                .withEndAction(() -> {
                    activeTab = nextTab;
                    render();
                    pageHost.setAlpha(0f);
                    pageHost.setTranslationX(direction * dp(28));
                    pageHost.animate()
                            .alpha(1f)
                            .translationX(0f)
                            .setDuration(220L)
                            .withEndAction(() -> switchingTab = false)
                            .start();
                })
                .start();
    }
    private void addScheduleHeader(LinearLayout parent, List<Course> courses) {
        LinearLayout summary = card();
        TextView semesterView = text(semester.title(), 18, textColor(), Typeface.BOLD);
        long last = AppPrefs.lastUpdate(this);
        String updateText = last == 0 ? "尚未同步" : "上次更新：" + formatTime(last);
        TextView accountView = text("账号 " + maskAccount(AppPrefs.account(this)) + "  ·  " + updateText,
                12, mutedColor(), Typeface.NORMAL);
        summary.addView(semesterView);
        summary.addView(space(5));
        summary.addView(accountView);
        parent.addView(summary);
        parent.addView(space(12));
    }

    private void addWeekNavigator(LinearLayout parent) {
        LinearLayout navigator = new LinearLayout(this);
        navigator.setOrientation(LinearLayout.HORIZONTAL);
        navigator.setGravity(Gravity.CENTER_VERTICAL);

        Button previous = secondaryButton("‹");
        previous.setTextSize(24);
        previous.setPadding(0, 0, 0, dp(2));
        previous.setEnabled(selectedWeek > 1);
        previous.setAlpha(selectedWeek > 1 ? 1f : 0.38f);
        previous.setOnClickListener(v -> {
            if (selectedWeek > 1) {
                selectedWeek--;
                render();
            }
        });

        LinearLayout middle = new LinearLayout(this);
        middle.setOrientation(LinearLayout.VERTICAL);
        middle.setGravity(Gravity.CENTER);
        TextView weekTitle = text("第 " + selectedWeek + " 周", 18, textColor(), Typeface.BOLD);
        weekTitle.setGravity(Gravity.CENTER);
        LocalDate start = semester.weekStart(selectedWeek);
        LocalDate end = semester.weekEnd(selectedWeek);
        TextView range = text(monthDay(start) + " - " + monthDay(end) + "  ·  " + weekRelationship(),
                12, weekRelationship().equals("本周") ? primary() : mutedColor(), Typeface.BOLD);
        range.setGravity(Gravity.CENTER);
        middle.addView(weekTitle);
        middle.addView(space(2));
        middle.addView(range);

        Button next = secondaryButton("›");
        next.setTextSize(24);
        next.setPadding(0, 0, 0, dp(2));
        next.setEnabled(selectedWeek < semester.totalWeeks);
        next.setAlpha(selectedWeek < semester.totalWeeks ? 1f : 0.38f);
        next.setOnClickListener(v -> {
            if (selectedWeek < semester.totalWeeks) {
                selectedWeek++;
                render();
            }
        });

        navigator.addView(previous, new LinearLayout.LayoutParams(dp(44), dp(44)));
        navigator.addView(middle, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        navigator.addView(next, new LinearLayout.LayoutParams(dp(44), dp(44)));
        parent.addView(navigator);
        parent.addView(space(10));
    }

    private String weekRelationship() {
        LocalDate today = LocalDate.now();
        if (today.isBefore(semester.startDate)) return selectedWeek == 1 ? "即将开课" : "非本周";
        if (!today.isBefore(semester.endExclusive)) return "假期中";
        return selectedWeek == semester.weekFor(today) ? "本周" : "非本周";
    }

    private void addWeekView(LinearLayout parent) {
        List<Course> courses = AppPrefs.courses(this);
        addScheduleHeader(parent, courses);
        addWeekNavigator(parent);

        List<Course> visible = coursesForWeek(courses, selectedWeek);
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(dp(4), dp(8), dp(4), dp(8));
        grid.setBackground(rounded(cardColor(), 18));

        LinearLayout dayHeader = new LinearLayout(this);
        dayHeader.setOrientation(LinearLayout.HORIZONTAL);
        dayHeader.setGravity(Gravity.BOTTOM);
        TextView periodHeader = text("节次", 10, mutedColor(), Typeface.BOLD);
        periodHeader.setGravity(Gravity.CENTER);
        dayHeader.addView(periodHeader, new LinearLayout.LayoutParams(dp(42), dp(50)));
        LocalDate monday = semester.weekStart(selectedWeek);
        String[] dayNames = {"一", "二", "三", "四", "五", "六", "日"};
        for (int i = 0; i < 7; i++) {
            LocalDate date = monday.plusDays(i);
            LinearLayout day = new LinearLayout(this);
            day.setOrientation(LinearLayout.VERTICAL);
            day.setGravity(Gravity.CENTER);
            TextView name = text(dayNames[i], 12, date.equals(LocalDate.now()) ? primary() : mutedColor(), Typeface.BOLD);
            TextView dateView = text((date.getMonthValue()) + "/" + date.getDayOfMonth(), 11,
                    date.equals(LocalDate.now()) ? primary() : textColor(),
                    date.equals(LocalDate.now()) ? Typeface.BOLD : Typeface.NORMAL);
            name.setGravity(Gravity.CENTER);
            dateView.setGravity(Gravity.CENTER);
            day.addView(name);
            day.addView(dateView);
            if (date.equals(LocalDate.now())) {
                day.setBackground(rounded(tint(primary(), 32), 10));
            }
            dayHeader.addView(day, new LinearLayout.LayoutParams(0, dp(50), 1f));
        }
        grid.addView(dayHeader);
        grid.addView(divider());

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        int bodyHeight = dp(GRID_PERIODS * GRID_ROW_HEIGHT_DP);

        LinearLayout timeColumn = new LinearLayout(this);
        timeColumn.setOrientation(LinearLayout.VERTICAL);
        String[] periodTimes = {"08:30\n09:10", "09:20\n10:00", "10:20\n11:00", "11:10\n11:50",
                "14:00\n14:40", "14:50\n15:30", "15:40\n16:20", "16:30\n17:10",
                "19:00\n19:40", "19:50\n20:30", "20:40\n21:30"};
        for (int i = 0; i < GRID_PERIODS; i++) {
            TextView time = text((i + 1) + "\n" + periodTimes[i], 9, mutedColor(), Typeface.NORMAL);
            time.setGravity(Gravity.CENTER);
            time.setLineSpacing(0, 1.05f);
            timeColumn.addView(time, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(GRID_ROW_HEIGHT_DP)));
        }
        body.addView(timeColumn, new LinearLayout.LayoutParams(dp(42), bodyHeight));

        for (int dayIndex = 0; dayIndex < 7; dayIndex++) {
            final int weekday = dayIndex + 1;
            FrameLayout dayColumn = new FrameLayout(this);
            dayColumn.setBackgroundColor(cardColor());
            for (int period = 0; period <= GRID_PERIODS; period++) {
                View line = new View(this);
                line.setBackgroundColor(dividerColor());
                FrameLayout.LayoutParams lineParams = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
                lineParams.topMargin = Math.min(bodyHeight - dp(1), period * dp(GRID_ROW_HEIGHT_DP));
                dayColumn.addView(line, lineParams);
            }

            List<Course> dayCourses = new ArrayList<>();
            for (Course course : visible) {
                if (course.weekdayNumber() == weekday) dayCourses.add(course);
            }
            dayCourses.sort(Comparator.comparingInt(Course::periodStart));

            int[] lanes = assignLanes(dayCourses);
            int laneCount = 1;
            for (int lane : lanes) laneCount = Math.max(laneCount, lane + 1);

            for (int i = 0; i < dayCourses.size(); i++) {
                Course course = dayCourses.get(i);
                int start = Math.max(1, Math.min(GRID_PERIODS, course.periodStart()));
                int end = Math.max(start, Math.min(GRID_PERIODS, course.periodEnd()));
                int span = end - start + 1;
                TextView block = text(courseBlockText(course, span), 10,
                        courseTextColor(), Typeface.BOLD);
                block.setGravity(Gravity.CENTER);
                block.setLineSpacing(0, 0.95f);
                block.setMaxLines(span >= 3 ? 8 : 4);
                block.setPadding(dp(2), dp(3), dp(2), dp(2));
                block.setBackground(rounded(courseColor(course), 8));
                block.setOnClickListener(v -> showCourseDetails(course));
                block.setOnLongClickListener(v -> { showEditCourseTimeDialog(course); return true; });
                FrameLayout.LayoutParams blockParams = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, span * dp(GRID_ROW_HEIGHT_DP) - dp(4));
                blockParams.topMargin = (start - 1) * dp(GRID_ROW_HEIGHT_DP) + dp(2);
                int laneShift = dp(20);
                blockParams.leftMargin = lanes[i] * laneShift + dp(1);
                blockParams.rightMargin = (laneCount - lanes[i] - 1) * laneShift + dp(1);
                dayColumn.addView(block, blockParams);
            }

            View rightLine = new View(this);
            rightLine.setBackgroundColor(dividerColor());
            FrameLayout.LayoutParams rightParams = new FrameLayout.LayoutParams(dp(1), bodyHeight, Gravity.END);
            dayColumn.addView(rightLine, rightParams);
            body.addView(dayColumn, new LinearLayout.LayoutParams(0, bodyHeight, 1f));
        }
        grid.addView(body);
        parent.addView(grid);

        if (visible.isEmpty()) {
            TextView empty = text("本周没有匹配课程，可切换周次查看。", 13, mutedColor(), Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(14), 0, 0);
            parent.addView(empty);
        }
    }

    private int[] assignLanes(List<Course> courses) {
        int[] lanes = new int[courses.size()];
        for (int i = 0; i < courses.size(); i++) {
            Course current = courses.get(i);
            int candidate = 0;
            while (true) {
                boolean conflict = false;
                for (int j = 0; j < i; j++) {
                    if (lanes[j] != candidate) continue;
                    Course previous = courses.get(j);
                    if (overlaps(previous, current)) {
                        conflict = true;
                        break;
                    }
                }
                if (!conflict) break;
                candidate++;
            }
            lanes[i] = candidate;
        }
        return lanes;
    }

    private boolean overlaps(Course first, Course second) {
        int firstStart = Math.max(1, Math.min(GRID_PERIODS, first.periodStart()));
        int firstEnd = Math.max(firstStart, Math.min(GRID_PERIODS, first.periodEnd()));
        int secondStart = Math.max(1, Math.min(GRID_PERIODS, second.periodStart()));
        int secondEnd = Math.max(secondStart, Math.min(GRID_PERIODS, second.periodEnd()));
        return firstStart <= secondEnd && secondStart <= firstEnd;
    }

    private String courseBlockText(Course course, int span) {
        String name = course.name == null ? "" : course.name;
        if (span <= 1) return name;
        String location = shortLocation(course.location);
        return location.isEmpty() ? name : name + "\n" + location;
    }

    private String shortLocation(String location) {
        if (location == null) return "";
        return location.replace("南海校园", "").replace("大学城校园", "").trim();
    }

    private void addAgendaView(LinearLayout parent) {
        List<Course> courses = AppPrefs.courses(this);
        addScheduleHeader(parent, courses);
        addWeekNavigator(parent);
        List<Course> visible = coursesForWeek(courses, selectedWeek);
        TextView section = text("本周课程 · " + visible.size() + " 条安排", 17,
                textColor(), Typeface.BOLD);
        parent.addView(section);
        parent.addView(space(8));

        LocalDate monday = semester.weekStart(selectedWeek);
        boolean hasAny = false;
        for (int weekday = 1; weekday <= 7; weekday++) {
            List<Course> dayCourses = new ArrayList<>();
            for (Course course : visible) {
                if (course.weekdayNumber() == weekday) dayCourses.add(course);
            }
            if (dayCourses.isEmpty()) continue;
            hasAny = true;
            dayCourses.sort(Comparator.comparingInt(Course::periodStart));
            LocalDate date = monday.plusDays(weekday - 1L);
            TextView dayHeader = text(weekdayName(weekday) + " · " + monthDay(date), 14,
                    date.equals(LocalDate.now()) ? primary() : textColor(), Typeface.BOLD);
            dayHeader.setPadding(dp(4), dp(10), 0, dp(5));
            parent.addView(dayHeader);
            for (Course course : dayCourses) {
                parent.addView(courseCard(course));
                parent.addView(space(8));
            }
        }
        if (!hasAny) {
            LinearLayout empty = card();
            TextView message = text("这一周没有课程安排。", 15, mutedColor(), Typeface.NORMAL);
            message.setGravity(Gravity.CENTER);
            empty.addView(message);
            parent.addView(empty);
        }
    }

    private List<Course> coursesForWeek(List<Course> courses, int week) {
        List<Course> result = new ArrayList<>();
        for (Course course : courses) {
            if (course.occursInWeek(week) && course.weekdayNumber() >= 1 && course.weekdayNumber() <= 7) {
                result.add(course);
            }
        }
        result.sort((a, b) -> {
            int day = Integer.compare(a.weekdayNumber(), b.weekdayNumber());
            return day != 0 ? day : Integer.compare(a.periodStart(), b.periodStart());
        });
        return result;
    }

    private View courseCard(Course course) {
        LinearLayout card = card();
        card.setBackground(roundedStroke(cardColor(), 18, dividerColor()));
        card.setClickable(true);
        card.setOnClickListener(v -> showCourseDetails(course));
        card.setOnLongClickListener(v -> { showEditCourseTimeDialog(course); return true; });
        TextView name = text(course.name, 17, textColor(), Typeface.BOLD);
        TextView time = text(course.compactTime() + "   " + course.periods, 14,
                primary(), Typeface.BOLD);
        TextView detail = text("教师：" + safe(course.teacher) + "\n地点：" + safe(course.location)
                + "\n周次：" + safe(course.weeks) + "    学分：" + safe(course.credits), 13,
                mutedColor(), Typeface.NORMAL);
        detail.setLineSpacing(0, 1.18f);
        card.addView(name);
        card.addView(space(4));
        card.addView(time);
        card.addView(space(6));
        card.addView(detail);
        return card;
    }

    private void showCourseDetails(Course course) {
        String message = "时间：" + safe(course.compactTime()) + "  " + safe(course.periods)
                + "\n教师：" + safe(course.teacher)
                + "\n地点：" + safe(course.location)
                + "\n周次：" + safe(course.weeks) + "    学分：" + safe(course.credits)
                + "\n课程代码：" + safe(course.code);
        showStyledMessageDialog(safe(course.name), message, "关闭", null, null, null, true);
    }

    private void showEditCourseTimeDialog(Course course) {
        int[] startParts = parseCourseTime(course.time, true);
        int[] endParts = parseCourseTime(course.time, false);
        final int[] selectedStart = {startParts[0], startParts[1]};
        final int[] selectedEnd = {endParts[0], endParts[1]};

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(8), dp(20), dp(4));
        TextView hint = text("仅修改本机显示和提醒时间，不影响学校系统。", 12, mutedColor(), Typeface.NORMAL);
        hint.setLineSpacing(0, 1.2f);
        panel.addView(hint);
        panel.addView(space(12));

        Button startButton = secondaryButton("上课时间 " + formatClock(selectedStart));
        Button endButton = secondaryButton("下课时间 " + formatClock(selectedEnd));
        panel.addView(startButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        panel.addView(space(8));
        panel.addView(endButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));

        startButton.setOnClickListener(v -> {
            TimePickerDialog picker = new TimePickerDialog(this, (view, hour, minute) -> {
                selectedStart[0] = hour;
                selectedStart[1] = minute;
                startButton.setText("上课时间 " + formatClock(selectedStart));
            }, selectedStart[0], selectedStart[1], true);
            picker.setTitle("选择上课时间");
            picker.show();
        });
        endButton.setOnClickListener(v -> {
            TimePickerDialog picker = new TimePickerDialog(this, (view, hour, minute) -> {
                selectedEnd[0] = hour;
                selectedEnd[1] = minute;
                endButton.setText("下课时间 " + formatClock(selectedEnd));
            }, selectedEnd[0], selectedEnd[1], true);
            picker.setTitle("选择下课时间");
            picker.show();
        });

        boolean hasOverride = AppPrefs.hasCourseTimeOverride(this, course);
        showStyledDialog("修改课程时间", panel, "保存", () -> {
            if (minutesOfDay(selectedStart) >= minutesOfDay(selectedEnd)) {
                toast("下课时间必须晚于上课时间");
                return;
            }
            AppPrefs.saveCourseTimeOverride(this, course,
                    formatClock(selectedStart), formatClock(selectedEnd));
            ReminderScheduler.scheduleAll(this);
            ScheduleWidgetProvider.updateAll(this);
            render();
            toast("课程时间已更新");
        }, "取消", null, hasOverride ? "恢复默认" : null, () -> {
            AppPrefs.clearCourseTimeOverride(this, course);
            ReminderScheduler.scheduleAll(this);
            ScheduleWidgetProvider.updateAll(this);
            render();
            toast("已恢复学校默认时间");
        }, true);
    }

    private int[] parseCourseTime(String time, boolean start) {
        String value = time == null ? "" : time.split("、")[0];
        String[] range = value.split("-");
        String target = start ? (range.length > 0 ? range[0] : "") : (range.length > 1 ? range[1] : "");
        try {
            String[] parts = target.trim().split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            if (hour >= 0 && hour < 24 && minute >= 0 && minute < 60) return new int[]{hour, minute};
        } catch (Exception ignored) {
        }
        return start ? new int[]{8, 30} : new int[]{10, 0};
    }

    private int minutesOfDay(int[] value) {
        return value[0] * 60 + value[1];
    }

    private String formatClock(int[] value) {
        return String.format(Locale.CHINA, "%02d:%02d", value[0], value[1]);
    }
    private void addMoreView(LinearLayout parent) {
        TextView subtitle = text("课表同步、日历、提醒和应用信息", 13, mutedColor(), Typeface.NORMAL);
        subtitle.setPadding(dp(2), 0, 0, dp(4));
        parent.addView(subtitle);
        parent.addView(space(8));

        LinearLayout syncCard = card();
        TextView syncTitle = text("课表同步", 15, textColor(), Typeface.BOLD);
        long last = AppPrefs.lastUpdate(this);
        statusView = text(syncing ? "正在登录并同步课表……" :
                        (last == 0 ? "尚未同步课表" : "上次更新：" + formatTime(last)),
                12, mutedColor(), Typeface.NORMAL);
        progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        progress.setVisibility(syncing ? View.VISIBLE : View.GONE);
        syncCard.addView(syncTitle);
        syncCard.addView(space(4));
        syncCard.addView(statusView);
        syncCard.addView(progress);
        parent.addView(syncCard);
        parent.addView(space(10));

        parent.addView(settingRow("更新课表", "立即从学校研究生系统重新获取", v -> startSync()));
        parent.addView(space(8));
        parent.addView(settingRow("导入日历", "将当前课表写入手机本地日历", v -> requestCalendarImport()));
        parent.addView(space(8));
        parent.addView(settingRow("自动更新频率", AppPrefs.frequencyLabel(AppPrefs.updateFrequency(this)),
                v -> showFrequencyDialog()));
        parent.addView(space(8));
        parent.addView(settingRow("上课提醒", reminderSummary(), v -> showReminderDialog()));
        parent.addView(space(8));
        parent.addView(settingRow("桌面小组件", "查看 4 步图文添加教程", v -> startActivity(new Intent(this, WidgetTutorialActivity.class))));
        parent.addView(space(18));

        TextView aboutTitle = text("应用信息", 17, textColor(), Typeface.BOLD);
        parent.addView(aboutTitle);
        parent.addView(space(8));
        parent.addView(settingRow("隐私与权限", "查看数据收集、存储和授权说明", v -> showPrivacyDialog(false)));
        parent.addView(space(8));
        parent.addView(settingRow("当前版本", "v" + versionName(), null));
        parent.addView(space(8));
        String latestVersion = AppPrefs.latestVersion(this);
        String communitySubtitle = UpdateChecker.hasCachedUpdate(this, versionName())
                ? "发现新版本 v" + latestVersion + "，点击查看"
                : "GitHub Releases、QQ 频道和 QQ 群";
        parent.addView(settingRow("版本与社区", communitySubtitle,
                v -> showUpdateAndCommunityDialog()));
        parent.addView(space(8));
        parent.addView(settingRow("打赏作者", "请作者喝一杯奶茶", v -> showSponsorDialog()));
        parent.addView(space(18));

        Button logout = secondaryButton("退出登录");
        logout.setTextColor(errorColor());
        logout.setOnClickListener(v -> confirmLogout());
        parent.addView(logout, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
    }

    private View settingRow(String titleText, String subtitleText, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(15), dp(13), dp(13), dp(13));
        row.setBackground(roundedStroke(cardColor(), 16, dividerColor()));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(titleText, 15, textColor(), Typeface.BOLD);
        labels.addView(title);
        if (subtitleText != null && !subtitleText.isEmpty()) {
            TextView subtitle = text(subtitleText, 12, mutedColor(), Typeface.NORMAL);
            subtitle.setPadding(0, dp(3), 0, 0);
            labels.addView(subtitle);
        }
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (listener != null) {
            TextView arrow = text("›", 24, mutedColor(), Typeface.NORMAL);
            arrow.setGravity(Gravity.CENTER);
            row.addView(arrow, new LinearLayout.LayoutParams(dp(28), dp(36)));
            row.setClickable(true);
            row.setOnClickListener(listener);
        }
        return row;
    }

    private String reminderSummary() {
        Set<Integer> values = AppPrefs.reminderMinutes(this);
        String type = AppPrefs.reminderTypeLabel(AppPrefs.reminderType(this));
        if (values.isEmpty()) return type + " · 已关闭";
        StringBuilder builder = new StringBuilder();
        for (Integer value : values) {
            if (builder.length() > 0) builder.append("、");
            builder.append("提前").append(value).append("分钟");
        }
        return type + " · " + builder;
    }
    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            return "1.4.3";
        }
    }

    private void showFrequencyDialog() {
        String[] labels = {"仅手动更新", "每周自动更新", "每月自动更新"};
        String[] values = {AppPrefs.FREQ_MANUAL, AppPrefs.FREQ_WEEKLY, AppPrefs.FREQ_MONTHLY};
        String current = AppPrefs.updateFrequency(this);
        int checked = AppPrefs.FREQ_MANUAL.equals(current) ? 0 : AppPrefs.FREQ_MONTHLY.equals(current) ? 2 : 1;
        final int[] selected = {checked};

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        TextView intro = text("选择课表在后台自动同步的频率。", 13, mutedColor(), Typeface.NORMAL);
        intro.setLineSpacing(0, 1.25f);
        content.addView(intro);
        content.addView(space(12));
        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);
        for (int i = 0; i < labels.length; i++) {
            RadioButton option = new RadioButton(this);
            option.setText(labels[i]);
            option.setTextColor(textColor());
            option.setTextSize(15);
            option.setPadding(dp(4), dp(8), dp(4), dp(8));
            option.setButtonTintList(android.content.res.ColorStateList.valueOf(primary()));
            final int index = i;
            option.setOnCheckedChangeListener((button, isChecked) -> {
                if (isChecked) selected[0] = index;
            });
            group.addView(option);
            if (i == checked) option.setChecked(true);
        }
        content.addView(group);
        showStyledDialog("自动更新频率", content, "保存", () -> {
            AppPrefs.setUpdateFrequency(this, values[selected[0]]);
            SyncScheduler.schedule(this, values[selected[0]]);
            render();
        }, "取消", null, true);
    }
    private void showUpdateAndCommunityDialog() {
        final Dialog[] holder = new Dialog[1];
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        TextView intro = text("检查更新、下载正式版本或加入交流社区。", 13, mutedColor(), Typeface.NORMAL);
        intro.setLineSpacing(0, 1.25f);
        content.addView(intro);
        content.addView(space(10));
        View check = settingRow("检查 GitHub 更新", "检查最新 Release、预发布版和 Tags",
                v -> { holder[0].dismiss(); checkForUpdates(true); });
        View releases = settingRow("打开 GitHub Releases", "查看版本说明和安装包",
                v -> { holder[0].dismiss(); openUrl(RELEASES_PAGE); });
        View channel = settingRow("加入 QQ 频道", "获取更新通知和交流信息",
                v -> { holder[0].dismiss(); openUrl(QQ_CHANNEL_URL); });
        View group = settingRow("加入 QQ 群", "群号 1126483712",
                v -> { holder[0].dismiss(); openUrl(QQ_GROUP_URL); });
        content.addView(check);
        content.addView(space(8));
        content.addView(releases);
        content.addView(space(8));
        content.addView(channel);
        content.addView(space(8));
        content.addView(group);
        holder[0] = showStyledDialog("版本与社区", content, "关闭", null, null, null, true);
    }
    private void checkForUpdates(boolean userInitiated) {
        if (!userInitiated && !UpdateChecker.shouldCheck(this)) {
            refreshUpdateBadge();
            return;
        }
        if (userInitiated) toast("正在检查更新……");
        UpdateChecker.check(this, userInitiated, versionName(), new UpdateChecker.Callback() {
            @Override
            public void onResult(boolean updateAvailable, String version, String url, String notes) {
                runOnUiThread(() -> {
                    refreshUpdateBadge();
                    if (activeTab == TAB_MORE) render();
                    if (!userInitiated) return;
                    if (updateAvailable) {
                        String safeUrl = url == null || url.isEmpty() ? RELEASES_PAGE : url;
                        String message = "当前版本：v" + versionName() + "\n\n"
                                + (notes == null || notes.isEmpty()
                                ? "请前往 GitHub Releases 查看更新内容。" : abbreviateText(notes, 1200));
                        showStyledMessageDialog("发现新版本 v" + version, message,
                                "打开下载页", () -> openUrl(safeUrl), "稍后", null, true);
                    } else if (version == null || version.isEmpty()) {
                        showStyledMessageDialog("暂未发现可用版本",
                                "GitHub 当前没有可识别的 Release 或新版本标签。\n\n发布后，App 会通过红点自动提示。",
                                "打开 Releases", () -> openUrl(RELEASES_PAGE), "取消", null, true);
                    } else {
                        toast("当前已是最新版本 v" + versionName());
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    refreshUpdateBadge();
                    if (!userInitiated) return;
                    showStyledMessageDialog("无法检查更新",
                            "暂时无法访问 GitHub。可尝试直接打开 Releases 页面查看版本。\n\n错误：" + message,
                            "打开 Releases", () -> openUrl(RELEASES_PAGE), "取消", null, true);
                });
            }
        });
    }

    private String abbreviateText(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value == null ? "" : value;
        return value.substring(0, maxLength) + "…";
    }
    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
            toast("无法打开下载页");
        }
    }

    private void startSync() {
        if (syncing || !AppPrefs.isLoggedIn(this)) return;
        syncing = true;
        if (statusView != null) {
            statusView.setTextColor(mutedColor());
            statusView.setText("正在登录并同步课表……");
        }
        if (progress != null) progress.setVisibility(View.VISIBLE);
        if (loginButton != null) {
            loginButton.setEnabled(false);
            loginButton.setText("正在登录…");
        }
        setWebViewSmall();
        if (engine != null) engine.stop();
        engine = new CourseSyncEngine(this, webView, new CourseSyncEngine.Callback() {
            @Override
            public void onStatus(String message) {
                runOnUiThread(() -> {
                    if (statusView != null) {
                        statusView.setTextColor(mutedColor());
                        statusView.setText(message);
                    }
                });
            }

            @Override
            public void onSuccess(String semesterName, List<Course> courses) {
                runOnUiThread(() -> {
                    AppPrefs.saveCourses(MainActivity.this, semesterName, courses);
                    ScheduleWidgetProvider.updateAll(MainActivity.this);
                    if (!AppPrefs.reminderMinutes(MainActivity.this).isEmpty()) requestNotificationPermissionIfNeeded();
                    ReminderScheduler.scheduleAll(MainActivity.this);
                    SyncScheduler.schedule(MainActivity.this, AppPrefs.updateFrequency(MainActivity.this));
                    syncing = false;
                    selectedSemesterKey = "";
                    selectedWeek = 1;
                    hideWebView();
                    toast("课表更新成功");
                    render();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    syncing = false;
                    if (progress != null) progress.setVisibility(View.GONE);
                    String reason = message == null || message.trim().isEmpty()
                            ? "未知错误，请稍后重试。" : message.trim();
                    if (loginButton != null) {
                        showLoginError("登录失败：" + reason.replaceFirst("^登录失败[：:]\\s*", ""));
                    } else if (statusView != null) {
                        statusView.setTextColor(errorColor());
                        statusView.setText("同步失败：" + reason);
                    }
                    toast(reason);
                });
            }
        });
        engine.start();
    }

    private void requestCalendarImport() {
        if (!CalendarImporter.hasPermission(this)) {
            requestPermissions(new String[]{Manifest.permission.READ_CALENDAR,
                    Manifest.permission.WRITE_CALENDAR}, REQUEST_CALENDAR);
            return;
        }
        importCalendarNow();
    }

    private void importCalendarNow() {
        if (statusView != null) {
            statusView.setTextColor(mutedColor());
            statusView.setText("正在导入手机日历……");
        }
        List<Course> snapshot = new ArrayList<>(AppPrefs.courses(this));
        executor.execute(() -> CalendarImporter.importCourses(this, snapshot, message ->
                runOnUiThread(() -> {
                    if (statusView != null) {
                        statusView.setTextColor(mutedColor());
                        statusView.setText(message);
                    }
                    toast(message);
                })));
    }
    private void showReminderDialog() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(22), dp(4), dp(22), 0);

        Set<Integer> current = AppPrefs.reminderMinutes(this);
        CheckBox enable = new CheckBox(this);
        enable.setText("启用上课提醒");
        enable.setTextColor(textColor());
        enable.setTextSize(15);
        enable.setTypeface(Typeface.DEFAULT_BOLD);
        enable.setButtonTintList(android.content.res.ColorStateList.valueOf(primary()));
        enable.setChecked(!current.isEmpty());
        panel.addView(enable);
        panel.addView(space(8));

        TextView typeLabel = text("提醒方式", 14, textColor(), Typeface.BOLD);
        panel.addView(typeLabel);
        RadioGroup typeGroup = new RadioGroup(this);
        typeGroup.setOrientation(RadioGroup.VERTICAL);
        RadioButton message = new RadioButton(this);
        message.setText("消息提醒（普通通知与提示音）");
        message.setTextColor(textColor());
        message.setButtonTintList(android.content.res.ColorStateList.valueOf(primary()));
        RadioButton alarm = new RadioButton(this);
        alarm.setText("闹钟提醒（闹钟铃声与振动）");
        alarm.setTextColor(textColor());
        alarm.setButtonTintList(android.content.res.ColorStateList.valueOf(primary()));
        boolean alarmType = AppPrefs.REMINDER_TYPE_ALARM.equals(AppPrefs.reminderType(this));
        typeGroup.addView(message);
        typeGroup.addView(alarm);
        if (alarmType) alarm.setChecked(true); else message.setChecked(true);
        panel.addView(typeGroup);
        panel.addView(space(10));

        TextView timeLabel = text("提前时间", 14, textColor(), Typeface.BOLD);
        panel.addView(timeLabel);
        Integer[] options = {30, 15, 5};
        CheckBox[] boxes = new CheckBox[options.length];
        for (int i = 0; i < options.length; i++) {
            CheckBox box = new CheckBox(this);
            box.setText("提前 " + options[i] + " 分钟");
            box.setTextColor(textColor());
            box.setTextSize(14);
            box.setChecked(current.contains(options[i]));
            box.setButtonTintList(android.content.res.ColorStateList.valueOf(primary()));
            boxes[i] = box;
            panel.addView(box);
        }
        Runnable updateState = () -> {
            boolean on = enable.isChecked();
            typeGroup.setEnabled(on);
            typeGroup.setAlpha(on ? 1f : 0.45f);
            timeLabel.setAlpha(on ? 1f : 0.45f);
            for (CheckBox box : boxes) {
                box.setEnabled(on);
                box.setAlpha(on ? 1f : 0.45f);
            }
        };
        enable.setOnCheckedChangeListener((button, checked) -> updateState.run());
        updateState.run();

        showStyledDialog("上课提醒", panel, "保存", () -> {
            LinkedHashSet<Integer> values = new LinkedHashSet<>();
            if (enable.isChecked()) {
                for (int i = 0; i < options.length; i++) if (boxes[i].isChecked()) values.add(options[i]);
            }
            String type = alarm.isChecked()
                    ? AppPrefs.REMINDER_TYPE_ALARM : AppPrefs.REMINDER_TYPE_MESSAGE;
            AppPrefs.setReminderType(this, type);
            AppPrefs.setReminderMinutes(this, values);
            ReminderScheduler.scheduleAll(this);
            toast(values.isEmpty() ? "已关闭上课提醒" : "提醒设置已保存");
            render();
            if (!values.isEmpty()) {
                requestNotificationPermissionIfNeeded();
                if (AppPrefs.REMINDER_TYPE_ALARM.equals(type)) requestExactAlarmPermissionIfNeeded();
            }
        }, "取消", null, true);
    }
    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        }
    }

    private void requestExactAlarmPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        AlarmManager manager = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (manager != null && !manager.canScheduleExactAlarms()) {
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception ignored) {
            }
        }
    }

    private void confirmLogout() {
        showStyledMessageDialog("退出登录", "退出后会保留已下载课表，但停止自动更新和提醒。",
                "退出", () -> {
                    ReminderScheduler.cancelAll(this, AppPrefs.courses(this));
                    SyncScheduler.schedule(this, AppPrefs.FREQ_MANUAL);
                    AppPrefs.clearCredentials(this);
                    ScheduleWidgetProvider.updateAll(this);
                    activeTab = TAB_WEEK;
                    selectedSemesterKey = "";
                    render();
                }, "取消", null, true);
    }

    private void showWebVerification() {
        if (engine == null) {
            if (AppPrefs.isLoggedIn(this)) startSync();
            else toast("请先输入账号和密码并点击登录");
            return;
        }
        webView.setBackgroundColor(Color.WHITE);
        webView.setAlpha(1f);
        webView.setTranslationX(0f);
        webView.bringToFront();
        toast("完成网页验证后可按返回键隐藏");
    }

    private void hideWebView() {
        if (webView == null) return;
        webView.setAlpha(0f);
        webView.setTranslationX(Math.max(dp(2000), root.getWidth() + dp(40)));
    }

    private void setWebViewSmall() {
        webView.setAlpha(0f);
        webView.setTranslationX(10000f);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CALENDAR) {
            boolean granted = true;
            for (int result : grantResults) granted &= result == PackageManager.PERMISSION_GRANTED;
            if (granted) importCalendarNow();
            else toast("需要日历权限才能导入课表");
        }
    }

    private TextView label(String value) {
        TextView view = text(value, 13, mutedColor(), Typeface.BOLD);
        view.setPadding(dp(2), 0, 0, dp(6));
        return view;
    }

    private EditText input(String hint, int inputType) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextSize(16);
        input.setTextColor(textColor());
        input.setHintTextColor(dark ? Color.BLACK : Color.WHITE);
        input.setInputType(inputType);
        input.setSingleLine(true);
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        input.setBackground(rounded(dark ? Color.parseColor("#2B3440") : Color.parseColor("#F1F4F8"), 14));
        input.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primary()));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        input.setLayoutParams(params);
        return input;
    }

    private Button primaryButton(String textValue) {
        Button button = new Button(this);
        button.setText(textValue);
        button.setTextSize(14);
        button.setTextColor(dark ? Color.parseColor("#111923") : Color.WHITE);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackground(rounded(primary(), 14));
        button.setPadding(dp(12), 0, dp(12), 0);
        return button;
    }

    private Button secondaryButton(String textValue) {
        Button button = new Button(this);
        button.setText(textValue);
        button.setTextSize(13);
        button.setTextColor(primary());
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackground(rounded(dark ? Color.parseColor("#2B3440") : Color.parseColor("#EAF0F7"), 14));
        button.setPadding(dp(10), 0, dp(10), 0);
        return button;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(rounded(cardColor(), 20));
        card.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private TextView text(String value, int size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", style));
        return view;
    }

    private View space(int height) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, dp(height)));
        return view;
    }

    private View horizontalSpace(int width) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(width), 1));
        return view;
    }

    private View divider() {
        View view = new View(this);
        view.setBackgroundColor(dividerColor());
        view.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        return view;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private GradientDrawable roundedStroke(int color, int radiusDp, int strokeColor) {
        GradientDrawable drawable = rounded(color, radiusDp);
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private int tint(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private int courseColor(Course course) {
        int index = Math.floorMod((course.code + course.name).hashCode(), coursePalette().length);
        return coursePalette()[index];
    }

    private int[] coursePalette() {
        if (dark) {
            return new int[]{
                    Color.parseColor("#304A70"), Color.parseColor("#285A55"),
                    Color.parseColor("#684239"), Color.parseColor("#4B3E68"),
                    Color.parseColor("#62562F"), Color.parseColor("#354E64")
            };
        }
        return new int[]{
                Color.parseColor("#DCE9FF"), Color.parseColor("#CFE7E2"),
                Color.parseColor("#FFE2D1"), Color.parseColor("#E7DDF7"),
                Color.parseColor("#F7E7A9"), Color.parseColor("#D8E8F5")
        };
    }

    private int courseTextColor() {
        return dark ? Color.parseColor("#F4F8FF") : Color.parseColor("#172435");
    }

    private int bg() {
        return dark ? Color.parseColor("#111923") : Color.parseColor("#F4F6F8");
    }

    private int cardColor() {
        return dark ? Color.parseColor("#252C35") : Color.WHITE;
    }

    private int textColor() {
        return dark ? Color.parseColor("#F1F5F9") : Color.parseColor("#121C28");
    }

    private int mutedColor() {
        return dark ? Color.parseColor("#B6C1CF") : Color.parseColor("#617083");
    }

    private int primary() {
        return dark ? Color.parseColor("#A8C7FA") : Color.parseColor("#356A9D");
    }

    private int errorColor() {
        return dark ? Color.parseColor("#FFB4AB") : Color.parseColor("#B3261E");
    }

    private int dividerColor() {
        return dark ? Color.parseColor("#3A4553") : Color.parseColor("#DCE2E8");
    }

    private String safe(String value) {
        return value == null || value.isEmpty() ? "未填写" : value;
    }

    private String maskAccount(String account) {
        if (account == null || account.length() < 6) return account == null ? "" : account;
        return account.substring(0, 4) + "****" + account.substring(account.length() - 2);
    }

    private String monthDay(LocalDate date) {
        return date.getMonthValue() + "月" + date.getDayOfMonth() + "日";
    }

    private String weekdayName(int weekday) {
        String[] names = {"", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"};
        return weekday >= 1 && weekday <= 7 ? names[weekday] : "未安排";
    }

    private String formatTime(long millis) {
        return new java.text.SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date(millis));
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}









































