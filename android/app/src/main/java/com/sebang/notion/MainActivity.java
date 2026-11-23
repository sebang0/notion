package com.sebang.notion;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/* ══════════════════════════════════════════════════════════════════════
   [업무 일정표 안드로이드 앱]
   - 웹앱(docs/index.html)을 앱 안에 그대로 넣어 WebView로 실행
   - 업무 데이터: 웹 버전과 같은 GitHub 저장소(data 브랜치 data.json, 암호문)
     → 비밀번호 입력 시 웹·앱 어디서 고쳐도 같은 내용
   - 웹 브라우저 기능 중 WebView에 없는 것을 앱이 대신 처리
       [1] 앱 내 https 주소 제공  : WebViewAssetLoader (Web Crypto 사용 조건 = 보안 주소)
       [2] 열쇠 파일 최신본       : key.json 요청 → GitHub Pages 최신본, 실패 시 앱 포함본
       [3] PDF 출력              : window.print() → 안드로이드 인쇄(PDF로 저장)
       [4] 백업 파일 내려받기     : 다운로드 링크 → 저장 위치 선택 창
       [5] 백업 파일 불러오기     : <input type=file> → 파일 선택 창
       [6] 알림·확인 창           : alert / confirm → 안드로이드 대화상자
       [7] 뒤로 가기              : 앱 종료 대신 백그라운드 이동 (화면 상태 유지)
   ══════════════════════════════════════════════════════════════════════ */
public class MainActivity extends Activity {

    /* ── 주소 설정 ── */
    private static final String APP_HOST  = "appassets.androidplatform.net";
    private static final String START_URL = "https://" + APP_HOST + "/assets/index.html";
    // 웹앱이 토큰 교체 시 갱신하는 열쇠 파일 (main 브랜치 docs/key.json → GitHub Pages 공개본)
    private static final String LIVE_KEY_URL = "https://sebang0.github.io/notion/key.json";

    private static final int REQ_FILE_CHOOSER = 1001;
    private static final int REQ_SAVE_FILE    = 1002;

    private WebView webView;
    private WebViewAssetLoader assetLoader;
    private ValueCallback<Uri[]> fileCallback;     // 파일 선택 창 결과 전달용
    private byte[] pendingSaveBytes;               // 저장 위치 선택 후 기록할 내용
    private final Handler ui = new Handler(Looper.getMainLooper());

    /* ──────────────────────────────────────────────────────────────
       [0] 화면 생성: WebView 설정 → 웹앱 열기
       ────────────────────────────────────────────────────────────── */
    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // [1] 앱 assets/ 를 https://appassets.androidplatform.net/assets/ 로 제공
        assetLoader = new WebViewAssetLoader.Builder()
                .setDomain(APP_HOST)
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView = new WebView(this);
        setContentView(webView);

