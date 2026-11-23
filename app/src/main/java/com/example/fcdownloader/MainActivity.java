package com.example.fcdownloader;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.database.Cursor;
import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputType;
import android.util.LruCache;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.WebResourceError;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.app.AlertDialog;
import android.content.res.AssetFileDescriptor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    static final String DEFAULT_PATTERN =
            "/(v|f|d|e|file|files|video|videos|watch|embed|play|stream)/|\\.(mp4|webm|mkv|mov|m4v)(\\?|$)";
    static final Pattern MEDIA = Pattern.compile("\\.(mp4|webm|mkv|mov|m4v|flv|3gp|m3u8)(\\?|#|$)", Pattern.CASE_INSENSITIVE);
    // link tanpa ekstensi yang jelas-jelas video (mis. ...?mime=video/mp4)
    static final Pattern VIDEO_HINT = Pattern.compile("[?&](mime|type|content_?type)=video", Pattern.CASE_INSENSITIVE);
    static final Pattern URL_IN_TEXT = Pattern.compile("https?://[^\\s<>\"]+", Pattern.CASE_INSENSITIVE);
    static final int MAX_SNIFF = 30;
    static final Pattern SEGMENT = Pattern.compile("\\.(ts|m4s|aac)(\\?|$)", Pattern.CASE_INSENSITIVE);
    static final Pattern SKIP = Pattern.compile(
            "(login|register|signup|logout|premium|upgrade|contact|terms|privacy|dmca|faq|about)", Pattern.CASE_INSENSITIVE);
    static final Pattern AD = Pattern.compile(
            "(doubleclick|googlesyndication|googleadservices|adservice|imasdk|adserver|adsystem|adnxs|"
            + "/ads?[/_.?=-]|[/_.-]ads?[/_.?=-]|//(ads?|pop\\w*|track\\w*|click\\w*)\\.|"
            + "vast|vpaid|preroll|pre-roll|popunder|popads|propeller|adsterra|exoclick|exosrv|juicyads|"
            + "trafficjunky|trafficstars|tsyndicate|hilltopads|clickadu|adcash|a-ads|adskeeper|mgid|"
            + "taboola|outbrain|clickunder|prebid|monetag|onclicka|richads|galaksion|admaven|bidvertiser)",
            Pattern.CASE_INSENSITIVE);
    static final Pattern FOLDER_KEYWORD = Pattern.compile(
            "/(folder|folders|dir|directory|album|albums|collection|collections)(/|\\?|$)", Pattern.CASE_INSENSITIVE);
    static final Pattern BACK_TEXT = Pattern.compile(
            "^(\\.\\.|kembali|back|up|parent|home|beranda|induk)$", Pattern.CASE_INSENSITIVE);
    static final int MAX_SCAN_PAGES = 400, MAX_DEPTH = 12;
    static final Pattern DIRECT = Pattern.compile("\\.(mp4|webm|mkv|mov|m4v|flv|3gp|m3u8)$", Pattern.CASE_INSENSITIVE);
    static final long CAPTURE_TIMEOUT_MS = 60000;
    static final long SETTLE_MS = 10000;          // tunggu sejak kandidat terakhir muncul
    static final double AD_MAX_DURATION = 45;     // video < 45 detik dianggap iklan
    static final long AD_MAX_SIZE = 1500000;      // file < 1,5 MB dianggap iklan

    static final String JS_LINKS =
            "(function(){var a=document.querySelectorAll('a[href]');var r=[];" +
            "var navSel='nav,header,footer,[class*=breadcrumb i],[id*=breadcrumb i],[class*=crumb i]';" +
            "for(var i=0;i<a.length;i++){var nv=0;try{nv=a[i].closest(navSel)?1:0;}catch(e){}" +
            "r.push([a[i].href,((a[i].innerText||a[i].title||'')+'').trim().slice(0,100),nv]);}" +
            "return r;})()";
    static final String JS_SCROLL =
            "(function(){window.scrollTo(0,document.body.scrollHeight);return document.body.scrollHeight;})()";
    static final String JS_POLL =
            "(function(){var out={media:[],iframes:[],poster:''};" +
            "function findPoster(doc){try{var v=doc.querySelectorAll('video[poster]');for(var i=0;i<v.length;i++){if(v[i].poster&&v[i].poster.indexOf('http')===0)return v[i].poster;}" +
            "var og=doc.querySelector('meta[property=\"og:image\"],meta[name=\"twitter:image\"]');if(og&&og.content&&og.content.indexOf('http')===0)return og.content;" +
            "var im=doc.querySelectorAll('img[src]');for(var j=0;j<im.length;j++){var r=im[j].getBoundingClientRect();if(r.width>=120&&r.height>=68&&im[j].src.indexOf('http')===0)return im[j].src;}}catch(e){}return '';}" +
            "function scan(doc){try{if(!out.poster)out.poster=findPoster(doc);var v=doc.querySelectorAll('video, video source');" +
            "for(var i=0;i<v.length;i++){var s=v[i].currentSrc||v[i].src;var d=0,rs=0;" +
            "try{var vv=v[i].tagName==='SOURCE'?v[i].parentNode:v[i];d=isFinite(vv.duration)?vv.duration:0;rs=vv.readyState||0;if(!out.poster&&vv.poster&&vv.poster.indexOf('http')===0)out.poster=vv.poster;}catch(e){}" +
            "if(s&&s.indexOf('http')===0)out.media.push([s,d,rs]);}" +
            "var f=doc.querySelectorAll('iframe');" +
            "for(var j=0;j<f.length;j++){if(f[j].src&&f[j].src.indexOf('http')===0)out.iframes.push(f[j].src);" +
            "try{if(f[j].contentDocument)scan(f[j].contentDocument);}catch(e){}}}catch(e){}}" +
            "scan(document);return out;})()";
    static final String JS_PLAY =
            "(function(){var skips=['[class*=skip i]','[id*=skip i]','[aria-label*=skip i]','[class*=ad-close i]'];" +
            "function go(doc){try{" +
            "for(var a=0;a<skips.length;a++){var ss=doc.querySelectorAll(skips[a]);" +
            "for(var b=0;b<ss.length;b++){try{ss[b].click();}catch(e){}}}" +
            "var v=doc.querySelectorAll('video');for(var k=0;k<v.length;k++){v[k].muted=true;" +
            "var du=v[k].duration;if(isFinite(du)&&du>0&&du<45&&v[k].currentTime<du-0.5){" +
            "try{v[k].currentTime=du-0.1;}catch(e){}}" +
            "if(v[k].paused){var p=v[k].play();if(p&&p.catch)p.catch(function(){});}}" +
            "var f=doc.querySelectorAll('iframe');for(var j=0;j<f.length;j++){" +
            "try{if(f[j].contentDocument)go(f[j].contentDocument);}catch(e){}}}catch(e){}}" +
            "go(document);return true;})()";
    // cari posisi tombol play (koordinat CSS di layar) untuk diketuk seperti jari sungguhan
    static final String JS_FIND_PLAY =
            "(function(){var sels=['.vjs-big-play-button','.plyr__control--overlaid','.jw-display-icon-container'," +
            "'.jw-icon-display','[aria-label*=play i]','[title*=play i]','button[class*=play i]','video','iframe'," +
            "'[class*=play i]','[id*=play i]'];" +
            "var pl=document.querySelector('video, iframe, [class*=player i]');" +
            "if(pl&&pl.scrollIntoView){try{pl.scrollIntoView({block:'center'});}catch(e){}}" +
            "function ok(r){return r.width>8&&r.height>8&&r.bottom>0&&r.right>0;}" +
            "function find(doc,ox,oy){for(var i=0;i<sels.length;i++){var els=doc.querySelectorAll(sels[i]);" +
            "for(var j=0;j<els.length;j++){var el=els[j];var r=el.getBoundingClientRect();if(!ok(r))continue;" +
            "if(el.tagName==='IFRAME'){var d=null;try{d=el.contentDocument;}catch(e){}" +
            "if(d){var inner=find(d,ox+r.left,oy+r.top);if(inner)return inner;}}" +
            "return [ox+r.left+r.width/2,oy+r.top+r.height/2];}}return null;}" +
            "var c=find(document,0,0)||[window.innerWidth/2,window.innerHeight/2];" +
            "return [c[0],c[1],window.innerWidth,window.innerHeight];})()";

    static final String[] SAVE_MODES = {
            "Folder di dalam folder (ikuti struktur subfolder)",
            "Hanya satu folder (semua video jadi satu)",
            "Tanpa folder (langsung di folder utama)"};
    static final String[] BASE_NAMES = {"Download", "Movies", "DCIM"};
    static final String[] BASE_DIRS = {Environment.DIRECTORY_DOWNLOADS, Environment.DIRECTORY_MOVIES,
            Environment.DIRECTORY_DCIM};

    enum Mode { IDLE, SCAN, CAPTURE, DOWNLOAD }

    /**
     * WebView yang tidak merebut fokus dan tidak meminta layar menggulung ke dirinya.
     * Setiap halaman baru biasanya meminta fokus, dan ScrollView induknya lalu menggulung
     * ke WebView itu: itulah yang membuat layar "loncat" tiap berganti video.
     */
    static class QuietWebView extends WebView {
        volatile boolean quiet = true;

        QuietWebView(Context c) {
            super(c);
        }

        @Override
        public boolean requestFocus(int direction, Rect previouslyFocusedRect) {
            return quiet ? false : super.requestFocus(direction, previouslyFocusedRect);
        }

        @Override
        public boolean requestRectangleOnScreen(Rect rectangle, boolean immediate) {
            return quiet ? false : super.requestRectangleOnScreen(rectangle, immediate);
        }
    }

    final Handler h = new Handler(Looper.getMainLooper());
    EditText urlInput, patternInput;
    TextView mainBtn, secBtn, darkBtn, pasteBtn, copyLogBtn, previewToggle;
    TextView statusText, settingsSummary, settingsChevron, destPreview, tabVideos, tabLog, emptyList;
    TextView statFolders, statVideos, statDone, statFail;
    View statusDot;
    LinearLayout settingsBody, listBox, logPane;
    FrameLayout webFrame;
    RingView ring;
    boolean previewOpen;
    static final int ST_WAIT = 0, ST_SEARCH = 1, ST_QUEUED = 2, ST_DL = 3, ST_DONE = 4, ST_FAIL = 5, ST_READY = 6;
    static final String[] PAR_NAMES = {"1 (satu per satu)", "2 sekaligus", "3 sekaligus", "5 sekaligus"};
    static final int[] PAR_VALUES = {1, 2, 3, 5};
    int[] vState = new int[0], vPct = new int[0];
    String[] vNote = new String[0];
    final List<TextView> rowChips = new ArrayList<>(), rowNotes = new ArrayList<>();
    final Map<Long, Integer> dlMap = new HashMap<>();
    boolean tracking;
    CheckBox keepBox, fastBox, autoBox;
    Spinner parSpin, colSpin;
    TextView deviceInfo, colHint, recBtn;
    LinearLayout workerBox;
    DeviceProfile profile;
    CheckBox darkWebBox;
    TextView webCover, stageCover;
    FrameLayout stage;
    QuietWebView quietMain;
    static final int MAX_COLLECT = 6;   // batas pilihan di pengaturan (6 diberi peringatan)
    static final int REC_MAX = 4;       // batas atas untuk angka rekomendasi otomatis
    final ArrayDeque<Integer> dlQueue = new ArrayDeque<>();
    String[] rUrl, rRef, rName;   // link unduhan yang sudah terkumpul (tahap 1)
    TextView singleBtn;
    TextView batteryStatus, batteryBtn;
    String lastNotif = "";
    boolean loadingPrefs = true;
    TextView darkOverlay;
    boolean paused;
    CheckBox adBox, subBox, dnsBox, askDelWatchedBox;
    Spinner saveSpin;
    EditText folderPatternInput;
    Spinner baseSpin;
    EditText folderInput;
    SharedPreferences prefs;
    volatile boolean adBlock = true;
    final ExecutorService bg = Executors.newFixedThreadPool(6);
    TextView logView;
    WebView web;

    // riwayat link
    static final int MAX_HISTORY = 50;
    LinearLayout historyPane, historyBox;
    TextView tabHistory, clearHistoryBtn;
    ScrollView mainScroll;
    String historyUrl = "";
    boolean clearArmed;
    int subfolderCount;
    String scanTitle, scanUrl;

    Mode mode = Mode.IDLE;
    String baseDomain = "", folderPath = "";
    Pattern linkPattern;
    final Set<String> allowedHosts = new HashSet<>();

    // scan
    final ArrayDeque<String[]> pageQueue = new ArrayDeque<>();   // {url, relPath, depth}
    final Set<String> seenPages = new HashSet<>();
    final LinkedHashMap<String, String[]> found = new LinkedHashMap<>(); // url -> {text, relPath}
    final LinkedHashMap<String, String> allLinks = new LinkedHashMap<>();
    String[] curPage;
    String folderPrefix = "";
    Pattern folderPattern;
    int scanSeq = 0, foldersScanned = 0;

    // capture: beberapa proses sekaligus, tiap proses punya WebView dan state sendiri
    final List<String[]> videos = new ArrayList<>();
    final List<Worker> workers = new ArrayList<>();
    int nextIdx, capTotal, capLimit, ok, fail;
    long lastTrimAt;

    // unduhan stream m3u8 yang sedang berjalan: indeks video -> tanda batal
    final Map<Integer, AtomicBoolean> hlsJobs = new HashMap<>();
    final ExecutorService dlPool = Executors.newCachedThreadPool();

    // mode jelajah ala IDM: halaman dibuka di pratinjau, semua video yang dimuat ikut terdeteksi
    boolean sniffing;
    int sniffSeq;
    String sniffPage = "";
    final LinkedHashMap<String, String[]> sniffed = new LinkedHashMap<>();   // url -> {jenis, ukuran, sudah ditambah}
    LinearLayout prevCard, sniffCard, sniffBox;
    TextView sniffTitle, browseBtn, sniffCloseBtn;

    // pemutar: file hasil unduhan yang sudah selesai (indeks video -> uri) + daftar video di HP
    final Map<Integer, String> doneUri = new HashMap<>();
    final Map<Integer, Long> rSizes = new HashMap<>();   // ukuran link yang sudah terkumpul (sebelum diunduh)
    final Map<Integer, Long> rDurs = new HashMap<>();    // durasi video yang sudah terkumpul (milidetik)
    final Map<Integer, String> rPoster = new HashMap<>(); // url gambar poster dari halaman web
    final Set<Integer> dlSelected = new HashSet<>();     // indeks video yang dicentang untuk diunduh manual
    final List<LinearLayout> rowCards = new ArrayList<>();
    final List<ImageView> rowThumbs = new ArrayList<>();
    final List<TextView> rowChecks = new ArrayList<>();
    final List<TextView> rowSizeBadges = new ArrayList<>();
    final List<TextView> rowDurBadges = new ArrayList<>();
    LinearLayout dlSelBar;
    TextView dlSelInfo, dlSelAllBtn, dlSelNoneBtn, dlSelGoBtn;
    String sniffPoster = "";
    static final int MAX_LIBRARY = 150;
    static final int REQ_OPEN_FILE = 5;
    static final int MAX_DOWNLOADS = 500;
    LinearLayout libraryPane, libBox, libSelBar;
    TextView tabPlayer, libViewBtn, libOpenBtn, libSelectBtn, libCleanBtn, libSelCount, libSelAllBtn, libSelDelBtn, libSelCancelBtn;
    final List<String[]> libItems = new ArrayList<>();   // {uri, nama, info}
    final Set<String> libSelected = new HashSet<>();
    int libSeq;
    boolean libGrid = true, libSelectMode = false;
    final LruCache<String, Bitmap> thumbs = new LruCache<String, Bitmap>(16 * 1024 * 1024) {
        @Override
        protected int sizeOf(String k, Bitmap b) {
            return b.getByteCount();
        }
    };

    // kunci PIN. decoy = PIN salah: aplikasi tampil seperti baru dipasang (riwayat & daftar video terpisah)
    static final int PIN_UNLOCK = 0, PIN_NEW = 1, PIN_CONFIRM = 2, PIN_OLD_CHANGE = 3, PIN_OLD_REMOVE = 4;
    static final long RELOCK_AFTER_MS = 2 * 60 * 1000;
    FrameLayout lockLayer;
    TextView lockTitle, lockHint, lockDots, lockSkip, pinInfo, pinSetBtn, pinOffBtn;
    final StringBuilder pinBuf = new StringBuilder();
    int pinStage;
    String pinFirst;
    boolean decoy, locked;
    long stoppedAt;

    // tampilan baru: empat halaman dengan navigasi bawah futuristik
    static final int PAGE_HOME = 0, PAGE_VIDEOS = 1, PAGE_HISTORY = 2, PAGE_MORE = 3;
    LinearLayout pageHome, pageVideos, pageHistory, pageMore, progressCard, listCard, recentCard, recentBox, advancedBody;
    FuturisticNavBar navBar;
    TextView navHome, navHistory, navMore, recentEmpty, logChevron;
    int currentPage, sniffPolls;
    Spinner libFilterSpin, libSortSpin;
    final List<Integer> libView = new ArrayList<>();
    static final String[] LIB_FILTERS = {"Semua video", "Belum ditonton", "Sedang ditonton", "Sudah ditonton"};
    static final String[] LIB_SORTS = {"Terbaru diunduh", "Terakhir ditonton", "Nama (A-Z)", "Ukuran terbesar"};
    Intent pendingShare;

    // pembaruan aplikasi
    static final long UPDATE_CHECK_EVERY_MS = 12L * 60 * 60 * 1000;
    LinearLayout updateCard;
    TextView updateTitle, updateNotes, updateBtn, updateCheckBtn, versionInfo;
    CheckBox autoUpdateBox;
    Updater.Info pendingUpdate;
    boolean updChecking, updBusy, updReinstall, updFallbackTried;
    AtomicBoolean updCancel;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        AppLock.install(getApplication());
        buildUi();
        setupWebView();
        KeepAliveService.stopHook = () -> {
            if (mode == Mode.SCAN) stopScan();
            else if (mode == Mode.CAPTURE) stopCapture();
        };
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 2);
        }
        loadingPrefs = false;
        setStatus("Tempel link video, lalu tekan Cari video.", Ui.MUTED);
        buildRows();
        showTab(0);
        renderHistory();
        refreshUi();
        if (PinLock.isSet(this)) {
            AppLock.needLock = false;
            AppLock.unlocked = false;
            showLock(PIN_UNLOCK);
        } else if (!prefs.getBoolean("pinAsked", false)) {
            showLock(PIN_NEW);          // tawarkan membuat PIN pada pembukaan pertama (bisa dilewati)
        }
        updatePinUi();
        if (b == null) handleShare(getIntent());
        UpdateReceiver.listener = (okInstall, status, msg, raw) -> h.post(() -> onInstallResult(okInstall, status, msg, raw));
        // Pemeriksaan pembaruan otomatis paling sering 12 jam sekali (bisa dimatikan di Pengaturan).
        if (autoUpdateBox.isChecked()
                && System.currentTimeMillis() - prefs.getLong("updLast", 0) > UPDATE_CHECK_EVERY_MS) {
            checkUpdate(false);
        }
    }

    @Override
    protected void onNewIntent(Intent it) {
        super.onNewIntent(it);
        setIntent(it);
        handleShare(it);
    }

    /** Link yang dibagikan dari aplikasi lain (Chrome, YouTube, dsb.) langsung dibuka dan dideteksi videonya. */
    void handleShare(Intent it) {
        if (it == null || !Intent.ACTION_SEND.equals(it.getAction())) return;
        if (locked) {                       // diproses setelah PIN dimasukkan, agar masuk ke riwayat yang benar
            pendingShare = it;
            return;
        }
        CharSequence t = it.getCharSequenceExtra(Intent.EXTRA_TEXT);
        Matcher m = t == null ? null : URL_IN_TEXT.matcher(t);
        if (m == null || !m.find()) {
            setStatus("Teks yang dibagikan tidak berisi link.", Ui.AMBER);
            return;
        }
        urlInput.setText(m.group());
        if (mode == Mode.SCAN || mode == Mode.CAPTURE) {
            setStatus("Link diterima. Tunggu proses selesai, lalu tekan Buka & deteksi video.", Ui.AMBER);
            return;
        }
        startBrowse();
    }

    void buildUi() {
        prefs = getSharedPreferences("fc", MODE_PRIVATE);
        profile = DeviceProfile.read(this);
        getWindow().setStatusBarColor(Ui.ABYSS);
        getWindow().setNavigationBarColor(Ui.ABYSS);
        getWindow().setBackgroundDrawable(new ColorDrawable(Ui.ABYSS));
        final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT, WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

        // ===== empat halaman: Unduh, Video saya, Riwayat, Lainnya =====
        LinearLayout root = Ui.col(this);
        root.setPadding(dp(16), dp(14), dp(16), dp(24));
        pageHome = Ui.col(this);
        pageVideos = Ui.col(this);
        pageHistory = Ui.col(this);
        pageMore = Ui.col(this);
        root.addView(pageHome);
        root.addView(pageVideos);
        root.addView(pageHistory);
        root.addView(pageMore);

        // ---------- HALAMAN UNDUH ----------
        LinearLayout head = Ui.row(this);
        LinearLayout titles = Ui.col(this);
        TextView appName = Ui.text(this, "Filecoster", 28, Ui.TEXT, Ui.DISPLAY);
        appName.setLetterSpacing(0.03f);
        titles.addView(appName);
        titles.addView(Ui.text(this, "Unduh video dari link apa pun", 13, Ui.MUTED, Ui.BODY));
        head.addView(titles, Ui.weight(1));
        updateCheckBtn = Ui.ghost(this, "\u27F3 Perbarui");
        updateCheckBtn.setTextSize(12);
        updateCheckBtn.setPadding(dp(10), dp(7), dp(10), dp(7));
        head.addView(updateCheckBtn);
        darkBtn = Ui.ghost(this, "\u25D0 Gelap");
        darkBtn.setTextSize(12);
        darkBtn.setPadding(dp(10), dp(7), dp(10), dp(7));
        LinearLayout.LayoutParams dbl = new LinearLayout.LayoutParams(WRAP, WRAP);
        dbl.leftMargin = dp(6);
        head.addView(darkBtn, dbl);
        pageHome.addView(head);

        // kartu pembaruan (muncul jika ada versi baru di GitHub)
        updateCard = Ui.card(this, true);
        updateCard.setBackground(Ui.box(this, Ui.PANEL_HI, 14, Ui.ION));
        LinearLayout updHdr = Ui.row(this);
        updateTitle = Ui.title(this, "\u26A1 Versi baru tersedia");
        updateTitle.setTextColor(Ui.ION);
        updHdr.addView(updateTitle, Ui.weight(1));
        TextView updClose = Ui.ghost(this, "\u2715");
        updClose.setTextSize(12);
        updClose.setPadding(dp(8), dp(4), dp(8), dp(4));
        updClose.setOnClickListener(v -> updateCard.setVisibility(View.GONE));
        updHdr.addView(updClose);
        updateCard.addView(updHdr);
        updateNotes = Ui.text(this, "", 13, Ui.TEXT, Ui.BODY);
        updateNotes.setLineSpacing(0, 1.25f);
        updateNotes.setBackground(Ui.box(this, Ui.ABYSS, 10, Ui.LINE));
        updateNotes.setPadding(dp(12), dp(10), dp(12), dp(10));
        updateCard.addView(updateNotes, Ui.fill(this, 10));
        updateBtn = Ui.primary(this, "\u2B07  Unduh & pasang");
        updateCard.addView(updateBtn, Ui.fill(this, 12));
        updateCard.setVisibility(View.GONE);
        pageHome.addView(updateCard, Ui.fill(this, 12));

        // kartu utama: tempel link -> Cari video
        LinearLayout linkCard = Ui.card(this, true);
        linkCard.addView(Ui.title(this, "Tempel link video"));
        linkCard.addView(Ui.hint(this, "Salin link dari browser atau aplikasi lain, tempel di sini, lalu tekan Cari video."),
                Ui.fill(this, 2));
        LinearLayout lrow = Ui.row(this);
        urlInput = Ui.input(this, "https://...");
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        // jika PIN aktif, link terakhir baru diisi setelah PIN dimasukkan (lihat applyProfile)
        urlInput.setText(PinLock.isSet(this) ? "" : prefs.getString("url", ""));
        lrow.addView(urlInput, Ui.weight(1));
        pasteBtn = Ui.ghost(this, "Tempel");
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(WRAP, WRAP);
        pl.leftMargin = dp(8);
        lrow.addView(pasteBtn, pl);
        linkCard.addView(lrow, Ui.fill(this, 12));
        browseBtn = Ui.primary(this, "\u26A1  Cari video");
        linkCard.addView(browseBtn, Ui.fill(this, 14));
        mainBtn = Ui.primary(this, "\u2B07  Unduh semua video");
        linkCard.addView(mainBtn, Ui.fill(this, 8));
        secBtn = Ui.ghost(this, "\u27F3  Pindai ulang");
        linkCard.addView(secBtn, Ui.fill(this, 8));
        singleBtn = Ui.ghost(this, "Unduh langsung (satu video)");
        singleBtn.setVisibility(View.GONE);         // digantikan oleh Cari video
        linkCard.addView(singleBtn, Ui.fill(this, 8));
        // baris status
        LinearLayout srow = Ui.row(this);
        statusDot = new View(this);
        srow.addView(statusDot, new LinearLayout.LayoutParams(dp(8), dp(8)));
        statusText = Ui.text(this, "", 14, Ui.TEXT, Ui.BODY);
        statusText.setPadding(dp(10), 0, 0, 0);
        srow.addView(statusText, Ui.weight(1));
        linkCard.addView(srow, Ui.fill(this, 14));
        pageHome.addView(linkCard, Ui.fill(this, 14));

        // tab History: berisi Terakhir dibuka & Riwayat link
        pageHistory.addView(Ui.text(this, "History", 26, Ui.TEXT, Ui.DISPLAY));
        recentCard = Ui.card(this, false);
        recentCard.addView(Ui.title(this, "Terakhir dibuka"));
        recentEmpty = Ui.hint(this, "Belum ada link yang dibuka.");
        recentEmpty.setPadding(0, dp(6), 0, 0);
        recentCard.addView(recentEmpty);
        recentBox = Ui.col(this);
        recentCard.addView(recentBox, Ui.fill(this, 6));
        pageHistory.addView(recentCard, Ui.fill(this, 14));

        // riwayat link
        LinearLayout hist = Ui.card(this, false);
        tabHistory = Ui.title(this, "Riwayat");
        hist.addView(tabHistory);
        historyPane = Ui.col(this);
        clearHistoryBtn = Ui.ghost(this, "Hapus semua riwayat");
        clearHistoryBtn.setTextSize(13);
        LinearLayout.LayoutParams chl = new LinearLayout.LayoutParams(WRAP, WRAP);
        chl.gravity = Gravity.END;
        historyPane.addView(clearHistoryBtn, chl);
        historyBox = Ui.col(this);
        historyPane.addView(historyBox, Ui.fill(this, 8));
        hist.addView(historyPane, Ui.fill(this, 6));
        pageHistory.addView(hist, Ui.fill(this, 12));

        // kemajuan (hanya saat bekerja / ada daftar)
        progressCard = Ui.card(this, true);
        progressCard.setBackground(Ui.box(this, Ui.PANEL, 14, Ui.ION_DEEP));
        progressCard.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView telHdr = Ui.text(this, "\u26A1 TELEMETRI UNDUHAN", 11.5f, Ui.ION, Ui.DISPLAY);
        telHdr.setLetterSpacing(0.12f);
        telHdr.setPadding(0, 0, 0, dp(6));
        progressCard.addView(telHdr);
        ring = new RingView(this);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(WRAP, WRAP);
        rp.gravity = Gravity.CENTER_HORIZONTAL;
        progressCard.addView(ring, rp);
        LinearLayout stats = Ui.row(this);
        statFolders = stat(stats, "folder");
        statVideos = stat(stats, "video");
        statDone = stat(stats, "selesai");
        statFail = stat(stats, "gagal");
        progressCard.addView(stats, Ui.fill(this, 12));
        progressCard.setVisibility(View.GONE);
        pageHome.addView(progressCard, Ui.fill(this, 12));

        // halaman web (browser dalam aplikasi)
        LinearLayout prev = Ui.card(this, false);
        LinearLayout phead = Ui.row(this);
        LinearLayout pt = Ui.col(this);
        pt.addView(Ui.title(this, "Halaman web"));
        pt.addView(Ui.hint(this, "Video di halaman ini dicari otomatis. Jika belum ketemu, tekan tombol play pada videonya."),
                Ui.fill(this, 2));
        phead.addView(pt, Ui.weight(1));
        previewToggle = Ui.ghost(this, "Tutup");
        previewToggle.setTextSize(13);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(WRAP, WRAP);
        tl.leftMargin = dp(10);
        phead.addView(previewToggle, tl);
        prev.addView(phead);
        webFrame = new FrameLayout(this);
        webFrame.setBackground(Ui.box(this, Color.BLACK, 10, Ui.LINE));
        webFrame.setPadding(dp(1), dp(1), dp(1), dp(1));
        webFrame.setClipToOutline(true);
        quietMain = new QuietWebView(this);
        quietMain.quiet = false;
        web = quietMain;
        web.setBackgroundColor(Color.BLACK);
        webFrame.addView(web, new FrameLayout.LayoutParams(MATCH, MATCH));
        webCover = darkCover();
        webCover.setVisibility(View.GONE);
        webFrame.addView(webCover, new FrameLayout.LayoutParams(MATCH, MATCH));
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(MATCH, dp(240));
        wl.topMargin = dp(12);
        prev.addView(webFrame, wl);
        workerBox = Ui.col(this);
        workerBox.setVisibility(View.GONE);
        prev.addView(workerBox, Ui.fill(this, 8));
        stage = new FrameLayout(this);
        stageCover = darkCover();
        prevCard = prev;
        prev.setVisibility(View.GONE);
        pageHome.addView(prev, Ui.fill(this, 12));

        // video yang ditemukan di halaman (mode IDM)
        sniffCard = Ui.card(this, true);
        LinearLayout sh = Ui.row(this);
        LinearLayout stc = Ui.col(this);
        sniffTitle = Ui.title(this, "Video ditemukan");
        stc.addView(sniffTitle);
        stc.addView(Ui.hint(this, "Tekan Unduh untuk menyimpan, atau Putar untuk menonton tanpa mengunduh."), Ui.fill(this, 2));
        sh.addView(stc, Ui.weight(1));
        sniffCloseBtn = Ui.ghost(this, "Selesai");
        sniffCloseBtn.setTextSize(13);
        LinearLayout.LayoutParams scl = new LinearLayout.LayoutParams(WRAP, WRAP);
        scl.leftMargin = dp(10);
        sh.addView(sniffCloseBtn, scl);
        sniffCard.addView(sh);
        sniffBox = Ui.col(this);
        sniffCard.addView(sniffBox, Ui.fill(this, 8));
        sniffCard.setVisibility(View.GONE);
        pageHome.addView(sniffCard, Ui.fill(this, 12));

        // daftar unduhan
        listCard = Ui.col(this);
        tabVideos = Ui.text(this, "Daftar video & link", 17, Ui.TEXT, Ui.DISPLAY);
        listCard.addView(tabVideos);
        listCard.addView(Ui.hint(this, "Centang video yang ingin diunduh setelah link, ukuran & thumbnail muncul. Ketuk thumbnail untuk pratinjau."), Ui.fill(this, 2));
        dlSelBar = Ui.row(this);
        dlSelBar.setBackground(Ui.box(this, Ui.PANEL_HI, 10, Ui.ION));
        dlSelBar.setPadding(dp(12), dp(8), dp(10), dp(8));
        dlSelBar.setVisibility(View.GONE);
        dlSelInfo = Ui.text(this, "0 dipilih", 13, Ui.ION, Ui.MEDIUM);
        dlSelBar.addView(dlSelInfo, Ui.weight(1));
        dlSelAllBtn = Ui.ghost(this, "Pilih semua");
        dlSelAllBtn.setTextSize(12);
        dlSelAllBtn.setPadding(dp(10), dp(6), dp(10), dp(6));
        dlSelAllBtn.setOnClickListener(v -> selectAllReady(true));
        dlSelBar.addView(dlSelAllBtn);
        dlSelNoneBtn = Ui.ghost(this, "Reset");
        dlSelNoneBtn.setTextSize(12);
        dlSelNoneBtn.setPadding(dp(10), dp(6), dp(10), dp(6));
        LinearLayout.LayoutParams dsnLp = new LinearLayout.LayoutParams(WRAP, WRAP);
        dsnLp.leftMargin = dp(6);
        dlSelNoneBtn.setOnClickListener(v -> selectAllReady(false));
        dlSelBar.addView(dlSelNoneBtn, dsnLp);
        dlSelGoBtn = Ui.text(this, "\u2B07 Unduh", 12, Ui.ABYSS, Ui.DISPLAY);
        dlSelGoBtn.setBackground(Ui.box(this, Ui.ION, 8, 0));
        dlSelGoBtn.setPadding(dp(10), dp(6), dp(10), dp(6));
        dlSelGoBtn.setClickable(true);
        dlSelGoBtn.setVisibility(View.GONE);
        LinearLayout.LayoutParams dsgLp = new LinearLayout.LayoutParams(WRAP, WRAP);
        dsgLp.leftMargin = dp(6);
        dlSelGoBtn.setOnClickListener(v -> startDownloads());
        dlSelBar.addView(dlSelGoBtn, dsgLp);
        listCard.addView(dlSelBar, Ui.fill(this, 10));
        listBox = Ui.col(this);
        emptyList = Ui.hint(this, "Belum ada video.");
        emptyList.setPadding(dp(4), dp(8), dp(4), dp(8));
        listCard.addView(listBox, Ui.fill(this, 8));
        listCard.setVisibility(View.GONE);
        pageHome.addView(listCard, Ui.fill(this, 16));

        // ---------- HALAMAN VIDEO SAYA ----------
        pageVideos.addView(Ui.text(this, "Video saya", 26, Ui.TEXT, Ui.DISPLAY));
        pageVideos.addView(Ui.hint(this, "Video yang sudah kamu unduh. Ketuk untuk memutar, tekan lama untuk pilihan lain."),
                Ui.fill(this, 2));
        libraryPane = Ui.col(this);
        LinearLayout fsrow = Ui.row(this);
        libFilterSpin = Ui.spinner(this, LIB_FILTERS);
        libFilterSpin.setSelection(Math.max(0, Math.min(LIB_FILTERS.length - 1, prefs.getInt("libFilter", 0))));
        fsrow.addView(libFilterSpin, Ui.weight(1));
        libSortSpin = Ui.spinner(this, LIB_SORTS);
        libSortSpin.setSelection(Math.max(0, Math.min(LIB_SORTS.length - 1, prefs.getInt("libSort", 0))));
        LinearLayout.LayoutParams ssl = Ui.weight(1);
        ssl.leftMargin = dp(8);
        fsrow.addView(libSortSpin, ssl);
        libraryPane.addView(fsrow, Ui.fill(this, 12));
        LinearLayout lbtn = Ui.row(this);
        libGrid = prefs.getInt("libView", 0) == 0;
        libViewBtn = Ui.ghost(this, "");
        libViewBtn.setTextSize(13);
        lbtn.addView(libViewBtn, Ui.weight(1));
        libOpenBtn = Ui.ghost(this, "Buka file ...");
        libOpenBtn.setTextSize(13);
        LinearLayout.LayoutParams lrl = Ui.weight(1);
        lrl.leftMargin = dp(6);
        lbtn.addView(libOpenBtn, lrl);
        libSelectBtn = Ui.ghost(this, "Pilih");
        libSelectBtn.setTextSize(13);
        LinearLayout.LayoutParams lsl = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lsl.leftMargin = dp(6);
        lbtn.addView(libSelectBtn, lsl);
        libraryPane.addView(lbtn, Ui.fill(this, 8));
        libCleanBtn = Ui.ghost(this, "Hapus video >90% ditonton");
        libCleanBtn.setTextSize(13);
        libCleanBtn.setTextColor(Ui.PULSE);
        libCleanBtn.setBackground(Ui.box(this, Ui.ABYSS, 10, Ui.PULSE));
        libCleanBtn.setVisibility(View.GONE);
        libraryPane.addView(libCleanBtn, Ui.fill(this, 8));
        libSelBar = Ui.row(this);
        libSelBar.setBackground(Ui.box(this, Ui.PANEL_HI, 10, Ui.ION));
        libSelBar.setPadding(dp(10), dp(8), dp(10), dp(8));
        libSelBar.setVisibility(View.GONE);
        libSelCount = Ui.text(this, "0 dipilih", 13, Ui.ION, Ui.MEDIUM);
        libSelBar.addView(libSelCount, Ui.weight(1));
        libSelAllBtn = Ui.ghost(this, "Semua");
        libSelAllBtn.setTextSize(12);
        libSelAllBtn.setPadding(dp(10), dp(6), dp(10), dp(6));
        libSelBar.addView(libSelAllBtn);
        libSelDelBtn = Ui.ghost(this, "Hapus");
        libSelDelBtn.setTextSize(12);
        libSelDelBtn.setTextColor(Ui.PULSE);
        libSelDelBtn.setBackground(Ui.box(this, Ui.ABYSS, 8, Ui.PULSE));
        libSelDelBtn.setPadding(dp(10), dp(6), dp(10), dp(6));
        LinearLayout.LayoutParams sdl = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sdl.leftMargin = dp(6);
        libSelBar.addView(libSelDelBtn, sdl);
        libSelCancelBtn = Ui.ghost(this, "✕");
        libSelCancelBtn.setTextSize(12);
        libSelCancelBtn.setPadding(dp(10), dp(6), dp(10), dp(6));
        LinearLayout.LayoutParams libSelCancelLp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        libSelCancelLp.leftMargin = dp(6);
        libSelBar.addView(libSelCancelBtn, libSelCancelLp);
        libraryPane.addView(libSelBar, Ui.fill(this, 8));
        libBox = Ui.col(this);
        libraryPane.addView(libBox, Ui.fill(this, 12));
        pageVideos.addView(libraryPane, Ui.fill(this, 4));

        // ---------- HALAMAN LAINNYA ----------
        pageMore.addView(Ui.text(this, "Lainnya", 26, Ui.TEXT, Ui.DISPLAY));

        // pengaturan dasar
        LinearLayout basic = Ui.card(this, false);
        basic.addView(Ui.title(this, "Pengaturan"));
        basic.addView(label("Simpan video ke"), Ui.fill(this, 14));
        LinearLayout frow = Ui.row(this);
        baseSpin = Ui.spinner(this, BASE_NAMES);
        baseSpin.setSelection(Math.max(0, Math.min(BASE_NAMES.length - 1, prefs.getInt("base", 0))));
        frow.addView(baseSpin, Ui.weight(1));
        TextView slash = Ui.text(this, "/", 18, Ui.MUTED, Ui.BODY);
        slash.setPadding(dp(10), 0, dp(10), 0);
        frow.addView(slash);
        folderInput = Ui.input(this, "nama folder");
        folderInput.setText(prefs.getString("folder", "Filecoster"));
        frow.addView(folderInput, Ui.weight(1.4f));
        basic.addView(frow, Ui.fill(this, 6));
        destPreview = Ui.hint(this, "");
        basic.addView(destPreview, Ui.fill(this, 6));
        basic.addView(label("Unduh berapa video sekaligus"), Ui.fill(this, 14));
        parSpin = Ui.spinner(this, parNames());
        parSpin.setSelection(Math.max(0, Math.min(PAR_VALUES.length - 1,
                prefs.getInt("par", indexOfPar(profile.recDownload)))));
        basic.addView(parSpin, Ui.fill(this, 6));
        keepBox = Ui.check(this, "Tetap berjalan saat layar dimatikan", prefs.getBoolean("keep", true));
        basic.addView(keepBox, Ui.fill(this, 14));
        LinearLayout brow = Ui.row(this);
        batteryStatus = Ui.hint(this, "");
        brow.addView(batteryStatus, Ui.weight(1));
        batteryBtn = Ui.ghost(this, "Izinkan");
        batteryBtn.setTextSize(13);
        brow.addView(batteryBtn);
        basic.addView(brow, Ui.fill(this, 4));
        adBox = Ui.check(this, "Blokir iklan", prefs.getBoolean("ads", true));
        adBlock = adBox.isChecked();
        basic.addView(adBox, Ui.fill(this, 10));
        dnsBox = Ui.check(this, "Buka situs yang diblokir provider (DNS Google 8.8.8.8)", prefs.getBoolean("dns", true));
        Net.enabled = dnsBox.isChecked();
        basic.addView(dnsBox, Ui.fill(this, 4));
        askDelWatchedBox = Ui.check(this, "Tanyakan hapus video setelah ditonton >90%",
                prefs.getBoolean("askdel90", true));
        basic.addView(askDelWatchedBox, Ui.fill(this, 4));
        pageMore.addView(basic, Ui.fill(this, 14));

        // keamanan
        LinearLayout sec = Ui.card(this, false);
        sec.addView(Ui.title(this, "Kunci PIN"));
        pinInfo = Ui.hint(this, "");
        sec.addView(pinInfo, Ui.fill(this, 6));
        LinearLayout pinRow = Ui.row(this);
        pinSetBtn = Ui.ghost(this, "Atur PIN");
        pinSetBtn.setTextSize(13);
        pinRow.addView(pinSetBtn, Ui.weight(1));
        pinOffBtn = Ui.ghost(this, "Matikan PIN");
        pinOffBtn.setTextSize(13);
        pinOffBtn.setTextColor(Ui.PULSE);
        LinearLayout.LayoutParams pol = Ui.weight(1);
        pol.leftMargin = dp(8);
        pinRow.addView(pinOffBtn, pol);
        sec.addView(pinRow, Ui.fill(this, 10));
        pageMore.addView(sec, Ui.fill(this, 12));

        // pembaruan aplikasi
        LinearLayout upd = Ui.card(this, false);
        upd.addView(Ui.title(this, "Pembaruan aplikasi"));
        boolean debugKey = Updater.installedWithDebugKey(this);
        versionInfo = Ui.hint(this, "Versi terpasang: " + BuildConfig.VERSION_NAME + (debugKey
                ? "\n\u26A0 Aplikasi ini masih memakai kunci lama (build 16 atau sebelumnya), jadi pembaruan dari dalam "
                + "aplikasi akan ditolak Android. Hapus aplikasi ini sekali, lalu pasang APK terbaru dari GitHub."
                : ""));
        if (debugKey) versionInfo.setTextColor(Ui.AMBER);
        upd.addView(versionInfo, Ui.fill(this, 6));
        autoUpdateBox = Ui.check(this, "Periksa pembaruan otomatis", prefs.getBoolean("autoupd", true));
        upd.addView(autoUpdateBox, Ui.fill(this, 6));
        pageMore.addView(upd, Ui.fill(this, 12));

        // pengaturan lanjutan (dilipat)
        LinearLayout adv = Ui.card(this, false);
        LinearLayout shead = Ui.row(this);
        LinearLayout st = Ui.col(this);
        st.addView(Ui.title(this, "Pengaturan lanjutan"));
        settingsSummary = Ui.hint(this, "");
        st.addView(settingsSummary, Ui.fill(this, 2));
        shead.addView(st, Ui.weight(1));
        settingsChevron = Ui.text(this, "\u25BE", 20, Ui.MUTED, Ui.BODY);
        settingsChevron.setPadding(dp(12), 0, dp(4), 0);
        shead.addView(settingsChevron);
        shead.setClickable(true);
        adv.addView(shead);
        settingsBody = Ui.col(this);
        settingsBody.setVisibility(View.GONE);
        advancedBody = settingsBody;

        settingsBody.addView(section("Unduh semua dari folder"), Ui.fill(this, 18));
        subBox = Ui.check(this, "Ikut ambil subfolder (folder di dalam folder)", prefs.getBoolean("sub", true));
        settingsBody.addView(subBox, Ui.fill(this, 4));
        autoBox = Ui.check(this, "Mulai unduh otomatis setelah semua link terkumpul (tanpa pilih manual)", prefs.getBoolean("auto", false));
        settingsBody.addView(autoBox, Ui.fill(this, 4));
        fastBox = Ui.check(this, "Ambil video begitu siap diputar (lebih cepat)", prefs.getBoolean("fast", true));
        settingsBody.addView(fastBox, Ui.fill(this, 4));
        settingsBody.addView(label("Susunan folder hasil unduhan"), Ui.fill(this, 12));
        saveSpin = Ui.spinner(this, SAVE_MODES);
        saveSpin.setSelection(Math.max(0, Math.min(SAVE_MODES.length - 1, prefs.getInt("save", 0))));
        settingsBody.addView(saveSpin, Ui.fill(this, 6));

        settingsBody.addView(section("Performa HP"), Ui.fill(this, 20));
        deviceInfo = Ui.hint(this, profile.describe());
        settingsBody.addView(deviceInfo, Ui.fill(this, 6));
        settingsBody.addView(label("Pengumpulan link bersamaan"), Ui.fill(this, 12));
        colSpin = Ui.spinner(this, colNames());
        colSpin.setSelection(Math.max(0, Math.min(MAX_COLLECT, prefs.getInt("col", 0))));
        settingsBody.addView(colSpin, Ui.fill(this, 6));
        colHint = Ui.hint(this, "");
        settingsBody.addView(colHint, Ui.fill(this, 8));
        recBtn = Ui.ghost(this, "Pakai rekomendasi untuk HP ini");
        recBtn.setTextSize(13);
        settingsBody.addView(recBtn, Ui.fill(this, 10));
        darkWebBox = Ui.check(this, "Sembunyikan tampilan web saat bekerja", prefs.getBoolean("darkweb", false));
        settingsBody.addView(darkWebBox, Ui.fill(this, 10));

        settingsBody.addView(section("Pola link"), Ui.fill(this, 20));
        settingsBody.addView(Ui.hint(this, "Isi hanya jika video atau subfolder tidak terdeteksi. "
                + "Masukkan bagian khas dari link-nya, misalnya /watch/ atau /folder/."), Ui.fill(this, 4));
        patternInput = Ui.input(this, "Pola link video");
        patternInput.setText(prefs.getString("pat", ""));
        settingsBody.addView(patternInput, Ui.fill(this, 10));
        folderPatternInput = Ui.input(this, "Pola link folder");
        folderPatternInput.setText(prefs.getString("fpat", ""));
        settingsBody.addView(folderPatternInput, Ui.fill(this, 8));
        adv.addView(settingsBody);
        pageMore.addView(adv, Ui.fill(this, 12));

        // catatan teknis (dilipat)
        LinearLayout logCard = Ui.card(this, false);
        LinearLayout lhead = Ui.row(this);
        tabLog = Ui.title(this, "Catatan teknis");
        lhead.addView(tabLog, Ui.weight(1));
        logChevron = Ui.text(this, "\u25BE", 20, Ui.MUTED, Ui.BODY);
        logChevron.setPadding(dp(12), 0, dp(4), 0);
        lhead.addView(logChevron);
        lhead.setClickable(true);
        lhead.setOnClickListener(v -> toggleLog(logPane.getVisibility() != View.VISIBLE));
        logCard.addView(lhead);
        logPane = Ui.col(this);
        copyLogBtn = Ui.ghost(this, "Salin catatan");
        copyLogBtn.setTextSize(13);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(WRAP, WRAP);
        cl.gravity = Gravity.END;
        logPane.addView(copyLogBtn, Ui.fill(this, 10));
        logView = Ui.text(this, "", 12, Color.parseColor("#B7C1E6"), Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        logView.setLineSpacing(0, 1.2f);
        logView.setBackground(Ui.box(this, Ui.ABYSS, 12, 0));
        logView.setPadding(dp(12), dp(12), dp(12), dp(12));
        logView.setMinHeight(dp(120));
        logPane.addView(logView, Ui.fill(this, 8));
        logPane.setVisibility(View.GONE);
        logCard.addView(logPane);
        pageMore.addView(logCard, Ui.fill(this, 12));

        ScrollView scroll = new ScrollView(this) {
            @Override
            public void requestChildFocus(View child, View focused) {
                if (focused instanceof WebView) return;   // jangan loncat ke halaman web
                super.requestChildFocus(child, focused);
            }
        };
        mainScroll = scroll;
        scroll.setFillViewport(true);
        scroll.addView(root);

        // navigasi bawah futuristik (Cyber Dock HUD)
        navBar = new FuturisticNavBar(this);
        navBar.setOnTabSelectListener(this::showPage);
        LinearLayout shell = Ui.col(this);
        shell.addView(scroll, new LinearLayout.LayoutParams(MATCH, 0, 1));
        shell.addView(navBar, new LinearLayout.LayoutParams(MATCH, WRAP));

        // lapisan hitam: layar tampak mati tapi aplikasi tetap bekerja
        darkOverlay = new TextView(this);
        darkOverlay.setBackgroundColor(Color.BLACK);
        darkOverlay.setTextColor(Color.rgb(70, 78, 110));
        darkOverlay.setGravity(Gravity.CENTER);
        darkOverlay.setText("Aplikasi tetap bekerja.\nSentuh layar untuk kembali.");
        darkOverlay.setVisibility(View.GONE);
        darkOverlay.setClickable(true);
        darkOverlay.setOnClickListener(v -> setDark(false));

        FrameLayout frame = new FrameLayout(this);
        frame.setFitsSystemWindows(true);
        frame.addView(shell, new FrameLayout.LayoutParams(MATCH, MATCH));
        frame.addView(darkOverlay, new FrameLayout.LayoutParams(MATCH, MATCH));
        lockLayer = buildLockLayer();
        frame.addView(lockLayer, new FrameLayout.LayoutParams(MATCH, MATCH));
        setContentView(frame);
        showPage(PAGE_HOME);

        // aksi
        darkBtn.setOnClickListener(v -> setDark(true));
        mainBtn.setOnClickListener(v -> onMainAction());
        secBtn.setOnClickListener(v -> {
            if (mode == Mode.CAPTURE) {
                if (selectedReadyCount() > 0) startDownloads();
                return;
            }
            if (countState(ST_READY) > 0 && pendingCount() > 0) startCollect();
            else startScan();
        });
        singleBtn.setOnClickListener(v -> startSingle());
        browseBtn.setOnClickListener(v -> startBrowse());
        sniffCloseBtn.setOnClickListener(v -> stopSniff());
        pasteBtn.setOnClickListener(v -> pasteLink());
        copyLogBtn.setOnClickListener(v -> copyLog());
        shead.setOnClickListener(v -> {
            boolean open = settingsBody.getVisibility() != View.VISIBLE;
            settingsBody.setVisibility(open ? View.VISIBLE : View.GONE);
            settingsChevron.setText(open ? "\u25B4" : "\u25BE");
        });
        previewToggle.setOnClickListener(v -> {
            if (mode == Mode.SCAN || mode == Mode.CAPTURE) return;
            previewOpen = false;
            if (sniffing) stopSniff();
            web.loadUrl("about:blank");
            refreshUi();
        });
        android.widget.AdapterView.OnItemSelectedListener libSel = new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                prefs.edit().putInt("libFilter", libFilterSpin.getSelectedItemPosition())
                        .putInt("libSort", libSortSpin.getSelectedItemPosition()).apply();
                if (currentPage == PAGE_VIDEOS) renderLibrary(null);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        };
        libFilterSpin.setOnItemSelectedListener(libSel);
        libSortSpin.setOnItemSelectedListener(libSel);
        libViewBtn.setOnClickListener(v -> {
            libGrid = !libGrid;
            prefs.edit().putInt("libView", libGrid ? 0 : 1).apply();
            renderLibrary(null);
        });
        libOpenBtn.setOnClickListener(v -> openVideoFile());
        libSelectBtn.setOnClickListener(v -> setLibSelectMode(!libSelectMode));
        libCleanBtn.setOnClickListener(v -> confirmDeleteWatched90());
        libSelCancelBtn.setOnClickListener(v -> setLibSelectMode(false));
        libSelAllBtn.setOnClickListener(v -> toggleSelectAllLibrary());
        libSelDelBtn.setOnClickListener(v -> confirmDeleteSelected());
        askDelWatchedBox.setOnCheckedChangeListener((bt, on) -> saveSettings());
        pinSetBtn.setOnClickListener(v -> showLock(PinLock.isSet(this) ? PIN_OLD_CHANGE : PIN_NEW));
        pinOffBtn.setOnClickListener(v -> showLock(PIN_OLD_REMOVE));
        updateBtn.setOnClickListener(v -> startUpdate());
        updateCheckBtn.setOnClickListener(v -> checkUpdate(true));
        autoUpdateBox.setOnCheckedChangeListener((bt, on) -> saveSettings());
        clearHistoryBtn.setOnClickListener(v -> {
            if (clearArmed) {
                clearArmed = false;
                prefs.edit().remove(key("history")).apply();
                historyUrl = "";
                clearHistoryBtn.setText("Hapus semua riwayat");
                renderHistory();
            } else {
                clearArmed = true;
                clearHistoryBtn.setText("Tekan lagi untuk menghapus");
                h.postDelayed(() -> {
                    clearArmed = false;
                    clearHistoryBtn.setText("Hapus semua riwayat");
                }, 3000);
            }
        });
        subBox.setOnCheckedChangeListener((bt, on) -> {
            updateSettingsSummary();
            saveSettings();
        });
        dnsBox.setOnCheckedChangeListener((bt, on) -> {
            Net.enabled = on;
            saveSettings();
        });
        adBox.setOnCheckedChangeListener((bt, on) -> {
            adBlock = on;
            updateSettingsSummary();
            saveSettings();
        });
        fastBox.setOnCheckedChangeListener((bt, on) -> saveSettings());
        autoBox.setOnCheckedChangeListener((bt, on) -> saveSettings());
        keepBox.setOnCheckedChangeListener((bt, on) -> {
            saveSettings();
            refreshUi();
        });
        batteryBtn.setOnClickListener(v -> askIgnoreBattery());
        recBtn.setOnClickListener(v -> {
            colSpin.setSelection(0);
            parSpin.setSelection(indexOfPar(profile.recDownload));
            updateSettingsSummary();
            saveSettings();
            setStatus("Pengaturan disesuaikan dengan rekomendasi untuk HP ini.", Ui.ION);
        });
        TextWatcher saver = new TextWatcher() {
            public void beforeTextChanged(CharSequence c, int a, int b, int d) { }
            public void onTextChanged(CharSequence c, int a, int b, int d) { }
            public void afterTextChanged(Editable e) { saveSettings(); }
        };
        patternInput.addTextChangedListener(saver);
        folderPatternInput.addTextChangedListener(saver);
        urlInput.addTextChangedListener(saver);
        android.widget.AdapterView.OnItemSelectedListener sel = new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                updateSettingsSummary();
                saveSettings();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        };
        baseSpin.setOnItemSelectedListener(sel);
        saveSpin.setOnItemSelectedListener(sel);
        parSpin.setOnItemSelectedListener(sel);
        colSpin.setOnItemSelectedListener(sel);
        darkWebBox.setOnCheckedChangeListener((bt, on) -> {
            saveSettings();
            layoutWorkers();
            refreshUi();
        });
        folderInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence c, int a, int b, int d) { }
            public void onTextChanged(CharSequence c, int a, int b, int d) { }
            public void afterTextChanged(Editable e) {
                updateSettingsSummary();
                saveSettings();
            }
        });
        updateBatteryStatus();
        updateSettingsSummary();
    }

    // ---------------- TAMPILAN ----------------

    TextView stat(LinearLayout parent, String label) {
        LinearLayout c = Ui.col(this);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView n = Ui.text(this, "0", 22, Ui.TEXT, Ui.DISPLAY);
        c.addView(n);
        c.addView(Ui.text(this, label, 12, Ui.MUTED, Ui.BODY));
        parent.addView(c, Ui.weight(1));
        return n;
    }

    TextView section(String s) {
        return Ui.text(this, s, 14, Ui.ION, Ui.MEDIUM);
    }

    TextView label(String s) {
        return Ui.text(this, s, 12.5f, Ui.MUTED, Ui.MEDIUM);
    }

    TextView tab(String s) {
        TextView t = Ui.text(this, s, 13, Ui.MUTED, Ui.MEDIUM);
        t.setSingleLine(true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(10), 0, dp(10));
        t.setClickable(true);
        return t;
    }

    /** Kompatibel dengan pemanggil lama: 0 = daftar unduhan, 1 = catatan, 2 = riwayat, 3 = video saya. */
    void showTab(int which) {
        if (which == 1) {
            toggleLog(true);           // catatan dibuka, tanpa memindahkan halaman
            return;
        }
        showPage(which == 3 ? PAGE_VIDEOS : (which == 2 ? PAGE_HISTORY : PAGE_HOME));
    }

    void showPage(int page) {
        boolean changed = currentPage != page;
        currentPage = page;
        LinearLayout[] pages = {pageHome, pageVideos, pageHistory, pageMore};
        for (int i = 0; i < pages.length; i++) {
            if (i == page) {
                pages[i].setVisibility(View.VISIBLE);
                if (changed) {
                    pages[i].setAlpha(0f);
                    pages[i].setTranslationY(dp(12));
                    pages[i].animate().alpha(1f).translationY(0f).setDuration(220).start();
                }
            } else {
                pages[i].setVisibility(View.GONE);
            }
        }
        if (navBar != null) navBar.setSelectedIndex(page, changed);
        if (changed) mainScroll.scrollTo(0, 0);
        if (page == PAGE_VIDEOS) loadLibrary();
        if (page == PAGE_HISTORY) renderHistory();
    }

    TextView navItem(String icon, String label) {
        TextView t = Ui.text(this, icon + "\n" + label, 12, Ui.MUTED, Ui.MEDIUM);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(6), 0, dp(6));
        t.setClickable(true);
        return t;
    }

    void toggleLog(boolean open) {
        logPane.setVisibility(open ? View.VISIBLE : View.GONE);
        logChevron.setText(open ? "\u25B4" : "\u25BE");
    }

    static void styleGhost(Context c, TextView b) {
        b.setBackground(Ui.box(c, Color.TRANSPARENT, 10, Ui.LINE));
        b.setTextColor(Ui.ION);
    }

    /** Link terakhir di halaman Unduh: ketuk untuk mengisi kotak link. */
    void renderRecent(List<JSONObject> list) {
        if (recentBox == null) return;
        recentBox.removeAllViews();
        int n = 0;
        for (final JSONObject o : list) {
            if (n++ >= 3) break;
            final String url = o.optString("url");
            LinearLayout r = Ui.col(this);
            r.setBackground(Ui.box(this, Ui.ABYSS, 10, 0));
            r.setPadding(dp(12), dp(9), dp(12), dp(9));
            TextView name = Ui.text(this, o.optString("name", url), 14, Ui.TEXT, Ui.BODY);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            r.addView(name);
            TextView link = Ui.hint(this, url);
            link.setSingleLine(true);
            link.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            r.addView(link);
            r.setClickable(true);
            r.setOnClickListener(v -> {
                if (mode == Mode.SCAN || mode == Mode.CAPTURE) return;
                urlInput.setText(url);
                setStatus("Link dipilih. Tekan Cari video.", Ui.ION);
                showPage(PAGE_HOME);
            });
            recentBox.addView(r, Ui.fill(this, 6));
        }
        if (recentEmpty != null) recentEmpty.setVisibility(recentBox.getChildCount() == 0 ? View.VISIBLE : View.GONE);
    }
    void setStatus(String msg, int color) {
        statusText.setText(msg);
        statusDot.setBackground(Ui.box(this, color, 4, 0));
    }

    void updateSettingsSummary() {
        int m = saveSpin.getSelectedItemPosition();
        folderInput.setEnabled(m != 2);
        folderInput.setAlpha(m == 2 ? 0.4f : 1f);
        String where = destLabel();
        String example = BASE_NAMES[baseSpin.getSelectedItemPosition()];
        String sub = targetSub("Drama");
        destPreview.setText("Contoh hasil: " + example + (sub.isEmpty() ? "" : "/" + sub) + "/001 - Judul video.mp4");
        settingsSummary.setText((subBox.isChecked() ? "Termasuk subfolder" : "Hanya folder ini")
                + ", simpan ke " + where + (adBox.isChecked() ? ", iklan diblokir" : "")
                + ", kumpul " + collectParallel() + " proses.");
        if (colSpin != null && colHint != null) {
            int n = collectParallel(), rec = profile.recCollect;
            if (n >= MAX_COLLECT) {
                colHint.setText("\u26A0 " + n + " proses sekaligus sangat berat: tiap proses membuka satu halaman web penuh. "
                        + "HP bisa lag, panas, atau aplikasi tertutup sendiri, dan server mudah memblokir. "
                        + "Hanya disarankan untuk HP RAM besar (12 GB ke atas). Jumlah proses tetap diturunkan otomatis jika memori menipis.");
                colHint.setTextColor(Ui.PULSE);
            } else if (n > rec) {
                colHint.setText("Lebih tinggi dari rekomendasi (" + rec + "). Pengumpulan bisa lebih cepat, tapi HP bisa "
                        + "lambat, panas, atau aplikasi tertutup sendiri. Server juga lebih mudah memblokir.");
                colHint.setTextColor(Ui.AMBER);
            } else if (n < rec) {
                colHint.setText("Lebih rendah dari rekomendasi (" + rec + "). Paling aman, tapi lebih lambat.");
                colHint.setTextColor(Ui.MUTED);
            } else {
                colHint.setText("Sesuai rekomendasi untuk HP ini.");
                colHint.setTextColor(Ui.ION);
            }
        }
    }

    static String parLabel(int v, int rec) {
        String t = v == 1 ? "1 (satu per satu)" : v + " sekaligus";
        if (v == rec) return t + "  \u2605 rekomendasi";
        if (v > rec) return t + "  (berat untuk HP ini)";
        return t;
    }

    static String collectLabel(int n, int rec) {
        String t = n == 1 ? "1 (satu per satu)" : n + " sekaligus";
        if (n >= MAX_COLLECT) return t + "  \u26A0 sangat berat, risiko lag/panas/tertutup";
        if (n == rec) return t + "  \u2605 rekomendasi";
        if (n > rec) return t + "  \u26A0 berat untuk HP ini";
        return t;
    }

    String[] colNames() {
        String[] n = new String[MAX_COLLECT + 1];
        n[0] = "Otomatis (rekomendasi: " + profile.recCollect + " sekaligus)";
        for (int k = 1; k <= MAX_COLLECT; k++) n[k] = collectLabel(k, profile.recCollect);
        return n;
    }

    boolean darkWeb() {
        return darkWebBox != null && darkWebBox.isChecked();
    }

    TextView darkCover() {
        TextView t = new TextView(this);
        t.setBackgroundColor(Color.BLACK);
        t.setTextColor(Color.rgb(70, 78, 110));
        t.setGravity(Gravity.CENTER);
        t.setText("Tampilan web disembunyikan.\nProses tetap berjalan di belakang.");
        t.setClickable(true);       // sentuhan tidak diteruskan ke halaman di bawahnya
        return t;
    }

    static void detach(View v) {
        if (v != null && v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
    }

    /**
     * Susun WebView para pekerja. Mode biasa: berderet ke bawah. Mode gelap: semua ditumpuk
     * dalam satu panel kecil yang ditutup layar hitam. WebView tetap terpasang, berukuran
     * penuh, dan tergambar di bawah penutup, jadi halaman dan pemutar bekerja seperti biasa.
     */
    void layoutWorkers() {
        if (workerBox == null) return;
        for (Worker w : workers) detach(w.holder);
        detach(stageCover);
        stage.removeAllViews();
        workerBox.removeAllViews();
        if (workers.isEmpty()) return;
        final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT, WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
        if (darkWeb()) {
            int maxH = 0;
            for (Worker w : workers) {
                stage.addView(w.holder, new FrameLayout.LayoutParams(MATCH, WRAP));
                maxH = Math.max(maxH, w.hdp);
            }
            stage.addView(stageCover, new FrameLayout.LayoutParams(MATCH, MATCH));
            workerBox.addView(stage, new LinearLayout.LayoutParams(MATCH, dp(maxH + 30)));
        } else {
            for (Worker w : workers) workerBox.addView(w.holder, Ui.fill(this, 10));
        }
    }

    String[] parNames() {
        String[] n = new String[PAR_VALUES.length];
        for (int i = 0; i < n.length; i++) n[i] = parLabel(PAR_VALUES[i], profile.recDownload);
        return n;
    }

    static int indexOfPar(int v) {
        for (int i = 0; i < PAR_VALUES.length; i++) if (PAR_VALUES[i] == v) return i;
        return 1;
    }

    /** Jumlah proses pengumpul link yang dipakai (0 pada spinner = otomatis = rekomendasi). */
    int collectParallel() {
        int p = colSpin == null ? 0 : colSpin.getSelectedItemPosition();
        return p <= 0 ? profile.recCollect : Math.min(p, MAX_COLLECT);
    }

    /** Semua pengaturan disimpan di penyimpanan aplikasi (bukan cache), langsung saat diubah. */
    void saveSettings() {
        if (loadingPrefs || prefs == null) return;
        prefs.edit()
                .putString(key("url"), urlInput.getText().toString().trim())
                .putString("pat", patternInput.getText().toString())
                .putString("fpat", folderPatternInput.getText().toString())
                .putInt("base", baseSpin.getSelectedItemPosition())
                .putInt("save", saveSpin.getSelectedItemPosition())
                .putString("folder", folderInput.getText().toString())
                .putBoolean("sub", subBox.isChecked())
                .putBoolean("ads", adBox.isChecked())
                .putBoolean("dns", dnsBox.isChecked())
                .putBoolean("keep", keepBox.isChecked())
                .putBoolean("fast", fastBox.isChecked())
                .putBoolean("auto", autoBox.isChecked())
                .putInt("par", parSpin.getSelectedItemPosition())
                .putInt("col", colSpin.getSelectedItemPosition())
                .putBoolean("darkweb", darkWebBox.isChecked())
                .putBoolean("autoupd", autoUpdateBox.isChecked())
                .putBoolean("askdel90", askDelWatchedBox.isChecked())
                .apply();
    }

    boolean batteryUnrestricted() {
        if (Build.VERSION.SDK_INT < 23) return true;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    void updateBatteryStatus() {
        boolean ok = batteryUnrestricted();
        batteryStatus.setText(ok ? "Hemat baterai tidak membatasi aplikasi ini."
                : "Disarankan: izinkan aplikasi berjalan tanpa pembatasan baterai.");
        batteryStatus.setTextColor(ok ? Ui.ION : Ui.AMBER);
        batteryBtn.setVisibility(ok ? View.GONE : View.VISIBLE);
    }

    void askIgnoreBattery() {
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception ignored) {
                setStatus("Buka Pengaturan HP, cari Baterai, lalu izinkan aplikasi ini tanpa batasan.", Ui.AMBER);
            }
        }
    }

    /** Menyalakan/mematikan layanan latar depan sesuai keadaan, dan memperbarui notifikasi. */
    void syncService() {
        boolean want = mode != Mode.IDLE && keepBox != null && keepBox.isChecked();
        if (!want) {
            if (KeepAliveService.running) KeepAliveService.halt(this);
            lastNotif = "";
            return;
        }
        int total = videos.size();
        int done = countState(ST_DONE);
        int active = countState(ST_SEARCH) + countState(ST_QUEUED) + countState(ST_DL);
        String text;
        int pct;
        if (mode == Mode.SCAN) {
            text = "Memindai folder: " + found.size() + " video ditemukan";
            pct = -1;
        } else if (mode == Mode.DOWNLOAD) {
            text = "Mengunduh: " + done + " dari " + total + " selesai";
            pct = total == 0 ? -1 : done * 100 / total;
        } else {
            int cur = Math.min(Math.max(nextIdx, 1), Math.max(total, 1));
            int rd = countState(ST_READY);
            int run = runningWorkers();
            text = "Mengumpulkan link " + cur + " dari " + total + ", " + rd + " siap"
                    + (run > 1 ? " (" + run + " proses)" : "");
            pct = total == 0 ? -1 : rd * 100 / total;
        }
        String key = text + "|" + pct;
        if (!key.equals(lastNotif) || !KeepAliveService.running) {
            lastNotif = key;
            KeepAliveService.update(this, text, pct);
        }
    }

    /** Unduh satu video langsung dari link halaman video (atau link file .mp4), tanpa memindai folder. */
    void startSingle() {
        if (mode != Mode.IDLE) return;
        String url = urlInput.getText().toString().trim();
        Uri u = Uri.parse(url);
        if (u.getHost() == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            setStatus("Link belum benar. Tempel link halaman video atau link file video.", Ui.PULSE);
            return;
        }
        baseDomain = baseDomain(u.getHost());
        allowedHosts.clear();
        pageQueue.clear();
        found.clear();
        allLinks.clear();
        foldersScanned = 0;
        logView.setText("");
        videos.clear();
        doneUri.clear();
        rSizes.clear();
        rDurs.clear();
        rPoster.clear();
        dlSelected.clear();
        videos.add(new String[]{url, "", ""});
        String ssp = u.getPath() == null ? "" : lastSegment(u.getPath());
        recordHistory(url, ssp.isEmpty() ? String.valueOf(u.getHost()) : sanitize(Uri.decode(stripExt(ssp))), 1, 0, true);
        vState = new int[1];
        vPct = new int[1];
        vNote = new String[1];
        rUrl = null;
        buildRows();
        showTab(0);
        startCollect();
    }

    static String stripExt(String n) {
        int d = n.lastIndexOf('.');
        return d > 0 ? n.substring(0, d) : n;
    }


    void pasteLink() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
            CharSequence t = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
            if (t != null) urlInput.setText(t.toString().trim());
        } else {
            setStatus("Papan klip kosong. Salin dulu link foldernya.", Ui.AMBER);
        }
    }

    void copyLog() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("catatan", logView.getText()));
            copyLogBtn.setText("Tersalin");
            h.postDelayed(() -> copyLogBtn.setText("Salin catatan"), 1500);
        }
    }

    void onMainAction() {
        if (mode == Mode.SCAN) stopScan();
        else if (mode == Mode.CAPTURE) stopCapture();
        else if (mode == Mode.DOWNLOAD) stopDownloads();
        else if (countState(ST_READY) > 0) {
            if (selectedReadyCount() > 0) startDownloads();
            else setStatus("Pilih minimal 1 video yang sudah siap dari daftar di bawah.", Ui.AMBER);
        } else if (pendingCount() > 0) startCollect();
        else startScan();
    }

    int countState(int st) {
        int n = 0;
        for (int v : vState) if (v == st) n++;
        return n;
    }

    int pendingCount() {
        return countState(ST_WAIT) + countState(ST_FAIL);
    }

    int selectedReadyCount() {
        int n = 0;
        for (int i = 0; i < vState.length; i++) {
            if (vState[i] == ST_READY && dlSelected.contains(i) && rUrl != null && i < rUrl.length && rUrl[i] != null) n++;
        }
        return n;
    }

    void selectAllReady(boolean select) {
        if (!select) {
            dlSelected.clear();
        } else {
            for (int i = 0; i < vState.length; i++) {
                if (vState[i] == ST_READY && rUrl != null && i < rUrl.length && rUrl[i] != null) dlSelected.add(i);
            }
        }
        for (int i = 0; i < vState.length; i++) paintRow(i);
        refreshUi();
    }

    void toggleSelectVideo(int idx) {
        if (idx < 0 || idx >= vState.length || vState[idx] != ST_READY) return;
        if (!dlSelected.remove(idx)) dlSelected.add(idx);
        paintRow(idx);
        refreshUi();
    }

    void previewOrPlayRow(int i) {
        if (i < 0 || i >= vState.length) return;
        if (vState[i] == ST_DONE) {
            playDone(i);
            return;
        }
        if (vState[i] == ST_READY && rUrl != null && i < rUrl.length && rUrl[i] != null) {
            ArrayList<String> one = new ArrayList<>(), names = new ArrayList<>();
            one.add(rUrl[i]);
            names.add(rName != null && i < rName.length && rName[i] != null ? rName[i] : "Video " + (i + 1));
            String ref = rRef != null && i < rRef.length && rRef[i] != null ? rRef[i] : rUrl[i];
            PlayerActivity.open(this, one, names, 0, ref, web.getSettings().getUserAgentString());
        }
    }

    void refreshUi() {
        int total = videos.size();
        int done = countState(ST_DONE), failed = countState(ST_FAIL);
        int ready = countState(ST_READY);
        int selReady = selectedReadyCount();
        int active = countState(ST_SEARCH) + countState(ST_QUEUED) + countState(ST_DL) + ready;
        int pending = pendingCount();

        statFolders.setText(String.valueOf(foldersScanned));
        statVideos.setText(String.valueOf(total > 0 ? total : found.size()));
        statDone.setText(String.valueOf(done));
        statFail.setText(String.valueOf(failed));
        statFail.setTextColor(failed > 0 ? Ui.PULSE : Ui.TEXT);
        statDone.setTextColor(done > 0 ? Ui.ION : Ui.TEXT);

        if (navBar != null) {
            int badgeNum = mode == Mode.DOWNLOAD ? (countState(ST_QUEUED) + countState(ST_DL)) : ready;
            navBar.setBadge(PAGE_HOME, badgeNum > 0 ? String.valueOf(badgeNum) : "");
            navBar.setBadge(PAGE_VIDEOS, libItems.isEmpty() ? "" : String.valueOf(libItems.size()));
        }

        if (dlSelBar != null) {
            dlSelBar.setVisibility(ready > 0 ? View.VISIBLE : View.GONE);
            if (ready > 0) {
                dlSelInfo.setText("\u2713 " + selReady + " / " + ready + " video dipilih" + readySizeLabel());
                dlSelAllBtn.setText(selReady >= ready ? "Semua terpilih" : "Pilih semua (" + ready + ")");
            }
            if (dlSelGoBtn != null) {
                boolean canEarlyDl = selReady > 0 && (mode == Mode.IDLE || mode == Mode.CAPTURE || mode == Mode.DOWNLOAD);
                dlSelGoBtn.setVisibility(canEarlyDl ? View.VISIBLE : View.GONE);
                if (canEarlyDl) dlSelGoBtn.setText("\u2B07 Unduh (" + selReady + ")");
            }
        }

        if (mode == Mode.SCAN) {
            ring.setSpinning(true);
            ring.setCenter(String.valueOf(found.size()), "video ditemukan");
        } else {
            ring.setSpinning(false);
            if (total == 0) {
                ring.setProgress(0, 0, 0);
                ring.setCenter("0", "belum ada video");
            } else if (mode == Mode.CAPTURE) {
                ring.setProgress((float) done / total, (float) ready / total, (float) failed / total);
                ring.setCenter(String.valueOf(ready), "dari " + total + " link");
            } else {
                float t = total;
                float partial = 0;
                for (int i = 0; i < vState.length; i++) {
                    if (vState[i] == ST_DL && vPct[i] > 0) partial += vPct[i] / 100f;
                }
                ring.setProgress((done + partial) / t, (active - partial) / t, failed / t);
                ring.setCenter(String.valueOf(done), "dari " + total + " selesai");
            }
        }

        if (mode == Mode.SCAN) {
            mainBtn.setText("\u23F9  Hentikan pemindaian");
            Ui.stylePrimary(this, mainBtn, Ui.PANEL_HI, Ui.PULSE);
        } else if (mode == Mode.CAPTURE) {
            mainBtn.setText("\u23F9  Hentikan pengumpulan link (" + ready + "/" + total + ")");
            Ui.stylePrimary(this, mainBtn, Ui.PANEL_HI, Ui.PULSE);
        } else if (mode == Mode.DOWNLOAD) {
            mainBtn.setText("\u23F9  Hentikan antrean unduhan");
            Ui.stylePrimary(this, mainBtn, Ui.PANEL_HI, Ui.PULSE);
        } else if (ready > 0) {
            if (selReady > 0) {
                mainBtn.setText("\u2B07  Unduh " + selReady + " video terpilih" + readySizeLabel());
                Ui.stylePrimary(this, mainBtn, Ui.ION, Ui.ABYSS);
            } else {
                mainBtn.setText("Pilih video dari daftar di bawah (" + ready + " siap)");
                Ui.stylePrimary(this, mainBtn, Ui.PANEL_HI, Ui.AMBER);
            }
        } else if (pending > 0) {
            boolean onlyFailed = countState(ST_WAIT) == 0;
            mainBtn.setText(onlyFailed ? "\u27F3  Coba lagi " + pending + " video gagal" : "\u26A1  Dapatkan link " + pending + " video");
            Ui.stylePrimary(this, mainBtn, Ui.ION, Ui.ABYSS);
        } else {
            mainBtn.setText("\uD83D\uDD0D  Pindai folder & dapatkan link");
            Ui.stylePrimary(this, mainBtn, Ui.ION, Ui.ABYSS);
        }
        if (mode == Mode.CAPTURE && selReady > 0) {
            secBtn.setText("\u2B07  Unduh " + selReady + " video terpilih" + readySizeLabel());
            Ui.stylePrimary(this, secBtn, Ui.ION, Ui.ABYSS);
            secBtn.setVisibility(View.VISIBLE);
        } else {
            secBtn.setText(ready > 0 && pending > 0 ? "Ambil " + pending + " link yang belum" : "Pindai ulang folder");
            styleGhost(this, secBtn);
            secBtn.setVisibility(mode == Mode.IDLE && (pending > 0 || ready > 0) ? View.VISIBLE : View.GONE);
        }
        singleBtn.setVisibility(View.GONE);
        // Tampilan sederhana: saat tidak ada pekerjaan folder, "Cari video" jadi tombol utama.
        boolean folderWork = mode == Mode.SCAN || mode == Mode.CAPTURE || ready > 0 || pending > 0
                || (mode == Mode.DOWNLOAD && !sniffing);
        if (folderWork) {
            Ui.stylePrimary(this, browseBtn, Ui.PANEL_HI, Ui.ION);
            styleGhost(this, browseBtn);
        } else {
            Ui.stylePrimary(this, browseBtn, Ui.ION, Ui.ABYSS);
            if (mode == Mode.IDLE) styleGhost(this, mainBtn);
        }
        mainBtn.setVisibility(mode == Mode.DOWNLOAD && sniffing ? View.GONE : View.VISIBLE);

        // saat mengunduh, pratinjau tetap bisa dipakai menjelajah (mode IDM) dan menambah unduhan
        boolean webBusy = mode == Mode.SCAN || mode == Mode.CAPTURE;
        browseBtn.setVisibility(webBusy ? View.GONE : View.VISIBLE);
        urlInput.setEnabled(!webBusy);
        pasteBtn.setEnabled(!webBusy);
        pasteBtn.setAlpha(webBusy ? 0.4f : 1f);
        webFrame.setVisibility(mode == Mode.SCAN || (!webBusy && previewOpen) ? View.VISIBLE : View.GONE);
        workerBox.setVisibility(mode == Mode.CAPTURE ? View.VISIBLE : View.GONE);
        webCover.setVisibility(darkWeb() && mode == Mode.SCAN ? View.VISIBLE : View.GONE);
        quietMain.quiet = webBusy;  // di luar pindai/kumpul, pratinjau bisa disentuh/diketik (mis. untuk login)
        previewToggle.setText(webBusy ? "Aktif" : "Tutup");
        previewToggle.setAlpha(webBusy ? 0.5f : 1f);
        sniffCard.setVisibility(sniffing && !webBusy ? View.VISIBLE : View.GONE);
        tabVideos.setText(total > 0 ? "Daftar video & link (" + total + ")" : "Daftar video & link");
        listCard.setVisibility(total > 0 ? View.VISIBLE : View.GONE);
        progressCard.setVisibility(mode == Mode.SCAN || mode == Mode.CAPTURE || (total > 1 && !sniffing) ? View.VISIBLE : View.GONE);
        prevCard.setVisibility(webFrame.getVisibility() == View.VISIBLE || workerBox.getVisibility() == View.VISIBLE
                ? View.VISIBLE : View.GONE);
        recentCard.setVisibility(View.VISIBLE);
        syncService();
    }

    void buildRows() {
        listBox.removeAllViews();
        rowCards.clear();
        rowThumbs.clear();
        rowChecks.clear();
        rowSizeBadges.clear();
        rowDurBadges.clear();
        rowChips.clear();
        rowNotes.clear();
        if (videos.isEmpty()) {
            listBox.addView(emptyList);
            return;
        }
        String lastRel = null;
        for (int i = 0; i < videos.size(); i++) {
            String[] v = videos.get(i);
            if (!v[2].equals(lastRel)) {
                lastRel = v[2];
                TextView g = Ui.text(this, lastRel.isEmpty() ? "\uD83D\uDCC1 Folder utama" : "\uD83D\uDCC1 " + lastRel, 13, Ui.ION, Ui.MEDIUM);
                g.setPadding(dp(4), 0, 0, 0);
                listBox.addView(g, Ui.fill(this, i == 0 ? 4 : 16));
            }
            final int idx = i;
            LinearLayout r = Ui.row(this);
            r.setBackground(Ui.box(this, Ui.PANEL, 12, Ui.LINE));
            r.setPadding(dp(10), dp(10), dp(10), dp(10));

            // Kotak centang pilih video
            TextView chk = Ui.text(this, "", 13, Ui.ABYSS, Ui.DISPLAY);
            chk.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams chkLp = new LinearLayout.LayoutParams(dp(24), dp(24));
            chkLp.rightMargin = dp(8);
            chk.setOnClickListener(x -> toggleSelectVideo(idx));
            r.addView(chk, chkLp);

            // Bingkai thumbnail 16:9 + badge nomor + durasi
            FrameLayout tf = new FrameLayout(this);
            ImageView th = thumbView();
            tf.addView(th, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            TextView numPill = Ui.text(this, String.format("#%02d", i + 1), 9.5f, Ui.ION, Typeface.MONOSPACE);
            numPill.setBackground(Ui.box(this, 0xCC0A1024, 5, 0));
            numPill.setPadding(dp(4), dp(1), dp(4), dp(1));
            FrameLayout.LayoutParams npLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
            npLp.setMargins(dp(4), dp(4), 0, 0);
            tf.addView(numPill, npLp);

            TextView durBadge = Ui.text(this, "", 9.5f, Color.WHITE, Ui.MEDIUM);
            durBadge.setBackground(Ui.box(this, 0xCC000000, 4, 0));
            durBadge.setPadding(dp(4), dp(1), dp(4), dp(1));
            durBadge.setVisibility(View.GONE);
            FrameLayout.LayoutParams dbLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
            dbLp.setMargins(0, 0, dp(4), dp(4));
            tf.addView(durBadge, dbLp);

            tf.setOnClickListener(x -> previewOrPlayRow(idx));
            r.addView(tf, new LinearLayout.LayoutParams(dp(92), dp(54)));

            // Kolom judul + ukuran + catatan
            LinearLayout mid = Ui.col(this);
            mid.setPadding(dp(10), 0, dp(6), 0);
            String title = v[1].isEmpty() ? lastSegment(Uri.parse(v[0]).getPath() == null ? v[0] : Uri.parse(v[0]).getPath()) : v[1];
            TextView t = Ui.text(this, title, 13.5f, Ui.TEXT, Ui.MEDIUM);
            t.setMaxLines(2);
            t.setEllipsize(TextUtils.TruncateAt.END);
            mid.addView(t);

            LinearLayout metaRow = Ui.row(this);
            TextView sizeBadge = Ui.text(this, "", 11, Ui.ION, Ui.MEDIUM);
            sizeBadge.setBackground(Ui.box(this, Ui.ION_DEEP, 6, 0));
            sizeBadge.setPadding(dp(6), dp(2), dp(6), dp(2));
            sizeBadge.setVisibility(View.GONE);
            metaRow.addView(sizeBadge);

            TextView note = Ui.hint(this, "");
            note.setTextSize(11.5f);
            note.setSingleLine(true);
            note.setEllipsize(TextUtils.TruncateAt.END);
            note.setVisibility(View.GONE);
            metaRow.addView(note, Ui.weight(1));
            mid.addView(metaRow, Ui.fill(this, 4));
            r.addView(mid, Ui.weight(1));

            TextView chip = Ui.chip(this);
            chip.setOnClickListener(x -> {
                if (vState[idx] == ST_READY) {
                    dlSelected.add(idx);
                    startSingleReadyDownload(idx);
                } else if (vState[idx] == ST_DONE) {
                    playDone(idx);
                }
            });
            r.addView(chip);

            rowCards.add(r);
            rowThumbs.add(th);
            rowChecks.add(chk);
            rowSizeBadges.add(sizeBadge);
            rowDurBadges.add(durBadge);
            rowChips.add(chip);
            rowNotes.add(note);

            r.setOnClickListener(x -> {
                if (vState[idx] == ST_READY) toggleSelectVideo(idx);
                else if (vState[idx] == ST_DONE) playDone(idx);
            });
            listBox.addView(r, Ui.fill(this, 8));
            paintRow(i);
        }
    }

    void paintRow(int i) {
        if (i < 0 || i >= rowChips.size()) return;
        TextView chip = rowChips.get(i);
        TextView chk = rowChecks.get(i);
        ImageView th = rowThumbs.get(i);
        TextView sizeBadge = rowSizeBadges.get(i);
        TextView durBadge = rowDurBadges.get(i);
        LinearLayout card = rowCards.get(i);

        boolean isReady = vState[i] == ST_READY;
        boolean isSel = isReady && dlSelected.contains(i);

        int rowBorder = vState[i] == ST_DONE ? Ui.ION
                : (vState[i] == ST_FAIL ? Ui.PULSE
                : (isSel ? Ui.ION : Ui.LINE));
        int rowBg = isSel ? Ui.PANEL_HI : Ui.PANEL;
        card.setBackground(Ui.box(this, rowBg, 12, rowBorder));

        if (isReady) {
            chk.setVisibility(View.VISIBLE);
            chk.setText(isSel ? "\u2713" : "");
            chk.setTextColor(isSel ? Ui.ABYSS : Ui.TEXT);
            chk.setBackground(Ui.box(this, isSel ? Ui.ION : Ui.ABYSS, 7, isSel ? Ui.ION : Ui.MUTED));
        } else if (vState[i] == ST_DONE) {
            chk.setVisibility(View.VISIBLE);
            chk.setText("\u2713");
            chk.setTextColor(Ui.ION);
            chk.setBackground(Ui.box(this, Ui.ION_DEEP, 7, Ui.ION));
        } else {
            chk.setVisibility(View.GONE);
        }

        Long sz = rSizes.get(i);
        if (sz != null && sz > 0) {
            sizeBadge.setText((rUrl != null && i < rUrl.length && rUrl[i] != null && isHls(rUrl[i]) ? "\u00B1 " : "") + fmtSize(sz));
            sizeBadge.setVisibility(View.VISIBLE);
        } else {
            sizeBadge.setVisibility(View.GONE);
        }

        Long du = rDurs.get(i);
        if (du != null && du > 0) {
            durBadge.setText(PlayerActivity.fmt(du));
            durBadge.setVisibility(View.VISIBLE);
        } else {
            durBadge.setVisibility(View.GONE);
        }

        String tKey = doneUri.containsKey(i) ? doneUri.get(i)
                : (rUrl != null && i < rUrl.length && rUrl[i] != null ? rUrl[i] : videos.get(i)[0]);
        Bitmap b = tKey == null ? null : thumbs.get(tKey);
        if (b == null && rPoster.containsKey(i)) b = thumbs.get(rPoster.get(i));
        th.setImageBitmap(b != null ? b : placeholderThumb(i));

        switch (vState[i]) {
            case ST_SEARCH: Ui.setChip(this, chip, "\u26A1 Mencari", Ui.AMBER); break;
            case ST_QUEUED: Ui.setChip(this, chip, "\u23F3 Antre", Ui.AMBER); break;
            case ST_DL: Ui.setChip(this, chip, vPct[i] >= 0 ? "\u2B07 " + vPct[i] + "%" : "\u2B07 Unduh", Ui.AMBER); break;
            case ST_READY: Ui.setChip(this, chip, isSel ? "\u2B07 Siap" : "Pilih", isSel ? Ui.ION : Ui.MUTED); break;
            case ST_DONE: Ui.setChip(this, chip, doneUri.containsKey(i) ? "\u25B6 Putar" : "\u2713 Selesai", Ui.ION); break;
            case ST_FAIL: Ui.setChip(this, chip, "\u2715 Gagal", Ui.PULSE); break;
            default: Ui.setChip(this, chip, "\u2022 Menunggu", Ui.MUTED);
        }
        TextView note = rowNotes.get(i);
        String n = vNote[i];
        note.setText(n == null ? "" : (sizeBadge.getVisibility() == View.VISIBLE && !n.isEmpty() ? "  \u00B7 " + n : n));
        note.setTextColor(vState[i] == ST_FAIL ? Ui.PULSE : Ui.MUTED);
        note.setVisibility(n == null || n.isEmpty() ? View.GONE : View.VISIBLE);
    }

    void setState(int i, int st, String note) {
        if (i < 0 || i >= vState.length) return;
        vState[i] = st;
        vNote[i] = note;
        paintRow(i);
    }


    void stopScan() {
        scanSeq++;
        pageQueue.clear();
        web.stopLoading();
        log("Pemindaian dihentikan.");
        finishScan();
    }

    void stopCapture() {
        mode = hasActiveDownloads() ? Mode.DOWNLOAD : Mode.IDLE;
        paused = false;
        for (Worker w : workers) {
            if (w.active && w.item >= 0 && w.item < vState.length && vState[w.item] == ST_SEARCH) setState(w.item, ST_WAIT, "");
        }
        releaseWorkers();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        int rd = countState(ST_READY);
        log("\nPengumpulan link dihentikan. " + rd + " link sudah terkumpul.");
        if (mode == Mode.DOWNLOAD) {
            setStatus("Pengumpulan link dihentikan. Melanjutkan unduhan yang sedang berjalan ...", Ui.AMBER);
        } else {
            setStatus("Dihentikan. " + rd + " link sudah terkumpul dan siap diunduh.", Ui.AMBER);
        }
        refreshUi();
    }

    // ---------------- TAHAP 2: UNDUH MASSAL ----------------

    boolean hasActiveDownloads() {
        return !dlQueue.isEmpty() || activeDownloads() > 0;
    }

    int maxParallel() {
        return PAR_VALUES[parSpin.getSelectedItemPosition()];
    }

    /** Mulai mengunduh video yang dipilih dari link yang sudah terkumpul, tanpa membuka halaman web lagi. */
    void startDownloads() {
        int added = 0;
        for (int i = 0; i < vState.length; i++) {
            if (vState[i] == ST_READY && dlSelected.contains(i) && rUrl != null && i < rUrl.length && rUrl[i] != null
                    && !dlQueue.contains(i)) {
                dlQueue.add(i);
                added++;
            }
        }
        if (added == 0) return;
        if (mode == Mode.CAPTURE) {
            log("\nUnduhan lebih awal: " + added + " video terpilih ditambahkan ke antrean (pencarian link tetap berjalan).");
        } else {
            mode = Mode.DOWNLOAD;
            log("\nTahap 2: mengunduh " + dlQueue.size() + " video terpilih, maksimal " + maxParallel() + " sekaligus.");
            setStatus("Mengunduh " + dlQueue.size() + " video terpilih ...", Ui.AMBER);
        }
        pump();
        startTracker();
        refreshUi();
    }

    void startSingleReadyDownload(int idx) {
        if (idx < 0 || idx >= vState.length || vState[idx] != ST_READY || rUrl == null || idx >= rUrl.length || rUrl[idx] == null) return;
        if (!dlQueue.contains(idx)) dlQueue.add(idx);
        if (mode == Mode.IDLE) {
            mode = Mode.DOWNLOAD;
            log("\nMengunduh: " + rName[idx]);
            setStatus("Mengunduh " + rName[idx] + " ...", Ui.AMBER);
        } else if (mode == Mode.CAPTURE) {
            log("   mulai unduh lebih awal: " + rName[idx]);
        }
        pump();
        startTracker();
        refreshUi();
    }

    /** Isi slot unduhan yang kosong dari antrean. */
    void pump() {
        if (mode != Mode.DOWNLOAD && mode != Mode.CAPTURE) return;
        while (!dlQueue.isEmpty() && activeDownloads() < maxParallel()) beginDownload(dlQueue.poll());
        if (mode == Mode.DOWNLOAD && dlQueue.isEmpty() && activeDownloads() == 0) finishDownloads();
    }

    /** Unduhan yang sedang berjalan: lewat DownloadManager + stream m3u8 yang diunduh aplikasi sendiri. */
    int activeDownloads() {
        return dlMap.size() + hlsJobs.size();
    }

    void beginDownload(int i) {
        dlSelected.remove(i);
        if (rUrl[i] != null && isHls(rUrl[i])) {
            beginHls(i);
            return;
        }
        String name = rName[i];
        try {
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(rUrl[i]));
            r.addRequestHeader("User-Agent", web.getSettings().getUserAgentString());
            r.addRequestHeader("Referer", rRef[i]);
            String cookie = CookieManager.getInstance().getCookie(rUrl[i]);
            if (cookie != null) r.addRequestHeader("Cookie", cookie);
            r.setTitle(name);
            r.setDescription("Filecoster Downloader");
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN);   // kemajuan hanya tampil di dalam aplikasi
            String sub = targetSub(videos.get(i)[2]);
            r.setDestinationInExternalPublicDir(BASE_DIRS[baseSpin.getSelectedItemPosition()],
                    sub.isEmpty() ? name : sub + "/" + name);
            long id = dm.enqueue(r);
            dlMap.put(id, i);
            setState(i, ST_QUEUED, name);
            log("   mulai unduh: " + name);
        } catch (Exception e) {
            setState(i, ST_FAIL, "gagal memulai unduhan: " + e.getMessage());
            log("   x gagal memulai unduhan " + name + ": " + e.getMessage());
        }
    }

    /**
     * Unduh stream m3u8 di latar belakang: DownloadManager tidak bisa menangani HLS, jadi
     * potongan-potongannya diunduh dan disatukan oleh HlsDownloader.
     */
    void beginHls(final int i) {
        final String url = rUrl[i], ref = rRef[i], name = rName[i];
        final String ua = web.getSettings().getUserAgentString();
        final String baseDir = BASE_DIRS[baseSpin.getSelectedItemPosition()];
        final String sub = targetSub(videos.get(i)[2]);
        final AtomicBoolean cancel = new AtomicBoolean(false);
        final android.content.Context app = getApplicationContext();
        hlsJobs.put(i, cancel);
        setState(i, ST_QUEUED, "Membaca playlist stream");
        log("   mulai unduh (stream m3u8): " + name);
        dlPool.execute(() -> {
            HlsDownloader.Result res = null;
            String err = null;
            try {
                res = new HlsDownloader(ua, ref, cancel, new HlsDownloader.Listener() {
                    long lastPost;

                    @Override
                    public void onProgress(int done, int total, long bytes) {
                        long now = SystemClock.uptimeMillis();
                        if (done < total && now - lastPost < 700) return;
                        lastPost = now;
                        h.post(() -> {
                            if (hlsJobs.get(i) != cancel || i >= vPct.length) return;
                            vPct[i] = total > 0 ? done * 100 / total : -1;
                            setState(i, ST_DL, done + "/" + total + " potongan, " + fmtSize(bytes));
                        });
                    }

                    @Override
                    public void onLog(String s) {
                        h.post(() -> log("   " + s));
                    }
                }).run(app, url, baseDir, sub, name);
            } catch (Exception e) {
                err = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            final HlsDownloader.Result fr = res;
            final String fe = err;
            h.post(() -> {
                if (hlsJobs.get(i) != cancel) return;      // aplikasi ditutup / dibatalkan
                hlsJobs.remove(i);
                if (fr != null) {
                    if (i < vPct.length) vPct[i] = 100;
                    if (fr.uri != null) {
                        doneUri.put(i, fr.uri);
                        recordDownload(fr.uri, fr.location == null ? name : lastSegment(fr.location), fr.bytes);
                    }
                    setState(i, ST_DONE, fmtSize(fr.bytes));
                    log("   selesai: " + fr.location);
                } else {
                    setState(i, ST_FAIL, "Unduhan stream gagal: " + fe);
                    log("   x unduhan stream gagal: " + name + " (" + fe + ")");
                }
                updateHistoryResult();
                pump();
                refreshUi();
            });
        });
    }

    void finishDownloads() {
        if (mode != Mode.CAPTURE) mode = Mode.IDLE;
        int done = countState(ST_DONE), failed = countState(ST_FAIL);
        log("\nTahap 2 selesai: " + done + " berhasil, " + failed + " gagal. File ada di " + destLabel() + ".");
        updateHistoryResult();
        setStatus(failed > 0 ? "Selesai: " + done + " berhasil, " + failed + " gagal. Tekan Coba lagi untuk yang gagal."
                : "Selesai: " + done + " video terunduh ke " + destLabel() + ".", failed > 0 ? Ui.AMBER : Ui.ION);
        refreshUi();
    }

    void stopDownloads() {
        dlQueue.clear();
        if (mode != Mode.CAPTURE) mode = Mode.IDLE;
        int rd = countState(ST_READY);
        log("\nAntrean dihentikan. Unduhan yang sedang berjalan tetap diselesaikan.");
        setStatus("Antrean dihentikan." + (rd > 0 ? " " + rd + " video belum diunduh." : ""), Ui.AMBER);
        refreshUi();
    }

    // ---------------- PANTAU UNDUHAN ----------------

    final Runnable tracker = new Runnable() {
        @Override
        public void run() {
            tracking = false;
            if (dlMap.isEmpty() && dlQueue.isEmpty() && hlsJobs.isEmpty()) return;
            if (!dlMap.isEmpty()) pollDownloads();
            updateHistoryResult();
            pump();
            refreshUi();
            if (!dlMap.isEmpty() || !dlQueue.isEmpty() || !hlsJobs.isEmpty()) {
                tracking = true;
                h.postDelayed(this, 1500);
            }
        }
    };

    void startTracker() {
        if (tracking) return;
        tracking = true;
        h.postDelayed(tracker, 1000);
    }

    /** Tanpa notifikasi unduhan, daftarkan file ke galeri/Files secara manual agar tetap muncul. */
    void scanDownloaded(String localUri) {
        try {
            if (localUri == null) return;
            Uri u = Uri.parse(localUri);
            if ("file".equals(u.getScheme()) && u.getPath() != null) {
                MediaScannerConnection.scanFile(this, new String[]{u.getPath()}, null, null);
            }
        } catch (Exception ignored) { }
    }

    boolean pollDownloads() {
        if (dlMap.isEmpty()) return false;
        DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        long[] ids = new long[dlMap.size()];
        int k = 0;
        for (Long id : dlMap.keySet()) ids[k++] = id;
        Set<Long> seen = new HashSet<>();
        List<Long> finished = new ArrayList<>();
        boolean active = false;
        Cursor cur = null;
        try {
            cur = dm.query(new DownloadManager.Query().setFilterById(ids));
            if (cur == null) return false;
            int cId = cur.getColumnIndex(DownloadManager.COLUMN_ID);
            int cSt = cur.getColumnIndex(DownloadManager.COLUMN_STATUS);
            int cSo = cur.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
            int cTot = cur.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
            int cRe = cur.getColumnIndex(DownloadManager.COLUMN_REASON);
            int cLu = cur.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
            while (cur.moveToNext()) {
                long id = cur.getLong(cId);
                Integer i = dlMap.get(id);
                if (i == null) continue;
                seen.add(id);
                if (i >= vState.length || i >= vPct.length) {
                    finished.add(id);
                    continue;
                }
                int stt = cur.getInt(cSt);
                long so = cur.getLong(cSo), tot = cur.getLong(cTot);
                if (stt == DownloadManager.STATUS_SUCCESSFUL) {
                    vPct[i] = 100;
                    try {
                        Uri fu = dm.getUriForDownloadedFile(id);
                        if (fu != null) {
                            doneUri.put(i, fu.toString());
                            recordDownload(fu.toString(), rName != null && i < rName.length ? rName[i] : null, tot);
                        }
                    } catch (Exception ignored) { }
                    setState(i, ST_DONE, tot > 0 ? fmtSize(tot) : "");
                    scanDownloaded(cLu >= 0 ? cur.getString(cLu) : null);
                    finished.add(id);
                } else if (stt == DownloadManager.STATUS_FAILED) {
                    setState(i, ST_FAIL, "Unduhan gagal (kode " + cur.getInt(cRe) + ")");
                    log("   x unduhan gagal: video " + (i + 1) + " (kode " + cur.getInt(cRe) + ")");
                    finished.add(id);
                } else {
                    active = true;
                    vPct[i] = tot > 0 ? (int) (so * 100 / tot) : -1;
                    String note = stt == DownloadManager.STATUS_PAUSED ? "Menunggu jaringan"
                            : (tot > 0 ? fmtSize(so) + " dari " + fmtSize(tot) : "");
                    setState(i, stt == DownloadManager.STATUS_RUNNING ? ST_DL : ST_QUEUED, note);
                }
            }
        } catch (Exception e) {
            return false;
        } finally {
            if (cur != null) cur.close();
        }
        for (long id : ids) {
            if (!seen.contains(id)) {           // dibatalkan dari notifikasi
                Integer i = dlMap.get(id);
                if (i != null) setState(i, ST_FAIL, "Unduhan dibatalkan");
                finished.add(id);
            }
        }
        for (Long id : finished) dlMap.remove(id);
        return active;
    }

    void setupWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message msg) {
                return false; // blokir pop-up iklan
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                // WebView utama hanya dipakai otomatis saat memindai folder; di luar itu pengguna bebas menjelajah
                if (mode != Mode.SCAN) return false;
                return !isAllowed(req.getUrl().toString()); // blokir redirect iklan saat bekerja
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame()) onMainFrameError(String.valueOf(err.getDescription()));
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (sniffing && mode != Mode.SCAN && url != null && !stripSlash(url).equals(stripSlash(sniffPage))) {
                    sniffPage = url;      // halaman baru: daftar deteksi dimulai dari kosong
                    sniffed.clear();
                    renderSniff();
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (mode == Mode.SCAN) onScanPageLoaded();
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                String u = req.getUrl().toString();
                if (adBlock && !req.isForMainFrame() && isAd(u)) {
                    return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
                }
                if (sniffing && isMediaUrl(u)) h.post(() -> onSniff(u, false));   // dipanggil di thread lain
                return Net.fetch(req);      // lewat DNS Google; null = jaringan bawaan WebView
            }

            @Override
            public void onLoadResource(WebView view, String url) {
                if (sniffing) onSniff(url, false);
            }
        });
        // halaman yang memicu unduhan file (tombol Download, dsb.) ditangkap seperti IDM
        web.setDownloadListener((url, ua, disposition, mime, length) -> onWebDownload(url, disposition, mime, length));
    }

    /** Total ukuran link yang dipilih untuk diunduh, untuk tombol "Mulai unduh". Kosong jika belum ada yang diketahui. */
    String readySizeLabel() {
        long sum = 0;
        int unknown = 0;
        for (int i = 0; i < vState.length; i++) {
            if (vState[i] != ST_READY || !dlSelected.contains(i)) continue;
            Long sz = rSizes.get(i);
            if (sz != null && sz > 0) sum += sz;
            else unknown++;
        }
        if (sum <= 0) return "";
        return " (" + fmtSize(sum) + (unknown > 0 ? "+" : "") + ")";
    }

    static String sizeLabel(long[] est, boolean approx) {
        if (est[0] > 0) return (approx ? "\u00B1 " : "") + fmtSize(est[0]) + (est[1] > 0 ? " \u00B7 " + PlayerActivity.fmt(est[1]) : "");
        if (est[1] > 0) return "durasi " + PlayerActivity.fmt(est[1]) + ", ukuran tidak diketahui";
        return "ukuran tidak diketahui";
    }

    /** Hitung ukuran & ambil thumbnail link yang sudah terkumpul (di latar belakang), lalu tampilkan di daftar sebelum diunduh. */
    void measureLater(final int i, final String media, final String referer, final String name) {
        final String ua = web.getSettings().getUserAgentString();
        final String cookie = CookieManager.getInstance().getCookie(media);
        final boolean hls = isHls(media);
        final String poster = rPoster.get(i);
        bg.execute(() -> {
            final long[] est = hls ? HlsDownloader.estimate(media, ua, referer)
                    : new long[]{probeSize(media, referer, ua, cookie), -1};
            h.post(() -> {
                if (i >= vState.length || vState[i] != ST_READY || rUrl == null || i >= rUrl.length || !media.equals(rUrl[i])) return;
                if (est[0] > 0) rSizes.put(i, est[0]);
                if (est[1] > 0) rDurs.put(i, est[1]);
                setState(i, ST_READY, name);
                refreshUi();
            });
        });
        fetchRemoteThumb(media, referer, ua, cookie, poster, i, null);
    }

    Bitmap placeholderThumb(int index) {
        int w = 320, h = 180;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, w, h, 0xFF0A1024, 0xFF16224A, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        p.setColor(0x334FE3D4);
        c.drawCircle(w / 2f, h / 2f, 32f, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x884FE3D4);
        Path tri = new Path();
        float cx = w / 2f, cy = h / 2f;
        tri.moveTo(cx - 9f, cy - 13f);
        tri.lineTo(cx + 15f, cy);
        tri.lineTo(cx - 9f, cy + 13f);
        tri.close();
        c.drawPath(tri, p);
        return b;
    }

    Bitmap fetchImageBitmap(String imgUrl, String referer, String ua, String cookie) {
        if (imgUrl == null || !imgUrl.startsWith("http")) return null;
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(imgUrl).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setInstanceFollowRedirects(true);
            if (ua != null) c.setRequestProperty("User-Agent", ua);
            if (referer != null) c.setRequestProperty("Referer", referer);
            if (cookie != null) c.setRequestProperty("Cookie", cookie);
            if (c.getResponseCode() >= 400) return null;
            try (java.io.InputStream in = c.getInputStream()) {
                Bitmap raw = BitmapFactory.decodeStream(in);
                if (raw == null) return null;
                float sc = Math.min(1f, 320f / Math.max(1, raw.getWidth()));
                return Bitmap.createScaledBitmap(raw, Math.max(1, Math.round(raw.getWidth() * sc)),
                        Math.max(1, Math.round(raw.getHeight() * sc)), true);
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    void fetchRemoteThumb(final String mediaUrl, final String referer, final String ua, final String cookie,
                          final String posterUrl, final int rowIdx, final Runnable onDone) {
        if (mediaUrl == null) return;
        if (thumbs.get(mediaUrl) != null) {
            if (rowIdx >= 0) h.post(() -> paintRow(rowIdx));
            if (onDone != null) h.post(onDone);
            return;
        }
        bg.execute(() -> {
            Bitmap b = null;
            long durMs = -1;
            if (posterUrl != null && !posterUrl.isEmpty()) {
                b = thumbs.get(posterUrl);
                if (b == null) {
                    b = fetchImageBitmap(posterUrl, referer, ua, CookieManager.getInstance().getCookie(posterUrl));
                    if (b != null) thumbs.put(posterUrl, b);
                }
            }
            if (b == null && isHls(mediaUrl)) {
                File tmp = null;
                MediaMetadataRetriever r = new MediaMetadataRetriever();
                try {
                    byte[] seg = HlsDownloader.sampleSegment(mediaUrl, ua, referer);
                    if (seg != null && seg.length > 0) {
                        tmp = File.createTempFile("fcth_", ".ts", getCacheDir());
                        try ( java.io.FileOutputStream fos = new java.io.FileOutputStream(tmp)) {
                            fos.write(seg);
                        }
                        r.setDataSource(tmp.getAbsolutePath());
                        Bitmap f = r.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                        if (f == null) f = r.getFrameAtTime();
                        if (f != null) {
                            float sc = Math.min(1f, 320f / Math.max(1, f.getWidth()));
                            b = Bitmap.createScaledBitmap(f, Math.max(1, Math.round(f.getWidth() * sc)),
                                    Math.max(1, Math.round(f.getHeight() * sc)), true);
                        }
                    }
                } catch (Exception ignored) {
                } finally {
                    try { r.release(); } catch (Exception ignored) { }
                    if (tmp != null) try { tmp.delete(); } catch (Exception ignored) { }
                }
            } else if (b == null) {
                MediaMetadataRetriever r = new MediaMetadataRetriever();
                try {
                    Map<String, String> hdr = new HashMap<>();
                    if (ua != null) hdr.put("User-Agent", ua);
                    if (referer != null) hdr.put("Referer", referer);
                    if (cookie != null) hdr.put("Cookie", cookie);
                    r.setDataSource(mediaUrl, hdr);
                    String dStr = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    if (dStr != null) {
                        try { durMs = Long.parseLong(dStr); } catch (Exception ignored) { }
                    }
                    long atUs = durMs > 6000 ? 2000000L : 0L;
                    if (Build.VERSION.SDK_INT >= 27) {
                        b = r.getScaledFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 320, 180);
                    } else {
                        Bitmap f = r.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                        if (f != null) {
                            float sc = Math.min(1f, 320f / Math.max(1, f.getWidth()));
                            b = Bitmap.createScaledBitmap(f, Math.max(1, Math.round(f.getWidth() * sc)),
                                    Math.max(1, Math.round(f.getHeight() * sc)), true);
                        }
                    }
                } catch (Exception ignored) {
                } finally {
                    try { r.release(); } catch (Exception ignored) { }
                }
            }
            final Bitmap fb = b;
            final long fDur = durMs;
            h.post(() -> {
                if (fb != null) thumbs.put(mediaUrl, fb);
                if (rowIdx >= 0 && fDur > 0 && !rDurs.containsKey(rowIdx)) rDurs.put(rowIdx, fDur);
                if (rowIdx >= 0) paintRow(rowIdx);
                if (onDone != null) onDone.run();
            });
        });
    }

    // ---------------- JELAJAH & DETEKSI VIDEO (seperti IDM) ----------------

    static boolean isMediaUrl(String u) {
        if (u == null || !u.startsWith("http") || SEGMENT.matcher(u).find() || isAd(u)) return false;
        return MEDIA.matcher(u).find() || VIDEO_HINT.matcher(u).find();
    }

    /** Buka link di pratinjau seperti browser; semua video yang dimuat halaman dicatat dan bisa diunduh. */
    void startBrowse() {
        if (mode == Mode.SCAN || mode == Mode.CAPTURE) return;
        String url = urlInput.getText().toString().trim();
        if (!url.isEmpty() && !url.contains("://") && url.contains(".")) url = "https://" + url;
        Uri u = Uri.parse(url);
        if (u.getHost() == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            setStatus("Link belum benar. Tempel link halaman yang berisi video.", Ui.PULSE);
            return;
        }
        prefs.edit().putString(key("url"), url).apply();
        sniffing = true;
        sniffPage = url;
        sniffed.clear();
        previewOpen = true;
        recordHistory(url, String.valueOf(u.getHost()), 1, 0, true);
        web.loadUrl(url);
        final int s = ++sniffSeq;
        sniffPolls = 0;
        h.postDelayed(() -> sniffPoll(s), 2500);
        renderSniff();
        refreshUi();
        mainScroll.post(() -> mainScroll.smoothScrollTo(0, prevCard.getTop()));
        setStatus("Halaman dibuka. Putar videonya, lalu tekan Unduh di daftar Video terdeteksi.", Ui.ION);
    }

    void stopSniff() {
        sniffing = false;
        sniffSeq++;
        sniffed.clear();
        renderSniff();
        refreshUi();
    }

    /** Baca juga sumber dari elemen video di halaman (termasuk iframe yang bisa diakses). */
    void sniffPoll(final int s) {
        if (s != sniffSeq || !sniffing) return;
        // beberapa detik pertama: tekan play otomatis (tanpa suara) dan lewati iklan, agar video termuat
        if (sniffPolls++ < 8 && mode != Mode.SCAN && mode != Mode.CAPTURE) web.evaluateJavascript(JS_PLAY, null);
        if (mode != Mode.SCAN && mode != Mode.CAPTURE) {
            web.evaluateJavascript(JS_POLL, v -> {
                if (s != sniffSeq || !sniffing) return;
                try {
                    JSONObject jo = new JSONObject(v);
                    String pst = jo.optString("poster", "");
                    if (!pst.isEmpty()) sniffPoster = pst;
                    JSONArray m = jo.optJSONArray("media");
                    if (m != null) for (int i = 0; i < m.length(); i++) {
                        JSONArray it = m.optJSONArray(i);
                        if (it != null) onSniff(it.optString(0, ""), true);
                    }
                } catch (Exception ignored) { }
            });
        }
        h.postDelayed(() -> sniffPoll(s), 2000);
    }

    void onSniff(String u, boolean fromVideoTag) {
        if (!sniffing || mode == Mode.SCAN || mode == Mode.CAPTURE || u == null || !u.startsWith("http")) return;
        if (isAd(u) || SEGMENT.matcher(u).find() || u.toLowerCase(Locale.ROOT).contains(".mpd")) return;
        if (!fromVideoTag && !isMediaUrl(u)) return;
        if (sniffed.containsKey(u) || sniffed.size() >= MAX_SNIFF) return;
        String kind = isHls(u) ? "Stream HLS" : extOf(u).substring(1).toUpperCase(Locale.ROOT);
        sniffed.put(u, new String[]{kind, "menghitung ukuran ...", ""});
        renderSniff();
        if (mode == Mode.IDLE) setStatus(sniffed.size() + " video terdeteksi. Pilih video yang ingin diunduh di bawah.", Ui.ION);
        final String ref = web.getUrl(), ua = web.getSettings().getUserAgentString();
        final String cookie = CookieManager.getInstance().getCookie(u);
        fetchRemoteThumb(u, ref, ua, cookie, sniffPoster, -1, this::renderSniff);
        if (isHls(u)) {
            bg.execute(() -> {
                final long[] est = HlsDownloader.estimate(u, ua, ref);
                h.post(() -> {
                    String[] e = sniffed.get(u);
                    if (e == null) return;
                    e[1] = sizeLabel(est, true);
                    renderSniff();
                });
            });
            return;
        }
        bg.execute(() -> {
            final long sz = probeSize(u, ref, ua, cookie);
            h.post(() -> {
                String[] e = sniffed.get(u);
                if (e == null) return;
                e[1] = sz > 0 ? fmtSize(sz) : "ukuran tidak diketahui";
                renderSniff();
            });
        });
    }

    void renderSniff() {
        if (sniffBox == null) return;
        sniffBox.removeAllViews();
        sniffTitle.setText(sniffed.isEmpty() ? "Video terdeteksi" : "Video terdeteksi (" + sniffed.size() + ")");
        if (sniffed.isEmpty()) {
            TextView e = Ui.hint(this, "Mencari video ... Jika belum muncul, tekan tombol play pada video di halaman web di atas.");
            e.setPadding(dp(4), dp(6), dp(4), dp(6));
            sniffBox.addView(e);
            return;
        }
        int sIdx = 0;
        for (final Map.Entry<String, String[]> en : sniffed.entrySet()) {
            final String url = en.getKey();
            final String[] info = en.getValue();
            LinearLayout r = Ui.row(this);
            r.setBackground(Ui.box(this, Ui.PANEL, 12, Ui.LINE));
            r.setPadding(dp(10), dp(10), dp(10), dp(10));

            ImageView th = thumbView();
            Bitmap b = thumbs.get(url);
            if (b == null && !sniffPoster.isEmpty()) b = thumbs.get(sniffPoster);
            th.setImageBitmap(b != null ? b : placeholderThumb(sIdx++));
            th.setOnClickListener(v -> {
                ArrayList<String> one = new ArrayList<>(), names = new ArrayList<>();
                one.add(url);
                names.add(sniffTitleFor(url));
                PlayerActivity.open(this, one, names, 0, web.getUrl(), web.getSettings().getUserAgentString());
            });
            r.addView(th, new LinearLayout.LayoutParams(dp(88), dp(50)));

            LinearLayout mid = Ui.col(this);
            mid.setPadding(dp(10), 0, dp(6), 0);
            TextView t = Ui.text(this, sniffTitleFor(url), 13.5f, Ui.TEXT, Ui.MEDIUM);
            t.setMaxLines(1);
            t.setEllipsize(TextUtils.TruncateAt.END);
            mid.addView(t);
            mid.addView(Ui.text(this, info[0] + " \u00B7 " + info[1], 12, Ui.ION, Ui.MEDIUM), Ui.fill(this, 2));
            TextView link = Ui.hint(this, url);
            link.setTextSize(11);
            link.setSingleLine(true);
            link.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            mid.addView(link);
            r.addView(mid, Ui.weight(1));
            boolean added = "1".equals(info[2]);
            TextView dl = smallBtn(added ? "\u2713 Diunduh" : "\u2B07 Unduh", added ? Ui.MUTED : Ui.ION);
            dl.setEnabled(!added);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            bl.leftMargin = dp(6);
            TextView play = smallBtn("\u25B6", Ui.ION);
            r.addView(play, bl);
            play.setOnClickListener(v -> {
                ArrayList<String> one = new ArrayList<>(), names = new ArrayList<>();
                one.add(url);
                names.add(sniffTitleFor(url));
                PlayerActivity.open(this, one, names, 0, web.getUrl(), web.getSettings().getUserAgentString());
            });
            LinearLayout.LayoutParams bl2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            bl2.leftMargin = dp(6);
            r.addView(dl, bl2);
            dl.setOnClickListener(v -> {
                if (enqueueDirect(url, web.getUrl(), sniffTitleFor(url), isHls(url) ? "" : extOf(url))) {
                    info[2] = "1";
                    renderSniff();
                }
            });
            sniffBox.addView(r, Ui.fill(this, 8));
        }
    }

    /** Nama file dari judul halaman; kalau tidak ada, dari nama file di link. */
    String sniffTitleFor(String url) {
        String t = web.getTitle();
        if (t != null && !t.trim().isEmpty() && !t.startsWith("http") && !t.equals("about:blank")) {
            int n = 0;
            for (String[] e : sniffed.values()) if ("1".equals(e[2])) n++;
            return n > 0 ? t.trim() + " (" + (n + 1) + ")" : t.trim();
        }
        String p = Uri.parse(url).getPath();
        return p == null ? "video" : stripExt(Uri.decode(lastSegment(p)));
    }

    /** Halaman memicu unduhan file (bukan hanya video): diunduh langsung seperti IDM. */
    void onWebDownload(String url, String disposition, String mime, long length) {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            setStatus("Unduhan dari halaman ini tidak didukung (bukan link http).", Ui.AMBER);
            return;
        }
        final String fname = sanitize(URLUtil.guessFileName(url, disposition, mime));
        final String ref = web.getUrl();
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Unduh file ini?")
                .setMessage(fname + "\n\nUkuran: " + (length > 0 ? fmtSize(length) : "tidak diketahui"))
                .setPositiveButton("Unduh", (d, w) -> enqueueDirect(url, ref, fname, ""))
                .setNegativeButton("Batal", null)
                .show();
    }

    /**
     * Tambahkan satu link yang sudah pasti (hasil deteksi) ke daftar Video dan langsung unduh,
     * tanpa tahap pengumpulan link. Bisa dipanggil saat antrean unduhan sedang berjalan.
     */
    boolean enqueueDirect(String url, String referer, String title, String ext) {
        if (mode == Mode.SCAN || mode == Mode.CAPTURE) {
            setStatus("Tunggu proses yang sedang berjalan selesai dulu.", Ui.AMBER);
            return false;
        }
        if (Build.VERSION.SDK_INT < 29 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 3);
            setStatus("Izinkan akses penyimpanan, lalu tekan Unduh lagi.", Ui.AMBER);
            return false;
        }
        int i = videos.size(), n = i + 1;
        videos.add(new String[]{url, title, ""});
        vState = Arrays.copyOf(vState, n);
        vPct = Arrays.copyOf(vPct, n);
        vNote = Arrays.copyOf(vNote, n);
        if (rUrl == null || rRef == null || rName == null) {
            rUrl = new String[n];
            rRef = new String[n];
            rName = new String[n];
        } else {
            rUrl = Arrays.copyOf(rUrl, n);
            rRef = Arrays.copyOf(rRef, n);
            rName = Arrays.copyOf(rName, n);
        }
        String name = sanitize(title) + ext;
        rUrl[i] = url;
        rRef[i] = referer == null ? url : referer;
        rName[i] = name;
        vState[i] = ST_READY;
        vNote[i] = name;
        dlSelected.add(i);
        buildRows();
        dlQueue.add(i);
        if (mode == Mode.IDLE) {
            mode = Mode.DOWNLOAD;
            log("\nMengunduh dari halaman: " + name);
            setStatus("Mengunduh " + name + " ...", Ui.AMBER);
        } else {
            log("   ditambahkan ke antrean: " + name);
        }
        pump();
        startTracker();
        refreshUi();
        return true;
    }


    // ---------------- PINDAI FOLDER (termasuk subfolder) ----------------

    void startScan() {
        String url = urlInput.getText().toString().trim();
        Uri u = Uri.parse(url);
        if (u.getHost() == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            setStatus("Link belum benar. Contoh: https://filecoster.com/folder/...", Ui.PULSE);
            return;
        }
        String p = patternInput.getText().toString().trim();
        String fp = folderPatternInput.getText().toString().trim();
        try {
            linkPattern = Pattern.compile(p.isEmpty() ? DEFAULT_PATTERN : p, Pattern.CASE_INSENSITIVE);
            folderPattern = fp.isEmpty() ? null : Pattern.compile(fp, Pattern.CASE_INSENSITIVE);
        } catch (Exception e) {
            setStatus("Pola link tidak valid: " + e.getMessage(), Ui.PULSE);
            return;
        }
        baseDomain = baseDomain(u.getHost());
        folderPath = u.getPath() == null ? "" : u.getPath();
        // awalan link folder, mis. /folder/ABC -> /folder/
        String trimmed = stripSlash(folderPath);
        int cut = trimmed.lastIndexOf('/');
        folderPrefix = cut > 0 ? trimmed.substring(0, cut + 1) : "";
        allowedHosts.clear();
        pageQueue.clear();
        seenPages.clear();
        found.clear();
        allLinks.clear();
        videos.clear();
        doneUri.clear();
        rSizes.clear();
        rDurs.clear();
        rPoster.clear();
        dlSelected.clear();
        foldersScanned = 0;
        subfolderCount = 0;
        scanTitle = null;
        scanUrl = url;
        historyUrl = "";
        logView.setText("");
        vState = new int[0];
        vPct = new int[0];
        vNote = new String[0];
        rUrl = null;
        sniffing = false;            // WebView utama dipakai untuk memindai
        sniffSeq++;
        sniffed.clear();
        mode = Mode.SCAN;
        prefs.edit().putString(key("url"), url).apply();
        buildRows();
        refreshUi();
        seenPages.add(stripSlash(url));
        openScanPage(new String[]{url, "", "0"});
    }

    void openScanPage(String[] page) {
        curPage = page;
        foldersScanned++;
        String where = page[1].isEmpty() ? "(folder utama)" : page[1];
        log("Memindai " + where + " ...");
        setStatus("Memindai " + where, Ui.AMBER);
        refreshUi();
        web.loadUrl(page[0]);
    }

    void onScanPageLoaded() {
        final int seq = ++scanSeq;
        h.postDelayed(() -> { if (seq == scanSeq) scrollStep(seq, 0, -1); }, 2500);
    }

    void scrollStep(final int seq, final int n, final long lastH) {
        web.evaluateJavascript(JS_SCROLL, v -> {
            if (seq != scanSeq) return;
            long height = -2;
            try { height = (long) Double.parseDouble(v); } catch (Exception ignored) { }
            final long hh = height;
            if (n < 25 && hh != lastH) {
                h.postDelayed(() -> { if (seq == scanSeq) scrollStep(seq, n + 1, hh); }, 800);
            } else {
                collectLinks(seq);
            }
        });
    }

    boolean isFolderLink(String href, String path, String text, boolean inNav) {
        if (folderPattern != null) return folderPattern.matcher(href).find();
        if (inNav || BACK_TEXT.matcher(text.trim()).find()) return false;
        if (FOLDER_KEYWORD.matcher(path).find()) return true;
        return folderPrefix.length() > 1 && path.startsWith(folderPrefix)
                && path.length() > folderPrefix.length();
    }

    void collectLinks(final int seq) {
        final String current = web.getUrl() == null ? curPage[0] : web.getUrl();
        final String curPath = Uri.parse(current).getPath() == null ? "" : Uri.parse(current).getPath();
        final String rel = curPage[1];
        final int depth = Integer.parseInt(curPage[2]);
        web.evaluateJavascript(JS_LINKS, v -> {
            if (seq != scanSeq) return;
            if (scanTitle == null) {
                String wt = web.getTitle();
                if (wt != null && !wt.trim().isEmpty() && !wt.startsWith("http") && !wt.equals("about:blank")) scanTitle = wt.trim();
            }
            int newVideos = 0, newFolders = 0;
            try {
                JSONArray arr = new JSONArray(v);
                for (int i = 0; i < arr.length(); i++) {
                    JSONArray it = arr.getJSONArray(i);
                    String href = it.getString(0);
                    String text = it.optString(1, "");
                    boolean inNav = it.optInt(2, 0) == 1;
                    int hash = href.indexOf('#');
                    if (hash >= 0) href = href.substring(0, hash);
                    Uri u = Uri.parse(href);
                    String host = u.getHost();
                    String scheme = u.getScheme();
                    if (host == null || scheme == null || !scheme.startsWith("http")) continue;
                    if (!sameSite(host)) continue;
                    String key = stripSlash(href);
                    if (key.equals(stripSlash(current))) continue;
                    String path = u.getPath() == null ? "" : u.getPath();
                    if (SKIP.matcher(path).find()) continue;
                    if (!allLinks.containsKey(href)) allLinks.put(href, text);
                    String q = u.getQuery() == null ? "" : u.getQuery();
                    if (path.equals(curPath) && q.matches("(?i).*(^|&)(page|p)=\\d+.*")) {
                        if (!seenPages.contains(key)) {        // halaman berikutnya dari folder yang sama
                            seenPages.add(key);
                            pageQueue.addFirst(new String[]{href, rel, String.valueOf(depth)});
                        }
                    } else if (isFolderLink(href, path, text, inNav)) {
                        if (subBox.isChecked() && !seenPages.contains(key) && depth < MAX_DEPTH) {
                            seenPages.add(key);
                            String name = sanitize(text.isEmpty() ? lastSegment(path) : text);
                            String childRel = rel.isEmpty() ? name : rel + "/" + name;
                            pageQueue.add(new String[]{href, childRel, String.valueOf(depth + 1)});
                            newFolders++;
                        }
                    } else if (linkPattern.matcher(href).find() && !found.containsKey(href)) {
                        found.put(href, new String[]{text, rel});
                        newVideos++;
                    }
                }
            } catch (Exception e) {
                log("Gagal membaca halaman: " + e.getMessage());
            }
            subfolderCount += newFolders;
            log("   " + newVideos + " video" + (subBox.isChecked() ? ", " + newFolders + " subfolder" : ""));
            refreshUi();
            nextScanPage();
        });
    }

    void nextScanPage() {
        if (!pageQueue.isEmpty() && foldersScanned < MAX_SCAN_PAGES) {
            openScanPage(pageQueue.poll());
            return;
        }
        if (!pageQueue.isEmpty()) log("Batas " + MAX_SCAN_PAGES + " halaman tercapai, sisa folder dilewati.");
        finishScan();
    }

    void finishScan() {
        mode = Mode.IDLE;
        scanSeq++;
        if (found.isEmpty()) {
            setStatus("Tidak ada video terdeteksi. Lihat Catatan proses untuk detailnya.", Ui.PULSE);
            buildRows();
            showTab(1);
            refreshUi();
            log("Tidak ada link video yang cocok. Link yang ada di halaman:");
            int n = 0;
            for (Map.Entry<String, String> e : allLinks.entrySet()) {
                if (n++ >= 60) break;
                log("  " + e.getKey() + "  |  " + e.getValue());
            }
            log("\nIsi kolom 'Pola link video' (dan/atau 'Pola link folder') dengan bagian khas dari link-nya, lalu pindai lagi.");
            return;
        }
        for (Map.Entry<String, String[]> e : found.entrySet()) {
            videos.add(new String[]{e.getKey(), e.getValue()[0], e.getValue()[1]});
        }
        log("\nSelesai memindai " + foldersScanned + " halaman. Ditemukan " + videos.size() + " video:");
        String lastRel = null;
        for (int i = 0; i < videos.size(); i++) {
            String[] v = videos.get(i);
            if (!v[2].equals(lastRel)) {
                lastRel = v[2];
                log("[" + (lastRel.isEmpty() ? "folder utama" : lastRel) + "]");
            }
            log(String.format("%4d. %s", i + 1, v[1].isEmpty() ? v[0] : v[1]));
        }
        vState = new int[videos.size()];
        vPct = new int[videos.size()];
        vNote = new String[videos.size()];
        if (scanUrl != null) {
            Uri su = Uri.parse(scanUrl);
            String sp = su.getPath() == null ? "" : lastSegment(su.getPath());
            String hname = scanTitle != null ? sanitize(scanTitle)
                    : (sp.isEmpty() ? String.valueOf(su.getHost()) : sanitize(Uri.decode(sp)));
            recordHistory(scanUrl, hname, videos.size(), subfolderCount, false);
        }
        buildRows();
        showTab(0);
        setStatus(videos.size() + " video ditemukan. Mengumpulkan link, ukuran & thumbnail ...", Ui.AMBER);
        refreshUi();
        startCollect();
    }

    static String lastSegment(String path) {
        String t = stripSlash(path);
        int i = t.lastIndexOf('/');
        return i >= 0 ? t.substring(i + 1) : t;
    }

    // ---------------- TANGKAP & UNDUH ----------------

    void startCollect() {
        if (videos.isEmpty()) return;
        if (Build.VERSION.SDK_INT < 29 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            return;
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs.edit().putInt("base", baseSpin.getSelectedItemPosition())
                .putInt("save", saveSpin.getSelectedItemPosition())
                .putBoolean("sub", subBox.isChecked())
                .putBoolean("ads", adBox.isChecked())
                .putString("folder", cleanFolder(folderInput.getText().toString())).apply();
        log("Disimpan ke: " + destLabel());
        log("Tahap 1: mengumpulkan link unduhan semua video (belum ada yang diunduh).");
        mode = Mode.CAPTURE;
        paused = false;
        nextIdx = 0;
        ok = 0;
        fail = 0;
        int n = videos.size();
        if (rUrl == null || rUrl.length != n) {
            rUrl = new String[n];
            rRef = new String[n];
            rName = new String[n];
        }
        capTotal = Math.max(1, pendingCount());
        int want = Math.max(1, Math.min(collectParallel(), capTotal));
        capLimit = want;
        lastTrimAt = 0;
        releaseWorkers();
        for (int k = 0; k < want; k++) workers.add(new Worker(k, want));
        layoutWorkers();
        if (want >= MAX_COLLECT) log("\u26A0 " + want + " proses sekaligus sangat berat untuk kebanyakan HP. Jika HP lag atau panas, turunkan di Pengaturan.");
        log("Proses pengumpul link: " + want + " sekaligus (rekomendasi HP ini: " + profile.recCollect + ").");
        showTab(0);
        refreshUi();
        updateCaptureStatus();
        for (Worker w : workers) w.scheduleNext(w.slot * 1200L);   // mulai bergantian agar tidak berebut sekaligus
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        if (code != 1) return;
        if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) startCollect();
        else log("Izin penyimpanan ditolak, tidak bisa mengunduh.");
    }

    // ---------------- TAHAP 1: KUMPULKAN LINK (beberapa proses sekaligus) ----------------

    int runningWorkers() {
        int n = 0;
        for (Worker w : workers) if (w.active) n++;
        return n;
    }

    /** Ambil video berikutnya yang belum dikerjakan; -1 jika sudah habis. */
    int claimNext() {
        while (nextIdx < vState.length) {
            int i = nextIdx++;
            if (i < videos.size() && (vState[i] == ST_WAIT || vState[i] == ST_FAIL)) return i;
        }
        return -1;
    }

    void updateCaptureStatus() {
        int run = runningWorkers();
        setStatus("Mengumpulkan link " + Math.min(ok + fail, capTotal) + " dari " + capTotal
                + (run > 1 ? " (" + run + " proses sekaligus)" : ""), Ui.AMBER);
    }

    void finishCapture() {
        mode = Mode.IDLE;
        releaseWorkersLater();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        int rd = countState(ST_READY), failedNow = countState(ST_FAIL);
        log("\nTahap 1 selesai: " + rd + " link terkumpul, " + failedNow + " gagal.");
        if (rd > 0 && autoBox.isChecked()) {
            selectAllReady(true);
            startDownloads();
        } else if (hasActiveDownloads()) {
            mode = Mode.DOWNLOAD;
            setStatus("Pengumpulan link selesai. Melanjutkan unduhan yang sedang berjalan ...", Ui.AMBER);
            refreshUi();
        } else {
            setStatus(rd + " link siap" + (failedNow > 0 ? ", " + failedNow + " gagal" : "")
                    + (rd > 0 ? ". Pilih video di bawah lalu tekan Unduh." : "."), failedNow > 0 ? Ui.AMBER : Ui.ION);
            refreshUi();
        }
    }

    void releaseWorkers() {
        final List<Worker> old = new ArrayList<>(workers);
        workers.clear();
        for (Worker w : old) w.release();
    }

    /** Dipakai dari dalam callback WebView: WebView baru dibongkar setelah callback selesai. */
    void releaseWorkersLater() {
        final List<Worker> old = new ArrayList<>(workers);
        workers.clear();
        h.post(() -> { for (Worker w : old) w.release(); });
    }

    // Jika Android memberi tanda memori menipis, jumlah proses dikurangi satu per satu.
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (mode != Mode.CAPTURE) return;
        if (level < ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW || level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastTrimAt < 8000) return;
        lastTrimAt = now;
        if (capLimit > 1) {
            capLimit--;
            log("\n[memori menipis] proses pengumpul dikurangi menjadi " + capLimit + " sekaligus.");
            setStatus("Memori HP menipis, proses dikurangi menjadi " + capLimit + " sekaligus.", Ui.AMBER);
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mode == Mode.CAPTURE && capLimit > 1) {
            capLimit = 1;
            log("\n[memori hampir habis] proses pengumpul diturunkan menjadi 1.");
        }
    }

    /** Satu proses pengumpul link: punya WebView dan state sendiri. */
    final class Worker {
        final int slot;
        final WebView wv;
        final LinearLayout holder;
        final int hdp;
        final Set<String> captured = Collections.synchronizedSet(new LinkedHashSet<String>());
        final LinkedHashMap<String, Double> cand = new LinkedHashMap<>();
        final Set<String> readyUrls = new HashSet<>();
        final Set<String> allowed = new HashSet<>();
        long lastNewCand, deadline;
        int item = -1, pollCount, seq, stepSeq, retries;
        boolean active, jumpedToIframe, earlyDecision, fastOff;
        String pageTitle, pagePoster;

        Worker(int slot, int total) {
            this.slot = slot;
            holder = Ui.col(MainActivity.this);
            holder.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            holder.addView(Ui.hint(MainActivity.this, total > 1 ? "Proses " + (slot + 1) + " dari " + total : "Proses pengumpulan"));
            wv = new QuietWebView(MainActivity.this);
            wv.setBackgroundColor(Color.BLACK);
            WebSettings st = wv.getSettings();
            st.setJavaScriptEnabled(true);
            st.setDomStorageEnabled(true);
            st.setMediaPlaybackRequiresUserGesture(false);
            st.setSupportMultipleWindows(true);
            st.setJavaScriptCanOpenWindowsAutomatically(false);
            CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true);
            wv.setWebChromeClient(new WebChromeClient() {
                @Override
                public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message msg) {
                    return false; // blokir pop-up iklan
                }
            });
            wv.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                    if (mode == Mode.IDLE) return false;
                    return !isAllowed(req.getUrl().toString());
                }

                @Override
                public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
                    if (req.isForMainFrame()) onError(String.valueOf(err.getDescription()));
                }

                @Override
                public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                    String u = req.getUrl().toString();
                    if (adBlock && !req.isForMainFrame() && MainActivity.isAd(u)) {
                        return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
                    }
                    note(u);
                    return Net.fetch(req);      // lewat DNS Google; null = jaringan bawaan WebView
                }

                @Override
                public void onLoadResource(WebView view, String url) {
                    note(url);
                }
            });
            hdp = Math.max(120, Math.min(240, 480 / Math.max(1, total)));
            LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(hdp));
            wlp.topMargin = dp(4);
            holder.addView(wv, wlp);
        }

        void release() {
            seq++;
            stepSeq++;
            active = false;
            try { wv.stopLoading(); } catch (Exception ignored) { }
            detach(holder);
            holder.removeAllViews();
            try { wv.destroy(); } catch (Exception ignored) { }
        }

        void wl(String s) {
            log((workers.size() > 1 && item >= 0 ? "[" + (item + 1) + "] " : "") + s);
        }

        boolean isAllowed(String url) {
            String host = Uri.parse(url).getHost();
            if (host == null) return true;
            return sameSite(host) || allowed.contains(host);
        }

        void note(String u) {
            if (mode == Mode.CAPTURE && active && isMediaUrl(u) && !u.toLowerCase(Locale.ROOT).contains(".mpd")) {
                captured.add(u);
            }
        }

        void scheduleNext(long delay) {
            final int s = ++stepSeq;
            h.postDelayed(() -> { if (s == stepSeq && !paused) pickNext(); }, delay);
        }

        void scheduleBegin(long delay) {
            final int s = ++stepSeq;
            h.postDelayed(() -> { if (s == stepSeq && !paused && active && mode == Mode.CAPTURE) begin(); }, delay);
        }

        /** Proses ini selesai atau tidak dibutuhkan lagi: lepas WebView-nya agar RAM kembali. */
        void drop() {
            workers.remove(this);
            h.post(this::release);
        }

        void pickNext() {
            if (paused || mode != Mode.CAPTURE || !workers.contains(this)) return;
            active = false;
            item = -1;
            if (runningWorkers() >= capLimit) {          // jatah proses dikurangi: proses ini berhenti
                drop();
                return;
            }
            int i = claimNext();
            if (i < 0) {
                drop();
                if (runningWorkers() == 0) finishCapture();
                return;
            }
            item = i;
            active = true;
            retries = 0;
            begin();
        }

        void begin() {
            String[] v = videos.get(item);
            setState(item, ST_SEARCH, "Membuka halaman dan menekan play");
            captured.clear();
            cand.clear();
            readyUrls.clear();
            allowed.clear();
            lastNewCand = 0;
            earlyDecision = false;
            fastOff = false;
            pageTitle = null;
            pagePoster = null;
            jumpedToIframe = false;
            pollCount = 0;
            deadline = System.currentTimeMillis() + CAPTURE_TIMEOUT_MS;
            final int s = ++seq;
            log("\n[" + (item + 1) + "/" + videos.size() + "] " + (v[2].isEmpty() ? "" : v[2] + " / ")
                    + (v[1].isEmpty() ? v[0] : v[1]));
            updateCaptureStatus();
            refreshUi();
            String vpath = Uri.parse(v[0]).getPath();
            if (vpath != null && DIRECT.matcher(vpath).find()) {
                // link langsung ke file video: tidak perlu membuka halaman atau mencari sumbernya
                pageTitle = stripExt(Uri.decode(lastSegment(vpath)));
                wl(isHls(v[0]) ? "   link stream m3u8 langsung, diunduh tanpa membuka halaman"
                        : "   file video langsung, diunduh tanpa membuka halaman");
                Uri vu = Uri.parse(v[0]);
                storeLink(v[0], vu.getScheme() + "://" + vu.getHost() + "/");
                return;
            }
            wv.loadUrl(v[0]);
            h.postDelayed(() -> poll(s), 3000);
        }

        void onError(String desc) {
            if (mode != Mode.CAPTURE || paused || !active) return;
            if (retries < 2) {
                retries++;
                wl("   halaman gagal dimuat (" + desc + "), mencoba lagi (" + retries + "/2)...");
                seq++;
                scheduleBegin(4000);
            }
        }

        void failItem(String reason) {
            wl("   x " + reason);
            MainActivity.this.fail++;
            setState(item, ST_FAIL, reason);
            refreshUi();
        }

        void advance() {
            seq++;
            active = false;
            item = -1;
            updateCaptureStatus();
            scheduleNext(1500);
        }

        boolean fastReady() {
            if (fastOff || !fastBox.isChecked()) return false;
            for (String u : readyUrls) {
                if (cand.containsKey(u) && !isHls(u)) return true;
            }
            return false;
        }

        void poll(final int sq) {
            if (sq != seq) return;
            wv.evaluateJavascript(JS_POLL, v -> {
                if (sq != seq) return;
                List<String> iframes = new ArrayList<>();
                try {
                    JSONObject o = new JSONObject(v);
                    String pst = o.optString("poster", "");
                    if (!pst.isEmpty() && pagePoster == null) pagePoster = pst;
                    JSONArray m = o.optJSONArray("media");
                    JSONArray f = o.optJSONArray("iframes");
                    if (m != null) for (int i = 0; i < m.length(); i++) {
                        JSONArray it = m.optJSONArray(i);
                        if (it != null) addCandidate(it.optString(0, ""), it.optDouble(1, 0), it.optInt(2, 0));
                    }
                    if (f != null) for (int i = 0; i < f.length(); i++) iframes.add(f.getString(i));
                } catch (Exception ignored) { }
                List<String> net;
                synchronized (captured) { net = new ArrayList<>(captured); }
                for (String u : net) addCandidate(u, 0, 0);
                pollCount++;
                long now = System.currentTimeMillis();
                if (pageTitle == null && !jumpedToIframe) {
                    String t = wv.getTitle();
                    if (t != null && !t.isEmpty() && !t.startsWith("http") && !t.equals("about:blank")) pageTitle = t;
                }

                boolean fast = !cand.isEmpty() && fastReady();
                if (!cand.isEmpty() && (fast || now - lastNewCand >= SETTLE_MS || now > deadline)) {
                    earlyDecision = fast;
                    if (fast) {
                        wl("   video siap diputar, langsung diambil");
                        setState(item, ST_SEARCH, "Video siap diputar, memulai unduhan");
                    }
                    decide(sq);
                    return;
                }
                if (now > deadline) {
                    failItem("sumber video tidak tertangkap");
                    advance();
                    return;
                }
                boolean real = hasRealCandidate();
                if (!real && pollCount % 2 == 1 && pollCount <= 30) {
                    tapPlay(sq);                                 // ketuk tombol play seperti jari sungguhan
                } else if (pollCount % 2 == 0 && pollCount <= 30) {
                    wv.evaluateJavascript(JS_PLAY, null);        // lewati/percepat iklan, pastikan video berputar
                }
                if (!jumpedToIframe && pollCount >= 6 && !real && !iframes.isEmpty()) {
                    String target = pickIframe(iframes);
                    if (target != null) {
                        jumpedToIframe = true;
                        String host = Uri.parse(target).getHost();
                        if (host != null) allowed.add(host);
                        Map<String, String> hdr = new HashMap<>();
                        String cur = wv.getUrl();
                        if (cur != null) hdr.put("Referer", cur);
                        wv.loadUrl(target, hdr);
                    }
                }
                h.postDelayed(() -> poll(sq), 1500);
            });
        }

        void tapPlay(final int sq) {
            wv.evaluateJavascript(JS_FIND_PLAY, v -> {
                if (sq != seq) return;
                try {
                    JSONArray a = new JSONArray(v);
                    double cx = a.getDouble(0), cy = a.getDouble(1), iw = a.getDouble(2), ih = a.getDouble(3);
                    if (iw <= 0 || ih <= 0) return;
                    float x = (float) (cx * wv.getWidth() / iw);
                    float y = (float) (cy * wv.getHeight() / ih);
                    x = Math.max(2, Math.min(wv.getWidth() - 2, x));
                    y = Math.max(2, Math.min(wv.getHeight() - 2, y));
                    nativeTap(x, y);
                } catch (Exception e) {
                    nativeTap(wv.getWidth() / 2f, wv.getHeight() / 2f);
                }
            });
        }

        void nativeTap(final float x, final float y) {
            final long t = SystemClock.uptimeMillis();
            MotionEvent down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0);
            wv.dispatchTouchEvent(down);
            down.recycle();
            h.postDelayed(() -> {
                MotionEvent up = MotionEvent.obtain(t, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0);
                wv.dispatchTouchEvent(up);
                up.recycle();
            }, 90);
        }

        void addCandidate(String u, double dur, int ready) {
            if (u == null || !u.startsWith("http") || SEGMENT.matcher(u).find() || MainActivity.isAd(u)) return;
            if (ready >= 3 && dur >= AD_MAX_DURATION) readyUrls.add(u);
            Double old = cand.get(u);
            if (old == null) {
                if (cand.size() >= 20) return;
                cand.put(u, dur);
                lastNewCand = System.currentTimeMillis();
            } else if (dur > 0 && old <= 0) {
                cand.put(u, dur);
            }
        }

        boolean hasRealCandidate() {
            for (Map.Entry<String, Double> e : cand.entrySet()) {
                if (isHls(e.getKey())) continue;
                double d = e.getValue();
                if (d <= 0 || d >= AD_MAX_DURATION) return true;
            }
            return false;
        }

        void decide(final int sq) {
            final List<String> urls = new ArrayList<>(cand.keySet());
            final List<Double> durs = new ArrayList<>(cand.values());
            final String referer = wv.getUrl();
            final String ua = wv.getSettings().getUserAgentString();
            final List<String> cookies = new ArrayList<>();
            for (String u : urls) cookies.add(CookieManager.getInstance().getCookie(u));
            wl("   memeriksa " + urls.size() + " kandidat...");
            setState(item, ST_SEARCH, "Memeriksa " + urls.size() + " kandidat video");
            bg.execute(() -> {
                final long[] sizes = new long[urls.size()];
                for (int i = 0; i < urls.size(); i++) {
                    sizes[i] = isHls(urls.get(i)) ? -1 : probeSize(urls.get(i), referer, ua, cookies.get(i));
                }
                h.post(() -> { if (sq == seq) choose(urls, durs, sizes, referer); });
            });
        }

        void choose(List<String> urls, List<Double> durs, long[] sizes, String referer) {
            int best = -1;
            String hls = null;
            for (int i = 0; i < urls.size(); i++) {
                String u = urls.get(i);
                double d = durs.get(i);
                long sz = sizes[i];
                boolean adLike = (d > 0 && d < AD_MAX_DURATION) || (sz >= 0 && sz < AD_MAX_SIZE);
                wl("   kandidat: " + fmtSize(sz) + ", " + fmtDur(d) + (adLike ? " [iklan?]" : "") + "\n     " + shorten(u));
                if (isHls(u)) {
                    if (hls == null || u.toLowerCase().contains("master")) hls = u;
                    continue;
                }
                if (adLike) continue;
                if (best < 0) { best = i; continue; }
                long bs = Math.max(sizes[best], 0), cs = Math.max(sz, 0);
                if (cs > bs || (cs == bs && d >= durs.get(best))) best = i;
            }
            if (best >= 0) {
                double bestDur = durs.get(best);
                if (bestDur > 0) rDurs.put(item, (long) (bestDur * 1000));
                storeLink(urls.get(best), referer, sizes[best]);
            } else if (earlyDecision && System.currentTimeMillis() < deadline) {
                earlyDecision = false;
                fastOff = true;
                wl("   kandidat siap putar ternyata bukan video utama, menunggu kandidat lain");
                final int again = seq;
                h.postDelayed(() -> poll(again), 1500);
            } else if (hls != null) {
                wl("   video berformat stream (m3u8): diunduh per potongan lalu disatukan");
                storeLink(hls, referer);
            } else {
                failItem("hanya iklan yang tertangkap, video asli tidak ditemukan");
                advance();
            }
        }

        void storeLink(String media, String referer) {
            storeLink(media, referer, -1);
        }

        /** Tahap 1: simpan link video yang berhasil ditemukan (beserta ukurannya). Pengunduhan baru dimulai di tahap 2. */
        void storeLink(String media, String referer, long size) {
            String[] v = videos.get(item);
            if (referer == null) referer = v[0];
            String title = !v[1].isEmpty() ? v[1]
                    : (pageTitle != null ? pageTitle : (wv.getTitle() == null ? "video" : wv.getTitle()));
            String prefix = videos.size() > 1 ? String.format("%03d - ", item + 1) : "";
            // stream m3u8: ekstensi (.ts/.mp4) ditentukan HlsDownloader setelah playlist dibaca
            String name = prefix + sanitize(title) + (isHls(media) ? "" : extOf(media));
            rUrl[item] = media;
            rRef[item] = referer;
            rName[item] = name;
            dlSelected.add(item);
            if (pagePoster != null && !pagePoster.isEmpty()) rPoster.put(item, pagePoster);
            ok++;
            wl("   link siap: " + name + "\n     " + shorten(media));
            if (size > 0) {
                rSizes.put(item, size);
                setState(item, ST_READY, name);
                final String ua = wv.getSettings().getUserAgentString();
                final String ck = CookieManager.getInstance().getCookie(media);
                fetchRemoteThumb(media, referer, ua, ck, pagePoster, item, null);
            } else {
                setState(item, ST_READY, name + " \u00B7 menghitung ukuran ...");
                measureLater(item, media, referer, name);
            }
            refreshUi();
            advance();
        }
    }




    static String cleanFolder(String f) {
        StringBuilder out = new StringBuilder();
        for (String part : f.replaceAll("[\\\\:*?\"<>|]", "").split("/")) {
            String t = part.trim();
            if (t.isEmpty() || t.equals(".") || t.equals("..")) continue;
            if (out.length() > 0) out.append('/');
            out.append(t);
        }
        return out.toString();
    }

    // folder tujuan (relatif terhadap Download/Movies/DCIM) untuk video dengan subfolder "rel"
    String targetSub(String rel) {
        int m = saveSpin.getSelectedItemPosition();
        if (m == 2) return "";
        String sub = cleanFolder(folderInput.getText().toString());
        if (m == 0 && rel != null && !rel.isEmpty()) sub = cleanFolder(sub.isEmpty() ? rel : sub + "/" + rel);
        return sub;
    }

    String destLabel() {
        String sub = targetSub(null);
        String label = BASE_NAMES[baseSpin.getSelectedItemPosition()] + (sub.isEmpty() ? "" : "/" + sub);
        return saveSpin.getSelectedItemPosition() == 0 ? label + " (beserta subfoldernya)" : label;
    }






    static long probeSize(String url, String referer, String ua, String cookie) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(10000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("Range", "bytes=0-0");
            c.setRequestProperty("User-Agent", ua);
            if (referer != null) c.setRequestProperty("Referer", referer);
            if (cookie != null) c.setRequestProperty("Cookie", cookie);
            int code = c.getResponseCode();
            if (code >= 400) return -1;
            String cr = c.getHeaderField("Content-Range");
            if (cr != null && cr.lastIndexOf('/') >= 0) {
                String t = cr.substring(cr.lastIndexOf('/') + 1).trim();
                if (!t.equals("*")) return Long.parseLong(t);
            }
            return code == 200 ? c.getContentLengthLong() : -1;
        } catch (Exception e) {
            return -1;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    String pickIframe(List<String> iframes) {
        for (String s : iframes) {
            String l = s.toLowerCase();
            if (isAd(s)) continue;
            if (l.contains("stream") || l.contains("embed") || l.contains("player")) return s;
        }
        for (String s : iframes) {
            if (isAd(s)) continue;
            String host = Uri.parse(s).getHost();
            if (host != null && sameSite(host)) return s;
        }
        return null;
    }




    void onMainFrameError(String desc) {
        if (mode == Mode.SCAN) {
            log("Halaman gagal dimuat (" + desc + "). Periksa koneksi, jangan matikan layar, lalu pindai lagi.");
            setStatus("Halaman gagal dimuat. Periksa koneksi internet.", Ui.PULSE);
        }
    }

    // Saat layar dimatikan / aplikasi ditinggal, Android memutus koneksi & menghentikan halaman web.
    // Proses dijeda dan dilanjutkan otomatis saat aplikasi dibuka lagi.
    @Override
    protected void onStop() {
        super.onStop();
        stoppedAt = SystemClock.elapsedRealtime();
        boolean keep = mode != Mode.IDLE && keepBox.isChecked() && KeepAliveService.running;
        if (keep) {
            // layar mati / aplikasi ditinggal: proses diteruskan, layanan latar depan menjaga CPU & Wi-Fi
            web.onResume();
            web.resumeTimers();
            for (Worker w : workers) w.wv.onResume();
            if (mode == Mode.CAPTURE) log("\n[layar mati] proses tetap berjalan di latar belakang.");
            return;
        }
        h.removeCallbacks(tracker);
        tracking = false;
        if (mode == Mode.CAPTURE && !paused) {
            paused = true;
            for (Worker w : workers) {
                w.seq++;
                w.stepSeq++;
            }
            log("\n|| Dijeda: layar mati atau aplikasi ditinggal. Akan dilanjutkan saat aplikasi dibuka lagi.");
            setStatus("Dijeda karena layar mati. Buka aplikasi untuk melanjutkan.", Ui.AMBER);
        }
    }

    @Override
    protected void onDestroy() {
        KeepAliveService.stopHook = null;
        KeepAliveService.halt(this);
        releaseWorkers();
        // Disengaja: unduhan stream m3u8 berjalan di proses aplikasi ini (bukan DownloadManager),
        // jadi ikut dihentikan saat aplikasi ditutup. File setengah jadi dihapus oleh HlsDownloader.Out.abort.
        for (AtomicBoolean c : hlsJobs.values()) c.set(true);
        if (updCancel != null) updCancel.set(true);
        UpdateReceiver.listener = null;
        hlsJobs.clear();
        dlPool.shutdownNow();
        bg.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
        updateBatteryStatus();
        // Aplikasi kembali dari latar belakang: PIN selalu diminta lagi (lihat AppLock). Proses yang
        // sedang berjalan tetap jalan di balik layar PIN.
        if (AppLock.needLock || (PinLock.isSet(this) && !AppLock.unlocked)) {
            AppLock.needLock = false;
            if (PinLock.isSet(this) && !(lockLayer.getVisibility() == View.VISIBLE && pinStage == PIN_UNLOCK)) {
                showLock(PIN_UNLOCK);
            }
        }
        if (currentPage == PAGE_VIDEOS && !libItems.isEmpty()) renderLibrary(null);   // perbarui penanda tonton
        if (!dlMap.isEmpty() || !dlQueue.isEmpty() || !hlsJobs.isEmpty()) startTracker();
        if (mode == Mode.CAPTURE && paused) {
            paused = false;
            log("> Melanjutkan pengumpulan link...");
            for (Worker w : new ArrayList<>(workers)) {
                if (w.active && w.item >= 0) w.scheduleBegin(1500);
                else w.scheduleNext(1500);
            }
        }
    }

    void setDark(boolean on) {
        darkOverlay.setVisibility(on ? View.VISIBLE : View.GONE);
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = on ? 0.01f : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(lp);
    }

    // ---------------- PEMUTAR (ala VLC) ----------------

    /** Putar video yang sudah selesai diunduh; semua video selesai lainnya jadi daftar putar. */
    void playDone(int i) {
        if (i < 0 || i >= vState.length || vState[i] != ST_DONE) return;
        if (!doneUri.containsKey(i)) {
            setStatus("File ini bisa diputar dari tab Pemutar.", Ui.AMBER);
            return;
        }
        ArrayList<String> uris = new ArrayList<>(), names = new ArrayList<>();
        int start = 0;
        for (int k = 0; k < vState.length; k++) {
            String u = doneUri.get(k);
            if (u == null || vState[k] != ST_DONE) continue;
            if (k == i) start = uris.size();
            uris.add(u);
            names.add(rName != null && k < rName.length && rName[k] != null ? rName[k] : "Video " + (k + 1));
        }
        PlayerActivity.open(this, uris, names, start, null, null);
    }

    // ---------------- DAFTAR UNDUHAN (isi tab Pemutar) ----------------

    /** Kunci penyimpanan per profil: profil kosong (PIN salah) memakai data terpisah. */
    String key(String k) {
        return decoy ? "d_" + k : k;
    }

    /** Catat file yang selesai diunduh agar muncul di tab Pemutar (terbaru di atas). */
    void recordDownload(String uri, String name, long size) {
        try {
            JSONArray old = new JSONArray(prefs.getString(key("downloads"), "[]"));
            JSONArray out = new JSONArray();
            JSONObject o = new JSONObject();
            o.put("uri", uri);
            o.put("name", name == null || name.isEmpty() ? "Video" : name);
            o.put("size", size);
            o.put("time", System.currentTimeMillis());
            out.put(o);
            for (int k = 0; k < old.length() && out.length() < MAX_DOWNLOADS; k++) {
                JSONObject e = old.optJSONObject(k);
                if (e != null && !uri.equals(e.optString("uri"))) out.put(e);
            }
            prefs.edit().putString(key("downloads"), out.toString()).apply();
        } catch (Exception ignored) { }
        if (libraryPane != null && libraryPane.getVisibility() == View.VISIBLE) loadLibrary();
    }

    /** Muat daftar video yang pernah diunduh lewat aplikasi; file yang sudah dihapus dibuang dari daftar. */
    void loadLibrary() {
        final int s = ++libSeq;
        final String dkey = key("downloads");
        final String json = prefs.getString(dkey, "[]");
        final ContentResolver cr = getContentResolver();
        final SimpleDateFormat fmt = new SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault());
        if (libItems.isEmpty()) renderLibrary("Memuat daftar video ...");
        bg.execute(() -> {
            final List<String[]> out = new ArrayList<>();
            final Set<String> missing = new HashSet<>();
            try {
                JSONArray a = new JSONArray(json);
                for (int k = 0; k < a.length(); k++) {
                    JSONObject o = a.optJSONObject(k);
                    if (o == null) continue;
                    String uri = o.optString("uri", "");
                    if (uri.isEmpty() || !fileExists(cr, uri)) {
                        missing.add(uri);
                        continue;
                    }
                    long size = o.optLong("size", -1);
                    String info = (size > 0 ? fmtSize(size) + " \u00B7 " : "") + fmt.format(new Date(o.optLong("time")));
                    out.add(new String[]{uri, o.optString("name", "Video"), info, String.valueOf(o.optLong("time")), String.valueOf(size)});
                }
            } catch (Exception ignored) { }
            h.post(() -> {
                if (s != libSeq) return;
                libItems.clear();
                libItems.addAll(out);
                Set<String> validUris = new HashSet<>();
                for (String[] row : libItems) validUris.add(row[0]);
                libSelected.retainAll(validUris);
                if (!missing.isEmpty()) removeDownloads(dkey, missing);
                renderLibrary(null);
            });
        });
    }

    static boolean fileExists(ContentResolver cr, String uri) {
        Uri u = Uri.parse(uri);
        if ("file".equals(u.getScheme())) return u.getPath() != null && new File(u.getPath()).exists();
        try (AssetFileDescriptor fd = cr.openAssetFileDescriptor(u, "r")) {
            return fd != null;
        } catch (java.io.FileNotFoundException | SecurityException e) {
            return false;          // file dihapus / tidak bisa diakses lagi
        } catch (Exception e) {
            return true;           // gangguan sementara: jangan dibuang dari daftar
        }
    }

    void removeDownloads(String dkey, Set<String> uris) {
        try {
            JSONArray old = new JSONArray(prefs.getString(dkey, "[]"));
            JSONArray out = new JSONArray();
            for (int k = 0; k < old.length(); k++) {
                JSONObject e = old.optJSONObject(k);
                if (e != null && !uris.contains(e.optString("uri"))) out.put(e);
            }
            prefs.edit().putString(dkey, out.toString()).apply();
        } catch (Exception ignored) { }
    }

    void renderLibrary(String msg) {
        if (libBox == null) return;
        libBox.removeAllViews();
        libViewBtn.setText(libGrid ? "Tampilan: Thumbnail" : "Tampilan: Daftar");
        updateLibSelectionBar();
        if (msg != null || libItems.isEmpty()) {
            TextView e = Ui.hint(this, msg != null ? msg
                    : "Belum ada video. Video yang selesai diunduh lewat aplikasi ini akan muncul di sini.");
            e.setPadding(dp(4), dp(8), dp(4), dp(8));
            libBox.addView(e);
            return;
        }
        final int s = libSeq;
        // status tonton tiap video
        final List<WatchStore.Entry> ws = new ArrayList<>();
        for (String[] it : libItems) ws.add(WatchStore.get(this, it[0]));

        // "Lanjutkan menonton": video yang terakhir ditonton dan belum selesai
        int cont = -1;
        for (int k = 0; k < libItems.size(); k++) {
            WatchStore.Entry e = ws.get(k);
            if (e != null && e.resumable() && (cont < 0 || e.time > ws.get(cont).time)) cont = k;
        }
        if (cont >= 0) {
            final int ci = cont;
            WatchStore.Entry e = ws.get(ci);
            LinearLayout c = Ui.row(this);
            c.setBackground(Ui.box(this, Ui.ION_DEEP, 12, Ui.ION));
            c.setPadding(dp(10), dp(10), dp(12), dp(10));
            ImageView th = thumbView();
            c.addView(th, new LinearLayout.LayoutParams(dp(96), dp(54)));
            LinearLayout mid = Ui.col(this);
            mid.setPadding(dp(12), 0, 0, 0);
            mid.addView(Ui.text(this, "\u25B6  Lanjutkan menonton", 13, Ui.ION, Ui.MEDIUM));
            TextView t = Ui.text(this, libItems.get(ci)[1], 14, Ui.TEXT, Ui.BODY);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            mid.addView(t, Ui.fill(this, 2));
            mid.addView(Ui.hint(this, "dari " + PlayerActivity.fmt(e.pos) + (e.dur > 0 ? " / " + PlayerActivity.fmt(e.dur) : "")));
            mid.addView(progressBar(e.percent()), Ui.fill(this, 6));
            c.addView(mid, Ui.weight(1));
            c.setClickable(true);
            c.setOnClickListener(v -> playLibraryItem(ci));
            libBox.addView(c);
            loadThumb(th, libItems.get(ci)[0], s);
        }

        // saring & urutkan
        int filter = libFilterSpin.getSelectedItemPosition(), sort = libSortSpin.getSelectedItemPosition();
        libView.clear();
        for (int k = 0; k < libItems.size(); k++) {
            WatchStore.Entry e = ws.get(k);
            boolean done = e != null && e.done, started = e != null && !e.done && e.pos > WatchStore.MIN_RESUME_MS;
            if (filter == 1 && (done || started)) continue;
            if (filter == 2 && !started) continue;
            if (filter == 3 && !done) continue;
            libView.add(k);
        }
        final List<WatchStore.Entry> wsf = ws;
        Collections.sort(libView, (a, b) -> {
            String[] x = libItems.get(a), y = libItems.get(b);
            switch (sort) {
                case 1: {
                    long ta = wsf.get(a) == null ? 0 : wsf.get(a).time, tb = wsf.get(b) == null ? 0 : wsf.get(b).time;
                    return Long.compare(tb, ta);
                }
                case 2:
                    return x[1].compareToIgnoreCase(y[1]);
                case 3:
                    return Long.compare(parseLong(y[4]), parseLong(x[4]));
                default:
                    return Long.compare(parseLong(y[3]), parseLong(x[3]));
            }
        });
        if (libView.isEmpty()) {
            TextView e = Ui.hint(this, "Tidak ada video untuk pilihan \"" + LIB_FILTERS[filter] + "\".");
            e.setPadding(dp(4), dp(12), dp(4), dp(8));
            libBox.addView(e);
            return;
        }
        if (libGrid) {
            LinearLayout row = null;
            for (int n = 0; n < libView.size(); n++) {
                final int idx = libView.get(n);
                if (n % 2 == 0) {
                    row = Ui.row(this);
                    row.setGravity(Gravity.TOP);
                    row.setWeightSum(2);
                    libBox.addView(row, Ui.fill(this, n == 0 ? 14 : 10));
                }
                final String[] it = libItems.get(idx);
                WatchStore.Entry e = ws.get(idx);
                boolean sel = libSelected.contains(it[0]);
                LinearLayout cell = Ui.col(this);
                cell.setBackground(Ui.box(this, sel ? Ui.PANEL_HI : Ui.PANEL, 10, sel ? Ui.ION : 0));
                cell.setPadding(dp(6), dp(6), dp(6), dp(8));
                FrameLayout tf = new FrameLayout(this);
                ImageView th = thumbView();
                tf.addView(th, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                if (libSelectMode) {
                    TextView chk = Ui.text(this, sel ? "✓" : "", 13, sel ? Ui.ABYSS : Ui.TEXT, Ui.DISPLAY);
                    chk.setGravity(Gravity.CENTER);
                    chk.setBackground(Ui.box(this, sel ? Ui.ION : 0xAA0A1024, 12, sel ? Ui.ION : Ui.TEXT));
                    FrameLayout.LayoutParams ckl = new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.TOP | Gravity.END);
                    ckl.setMargins(0, dp(5), dp(5), 0);
                    tf.addView(chk, ckl);
                }
                TextView badge = watchBadge(e);
                if (badge != null) {
                    FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
                    bl.setMargins(dp(5), dp(5), 0, 0);
                    tf.addView(badge, bl);
                }
                if (e != null && e.dur > 0) {
                    TextView du = Ui.text(this, PlayerActivity.fmt(e.dur), 11, Color.WHITE, Ui.MEDIUM);
                    du.setBackground(Ui.box(this, 0xAA000000, 4, 0));
                    du.setPadding(dp(5), dp(1), dp(5), dp(1));
                    FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
                    dl.setMargins(0, 0, dp(5), dp(7));
                    tf.addView(du, dl);
                }
                if (e != null && e.percent() > 0) {
                    FrameLayout.LayoutParams pbl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            dp(3), Gravity.BOTTOM);
                    tf.addView(progressBar(e.percent()), pbl);
                }
                cell.addView(tf, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(96)));
                TextView t = Ui.text(this, it[1], 13, e != null && e.done ? Ui.MUTED : Ui.TEXT, Ui.BODY);
                t.setMaxLines(2);
                t.setEllipsize(TextUtils.TruncateAt.END);
                t.setPadding(dp(2), dp(6), dp(2), 0);
                cell.addView(t);
                TextView inf = Ui.hint(this, it[2]);
                inf.setTextSize(11);
                inf.setSingleLine(true);
                inf.setEllipsize(TextUtils.TruncateAt.END);
                inf.setPadding(dp(2), dp(2), dp(2), 0);
                cell.addView(inf);
                LinearLayout.LayoutParams cl = Ui.weight(1);
                if (n % 2 == 1) cl.leftMargin = dp(10);
                row.addView(cell, cl);
                bindItem(cell, idx);
                loadThumb(th, it[0], s);
            }
            if (libView.size() % 2 == 1 && row != null) row.addView(new View(this), Ui.weight(1));
        } else {
            for (int n = 0; n < libView.size(); n++) {
                final int idx = libView.get(n);
                final String[] it = libItems.get(idx);
                WatchStore.Entry e = ws.get(idx);
                boolean sel = libSelected.contains(it[0]);
                LinearLayout r = Ui.row(this);
                r.setBackground(Ui.box(this, sel ? Ui.PANEL_HI : Ui.PANEL, 10, sel ? Ui.ION : 0));
                r.setPadding(dp(12), dp(10), dp(12), dp(10));
                boolean done = e != null && e.done;
                if (libSelectMode) {
                    TextView chk = Ui.text(this, sel ? "✓" : "", 13, sel ? Ui.ABYSS : Ui.TEXT, Ui.DISPLAY);
                    chk.setGravity(Gravity.CENTER);
                    chk.setBackground(Ui.box(this, sel ? Ui.ION : Ui.ABYSS, 6, sel ? Ui.ION : Ui.MUTED));
                    LinearLayout.LayoutParams ckl = Ui.lp(dp(22), dp(22));
                    ckl.rightMargin = dp(12);
                    r.addView(chk, ckl);
                } else {
                    TextView icon = Ui.text(this, done ? "\u2713" : "\u25B6", 16, done ? Ui.MUTED : Ui.ION, Ui.MEDIUM);
                    icon.setPadding(0, 0, dp(12), 0);
                    r.addView(icon);
                }
                LinearLayout mid = Ui.col(this);
                TextView t = Ui.text(this, it[1], 14, done ? Ui.MUTED : Ui.TEXT, Ui.BODY);
                t.setMaxLines(2);
                t.setEllipsize(TextUtils.TruncateAt.END);
                mid.addView(t);
                mid.addView(Ui.hint(this, watchText(e) + " \u00B7 " + it[2]), Ui.fill(this, 2));
                if (e != null && e.percent() > 0 && !done) mid.addView(progressBar(e.percent()), Ui.fill(this, 6));
                r.addView(mid, Ui.weight(1));
                libBox.addView(r, Ui.fill(this, n == 0 ? 14 : 6));
                bindItem(r, idx);
            }
        }
    }

    static long parseLong(String v) {
        try {
            return Long.parseLong(v);
        } catch (Exception e) {
            return 0;
        }
    }

    /** Penanda di pojok thumbnail: BARU (belum pernah diputar) atau sudah ditonton. */
    TextView watchBadge(WatchStore.Entry e) {
        String txt;
        int bg, fg;
        if (e == null) {
            txt = "BARU";
            bg = Ui.ION;
            fg = Ui.ABYSS;
        } else if (e.done) {
            txt = "\u2713 Ditonton";
            bg = 0xCC000000;
            fg = Color.WHITE;
        } else {
            return null;
        }
        TextView b = Ui.text(this, txt, 10.5f, fg, Ui.MEDIUM);
        b.setBackground(Ui.box(this, bg, 4, 0));
        b.setPadding(dp(5), dp(1), dp(5), dp(2));
        return b;
    }

    static String watchText(WatchStore.Entry e) {
        if (e == null) return "Belum ditonton";
        if (e.done) return "Sudah ditonton";
        if (e.pos > WatchStore.MIN_RESUME_MS) {
            int p = e.percent();
            return "Ditonton " + (p >= 0 ? p + "%" : "sampai " + PlayerActivity.fmt(e.pos));
        }
        return "Belum ditonton";
    }

    /** Bilah tipis kemajuan tonton (warna aksen di atas latar gelap). */
    View progressBar(int percent) {
        LinearLayout bar = new LinearLayout(this);
        bar.setBackground(Ui.box(this, 0x66000000, 2, 0));
        bar.setWeightSum(100);
        View fill = new View(this);
        fill.setBackground(Ui.box(this, Ui.ION, 2, 0));
        bar.addView(fill, new LinearLayout.LayoutParams(0, dp(3), Math.max(1, Math.min(100, percent))));
        bar.setMinimumHeight(dp(3));
        return bar;
    }
    ImageView thumbView() {
        ImageView th = new ImageView(this);
        th.setScaleType(ImageView.ScaleType.CENTER_CROP);
        th.setBackground(Ui.box(this, Color.BLACK, 6, 0));
        th.setClipToOutline(true);
        return th;
    }

    /** Ketuk = putar (daftar putar mengikuti urutan yang tampil), tekan lama = pilihan lain. */
    void setLibSelectMode(boolean on) {
        libSelectMode = on;
        if (!on) libSelected.clear();
        renderLibrary(null);
    }

    void updateLibSelectionBar() {
        if (libSelBar == null || libSelectBtn == null) return;
        if (libCleanBtn != null) {
            int over90 = watchedOver90Uris().size();
            libCleanBtn.setVisibility(over90 > 0 && !libSelectMode ? View.VISIBLE : View.GONE);
            if (over90 > 0) libCleanBtn.setText("\uD83D\uDDD1  Hapus video >90% ditonton (" + over90 + ")");
        }
        libSelectBtn.setText(libSelectMode ? "Selesai" : "Pilih");
        libSelectBtn.setTextColor(libSelectMode ? Ui.ION : Ui.TEXT);
        libSelectBtn.setBackground(Ui.box(this, libSelectMode ? Ui.ION_DEEP : Ui.PANEL, 10, libSelectMode ? Ui.ION : Ui.LINE));
        libSelBar.setVisibility(libSelectMode ? View.VISIBLE : View.GONE);
        if (!libSelectMode) return;
        int cnt = libSelected.size();
        libSelCount.setText(cnt + " dipilih");
        boolean allSel = !libView.isEmpty() && cnt >= libView.size();
        libSelAllBtn.setText(allSel ? "Batal semua" : "Semua");
        libSelDelBtn.setEnabled(cnt > 0);
        libSelDelBtn.setAlpha(cnt > 0 ? 1f : 0.45f);
    }

    void toggleSelectAllLibrary() {
        if (libView.isEmpty()) return;
        boolean allSel = true;
        for (int idx : libView) {
            if (!libSelected.contains(libItems.get(idx)[0])) { allSel = false; break; }
        }
        if (allSel) {
            for (int idx : libView) libSelected.remove(libItems.get(idx)[0]);
        } else {
            for (int idx : libView) libSelected.add(libItems.get(idx)[0]);
        }
        renderLibrary(null);
    }

    Set<String> watchedOver90Uris() {
        Set<String> out = new LinkedHashSet<>();
        for (String[] it : libItems) {
            WatchStore.Entry e = WatchStore.get(this, it[0]);
            if (e != null && e.percent() > 90) out.add(it[0]);
        }
        return out;
    }

    void deleteLibraryTargets(Set<String> targets, boolean deleteFiles) {
        if (targets.isEmpty()) return;
        removeDownloads(key("downloads"), targets);
        int okCount = 0, failCount = 0;
        for (String u : targets) {
            thumbs.remove(u);
            if (deleteFiles) {
                if (deleteVideoFileSilent(u)) okCount++;
                else failCount++;
            }
        }
        libSelected.removeAll(targets);
        if (libSelected.isEmpty()) libSelectMode = false;
        if (deleteFiles) {
            String msg = okCount + " video dihapus dari daftar & storage";
            if (failCount > 0) msg += " (" + failCount + " gagal dari storage)";
            setStatus(msg, failCount == 0 ? Ui.ION : Ui.AMBER);
        } else {
            setStatus(targets.size() + " video dihapus dari daftar.", Ui.ION);
        }
        loadLibrary();
    }

    void confirmDeleteTargets(final Set<String> targets, String title) {
        if (targets.isEmpty()) return;
        String[] opts = {"Hapus dari daftar saja", "Hapus dari daftar dan storage"};
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setItems(opts, (d, w) -> deleteLibraryTargets(targets, w == 1))
                .setNegativeButton("Batal", null)
                .show();
    }

    void confirmDeleteSelected() {
        if (libSelected.isEmpty()) return;
        confirmDeleteTargets(new HashSet<>(libSelected), "Hapus " + libSelected.size() + " video?");
    }

    void confirmDeleteWatched90() {
        Set<String> targets = watchedOver90Uris();
        if (targets.isEmpty()) {
            setStatus("Belum ada video yang ditonton lebih dari 90%.", Ui.AMBER);
            return;
        }
        confirmDeleteTargets(targets, "Hapus " + targets.size() + " video (>90% ditonton)?");
    }

    void bindItem(View v, final int idx) {
        v.setOnClickListener(x -> {
            if (idx >= libItems.size()) return;
            if (libSelectMode) {
                String u = libItems.get(idx)[0];
                if (!libSelected.remove(u)) libSelected.add(u);
                renderLibrary(null);
                return;
            }
            playLibraryItem(idx);
        });
        v.setOnLongClickListener(x -> {
            if (idx >= libItems.size()) return true;
            final String[] it = libItems.get(idx);
            if (libSelectMode) {
                if (!libSelected.remove(it[0])) libSelected.add(it[0]);
                renderLibrary(null);
                return true;
            }
            WatchStore.Entry e = WatchStore.get(this, it[0]);
            final boolean done = e != null && e.done;
            String[] opts = {done ? "Tandai belum ditonton" : "Tandai sudah ditonton", "Putar dari awal",
                    "Pilih beberapa", "Hapus dari daftar", "Hapus dari daftar dan storage"};
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(it[1])
                    .setItems(opts, (d, w) -> {
                        if (w == 0) {
                            WatchStore.setDone(this, it[0], !done);
                            renderLibrary(null);
                            return;
                        }
                        if (w == 1) {
                            WatchStore.setDone(this, it[0], false);
                            playLibraryItem(idx);
                            return;
                        }
                        if (w == 2) {
                            libSelected.add(it[0]);
                            setLibSelectMode(true);
                            return;
                        }
                        Set<String> one = new HashSet<>();
                        one.add(it[0]);
                        removeDownloads(key("downloads"), one);
                        if (w == 4) deleteVideoFile(it[0]);
                        thumbs.remove(it[0]);
                        loadLibrary();
                    })
                    .show();
            return true;
        });
    }

    void playLibraryItem(int idx) {
        ArrayList<String> uris = new ArrayList<>(), names = new ArrayList<>();
        List<Integer> order = libView.contains(idx) ? libView : java.util.Collections.singletonList(idx);
        int start = 0;
        for (int k : order) {
            if (k == idx) start = uris.size();
            uris.add(libItems.get(k)[0]);
            names.add(libItems.get(k)[1]);
        }
        PlayerActivity.open(this, uris, names, start, null, null);
    }
    boolean deleteVideoFileSilent(String uri) {
        try {
            Uri u = Uri.parse(uri);
            if ("file".equals(u.getScheme())) return u.getPath() != null && new File(u.getPath()).delete();
            return getContentResolver().delete(u, null, null) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    void deleteVideoFile(String uri) {
        boolean ok = deleteVideoFileSilent(uri);
        setStatus(ok ? "File dihapus dari storage." : "File tidak bisa dihapus dari aplikasi, hapus lewat aplikasi Files.", ok ? Ui.ION : Ui.AMBER);
    }

    /** Thumbnail diambil dari bingkai video (detik ke-2), disimpan sementara di memori. */
    void loadThumb(final ImageView iv, final String uri, final int s) {
        Bitmap cached = thumbs.get(uri);
        if (cached != null) {
            iv.setImageBitmap(cached);
            return;
        }
        final Context app = getApplicationContext();
        bg.execute(() -> {
            Bitmap b = null;
            MediaMetadataRetriever r = new MediaMetadataRetriever();
            try {
                r.setDataSource(app, Uri.parse(uri));
                if (Build.VERSION.SDK_INT >= 27) {
                    b = r.getScaledFrameAtTime(2000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 320, 180);
                } else {
                    Bitmap f = r.getFrameAtTime(2000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    if (f != null) {
                        float sc = Math.min(1f, 320f / Math.max(1, f.getWidth()));
                        b = Bitmap.createScaledBitmap(f, Math.max(1, Math.round(f.getWidth() * sc)),
                                Math.max(1, Math.round(f.getHeight() * sc)), true);
                    }
                }
            } catch (Exception ignored) {
                // format yang tidak bisa dibaca (mis. sebagian .ts): tampil tanpa thumbnail
            } finally {
                try { r.release(); } catch (Exception ignored) { }
            }
            final Bitmap fb = b;
            if (fb != null) h.post(() -> {
                thumbs.put(uri, fb);
                if (s == libSeq) iv.setImageBitmap(fb);
            });
        });
    }

    // ---------------- KUNCI PIN ----------------

    FrameLayout buildLockLayer() {
        FrameLayout layer = new FrameLayout(this);
        layer.setBackgroundColor(Ui.ABYSS);
        layer.setClickable(true);       // sentuhan tidak tembus ke tampilan di bawahnya
        layer.setVisibility(View.GONE);
        LinearLayout c = Ui.col(this);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(dp(24), dp(24), dp(24), dp(24));
        TextView app = Ui.text(this, "Filecoster", 26, Ui.TEXT, Ui.DISPLAY);
        c.addView(app);
        lockTitle = Ui.text(this, "", 17, Ui.TEXT, Ui.MEDIUM);
        lockTitle.setGravity(Gravity.CENTER);
        c.addView(lockTitle, Ui.fill(this, 22));
        lockHint = Ui.hint(this, "");
        lockHint.setGravity(Gravity.CENTER);
        c.addView(lockHint, Ui.fill(this, 6));
        lockDots = Ui.text(this, "", 30, Ui.ION, Ui.DISPLAY);
        lockDots.setGravity(Gravity.CENTER);
        lockDots.setLetterSpacing(0.35f);
        c.addView(lockDots, Ui.fill(this, 18));
        String[][] keys = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"skip", "0", "del"}};
        for (String[] row : keys) {
            LinearLayout r = Ui.row(this);
            r.setGravity(Gravity.CENTER);
            for (String k : row) {
                TextView b;
                if (k.equals("skip")) {
                    lockSkip = Ui.text(this, "Lewati", 14, Ui.MUTED, Ui.MEDIUM);
                    lockSkip.setGravity(Gravity.CENTER);
                    lockSkip.setClickable(true);
                    lockSkip.setOnClickListener(v -> cancelLock());
                    b = lockSkip;
                } else if (k.equals("del")) {
                    b = Ui.text(this, "\u232B", 22, Ui.TEXT, Ui.MEDIUM);
                    b.setGravity(Gravity.CENTER);
                    b.setClickable(true);
                    b.setOnClickListener(v -> {
                        if (pinBuf.length() > 0) pinBuf.setLength(pinBuf.length() - 1);
                        paintDots();
                    });
                } else {
                    b = Ui.text(this, k, 26, Ui.TEXT, Ui.DISPLAY);
                    b.setGravity(Gravity.CENTER);
                    b.setBackground(Ui.box(this, Ui.PANEL, 36, Ui.LINE));
                    b.setClickable(true);
                    b.setOnClickListener(v -> pinKey(k));
                }
                LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(dp(72), dp(72));
                kl.setMargins(dp(10), dp(8), dp(10), dp(8));
                r.addView(b, kl);
            }
            c.addView(r);
        }
        layer.addView(c, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        return layer;
    }

    void showLock(int stage) {
        pinStage = stage;
        pinBuf.setLength(0);
        pinFirst = null;
        locked = stage == PIN_UNLOCK;
        switch (stage) {
            case PIN_UNLOCK:
                lockTitle.setText("Masukkan PIN");
                lockHint.setText("");
                break;
            case PIN_NEW:
                lockTitle.setText("Buat PIN 6 digit");
                lockHint.setText("PIN diminta setiap aplikasi dibuka. Jika PIN salah, aplikasi tampil kosong "
                        + "seperti baru dipasang: riwayat dan video unduhan tidak ditampilkan.");
                break;
            case PIN_OLD_CHANGE:
                lockTitle.setText("Masukkan PIN lama");
                lockHint.setText("Untuk mengganti PIN.");
                break;
            default:
                lockTitle.setText("Masukkan PIN lama");
                lockHint.setText("Untuk mematikan PIN.");
                break;
        }
        lockSkip.setText(stage == PIN_NEW && !PinLock.isSet(this) && !prefs.getBoolean("pinAsked", false) ? "Lewati" : "Batal");
        lockSkip.setVisibility(stage == PIN_UNLOCK ? View.INVISIBLE : View.VISIBLE);
        paintDots();
        lockLayer.setVisibility(View.VISIBLE);
        lockLayer.bringToFront();
        View f = getCurrentFocus();
        if (f != null) {
            android.view.inputmethod.InputMethodManager im =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (im != null) im.hideSoftInputFromWindow(f.getWindowToken(), 0);
        }
    }

    void paintDots() {
        StringBuilder d = new StringBuilder();
        for (int k = 0; k < PinLock.LEN; k++) d.append(k < pinBuf.length() ? "\u25CF" : "\u25CB");
        lockDots.setText(d.toString());
    }

    void pinKey(String k) {
        if (pinBuf.length() >= PinLock.LEN) return;
        pinBuf.append(k);
        paintDots();
        if (pinBuf.length() == PinLock.LEN) {
            final String pin = pinBuf.toString();
            h.postDelayed(() -> submitPin(pin), 150);
        }
    }

    void submitPin(String pin) {
        pinBuf.setLength(0);
        switch (pinStage) {
            case PIN_UNLOCK:
                // Disengaja (permintaan pemilik): PIN salah TIDAK menampilkan pesan error. Aplikasi tetap
                // terbuka dengan profil kosong (decoy) yang memakai riwayat/daftar unduhan terpisah
                // (lihat key() & applyProfile()), sehingga terlihat seperti aplikasi baru dipasang.
                decoy = !PinLock.check(this, pin);
                AppLock.unlocked = true;
                hideLock();
                applyProfile();
                break;
            case PIN_NEW:
                pinFirst = pin;
                pinStage = PIN_CONFIRM;
                lockTitle.setText("Ulangi PIN");
                lockHint.setText("Masukkan PIN yang sama sekali lagi.");
                paintDots();
                break;
            case PIN_CONFIRM:
                if (pin.equals(pinFirst)) {
                    try {
                        PinLock.set(this, pin);
                        AppLock.unlocked = true;
                        prefs.edit().putBoolean("pinAsked", true).apply();
                        setStatus("PIN aktif. PIN akan diminta setiap aplikasi dibuka.", Ui.ION);
                    } catch (Exception e) {
                        setStatus("PIN gagal disimpan: " + e.getMessage(), Ui.PULSE);
                    }
                    hideLock();
                    updatePinUi();
                } else {
                    pinStage = PIN_NEW;
                    pinFirst = null;
                    lockTitle.setText("Buat PIN 6 digit");
                    lockHint.setText("PIN tidak sama. Ulangi dari awal.");
                    paintDots();
                }
                break;
            case PIN_OLD_CHANGE:
            case PIN_OLD_REMOVE:
                if (!PinLock.check(this, pin)) {
                    hideLock();
                    setStatus("PIN lama salah.", Ui.PULSE);
                    return;
                }
                if (decoy) {                // pemilik PIN: kembali ke data asli
                    decoy = false;
                    applyProfile();
                }
                if (pinStage == PIN_OLD_CHANGE) {
                    showLock(PIN_NEW);
                } else {
                    PinLock.clear(this);
                    hideLock();
                    updatePinUi();
                    setStatus("PIN dimatikan.", Ui.AMBER);
                }
                break;
            default:
                break;
        }
    }

    void cancelLock() {
        if (pinStage == PIN_UNLOCK) return;
        if (!PinLock.isSet(this)) prefs.edit().putBoolean("pinAsked", true).apply();
        hideLock();
    }

    void hideLock() {
        locked = false;
        lockLayer.setVisibility(View.GONE);
        pinBuf.setLength(0);
        if (pendingShare != null) {
            Intent it = pendingShare;
            pendingShare = null;
            handleShare(it);
        }
    }

    void updatePinUi() {
        if (pinInfo == null) return;
        boolean on = PinLock.isSet(this);
        pinInfo.setText(on ? "PIN aktif. Diminta setiap kali aplikasi dibuka atau kembali dari latar belakang. "
                + "Jika PIN salah, aplikasi tampil kosong seperti baru dipasang."
                : "PIN belum aktif. Siapa pun yang membuka aplikasi bisa melihat riwayat dan video unduhan.");
        pinSetBtn.setText(on ? "Ubah PIN" : "Atur PIN");
        pinOffBtn.setVisibility(on ? View.VISIBLE : View.GONE);
        // sembunyikan isi aplikasi di daftar aplikasi terbaru saat PIN aktif
        if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(!on);
    }

    /** Tampilkan data profil yang aktif (asli atau kosong). Profil kosong = seperti baru dipasang. */
    void applyProfile() {
        loadingPrefs = true;
        urlInput.setText(prefs.getString(key("url"), ""));
        loadingPrefs = false;
        if (decoy) {
            // Disengaja: profil kosong tidak boleh memperlihatkan pekerjaan profil asli. Pindai/kumpul dihentikan,
            // antrean unduhan dilepas (file yang sedang diunduh sistem tetap selesai di folder tujuan).
            if (mode != Mode.IDLE) {
                scanSeq++;
                pageQueue.clear();
                releaseWorkers();
                dlQueue.clear();
                dlMap.clear();
                for (AtomicBoolean c : hlsJobs.values()) c.set(true);
                hlsJobs.clear();
                h.removeCallbacks(tracker);
                tracking = false;
                paused = false;
                mode = Mode.IDLE;
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
            videos.clear();
            vState = new int[0];
            vPct = new int[0];
            vNote = new String[0];
            rUrl = null;
            doneUri.clear();
            rSizes.clear();
            rDurs.clear();
            rPoster.clear();
            dlSelected.clear();
            found.clear();
            foldersScanned = 0;
            historyUrl = "";
            logView.setText("");
            stopSniff();
            previewOpen = false;
            web.loadUrl("about:blank");
            buildRows();
            showTab(0);
            setStatus("Tempel link video, lalu tekan Cari video.", Ui.MUTED);
        }
        libItems.clear();
        thumbs.evictAll();
        renderHistory();
        loadLibrary();
        updatePinUi();
        refreshUi();
    }

    void playLibrary(int i) {
        ArrayList<String> uris = new ArrayList<>(), names = new ArrayList<>();
        for (String[] it : libItems) {
            uris.add(it[0]);
            names.add(it[1]);
        }
        PlayerActivity.open(this, uris, names, i, null, null);
    }

    /** Pilih file video apa pun lewat pemilih file Android (tanpa izin tambahan). */
    void openVideoFile() {
        Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        it.addCategory(Intent.CATEGORY_OPENABLE);
        it.setType("video/*");
        try {
            AppLock.expectExternal();
            startActivityForResult(it, REQ_OPEN_FILE);
        } catch (Exception e) {
            setStatus("Pemilih file tidak tersedia di HP ini.", Ui.AMBER);
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_OPEN_FILE || res != RESULT_OK || data == null || data.getData() == null) return;
        Uri u = data.getData();
        ArrayList<String> one = new ArrayList<>(), names = new ArrayList<>();
        one.add(u.toString());
        String seg = u.getLastPathSegment();
        names.add(seg == null ? "Video" : lastSegment(seg));
        PlayerActivity.open(this, one, names, 0, null, null);
    }

    // ---------------- PEMBARUAN APLIKASI ----------------

    /** Cek rilis terbaru di GitHub. manual = ditekan pengguna (hasil apa pun ditampilkan). */
    void checkUpdate(final boolean manual) {
        if (updChecking || updBusy) return;
        updChecking = true;
        if (manual) updateCheckBtn.setText("\u27F3 Cek...");
        bg.execute(() -> {
            Updater.Info info = null;
            String err = null;
            try {
                info = Updater.latest(BuildConfig.UPDATE_REPO);
            } catch (Exception e) {
                err = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            final Updater.Info fi = info;
            final String fe = err;
            h.post(() -> {
                updChecking = false;
                updateCheckBtn.setText("\u27F3 Perbarui");
                prefs.edit().putLong("updLast", System.currentTimeMillis()).apply();
                if (fi == null) {
                    if (manual) setStatus("Gagal memeriksa pembaruan: " + fe, Ui.AMBER);
                    return;
                }
                if (fi.build > BuildConfig.BUILD_NUMBER) {
                    showUpdate(fi);
                    if (manual) mainScroll.smoothScrollTo(0, 0);
                } else if (manual) {
                    updateCard.setVisibility(View.GONE);
                    setStatus("Aplikasi sudah versi terbaru (build " + BuildConfig.BUILD_NUMBER + ").", Ui.ION);
                }
            });
        });
    }

    void showUpdate(Updater.Info info) {
        pendingUpdate = info;
        updateTitle.setText("\u26A1 Update Tersedia (Build " + info.build + ")");
        String notes = info.notes == null ? "" : info.notes.trim();
        if (notes.length() > 1200) notes = notes.substring(0, 1200) + " ...";
        if (notes.isEmpty()) notes = "Pembaruan fitur, peningkatan tampilan futuristik, dan perbaikan stabilitas aplikasi.";
        String meta = "Versi terpasang: " + BuildConfig.VERSION_NAME
                + (info.size > 0 ? "  \u2022  Ukuran: " + fmtSize(info.size) : "");
        updateNotes.setText(meta + "\n\nDeskripsi Update:\n" + notes);
        updateCheckBtn.setText("\u26A1 Update");
        updateCheckBtn.setTextColor(Ui.ION);
        updateCheckBtn.setBackground(Ui.box(this, Ui.ION_DEEP, 10, Ui.ION));
        updReinstall = false;
        updateBtn.setText("\u2B07  Unduh & pasang" + (info.size > 0 ? " (" + fmtSize(info.size) + ")" : ""));
        updateCard.setVisibility(View.VISIBLE);
    }

    void startUpdate() {
        if (updBusy || pendingUpdate == null) return;
        if (updReinstall) {          // kunci berbeda: hanya bisa lewat hapus & pasang ulang
            openReleasePage();
            return;
        }
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            try {
                AppLock.expectExternal();
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) { }
            setStatus("Izinkan \"Pasang aplikasi tak dikenal\" untuk Filecoster, lalu tekan Unduh & pasang lagi.", Ui.AMBER);
            return;
        }
        updBusy = true;
        updFallbackTried = false;
        final Updater.Info info = pendingUpdate;
        final Context app = getApplicationContext();
        final AtomicBoolean cancel = new AtomicBoolean(false);
        updCancel = cancel;
        updateBtn.setText("Mengunduh pembaruan ...");
        setStatus("Mengunduh pembaruan build " + info.build + " ...", Ui.AMBER);
        dlPool.execute(() -> {
            String err;
            try {
                File f = Updater.download(app, info, cancel, (done, total) -> h.post(() ->
                        updateBtn.setText("Mengunduh " + (total > 0 ? (done * 100 / total) + "%" : fmtSize(done)))));
                err = Updater.verify(app, f);
                if (err == null) {
                    h.post(() -> updateBtn.setText("Membuka penginstal ..."));
                    AppLock.expectExternal();
                    Updater.install(app, f);
                }
            } catch (Exception e) {
                err = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            final String fe = err;
            h.post(() -> {
                updBusy = false;
                if (Updater.KEY_MISMATCH.equals(fe)) {
                    updReinstall = true;
                    updateBtn.setText("Buka halaman unduhan APK");
                    updateNotes.setText(fe);
                    setStatus("Perlu pasang ulang sekali. Lihat kartu pembaruan di atas.", Ui.PULSE);
                    log("\nPembaruan: " + fe);
                    mainScroll.smoothScrollTo(0, 0);
                } else if (fe != null) {
                    updateBtn.setText("Coba lagi");
                    setStatus("Pembaruan gagal: " + fe, Ui.PULSE);
                    log("\nPembaruan gagal: " + fe);
                } else {
                    updateBtn.setText("Menunggu konfirmasi pemasangan ...");
                    setStatus("Ikuti layar pemasangan Android untuk menyelesaikan pembaruan.", Ui.ION);
                }
            });
        });
    }

    /** Hasil dari PackageInstaller. Gagal selain dibatalkan/kunci beda -> coba penginstal APK bawaan. */
    void onInstallResult(boolean okInstall, int status, String msg, String raw) {
        log("\nPembaruan: " + msg + " [status " + status + (raw != null ? ", " + raw : "") + "]");
        if (okInstall) {
            setStatus(msg, Ui.ION);
            return;
        }
        boolean conflict = status == android.content.pm.PackageInstaller.STATUS_FAILURE_CONFLICT
                || status == android.content.pm.PackageInstaller.STATUS_FAILURE_INCOMPATIBLE;
        if (conflict) {
            updReinstall = true;
            updateBtn.setText("Buka halaman unduhan APK");
            updateNotes.setText(Updater.KEY_MISMATCH);
            setStatus(msg, Ui.PULSE);
            mainScroll.smoothScrollTo(0, 0);
            return;
        }
        if (status != android.content.pm.PackageInstaller.STATUS_FAILURE_ABORTED && !updFallbackTried
                && ApkProvider.file(this).isFile()) {
            updFallbackTried = true;
            setStatus("Cara pertama gagal (" + (raw != null ? raw : msg) + "). Mencoba lewat penginstal APK bawaan Android ...", Ui.AMBER);
            installViaSystem();
            return;
        }
        updateBtn.setText("Coba lagi");
        setStatus(msg + (raw != null ? " (" + raw + ")" : ""), Ui.PULSE);
    }

    /** Jalur cadangan: buka file APK dengan penginstal bawaan Android (berhasil di HP yang menolak cara pertama). */
    void installViaSystem() {
        try {
            Intent it = new Intent(Intent.ACTION_VIEW);
            it.setDataAndType(ApkProvider.uri(this), ApkProvider.MIME);
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            AppLock.expectExternal();
            startActivity(it);
            updateBtn.setText("Menunggu penginstal Android ...");
        } catch (Exception e) {
            updateBtn.setText("Coba lagi");
            setStatus("Penginstal APK tidak bisa dibuka: " + e.getMessage(), Ui.PULSE);
        }
    }

    void openReleasePage() {
        String url = pendingUpdate != null && pendingUpdate.pageUrl != null ? pendingUpdate.pageUrl
                : "https://github.com/" + BuildConfig.UPDATE_REPO + "/releases/latest";
        try {
            AppLock.expectExternal();
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            setStatus("Buka di browser: " + url, Ui.AMBER);
        }
    }

    // ---------------- RIWAYAT LINK ----------------

    List<JSONObject> historyList() {
        List<JSONObject> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs.getString(key("history"), "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o != null) out.add(o);
            }
        } catch (Exception ignored) { }
        return out;
    }

    void saveHistoryList(List<JSONObject> list) {
        JSONArray a = new JSONArray();
        for (JSONObject o : list) a.put(o);
        prefs.edit().putString(key("history"), a.toString()).apply();
    }

    /** Catat satu link ke riwayat (yang terbaru di atas). Link yang sama diganti dengan data terbaru. */
    void recordHistory(String url, String name, int count, int folders, boolean single) {
        try {
            List<JSONObject> list = historyList();
            for (int i = list.size() - 1; i >= 0; i--) {
                if (stripSlash(url).equals(stripSlash(list.get(i).optString("url")))) list.remove(i);
            }
            JSONObject o = new JSONObject();
            o.put("url", url);
            o.put("name", name);
            o.put("videos", count);
            o.put("folders", folders);
            o.put("single", single);
            o.put("done", 0);
            o.put("failed", 0);
            o.put("time", System.currentTimeMillis());
            list.add(0, o);
            while (list.size() > MAX_HISTORY) list.remove(list.size() - 1);
            saveHistoryList(list);
            historyUrl = url;
        } catch (Exception ignored) { }
        renderHistory();
    }

    /** Perbarui jumlah video selesai/gagal pada entri riwayat yang sedang dikerjakan. */
    void updateHistoryResult() {
        if (historyUrl.isEmpty() || prefs == null) return;
        int done = countState(ST_DONE), failed = countState(ST_FAIL);
        try {
            List<JSONObject> list = historyList();
            boolean changed = false;
            for (JSONObject o : list) {
                if (stripSlash(historyUrl).equals(stripSlash(o.optString("url")))) {
                    if (o.optInt("done") != done || o.optInt("failed") != failed) {
                        o.put("done", done);
                        o.put("failed", failed);
                        changed = true;
                    }
                    break;
                }
            }
            if (changed) {
                saveHistoryList(list);
                if (historyPane != null && historyPane.getVisibility() == View.VISIBLE) renderHistory();
            }
        } catch (Exception ignored) { }
    }

    void removeHistory(String url) {
        List<JSONObject> list = historyList();
        for (int i = list.size() - 1; i >= 0; i--) {
            if (url.equals(list.get(i).optString("url"))) list.remove(i);
        }
        saveHistoryList(list);
        renderHistory();
    }

    TextView smallBtn(String s, int color) {
        TextView b = Ui.ghost(this, s);
        b.setTextSize(13);
        b.setTextColor(color);
        b.setPadding(dp(8), dp(9), dp(8), dp(9));
        return b;
    }

    void renderHistory() {
        if (historyBox == null) return;
        historyBox.removeAllViews();
        List<JSONObject> list = historyList();
        tabHistory.setText(list.isEmpty() ? "Riwayat" : "Riwayat (" + list.size() + ")");
        renderRecent(list);
        if (recentCard != null) refreshUi();
        clearHistoryBtn.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
        if (list.isEmpty()) {
            TextView e = Ui.hint(this, "Belum ada riwayat. Link yang sudah dipindai atau diunduh akan muncul di sini.");
            e.setPadding(dp(4), dp(8), dp(4), dp(8));
            historyBox.addView(e);
            return;
        }
        SimpleDateFormat fmt = new SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault());
        for (final JSONObject o : list) {
            final String url = o.optString("url");
            final boolean single = o.optBoolean("single");
            LinearLayout card = Ui.col(this);
            card.setBackground(Ui.box(this, Ui.PANEL, 10, 0));
            card.setPadding(dp(12), dp(11), dp(12), dp(11));

            TextView name = Ui.text(this, o.optString("name", url), 14, Ui.TEXT, Ui.MEDIUM);
            name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(name);

            TextView link = Ui.hint(this, url);
            link.setSingleLine(true);
            link.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            card.addView(link, Ui.fill(this, 2));

            String meta = single ? "1 file"
                    : o.optInt("videos") + " video \u00B7 " + o.optInt("folders") + " subfolder";
            card.addView(Ui.text(this, meta, 13, Ui.ION, Ui.MEDIUM), Ui.fill(this, 8));

            int done = o.optInt("done"), failed = o.optInt("failed");
            StringBuilder st = new StringBuilder();
            if (done > 0 || failed > 0) {
                st.append(done).append(" selesai");
                if (failed > 0) st.append(", ").append(failed).append(" gagal");
                st.append(" \u00B7 ");
            }
            st.append(fmt.format(new Date(o.optLong("time"))));
            card.addView(Ui.hint(this, st.toString()), Ui.fill(this, 2));

            LinearLayout btns = Ui.row(this);
            final TextView use = smallBtn("Pakai link", Ui.ION);
            final TextView copy = smallBtn("Salin", Ui.ION);
            TextView del = smallBtn("Hapus", Ui.PULSE);
            LinearLayout.LayoutParams w1 = Ui.weight(1.4f);
            LinearLayout.LayoutParams w2 = Ui.weight(1);
            LinearLayout.LayoutParams w3 = Ui.weight(1);
            w2.leftMargin = dp(8);
            w3.leftMargin = dp(8);
            btns.addView(use, w1);
            btns.addView(copy, w2);
            btns.addView(del, w3);
            card.addView(btns, Ui.fill(this, 10));

            use.setOnClickListener(v -> {
                if (mode != Mode.IDLE) {
                    setStatus("Tunggu proses yang sedang berjalan selesai dulu.", Ui.AMBER);
                    return;
                }
                urlInput.setText(url);
                mainScroll.smoothScrollTo(0, 0);
                showPage(PAGE_HOME);
                setStatus(single ? "Link dipilih dari riwayat. Tekan Cari video."
                        : "Link dipilih dari riwayat. Tekan Pindai folder.", Ui.ION);
            });
            copy.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("link", url));
                    copy.setText("Tersalin");
                    h.postDelayed(() -> copy.setText("Salin"), 1500);
                }
            });
            del.setOnClickListener(v -> removeHistory(url));
            historyBox.addView(card, Ui.fill(this, 6));
        }
    }

    // ---------------- BANTUAN ----------------

    static boolean isAd(String url) {
        return AD.matcher(url).find();
    }

    static boolean isHls(String url) {
        return url.toLowerCase().contains(".m3u8");
    }

    static String fmtSize(long b) {
        if (b < 0) return "ukuran ?";
        return b >= 1073741824L ? String.format(Locale.ROOT, "%.2f GB", b / 1073741824.0)
                : String.format(Locale.ROOT, "%.1f MB", b / 1048576.0);
    }

    static String fmtDur(double d) {
        return d <= 0 ? "durasi ?" : String.format("%.0f dtk", d);
    }

    static String shorten(String u) {
        return u.length() > 90 ? u.substring(0, 90) + "..." : u;
    }

    boolean isAllowed(String url) {
        String host = Uri.parse(url).getHost();
        if (host == null) return true;
        return sameSite(host) || allowedHosts.contains(host);
    }

    boolean sameSite(String host) {
        return host.equals(baseDomain) || host.endsWith("." + baseDomain);
    }

    static String baseDomain(String host) {
        String[] p = host.split("\\.");
        if (p.length <= 2) return host;
        return p[p.length - 2] + "." + p[p.length - 1];
    }

    static String stripSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    static String sanitize(String s) {
        String r = s.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]+", " ").replaceAll("\\s+", " ").trim();
        if (r.length() > 100) r = r.substring(0, 100).trim();
        return r.isEmpty() ? "video" : r;
    }

    static String extOf(String url) {
        String seg = Uri.parse(url).getLastPathSegment();
        if (seg != null) {
            int dot = seg.lastIndexOf('.');
            if (dot >= 0) {
                String ext = seg.substring(dot).toLowerCase();
                if (ext.matches("\\.(mp4|webm|mkv|mov|m4v|flv|3gp)")) return ext;
            }
        }
        return ".mp4";
    }

    void log(String s) {
        logView.append(s + "\n");
        CharSequence t = logView.getText();
        if (t.length() > 60000) logView.setText(t.subSequence(t.length() - 40000, t.length()));
    }

    int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onBackPressed() {
        if (lockLayer.getVisibility() == View.VISIBLE) {
            if (pinStage == PIN_UNLOCK) moveTaskToBack(true);
            else cancelLock();
            return;
        }
        if (libSelectMode) setLibSelectMode(false);
        else if (darkOverlay.getVisibility() == View.VISIBLE) setDark(false);
        else if (mode == Mode.IDLE && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
