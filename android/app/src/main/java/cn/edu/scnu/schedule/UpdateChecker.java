package cn.edu.scnu.schedule;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class UpdateChecker {
    interface Callback {
        void onResult(boolean updateAvailable, String version, String url, String notes);
        void onError(String message);
    }

    private static final String REPO_URL = "https://github.com/tangyuangungun/SCNU-Schedule";
    private static final String RELEASES_API = "https://api.github.com/repos/tangyuangungun/SCNU-Schedule/releases?per_page=10";
    private static final String TAGS_API = "https://api.github.com/repos/tangyuangungun/SCNU-Schedule/tags?per_page=5";
    private static final String RELEASES_PAGE = REPO_URL + "/releases";
    private static final long CHECK_INTERVAL_MS = 48L * 60L * 60L * 1000L;

    private UpdateChecker() {}

    static boolean shouldCheck(Context context) {
        long last = AppPrefs.lastUpdateCheck(context);
        return last <= 0 || System.currentTimeMillis() - last >= CHECK_INTERVAL_MS;
    }

    static boolean hasCachedUpdate(Context context, String currentVersion) {
        String latest = AppPrefs.latestVersion(context);
        return !latest.isEmpty() && compareVersions(latest, currentVersion) > 0;
    }

    static void check(Context context, boolean force, String currentVersion, Callback callback) {
        if (!force && !shouldCheck(context)) {
            callback.onResult(hasCachedUpdate(context, currentVersion),
                    AppPrefs.latestVersion(context), AppPrefs.latestReleaseUrl(context),
                    AppPrefs.latestReleaseNotes(context));
            return;
        }
        Context appContext = context.getApplicationContext();
        new Thread(() -> {
            try {
                UpdateInfo info = fetchLatest();
                AppPrefs.saveUpdateInfo(appContext, info.version, info.url, info.notes);
                boolean available = !info.version.isEmpty()
                        && compareVersions(info.version, currentVersion) > 0;
                callback.onResult(available, info.version, info.url, info.notes);
            } catch (Exception error) {
                callback.onError(error.getMessage() == null ? "网络请求失败" : error.getMessage());
            }
        }, "update-check").start();
    }

    private static UpdateInfo fetchLatest() throws Exception {
        UpdateInfo release = fetchReleaseApi();
        if (release != null && !release.version.isEmpty()) return release;

        UpdateInfo tag = fetchTagApi();
        if (tag != null && !tag.version.isEmpty()) return tag;

        return fetchReleasesPage();
    }

    private static UpdateInfo fetchReleaseApi() throws Exception {
        HttpURLConnection connection = open(RELEASES_API, "application/vnd.github+json");
        try {
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_OK) {
                JSONArray releases = new JSONArray(readBody(connection));
                for (int i = 0; i < releases.length(); i++) {
                    JSONObject release = releases.optJSONObject(i);
                    if (release == null || release.optBoolean("draft", false)) continue;
                    String tag = cleanTag(release.optString("tag_name", ""));
                    String url = release.optString("html_url", RELEASES_PAGE);
                    String notes = release.optString("body", "");
                    return new UpdateInfo(tag, url, notes);
                }
            }
            return null;
        } finally {
            connection.disconnect();
        }
    }

    private static UpdateInfo fetchTagApi() throws Exception {
        HttpURLConnection connection = open(TAGS_API, "application/vnd.github+json");
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) return null;
            JSONArray tags = new JSONArray(readBody(connection));
            if (tags.length() == 0) return null;
            String tag = cleanTag(tags.optJSONObject(0).optString("name", ""));
            if (tag.isEmpty()) return null;
            return new UpdateInfo(tag, REPO_URL + "/releases/tag/" + tag,
                    "检测到 GitHub 新版本标签 " + tag + "，请前往发布页查看更新内容。");
        } finally {
            connection.disconnect();
        }
    }

    private static UpdateInfo fetchReleasesPage() throws Exception {
        HttpURLConnection connection = open(RELEASES_PAGE, "text/html");
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) return new UpdateInfo("", RELEASES_PAGE, "");
            String finalUrl = connection.getURL().toString();
            Matcher redirect = Pattern.compile("/releases/tag/([^/?#]+)").matcher(finalUrl);
            if (redirect.find()) {
                String tag = cleanTag(redirect.group(1));
                return new UpdateInfo(tag, finalUrl, "");
            }
            String html = readBody(connection);
            Matcher tagLink = Pattern.compile(
                    "/tangyuangungun/SCNU-Schedule/releases/tag/([^\"'?#]+)").matcher(html);
            if (tagLink.find()) {
                String tag = cleanTag(tagLink.group(1));
                return new UpdateInfo(tag, REPO_URL + "/releases/tag/" + tag, "");
            }
            return new UpdateInfo("", RELEASES_PAGE, "");
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection open(String url, String accept) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(8_000);
        connection.setReadTimeout(8_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", accept);
        connection.setRequestProperty("User-Agent", "SCNU-Schedule-App/1.4.2");
        connection.setRequestProperty("Cache-Control", "no-cache");
        return connection;
    }

    private static String readBody(HttpURLConnection connection) throws Exception {
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) body.append(line).append('\n');
        }
        return body.toString();
    }

    private static String cleanTag(String value) {
        return value == null ? "" : value.trim().replaceFirst("^[vV]", "");
    }

    static int compareVersions(String left, String right) {
        String[] leftParts = (left == null ? "" : left).split("\\.");
        String[] rightParts = (right == null ? "" : right).split("\\.");
        int count = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < count; i++) {
            int a = i < leftParts.length ? parseVersionPart(leftParts[i]) : 0;
            int b = i < rightParts.length ? parseVersionPart(rightParts[i]) : 0;
            if (a != b) return Integer.compare(a, b);
        }
        return 0;
    }

    private static int parseVersionPart(String value) {
        try {
            return Integer.parseInt(value.replaceAll("\\D", ""));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static final class UpdateInfo {
        final String version;
        final String url;
        final String notes;

        UpdateInfo(String version, String url, String notes) {
            this.version = version == null ? "" : version;
            this.url = url == null || url.isEmpty() ? RELEASES_PAGE : url;
            this.notes = notes == null ? "" : notes;
        }
    }
}