        // 웹앱 동작에 필요한 설정: 스크립트 · localStorage(화면 설정·미전송 변경 임시 보관)
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);
        s.setMediaPlaybackRequiresUserGesture(true);

        webView.addJavascriptInterface(new Bridge(), "SebangApp");
        webView.setWebViewClient(new AppWebViewClient());
        webView.setWebChromeClient(new AppChromeClient());

        if (savedInstanceState != null) webView.restoreState(savedInstanceState);
        else webView.loadUrl(START_URL);
    }

    /* ──────────────────────────────────────────────────────────────
       [1][2] 요청 처리: 앱 내 파일 제공 · 열쇠 파일 최신본 · 외부 링크는 브라우저로
       ────────────────────────────────────────────────────────────── */
    private class AppWebViewClient extends WebViewClient {

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
            Uri u = req.getUrl();
            if (APP_HOST.equals(u.getHost()) && u.getPath() != null && u.getPath().endsWith("/key.json")) {
                WebResourceResponse live = fetchLiveKey();
                if (live != null) return live;            // 최신본 성공
                // 실패(오프라인 등) → 아래 assetLoader 가 앱 포함본 제공
            }
            return assetLoader.shouldInterceptRequest(u);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
            Uri u = req.getUrl();
            if (APP_HOST.equals(u.getHost())) return false;   // 앱 내부 화면
            try { startActivity(new Intent(Intent.ACTION_VIEW, u)); }   // 외부 링크 → 기본 브라우저
            catch (ActivityNotFoundException e) { toast("열 수 있는 앱이 없습니다."); }
            return true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            injectShims();
        }
    }

    /** [2] GitHub Pages의 최신 key.json (토큰 교체가 앱 재설치 없이 반영) · 5초 제한 */
    private WebResourceResponse fetchLiveKey() {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(LIVE_KEY_URL + "?t=" + System.currentTimeMillis()).openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(5000);
            c.setUseCaches(false);
            if (c.getResponseCode() != 200) return null;
            byte[] body = readAll(c.getInputStream());
            String text = new String(body, StandardCharsets.UTF_8);
            if (!text.contains("sebang-co-key")) return null;   // 형식 검증
            Map<String, String> h = new HashMap<>();
            h.put("Cache-Control", "no-store");
            return new WebResourceResponse("application/json", "utf-8", 200, "OK", h, new ByteArrayInputStream(body));
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** [3][4] 웹앱 원본은 고치지 않고, 페이지 로드 후 인쇄·다운로드 동작만 앱 기능으로 연결 */
    private void injectShims() {
        String js =
            "(function(){if(window.__sebangShim)return;window.__sebangShim=1;" +
            // [3] 인쇄: 용지 방향(@page 주입 스타일)과 문서 제목(=파일명)을 앱에 전달
            "window.print=function(){var st=document.getElementById('print-page-size');" +
            "var land=!!(st&&/landscape/.test(st.textContent));" +
            "SebangApp.print(document.title||'print',land);};" +
            // [4] 다운로드 링크(a.download) 클릭 → 내용을 base64로 앱에 전달
            "var oc=HTMLAnchorElement.prototype.click;" +
            "HTMLAnchorElement.prototype.click=function(){var a=this;" +
            "if(a.hasAttribute('download')&&/^(blob:|data:)/.test(a.href)){" +
            "fetch(a.href).then(function(r){return r.blob();}).then(function(b){" +
            "var fr=new FileReader();fr.onload=function(){var d=String(fr.result);" +
            "SebangApp.saveFile(a.getAttribute('download')||'download',b.type||'application/octet-stream',d.substring(d.indexOf(',')+1));};" +
            "fr.readAsDataURL(b);});return;}" +
            "return oc.apply(this,arguments);};})();";
        webView.evaluateJavascript(js, null);
    }

    /* ──────────────────────────────────────────────────────────────
       [3][4] 웹앱 → 앱 호출 창구 (JavaScript에서 SebangApp.xxx 로 호출)
       ────────────────────────────────────────────────────────────── */
    private class Bridge {
        @JavascriptInterface
        public void print(String title, boolean landscape) {
            ui.post(() -> startPrint(title, landscape));
        }

        @JavascriptInterface
        public void saveFile(String name, String mime, String base64) {
            ui.post(() -> {
                pendingSaveBytes = Base64.decode(base64, Base64.DEFAULT);
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType(mime);
                i.putExtra(Intent.EXTRA_TITLE, name);
                try { startActivityForResult(i, REQ_SAVE_FILE); }
                catch (ActivityNotFoundException e) { toast("저장 창을 열 수 없습니다."); }
            });
        }
    }

    /** [3] 안드로이드 인쇄 창 열기 (대상: PDF로 저장) · 끝나면 웹앱에 afterprint 알림 → 화면 원상 복구 */
    private void startPrint(String title, boolean landscape) {
        PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
        final PrintDocumentAdapter base = webView.createPrintDocumentAdapter(title);
        PrintDocumentAdapter wrapped = new PrintDocumentAdapter() {
            @Override public void onStart() { base.onStart(); }
            @Override public void onLayout(PrintAttributes o, PrintAttributes n, CancellationSignal c,
                                           LayoutResultCallback cb, Bundle extras) { base.onLayout(o, n, c, cb, extras); }
            @Override public void onWrite(PageRange[] p, ParcelFileDescriptor d, CancellationSignal c,
                                          WriteResultCallback cb) { base.onWrite(p, d, c, cb); }
            @Override public void onFinish() {
                base.onFinish();
                webView.evaluateJavascript("window.dispatchEvent(new Event('afterprint'))", null);
            }
        };
        PrintAttributes attrs = new PrintAttributes.Builder()
                .setMediaSize(landscape ? PrintAttributes.MediaSize.ISO_A4.asLandscape()
                                        : PrintAttributes.MediaSize.ISO_A4.asPortrait())
                .build();
        pm.print(title, wrapped, attrs);
    }

    /* ──────────────────────────────────────────────────────────────
       [5][6] 파일 선택 창 · alert/confirm 대화상자
       ────────────────────────────────────────────────────────────── */
    private class AppChromeClient extends WebChromeClient {
        @Override
        public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = cb;
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");   // .json 백업 파일 (기기별 MIME 표기가 달라 전체 허용)
            try { startActivityForResult(i, REQ_FILE_CHOOSER); }
            catch (ActivityNotFoundException e) { fileCallback = null; toast("파일 선택 창을 열 수 없습니다."); return false; }
            return true;
        }

        @Override
        public boolean onJsAlert(WebView v, String url, String msg, android.webkit.JsResult r) {
            new AlertDialog.Builder(MainActivity.this).setMessage(msg).setCancelable(false)
                    .setPositiveButton("확인", (d, w) -> r.confirm()).show();
            return true;
        }

        @Override
        public boolean onJsConfirm(WebView v, String url, String msg, android.webkit.JsResult r) {
            new AlertDialog.Builder(MainActivity.this).setMessage(msg).setCancelable(false)
                    .setPositiveButton("확인", (d, w) -> r.confirm())
                    .setNegativeButton("취소", (d, w) -> r.cancel()).show();
            return true;
        }
    }

    /** 파일 선택·저장 창 결과 처리 */
    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_FILE_CHOOSER) {
            Uri[] r = (res == RESULT_OK && data != null && data.getData() != null) ? new Uri[]{data.getData()} : null;
            if (fileCallback != null) fileCallback.onReceiveValue(r);
            fileCallback = null;
        } else if (req == REQ_SAVE_FILE) {
            if (res == RESULT_OK && data != null && data.getData() != null && pendingSaveBytes != null) {
                try (OutputStream os = getContentResolver().openOutputStream(data.getData())) {
                    os.write(pendingSaveBytes);
                    toast("저장했습니다.");
                } catch (Exception e) {
                    toast("저장 실패: " + e.getMessage());
                }
            }
            pendingSaveBytes = null;
        }
    }

    /* ──────────────────────────────────────────────────────────────
       [7] 생명주기: 뒤로 가기 · 화면 회전 상태 보존 · 백그라운드 전환
       ────────────────────────────────────────────────────────────── */
    @Override
    public void onBackPressed() {
        // 웹앱은 한 화면 구성 → 종료하지 않고 뒤로 보내 입력 중 내용·잠금 해제 상태 유지
        moveTaskToBack(true);
    }

    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); webView.saveState(out); }
    @Override protected void onResume()  { super.onResume();  webView.onResume(); }   // 복귀 시 웹앱이 서버 최신 내용 반영
    @Override protected void onPause()   { webView.onPause(); super.onPause(); }
    @Override protected void onDestroy() { webView.destroy(); super.onDestroy(); }

    /* ── 공통 도구 ── */
    private static byte[] readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        return bo.toByteArray();
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
}
