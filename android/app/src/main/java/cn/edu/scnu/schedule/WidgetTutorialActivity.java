package cn.edu.scnu.schedule;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class WidgetTutorialActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean dark = AppPrefs.darkMode(this);
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(dark ? Color.parseColor("#111923") : Color.parseColor("#F4F6F8"));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
        } else {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            if (!dark) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            window.getDecorView().setSystemUiVisibility(flags);
        }
        setContentView(createContent(dark));
    }

    private View createContent(boolean dark) {
        int bg = dark ? Color.parseColor("#111923") : Color.parseColor("#F4F6F8");
        int card = dark ? Color.parseColor("#252C35") : Color.WHITE;
        int title = dark ? Color.parseColor("#F1F5F9") : Color.parseColor("#121C28");
        int muted = dark ? Color.parseColor("#B6C1CF") : Color.parseColor("#617083");
        int primary = dark ? Color.parseColor("#A8C7FA") : Color.parseColor("#356A9D");
        int border = dark ? Color.parseColor("#3A4553") : Color.parseColor("#DCE2E8");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(10), dp(10), dp(14), dp(8));
        Button back = new Button(this);
        back.setText("‹");
        back.setTextSize(24);
        back.setTextColor(primary);
        back.setAllCaps(false);
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setOnClickListener(v -> finish());
        TextView heading = new TextView(this);
        heading.setText("桌面小组件教程");
        heading.setTextColor(title);
        heading.setTextSize(20);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        heading.setGravity(Gravity.CENTER);
        top.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        top.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(new View(this), new LinearLayout.LayoutParams(dp(48), dp(1)));
        root.addView(top);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(4), dp(18), dp(30));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView intro = new TextView(this);
        intro.setText("按以下 4 步添加桌面小组件。不同品牌手机的入口名称可能略有不同。");
        intro.setTextColor(muted);
        intro.setTextSize(14);
        intro.setLineSpacing(0, 1.2f);
        content.addView(intro);
        content.addView(space(16));

        String[] stepTitles = {
                "第 1 步：进入桌面编辑",
                "第 2 步：选择全部应用",
                "第 3 步：找到华师课表",
                "第 4 步：选择尺寸并添加"
        };
        String[] captions = {
                "长按桌面空白处，点击底部“小部件”。",
                "在“添加小部件”页面选择“全部应用”。",
                "在支持小部件的应用列表中找到“华师课表”。",
                "选择“今日课程”“本周概览”或“一周课表”，按住拖到桌面，并可拖动边缘调整大小。"
        };
        int[] images = {
                R.drawable.widget_tutorial_1,
                R.drawable.widget_tutorial_2,
                R.drawable.widget_tutorial_3,
                R.drawable.widget_tutorial_4
        };
        for (int i = 0; i < images.length; i++) {
            TextView step = new TextView(this);
            step.setText(stepTitles[i]);
            step.setTextColor(primary);
            step.setTextSize(16);
            step.setTypeface(Typeface.DEFAULT_BOLD);
            content.addView(step);
            content.addView(space(6));

            ImageView image = new ImageView(this);
            image.setAdjustViewBounds(true);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            GradientDrawable imageBackground = rounded(card, 16, border);
            image.setBackground(imageBackground);
            image.setClipToOutline(true);
            image.setImageResource(images[i]);
            content.addView(image, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            content.addView(space(7));

            TextView caption = new TextView(this);
            caption.setText(captions[i]);
            caption.setTextColor(muted);
            caption.setTextSize(13);
            caption.setLineSpacing(0, 1.18f);
            content.addView(caption);
            content.addView(space(18));
        }

        Button done = new Button(this);
        done.setText("知道了");
        done.setTextColor(dark ? Color.parseColor("#111923") : Color.WHITE);
        done.setTextSize(14);
        done.setTypeface(Typeface.DEFAULT_BOLD);
        done.setAllCaps(false);
        done.setBackground(rounded(primary, 14));
        done.setOnClickListener(v -> finish());
        content.addView(done, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));

        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int topInset;
            int bottomInset;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                topInset = bars.top;
                bottomInset = bars.bottom;
            } else {
                topInset = insets.getSystemWindowInsetTop();
                bottomInset = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(0, topInset, 0, bottomInset);
            return insets;
        });
        root.requestApplyInsets();
        return root;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private View space(int height) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, dp(height)));
        return view;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private GradientDrawable rounded(int color, int radiusDp, int borderColor) {
        GradientDrawable drawable = rounded(color, radiusDp);
        drawable.setStroke(dp(1), borderColor);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

