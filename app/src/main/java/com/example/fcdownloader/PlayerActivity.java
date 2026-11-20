    package com.example.fcdownloader;

    import android.app.Activity;
    import android.app.Dialog;
    import android.content.ContentValues;
    import android.content.Context;
    import android.content.Intent;
    import android.content.IntentFilter;
    import android.content.SharedPreferences;
    import android.content.pm.ActivityInfo;
    import android.content.res.Configuration;
    import android.database.Cursor;
    import android.graphics.Bitmap;
    import android.graphics.Color;
    import android.graphics.Typeface;
    import android.graphics.drawable.ClipDrawable;
    import android.graphics.drawable.ColorDrawable;
    import android.graphics.drawable.Drawable;
    import android.graphics.drawable.GradientDrawable;
    import android.graphics.drawable.LayerDrawable;
    import android.media.AudioManager;
    import android.media.audiofx.Equalizer;
    import android.media.audiofx.LoudnessEnhancer;
    import android.net.Uri;
    import android.os.BatteryManager;
    import android.os.Build;
    import android.os.Bundle;
    import android.os.Handler;
    import android.os.Looper;
    import android.os.ParcelFileDescriptor;
    import android.os.SystemClock;
    import android.provider.MediaStore;
    import android.provider.OpenableColumns;
    import android.provider.Settings;
    import android.text.InputType;
    import android.text.TextUtils;
    import android.util.SparseLongArray;
    import android.view.GestureDetector;
    import android.view.Gravity;
    import android.view.HapticFeedbackConstants;
    import android.view.MotionEvent;
    import android.view.PixelCopy;
    import android.view.ScaleGestureDetector;
    import android.view.SurfaceView;
    import android.view.View;
    import android.view.ViewConfiguration;
    import android.view.ViewGroup;
    import android.view.Window;
    import android.view.WindowManager;
    import android.webkit.CookieManager;
    import android.widget.EditText;
    import android.widget.FrameLayout;
    import android.widget.LinearLayout;
    import android.widget.ScrollView;
    import android.widget.SeekBar;
    import android.widget.TextClock;
    import android.widget.TextView;
    import android.widget.Toast;

    import androidx.media3.common.AudioAttributes;
    import androidx.media3.common.C;
    import androidx.media3.common.Format;
    import androidx.media3.common.MediaItem;
    import androidx.media3.common.MediaMetadata;
    import androidx.media3.common.MimeTypes;
    import androidx.media3.common.PlaybackException;
    import androidx.media3.common.Player;
    import androidx.media3.common.TrackSelectionOverride;
    import androidx.media3.common.TrackSelectionParameters;
    import androidx.media3.common.Tracks;
    import androidx.media3.common.VideoSize;
    import androidx.media3.datasource.DefaultDataSource;
    import androidx.media3.datasource.DefaultHttpDataSource;
    import androidx.media3.exoplayer.DefaultLoadControl;
    import androidx.media3.exoplayer.ExoPlayer;
    import androidx.media3.exoplayer.SeekParameters;
    import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
    import androidx.media3.extractor.DefaultExtractorsFactory;
    import androidx.media3.extractor.Extractor;
    import androidx.media3.extractor.ExtractorInput;
    import androidx.media3.extractor.ExtractorOutput;
    import androidx.media3.extractor.ExtractorsFactory;
    import androidx.media3.extractor.PositionHolder;
    import androidx.media3.extractor.SeekMap;
    import androidx.media3.extractor.SeekPoint;
    import androidx.media3.extractor.TrackOutput;
    import androidx.media3.extractor.mp4.FragmentedMp4Extractor;
    import androidx.media3.extractor.mp4.Mp4Extractor;
    import androidx.media3.ui.AspectRatioFrameLayout;
    import androidx.media3.ui.CaptionStyleCompat;
    import androidx.media3.ui.PlayerView;
    import androidx.media3.ui.SubtitleView;

    import java.io.File;
    import java.io.FileInputStream;
    import java.io.IOException;
    import java.io.OutputStream;
    import java.nio.ByteBuffer;
    import java.nio.channels.FileChannel;
    import java.util.ArrayList;
    import java.util.Arrays;
    import java.util.HashMap;
    import java.util.List;
    import java.util.Locale;
    import java.util.Map;

    /**
     * Pemutar video futuristik (ExoPlayer / Media3): MP4, MKV, WEBM, TS, FLV, 3GP, HLS.
     *
     * Gestur: geser tegak kiri = kecerahan, kanan = volume (bisa diperkuat sampai 200%),
     * geser mendatar = maju/mundur, ketuk dua kali kiri/kanan = -10/+10 detik, tengah = jeda,
     * tahan = kecepatan 2x selama ditahan, cubit = perbesar/perkecil gambar.
     */
    public class PlayerActivity extends Activity {
        static final String EXTRA_URIS = "uris";
        static final String EXTRA_TITLES = "titles";
        static final String EXTRA_INDEX = "index";
        static final String EXTRA_UA = "ua";
        static final String EXTRA_REFERER = "referer";

        static final long HIDE_MS = 4000;
        static final long STEP_MS = 10000;
        static final long STEP_LONG_MS = 30000;
        static final long SAVE_EVERY_MS = 3000;
        static final int REQ_SUBTITLE = 7;
        static final int MAX_BOOST_MB = 1000;
        static final float[] SPEEDS = {0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f};
        static final String[] ASPECT_NAMES = {"Pas", "Penuh", "Potong", "16:9", "4:3"};
        static final String[] ORIENT_NAMES = {"Otomatis", "Lanskap", "Potret"};
        static final int[] ORIENT = {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR,
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        };
        static final String[] SUB_SIZE_NAMES = {"Kecil", "Normal", "Besar", "Sangat besar"};
        static final float[] SUB_SIZES = {0.042f, 0.0533f, 0.07f, 0.09f};

        static void open(Context c, ArrayList<String> uris, ArrayList<String> titles, int index, String ua, String referer) {
            Intent i = new Intent(c, PlayerActivity.class);
            i.putStringArrayListExtra(EXTRA_URIS, uris);
            i.putStringArrayListExtra(EXTRA_TITLES, titles);
            i.putExtra(EXTRA_INDEX, index);
            if (ua != null) i.putExtra(EXTRA_UA, ua);
            if (referer != null) i.putExtra(EXTRA_REFERER, referer);
            c.startActivity(i);
        }

        ExoPlayer player;
        PlayerView pv;
        FrameLayout root;
        LinearLayout topBar, bottomBar, centerHud, hudBox, seekHud, quickFloatBar, topRightFloatBar;
        TextView titleView, qualityBadge, posView, durView, playBtn, prevBtn, nextBtn, speedBtn;
        TextView aspectBtn, orientBtn, repeatBtn, abQuickBtn, batteryView, hudText, seekHudText;
        TextView unlockBtn, abBtn, resumeChip, speed2xBtn, floatLockBtn;
        View hudBarTrack, hudBarFill, seekHudBarTrack, seekHudBarFill, privacyCurtain;
        SeekBar seek;

        final List<String> uris = new ArrayList<>();
        final List<String> titles = new ArrayList<>();
        final Handler h = new Handler(Looper.getMainLooper());

        SharedPreferences prefs;
        AudioManager am;
        LoudnessEnhancer boost;
        Equalizer eq;
        int maxVol;
        int boostMb;
        int eqPreset = -1;

        boolean controlsShown = true;
        boolean locked;
        boolean userSeeking;
        boolean speedHeld;
        int lastQuickOrient = -1, lastQuickW, lastQuickH;
        GradientDrawable speed2xBg;
        long lastSeekAt, lastSeekTarget = -1;
        boolean showRemaining;
        int aspectIdx;
        int orientIdx;
        int speedIdx = 3;
        int subSizeIdx = 1;
        boolean subBackground;
        float zoom = 1f;
        long abA = -1, abB = -1;
        long sleepAt;
        String curUri;
        long curPos, curDur;
        long lastSave;

        // gestur sentuh
        GestureDetector gd;
        ScaleGestureDetector sgd;
        static final int G_NONE = 0, G_BRIGHT = 1, G_SEEK = 3, G_ZOOM = 4;
        int gMode = G_NONE;
        float downX, downY;
        float startBright;
        long startPos, seekTarget = -1;
        int touchSlop;

        final Runnable hider = () -> showControls(false);
        final Runnable hudHider = () -> fadeOut(hudBox);
        final Runnable seekHudHider = () -> fadeOut(seekHud);
        final Runnable unlockHider = () -> unlockBtn.setVisibility(View.GONE);
        final Runnable chipHider = () -> resumeChip.setVisibility(View.GONE);
        final Runnable sleeper = () -> {
            sleepAt = 0;
            if (player != null) player.pause();
            showCenter("Timer tidur: video dijeda", 2500, -1);
        };
        final Runnable ticker = new Runnable() {
            @Override
            public void run() {
                tick();
                h.postDelayed(this, 250);
            }
        };

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(false);
            AppLock.install(getApplication());
            if (PinLock.isSet(this) && !AppLock.unlocked && !Intent.ACTION_VIEW.equals(getIntent().getAction())) {
                redirectToPin();
                return;
            }
            requestWindowFeature(Window.FEATURE_NO_TITLE);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            if (Build.VERSION.SDK_INT >= 28) {
                WindowManager.LayoutParams lp = getWindow().getAttributes();
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                getWindow().setAttributes(lp);
            }
            prefs = getSharedPreferences("player", MODE_PRIVATE);
            am = (AudioManager) getSystemService(AUDIO_SERVICE);
            maxVol = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();

            int index = readIntent(getIntent());
            if (uris.isEmpty()) {
                Toast.makeText(this, "Tidak ada video untuk diputar.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            aspectIdx = clamp(prefs.getInt("aspect", 0), 0, ASPECT_NAMES.length - 1);
            orientIdx = clamp(prefs.getInt("orient", 0), 0, ORIENT.length - 1);
            subSizeIdx = clamp(prefs.getInt("subSize", 1), 0, SUB_SIZES.length - 1);
            subBackground = prefs.getBoolean("subBg", false);
            showRemaining = prefs.getBoolean("showRem", false);
            setRequestedOrientation(ORIENT[orientIdx]);
            buildUi();
            buildPlayer(index);
            immersive();
            h.post(ticker);
            showControls(true);
        }

        @Override
        protected void onNewIntent(Intent it) {
            super.onNewIntent(it);
            saveCurrent();
            setIntent(it);
            uris.clear();
            titles.clear();
            int index = readIntent(it);
            if (uris.isEmpty() || player == null) return;
            abA = abB = -1;
            updateAbButton();
            loadPlaylist(index);
        }

        static int clamp(int v, int lo, int hi) {
            return Math.max(lo, Math.min(hi, v));
        }

        int readIntent(Intent it) {
            if (it == null) return 0;
            if (Intent.ACTION_VIEW.equals(it.getAction()) && it.getData() != null) {
                Uri d = it.getData();
                if (isSafe(d)) {
                    uris.add(d.toString());
                    titles.add(displayName(d));
                }
                return 0;
            }
            ArrayList<String> u = it.getStringArrayListExtra(EXTRA_URIS);
            ArrayList<String> t = it.getStringArrayListExtra(EXTRA_TITLES);
            if (u == null) return 0;
            for (int i = 0; i < u.size(); i++) {
                Uri p = Uri.parse(u.get(i));
                if (!isSafe(p)) continue;
                uris.add(u.get(i));
                titles.add(t != null && i < t.size() && t.get(i) != null ? t.get(i) : displayName(p));
            }
            return clamp(it.getIntExtra(EXTRA_INDEX, 0), 0, Math.max(0, uris.size() - 1));
        }

        boolean isSafe(Uri u) {
            String s = u.getScheme();
            if (s == null) return false;
            s = s.toLowerCase(Locale.ROOT);
            if (s.equals("http") || s.equals("https") || s.equals("content")) return true;
            if (!s.equals("file") || u.getPath() == null) return false;
            try {
                String path = new File(u.getPath()).getCanonicalPath();
                String priv = new File(getApplicationInfo().dataDir).getCanonicalPath();
                return !path.startsWith(priv);
            } catch (Exception e) {
                return false;
            }
        }

        String displayName(Uri u) {
            if ("content".equals(u.getScheme())) {
                try (Cursor c = getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                    if (c != null && c.moveToFirst() && !c.isNull(0)) return c.getString(0);
                } catch (Exception ignored) { }
            }
            String seg = u.getLastPathSegment();
            return seg == null ? "Video" : seg;
        }

        // ---------------- TAMPILAN FUTURISTIK ----------------

        int dp(float v) {
            return Ui.dp(this, v);
        }

        GradientDrawable pill(int fill, float radiusDp, int strokeColor) {
            GradientDrawable g = new GradientDrawable();
            g.setColor(fill);
            g.setCornerRadius(dp(radiusDp));
            if (strokeColor != 0) g.setStroke(dp(1), strokeColor);
            return g;
        }

        TextView hudPillBtn(String s, int fill, int stroke, int textColor, float sp) {
            TextView t = new TextView(this);
            t.setText(s);
            t.setTextColor(textColor);
            t.setTextSize(sp);
            t.setTypeface(Ui.MEDIUM);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            t.setBackground(pill(fill, 14, stroke));
            t.setPadding(dp(11), dp(7), dp(11), dp(7));
            t.setClickable(true);
            t.setFocusable(true);
            return t;
        }

        TextView deckBtn(String s) {
            TextView t = new TextView(this);
            t.setText(s);
            t.setTextColor(Ui.TEXT);
            t.setTextSize(12);
            t.setTypeface(Ui.MEDIUM);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            t.setBackground(pill(0x88121A35, 10, Ui.LINE));
            t.setPadding(dp(6), dp(8), dp(6), dp(8));
            t.setClickable(true);
            t.setFocusable(true);
            return t;
        }

        TextView circleHudBtn(String s, int sizeDp, int fill, int stroke, int textColor, float sp) {
            TextView t = new TextView(this);
            t.setText(s);
            t.setTextColor(textColor);
            t.setTextSize(sp);
            t.setTypeface(Ui.DISPLAY);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(fill);
            g.setStroke(dp(1.5f), stroke);
            t.setBackground(g);
            t.setClickable(true);
            t.setFocusable(true);
            int sz = dp(sizeDp);
            t.setLayoutParams(new LinearLayout.LayoutParams(sz, sz));
            return t;
        }

        TextView label(float sp, int color) {
            TextView t = new TextView(this);
            t.setTextColor(color);
            t.setTextSize(sp);
            return t;
        }

        void styleNeonSeekBar(SeekBar sb) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0x5526325E);
            bg.setCornerRadius(dp(3));

            GradientDrawable sec = new GradientDrawable();
            sec.setColor(0x664FE3D4);
            sec.setCornerRadius(dp(3));

            GradientDrawable prog = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{0xFF28B8C8, Ui.ION}
            );
            prog.setCornerRadius(dp(3));

            LayerDrawable layers = new LayerDrawable(new Drawable[]{
                    bg,
                    new ClipDrawable(sec, Gravity.LEFT, ClipDrawable.HORIZONTAL),
                    new ClipDrawable(prog, Gravity.LEFT, ClipDrawable.HORIZONTAL)
            });
            layers.setId(0, android.R.id.background);
            layers.setId(1, android.R.id.secondaryProgress);
            layers.setId(2, android.R.id.progress);
            sb.setProgressDrawable(layers);

            GradientDrawable thumb = new GradientDrawable();
            thumb.setShape(GradientDrawable.OVAL);
            thumb.setColor(Ui.ION);
            thumb.setStroke(dp(2), Ui.ABYSS);
            thumb.setSize(dp(16), dp(16));
            sb.setThumb(thumb);
            sb.setThumbOffset(dp(8));
            sb.setSplitTrack(false);
        }

        void buildUi() {
            final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT, WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
            root = new FrameLayout(this);
            root.setBackgroundColor(Color.BLACK);

            pv = new PlayerView(this);
            pv.setUseController(false);
            pv.setKeepContentOnPlayerReset(true);
            pv.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);
            root.addView(pv, new FrameLayout.LayoutParams(MATCH, MATCH, Gravity.CENTER));
            applySubtitleStyle();

            // lapisan gestur di atas video
            final View touch = new View(this);
            gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDown(MotionEvent e) {
                    return true;
                }

                @Override
                public boolean onSingleTapConfirmed(MotionEvent e) {
                    showControls(!controlsShown);
                    return true;
                }

                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    float w = touch.getWidth();
                    if (e.getX() < w / 3f) seekBy(-STEP_MS);
                    else if (e.getX() > w * 2f / 3f) seekBy(STEP_MS);
                    else togglePlay();
                    return true;
                }

                @Override
                public void onLongPress(MotionEvent e) {
                    if (gMode != G_NONE || player == null || !player.isPlaying()) return;
                    speedHeld = true;
                    player.setPlaybackSpeed(2f);
                    showSeekHud("\u23E9 Kecepatan 2x", 0, -1);
                }
            });
            sgd = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector d) {
                    gMode = G_ZOOM;
                    setZoom(zoom * d.getScaleFactor());
                    return true;
                }
            });
            touch.setOnTouchListener((v, e) -> onTouch(v, e));
            root.addView(touch, new FrameLayout.LayoutParams(MATCH, MATCH));

            // HUD tengah untuk gestur & notifikasi futuristik (kaca gelap + neon progress bar)
            hudBox = new LinearLayout(this);
            hudBox.setOrientation(LinearLayout.VERTICAL);
            hudBox.setGravity(Gravity.CENTER);
            hudBox.setBackground(pill(0xE60A1024, 16, Ui.ION));
            hudBox.setPadding(dp(20), dp(12), dp(20), dp(12));
            hudBox.setVisibility(View.GONE);
            hudText = label(16, Ui.TEXT);
            hudText.setTypeface(Ui.DISPLAY);
            hudText.setGravity(Gravity.CENTER);
            hudBox.addView(hudText);

            LinearLayout track = new LinearLayout(this);
            track.setBackground(pill(0x5526325E, 3, 0));
            track.setWeightSum(100);
            hudBarFill = new View(this);
            hudBarFill.setBackground(pill(Ui.ION, 3, 0));
            track.addView(hudBarFill, new LinearLayout.LayoutParams(0, dp(4), 50));
            hudBarTrack = track;
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(dp(140), dp(4));
            tlp.topMargin = dp(8);
            hudBox.addView(hudBarTrack, tlp);
            hudBarTrack.setVisibility(View.GONE);
            hudBox.setClickable(false);
            hudBox.setFocusable(false);
            root.addView(hudBox, new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
            updateTopHudPosition(hudBox);

            // Bilah atas futuristik
            topBar = new LinearLayout(this);
            topBar.setOrientation(LinearLayout.HORIZONTAL);
            topBar.setGravity(Gravity.CENTER_VERTICAL);
            topBar.setClickable(true);
            topBar.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0xEE0A1024, 0x990A1024, 0x000A1024}));
            topBar.setPadding(dp(12), dp(12), dp(106), dp(22));

            TextView back = hudPillBtn("\u2190", 0xAA121A35, Ui.LINE, Ui.TEXT, 17);
            back.setOnClickListener(v -> finish());
            topBar.addView(back);

            LinearLayout titleCol = new LinearLayout(this);
            titleCol.setOrientation(LinearLayout.VERTICAL);
            titleCol.setPadding(dp(10), 0, dp(8), 0);
            titleView = label(15, Ui.TEXT);
            titleView.setTypeface(Ui.DISPLAY);
            titleView.setSingleLine(true);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            titleCol.addView(titleView);

            LinearLayout metaRow = new LinearLayout(this);
            metaRow.setOrientation(LinearLayout.HORIZONTAL);
            metaRow.setGravity(Gravity.CENTER_VERTICAL);
            qualityBadge = label(10.5f, Ui.ABYSS);
            qualityBadge.setTypeface(Ui.MEDIUM);
            qualityBadge.setBackground(pill(Ui.ION, 6, 0));
            qualityBadge.setPadding(dp(6), dp(1), dp(6), dp(1));
            qualityBadge.setVisibility(View.GONE);
            metaRow.addView(qualityBadge);

            TextClock clock = new TextClock(this);
            clock.setFormat24Hour("HH:mm");
            clock.setFormat12Hour("h:mm a");
            clock.setTextColor(Ui.MUTED);
            clock.setTextSize(11.5f);
            clock.setPadding(dp(6), 0, dp(4), 0);
            metaRow.addView(clock);

            batteryView = label(11.5f, Ui.MUTED);
            batteryView.setPadding(dp(4), 0, 0, 0);
            metaRow.addView(batteryView);
            titleCol.addView(metaRow);
            topBar.addView(titleCol, new LinearLayout.LayoutParams(0, WRAP, 1));

            speedBtn = hudPillBtn("1x", 0xAA121A35, Ui.LINE, Ui.ION, 12.5f);
            speedBtn.setOnClickListener(v -> chooseSpeed());
            speedBtn.setOnLongClickListener(v -> {
                setSpeed(3);
                return true;
            });
            topBar.addView(speedBtn);

            TextView audioBtn = hudPillBtn("Audio", 0xAA121A35, Ui.LINE, Ui.TEXT, 12.5f);
            audioBtn.setOnClickListener(v -> audioMenu());
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(WRAP, WRAP);
            alp.leftMargin = dp(6);
            topBar.addView(audioBtn, alp);

            TextView subBtn = hudPillBtn("Sub", 0xAA121A35, Ui.LINE, Ui.TEXT, 12.5f);
            subBtn.setOnClickListener(v -> subtitleMenu());
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(WRAP, WRAP);
            slp.leftMargin = dp(6);
            topBar.addView(subBtn, slp);

            TextView more = hudPillBtn("\u22EE", 0xAA121A35, Ui.LINE, Ui.TEXT, 16);
            more.setOnClickListener(v -> moreMenu());
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(WRAP, WRAP);
            mlp.leftMargin = dp(6);
            topBar.addView(more, mlp);

            root.addView(topBar, new FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP));

            // Bilah bawah futuristik
            bottomBar = new LinearLayout(this);
            bottomBar.setOrientation(LinearLayout.VERTICAL);
            bottomBar.setClickable(true);
            bottomBar.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                    new int[]{0xF20A1024, 0xCC0A1024, 0x000A1024}));
            bottomBar.setPadding(dp(12), dp(24), dp(12), dp(12));

            LinearLayout prow = new LinearLayout(this);
            prow.setGravity(Gravity.CENTER_VERTICAL);
            posView = label(12.5f, Ui.ION);
            posView.setTypeface(Typeface.MONOSPACE);
            posView.setPadding(dp(4), 0, dp(6), 0);
            prow.addView(posView);

            seek = new SeekBar(this);
            seek.setMax(1000);
            styleNeonSeekBar(seek);
            seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                    if (!fromUser || player == null) return;
                    long d = player.getDuration();
                    if (d > 0) {
                        long target = d * p / 1000;
                        posView.setText(fmt(target));
                        showCenter(fmt(target) + " / " + fmt(d), 0, (int) (p / 10));
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar s) {
                    userSeeking = true;
                    h.removeCallbacks(hider);
                }

                @Override
                public void onStopTrackingTouch(SeekBar s) {
                    userSeeking = false;
                    long d = player == null ? 0 : player.getDuration();
                    if (player != null && d > 0) {
                        if (player.isCurrentMediaItemSeekable()) {
                            player.seekTo(d * s.getProgress() / 1000);
                        } else {
                            showCenter("Video ini tidak mendukung lompat posisi", 1200, -1);
                        }
                    }
                    h.removeCallbacks(hudHider);
                    h.postDelayed(hudHider, 400);
                    showControls(true);
                }
            });
            prow.addView(seek, new LinearLayout.LayoutParams(0, WRAP, 1));

            durView = label(12.5f, Ui.TEXT);
            durView.setTypeface(Typeface.MONOSPACE);
            durView.setPadding(dp(6), dp(4), dp(4), dp(4));
            durView.setClickable(true);
            durView.setOnClickListener(v -> {
                showRemaining = !showRemaining;
                prefs.edit().putBoolean("showRem", showRemaining).apply();
                tick();
                showCenter(showRemaining ? "Tampilan sisa waktu" : "Tampilan total durasi", 800, -1);
            });
            prow.addView(durView);
            bottomBar.addView(prow);

            // Baris tombol transport (Prev, -10, Play/Pause, +10, Next) di bawah slider video
            centerHud = new LinearLayout(this);
            centerHud.setOrientation(LinearLayout.HORIZONTAL);
            centerHud.setGravity(Gravity.CENTER);

            prevBtn = circleHudBtn("\u23EE", 38, 0xCC0A1024, Ui.LINE, Ui.TEXT, 15);
            prevBtn.setOnClickListener(v -> skip(-1));

            TextView rewBtn = circleHudBtn("-10", 42, 0xCC121A35, Ui.LINE, Ui.TEXT, 12);
            rewBtn.setOnClickListener(v -> seekBy(-STEP_MS));

            playBtn = circleHudBtn("\u23F8", 52, 0xE60E3B46, Ui.ION, Ui.ION, 20);
            playBtn.setOnClickListener(v -> togglePlay());

            TextView fwdBtn = circleHudBtn("+10", 42, 0xCC121A35, Ui.LINE, Ui.TEXT, 12);
            fwdBtn.setOnClickListener(v -> seekBy(STEP_MS));

            nextBtn = circleHudBtn("\u23ED", 38, 0xCC0A1024, Ui.LINE, Ui.TEXT, 15);
            nextBtn.setOnClickListener(v -> skip(1));

            View[] cBtns = {prevBtn, rewBtn, playBtn, fwdBtn, nextBtn};
            for (int i = 0; i < cBtns.length; i++) {
                LinearLayout.LayoutParams clp = (LinearLayout.LayoutParams) cBtns[i].getLayoutParams();
                if (i > 0) clp.leftMargin = dp(14);
                centerHud.addView(cBtns[i], clp);
            }
            LinearLayout.LayoutParams crp = new LinearLayout.LayoutParams(MATCH, WRAP);
            crp.topMargin = dp(8);
            bottomBar.addView(centerHud, crp);

            LinearLayout brow = new LinearLayout(this);
            brow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams brp = new LinearLayout.LayoutParams(MATCH, WRAP);
            brp.topMargin = dp(8);

            TextView lockBtn = deckBtn("Kunci");
            lockBtn.setOnClickListener(v -> setLocked(true));

            repeatBtn = deckBtn("Ulang");
            repeatBtn.setOnClickListener(v -> cycleRepeat());

            abQuickBtn = deckBtn("A-B");
            abQuickBtn.setOnClickListener(v -> abStep());

            aspectBtn = deckBtn(ASPECT_NAMES[aspectIdx]);
            aspectBtn.setOnClickListener(v -> cycleAspect());

            orientBtn = deckBtn(ORIENT_NAMES[orientIdx]);
            orientBtn.setOnClickListener(v -> cycleOrient());

            TextView shotBtn = deckBtn("Foto");
            shotBtn.setOnClickListener(v -> screenshot());

            TextView listOrInfoBtn = deckBtn(uris.size() > 1 ? "Daftar" : "Info");
            listOrInfoBtn.setOnClickListener(v -> {
                if (uris.size() > 1) playlistMenu();
                else infoDialog();
            });

            TextView[] deck = {lockBtn, repeatBtn, abQuickBtn, aspectBtn, orientBtn, shotBtn, listOrInfoBtn};
            for (int i = 0; i < deck.length; i++) {
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(0, WRAP, 1);
                if (i > 0) dlp.leftMargin = dp(5);
                brow.addView(deck[i], dlp);
            }
            bottomBar.addView(brow, brp);
            root.addView(bottomBar, new FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM));

            // Penanda A-B mengambang saat kontrol disembunyikan
            abBtn = hudPillBtn("", 0xCC0E3B46, Ui.ION, Ui.ION, 12.5f);
            abBtn.setVisibility(View.GONE);
            abBtn.setOnClickListener(v -> abStep());
            FrameLayout.LayoutParams al = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.END | Gravity.TOP);
            al.topMargin = dp(64);
            al.rightMargin = dp(16);
            root.addView(abBtn, al);

            // Chip lanjutkan dari posisi terakhir
            resumeChip = hudPillBtn("", 0xE6121A35, Ui.ION, Ui.TEXT, 13);
            resumeChip.setVisibility(View.GONE);
            FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.START | Gravity.BOTTOM);
            rl.leftMargin = dp(16);
            rl.bottomMargin = dp(186);
            root.addView(resumeChip, rl);

            // Tombol cepat mengambang transparan (-30, -10, +10, +30, 2x tahan) yang tetap tampil saat video diputar
            quickFloatBar = new LinearLayout(this);
            quickFloatBar.setGravity(Gravity.CENTER);
            quickFloatBar.setClickable(true);
            quickFloatBar.setBackground(pill(0x3D0A1024, 31, 0x4426325E));
            quickFloatBar.setPadding(dp(8), dp(6), dp(8), dp(6));

            TextView qRew30 = circleHudBtn("-30", 50, 0x4D0A1024, 0x6626325E, 0xE6FFFFFF, 15f);
            qRew30.setOnClickListener(v -> { tapHaptic(v); seekBy(-STEP_LONG_MS); });

            TextView qRew10 = circleHudBtn("-10", 50, 0x4D0A1024, 0x6626325E, 0xE6FFFFFF, 15f);
            qRew10.setOnClickListener(v -> { tapHaptic(v); seekBy(-STEP_MS); });

            TextView qFwd10 = circleHudBtn("+10", 50, 0x4D0A1024, 0x6626325E, 0xE6FFFFFF, 15f);
            qFwd10.setOnClickListener(v -> { tapHaptic(v); seekBy(STEP_MS); });

            TextView qFwd30 = circleHudBtn("+30", 50, 0x4D0A1024, 0x6626325E, 0xE6FFFFFF, 15f);
            qFwd30.setOnClickListener(v -> { tapHaptic(v); seekBy(STEP_LONG_MS); });

            speed2xBtn = circleHudBtn("2x", 50, 0x4D0A1024, 0x664FE3D4, Ui.ION, 15f);
            speed2xBg = (GradientDrawable) speed2xBtn.getBackground();
            speed2xBtn.setOnTouchListener((v, e) -> {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        if (locked || player == null) return false;
                        if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
                        v.setPressed(true);
                        if (!speedHeld) {
                            speedHeld = true;
                            tapHaptic(v);
                            if (Math.abs(player.getPlaybackParameters().speed - 2f) > 0.01f) {
                                player.setPlaybackSpeed(2f);
                            }
                            styleSpeed2xBtn(true);
                            showSeekHud("\u23E9 Kecepatan 2x", 0, -1);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.setPressed(false);
                        if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(false);
                        releaseSpeedHold();
                        return true;
                    default:
                        return false;
                }
            });

            quickFloatBar.addView(qRew30);
            quickFloatBar.addView(qRew10);
            quickFloatBar.addView(qFwd10);
            quickFloatBar.addView(qFwd30);
            quickFloatBar.addView(speed2xBtn);
            root.addView(quickFloatBar, new FrameLayout.LayoutParams(WRAP, WRAP));
            layoutQuickFloatBar(getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);

            // Popup transparan di atas (di bawah bilah atas saat kontrol tampil) untuk umpan balik lompat waktu (+10/-10/+30/-30) & kecepatan 2x
            seekHud = new LinearLayout(this);
            seekHud.setOrientation(LinearLayout.VERTICAL);
            seekHud.setGravity(Gravity.CENTER);
            seekHud.setBackground(pill(0x3D0A1024, 14, 0x554FE3D4));
            seekHud.setPadding(dp(16), dp(8), dp(16), dp(8));
            seekHud.setClickable(false);
            seekHud.setFocusable(false);
            seekHud.setVisibility(View.GONE);
            seekHudText = label(14, 0xE6FFFFFF);
            seekHudText.setTypeface(Ui.DISPLAY);
            seekHudText.setGravity(Gravity.CENTER);
            seekHud.addView(seekHudText);

            LinearLayout seekTrack = new LinearLayout(this);
            seekTrack.setBackground(pill(0x3326325E, 3, 0));
            seekTrack.setWeightSum(100);
            seekHudBarFill = new View(this);
            seekHudBarFill.setBackground(pill(0x994FE3D4, 3, 0));
            seekTrack.addView(seekHudBarFill, new LinearLayout.LayoutParams(0, dp(3), 50));
            seekHudBarTrack = seekTrack;
            LinearLayout.LayoutParams stlp = new LinearLayout.LayoutParams(dp(120), dp(3));
            stlp.topMargin = dp(6);
            seekHud.addView(seekHudBarTrack, stlp);
            seekHudBarTrack.setVisibility(View.GONE);
            root.addView(seekHud, new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
            updateTopHudPosition(seekHud);

            // Tombol Lock & Close mengambang transparan di pojok kanan atas (selalu muncul)
            topRightFloatBar = new LinearLayout(this);
            topRightFloatBar.setOrientation(LinearLayout.HORIZONTAL);
            topRightFloatBar.setGravity(Gravity.CENTER);
            topRightFloatBar.setClickable(true);
            topRightFloatBar.setBackground(pill(0x3D0A1024, 26, 0x4426325E));
            topRightFloatBar.setPadding(dp(6), dp(5), dp(6), dp(5));

            floatLockBtn = circleHudBtn("\uD83D\uDD12", 44, 0x4D0A1024, 0x6626325E, 0xE6FFFFFF, 17.5f);
            floatLockBtn.setOnClickListener(v -> { tapHaptic(v); setLocked(!locked); });
            topRightFloatBar.addView(floatLockBtn);

            TextView floatCloseBtn = circleHudBtn("\u2715", 44, 0x4D0A1024, 0x6626325E, 0xE6FFFFFF, 18f);
            floatCloseBtn.setOnClickListener(v -> { tapHaptic(v); finish(); });
            LinearLayout.LayoutParams fclp = new LinearLayout.LayoutParams(dp(44), dp(44));
            fclp.leftMargin = dp(8);
            topRightFloatBar.addView(floatCloseBtn, fclp);

            FrameLayout.LayoutParams trlp = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.END | Gravity.TOP);
            trlp.topMargin = dp(12);
            trlp.rightMargin = dp(12);
            root.addView(topRightFloatBar, trlp);

            // Tombol buka kunci layar
            unlockBtn = hudPillBtn("\uD83D\uDD13 Buka Kunci", 0xE60A1024, Ui.ION, Ui.ION, 14);
            unlockBtn.setVisibility(View.GONE);
            unlockBtn.setOnClickListener(v -> setLocked(false));
            FrameLayout.LayoutParams ul = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.START | Gravity.CENTER_VERTICAL);
            ul.leftMargin = dp(24);
            root.addView(unlockBtn, ul);

            privacyCurtain = new View(this);
            privacyCurtain.setBackgroundColor(Color.BLACK);
            privacyCurtain.setVisibility(View.GONE);
            root.addView(privacyCurtain, new FrameLayout.LayoutParams(MATCH, MATCH));

            root.addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or, ob) -> {
                if (r - l != or - ol || bt - t != ob - ot) {
                    applyAspect();
                    int w = r - l, ht = bt - t;
                    if (w > 0 && ht > 0) layoutQuickFloatBar(w > ht);
                }
            });
            setContentView(root);
        }

        void tapHaptic(View v) {
            if (v != null) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }

        void styleSpeed2xBtn(boolean active) {
            if (speed2xBtn == null || speed2xBg == null) return;
            speed2xBg.setColor(active ? 0xCC0E3B46 : 0x4D0A1024);
            speed2xBg.setStroke(dp(1.5f), active ? Ui.ION : 0x664FE3D4);
            speed2xBtn.setTextColor(active ? Color.WHITE : Ui.ION);
        }

        void releaseSpeedHold() {
            if (!speedHeld) return;
            speedHeld = false;
            if (player != null) {
                float target = SPEEDS[speedIdx];
                if (Math.abs(player.getPlaybackParameters().speed - target) > 0.01f) {
                    player.setPlaybackSpeed(target);
                }
            }
            styleSpeed2xBtn(false);
            h.removeCallbacks(seekHudHider);
            h.postDelayed(seekHudHider, 300);
        }

        void layoutQuickFloatBar(boolean landscape) {
            if (quickFloatBar == null) return;
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            int rw = root != null && root.getWidth() > 0 ? root.getWidth() : (landscape ? Math.max(dm.widthPixels, dm.heightPixels) : Math.min(dm.widthPixels, dm.heightPixels));
            int rh = root != null && root.getHeight() > 0 ? root.getHeight() : (landscape ? Math.min(dm.widthPixels, dm.heightPixels) : Math.max(dm.widthPixels, dm.heightPixels));
            int orient = landscape ? 1 : 0;
            if (lastQuickOrient == orient && lastQuickW == rw && lastQuickH == rh) return;
            lastQuickOrient = orient;
            lastQuickW = rw;
            lastQuickH = rh;

            quickFloatBar.setOrientation(landscape ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
            FrameLayout.LayoutParams lp;
            if (landscape) {
                lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.CENTER_VERTICAL);
                lp.rightMargin = dp(12) + Math.round(rw * 0.05f);
                lp.topMargin = Math.round(rh * 0.05f);
            } else {
                lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
                lp.bottomMargin = dp(14);
            }
            quickFloatBar.setLayoutParams(lp);

            int sz = dp(landscape ? 47 : 52);
            int gap = dp(landscape ? 8 : 10);
            for (int i = 0; i < quickFloatBar.getChildCount(); i++) {
                View child = quickFloatBar.getChildAt(i);
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(sz, sz);
                if (i > 0) {
                    if (landscape) clp.topMargin = gap;
                    else clp.leftMargin = gap;
                }
                child.setLayoutParams(clp);
            }

            if (bottomBar != null) {
                bottomBar.setPadding(dp(12), dp(24), dp(12), dp(landscape ? 12 : 84));
            }
            if (resumeChip != null && resumeChip.getLayoutParams() instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams rlp = (FrameLayout.LayoutParams) resumeChip.getLayoutParams();
                rlp.bottomMargin = dp(landscape ? 186 : 254);
                resumeChip.setLayoutParams(rlp);
            }
            updateHudPositions();
        }

        // ---------------- PEMUTAR ----------------

        void buildPlayer(int index) {
            String ua = getIntent().getStringExtra(EXTRA_UA);
            if (ua == null || ua.isEmpty()) ua = "Mozilla/5.0 (Linux; Android " + Build.VERSION.RELEASE + ") FilecosterPlayer";
            boolean external = Intent.ACTION_VIEW.equals(getIntent().getAction());
            Map<String, String> hdr = new HashMap<>();
            String ref = getIntent().getStringExtra(EXTRA_REFERER);
            if (ref != null && !external) hdr.put("Referer", ref);
            String first = uris.get(index);
            if (first.startsWith("http") && !external) {
                try {
                    String cookie = CookieManager.getInstance().getCookie(first);
                    if (cookie != null) hdr.put("Cookie", cookie);
                } catch (Exception ignored) { }
            }
            DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                    .setUserAgent(ua)
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(15000)
                    .setReadTimeoutMs(30000)
                    .setDefaultRequestProperties(hdr);
            DefaultDataSource.Factory ds = new DefaultDataSource.Factory(this, http);
            DefaultExtractorsFactory baseExtractors = new DefaultExtractorsFactory()
                    .setConstantBitrateSeekingEnabled(true)
                    .setConstantBitrateSeekingAlwaysEnabled(true)
                    .setMp4ExtractorFlags(Mp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS);
            ExtractorsFactory extractors = new ExtractorsFactory() {
                @Override
                public Extractor[] createExtractors() {
                    return baseExtractors.createExtractors();
                }

                @Override
                public Extractor[] createExtractors(Uri uri, Map<String, List<String>> responseHeaders) {
                    Extractor[] list = baseExtractors.createExtractors(uri, responseHeaders);
                    if (isLocalUri(uri)) {
                        for (int i = 0; i < list.length; i++) {
                            if (list[i] instanceof FragmentedMp4Extractor) {
                                list[i] = new Fmp4SeekableExtractor(PlayerActivity.this, uri, list[i]);
                            }
                        }
                    }
                    return list;
                }
            };
            DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                    .setBackBuffer(35000, true)
                    .build();
            player = new ExoPlayer.Builder(this)
                    .setMediaSourceFactory(new DefaultMediaSourceFactory(ds, extractors))
                    .setLoadControl(loadControl)
                    .setSeekBackIncrementMs(STEP_MS)
                    .setSeekForwardIncrementMs(STEP_MS)
                    .build();
            player.setSeekParameters(new SeekParameters(5_000_000L, 5_000_000L));
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(), true);
            player.setHandleAudioBecomingNoisy(true);
            player.setRepeatMode(clamp(prefs.getInt("repeat", Player.REPEAT_MODE_OFF), 0, 2));
            player.addListener(new Player.Listener() {
                @Override
                public void onIsPlayingChanged(boolean playing) {
                    playBtn.setText(playing ? "\u23F8" : "\u25B6");
                    if (playing && controlsShown) {
                        h.removeCallbacks(hider);
                        h.postDelayed(hider, HIDE_MS);
                    }
                    if (!playing) saveCurrent();
                }

                @Override
                public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_ENDED && curUri != null) WatchStore.setDone(PlayerActivity.this, curUri, true);
                    updateNavButtons();
                }

                @Override
                public void onMediaItemTransition(MediaItem item, int reason) {
                    if (curUri != null) {
                        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) WatchStore.setDone(PlayerActivity.this, curUri, true);
                        else if (curDur > 0) WatchStore.save(PlayerActivity.this, curUri, curPos, curDur);
                    }
                    curUri = item == null ? null : item.mediaId;
                    curPos = curDur = 0;
                    abA = abB = -1;
                    updateAbButton();
                    updateTitle();
                    updateNavButtons();
                    if (curUri != null) offerResume(curUri, true);
                }

                @Override
                public void onVideoSizeChanged(VideoSize vs) {
                    applyAspect();
                    if (vs.height > 0) {
                        qualityBadge.setText(vs.height + "p");
                        qualityBadge.setVisibility(View.VISIBLE);
                    } else {
                        qualityBadge.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onAudioSessionIdChanged(int id) {
                    attachAudioEffects(id);
                }

                @Override
                public void onPlayerError(PlaybackException e) {
                    showCenter("Tidak bisa memutar video ini\n(" + e.getErrorCodeName() + ")", 6000, -1);
                    showControls(true);
                }
            });
            pv.setPlayer(player);
            updateRepeatButton();
            loadPlaylist(index);
        }

        void loadPlaylist(int index) {
            List<MediaItem> items = new ArrayList<>();
            for (int i = 0; i < uris.size(); i++) items.add(mediaItem(uris.get(i), titles.get(i), null));
            WatchStore.Entry e = WatchStore.get(this, uris.get(index));
            long start = e != null && e.resumable() ? e.pos : 0;
            curUri = uris.get(index);
            player.setMediaItems(items, index, start);
            player.prepare();
            player.play();
            updateTitle();
            updateNavButtons();
            offerResume(curUri, false);
        }

        MediaItem mediaItem(String u, String title, List<MediaItem.SubtitleConfiguration> subs) {
            MediaItem.Builder mb = new MediaItem.Builder()
                    .setUri(u)
                    .setMediaId(u)
                    .setMediaMetadata(new MediaMetadata.Builder().setTitle(title).build());
            if (u.toLowerCase(Locale.ROOT).contains(".m3u8")) mb.setMimeType(MimeTypes.APPLICATION_M3U8);
            if (subs != null) mb.setSubtitleConfigurations(subs);
            return mb.build();
        }

        void offerResume(String uri, boolean seekNow) {
            WatchStore.Entry e = WatchStore.get(this, uri);
            if (e == null || !e.resumable()) {
                resumeChip.setVisibility(View.GONE);
                return;
            }
            if (seekNow) player.seekTo(e.pos);
            resumeChip.setText("Dilanjutkan dari " + fmt(e.pos) + "  \u2022  \u21BA Mulai awal");
            resumeChip.setOnClickListener(v -> {
                player.seekTo(0);
                resumeChip.setVisibility(View.GONE);
                showCenter("Mulai dari awal", 800, -1);
            });
            resumeChip.setVisibility(View.VISIBLE);
            h.removeCallbacks(chipHider);
            h.postDelayed(chipHider, 7000);
        }

        void tick() {
            if (player == null) return;
            long d = player.getDuration(), p = player.getCurrentPosition();
            if (d > 0) {
                curPos = p;
                curDur = d;
            }
            if (!userSeeking) {
                posView.setText(fmt(p));
                if (d > 0) {
                    seek.setProgress((int) (p * 1000 / d));
                    seek.setSecondaryProgress((int) (player.getBufferedPosition() * 1000 / d));
                }
            }
            if (d > 0) {
                durView.setText(showRemaining ? "-" + fmt(Math.max(0, d - p)) : fmt(d));
            } else {
                durView.setText("--:--");
            }
            if (abA >= 0 && abB > abA && p >= abB) player.seekTo(abA);
            long now = System.currentTimeMillis();
            if (player.isPlaying() && now - lastSave > SAVE_EVERY_MS) {
                lastSave = now;
                saveCurrent();
            }
            if (controlsShown && now % 30000 < 300) updateBattery();
        }

        void saveCurrent() {
            if (player == null || curUri == null) return;
            long d = player.getDuration();
            if (d > 0) WatchStore.save(this, curUri, player.getCurrentPosition(), d);
        }

        void updateTitle() {
            int i = player == null ? 0 : player.getCurrentMediaItemIndex();
            String t = i >= 0 && i < titles.size() ? titles.get(i) : "Video";
            titleView.setText(uris.size() > 1 ? "[" + (i + 1) + "/" + uris.size() + "] " + t : t);
        }

        void updateNavButtons() {
            if (prevBtn == null || nextBtn == null || player == null) return;
            boolean hasPrev = player.hasPreviousMediaItem() || player.getCurrentPosition() > 3000;
            boolean hasNext = player.hasNextMediaItem();
            prevBtn.setAlpha(hasPrev ? 1f : 0.4f);
            nextBtn.setAlpha(hasNext ? 1f : 0.4f);
        }

        void updateBattery() {
            try {
                Intent bi = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                if (bi == null) return;
                int lvl = bi.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), sc = bi.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                boolean chg = bi.getIntExtra(BatteryManager.EXTRA_STATUS, 0) == BatteryManager.BATTERY_STATUS_CHARGING;
                if (lvl >= 0 && sc > 0) batteryView.setText("\u2022  " + (chg ? "\u26A1" : "") + (lvl * 100 / sc) + "%");
            } catch (Exception ignored) { }
        }

        void togglePlay() {
            if (player == null) return;
            if (player.getPlaybackState() == Player.STATE_ENDED) player.seekTo(0);
            if (player.isPlaying()) {
                player.pause();
                showCenter("Jeda", 800, -1);
            } else {
                player.play();
            }
            showControls(true);
        }

        void seekBy(long ms) {
            if (player == null) return;
            if (!player.isCurrentMediaItemSeekable()) {
                showCenter("Video ini tidak mendukung lompat posisi", 1200, -1);
                return;
            }
            long now = SystemClock.elapsedRealtime();
            long base = (lastSeekTarget >= 0 && now - lastSeekAt < 350) ? lastSeekTarget : player.getCurrentPosition();
            long d = player.getDuration();
            long t = Math.max(0, base + ms);
            if (d > 0) t = Math.min(t, d);
            lastSeekAt = now;
            lastSeekTarget = t;
            player.seekTo(t);
            int pct = d > 0 ? (int) (t * 100 / d) : -1;
            showSeekHud((ms > 0 ? "+" : "-") + (Math.abs(ms) / 1000) + " dtk  (" + fmt(t) + ")", 700, pct);
        }

        void skip(int dir) {
            if (player == null) return;
            saveCurrent();
            if (dir > 0 && player.hasNextMediaItem()) player.seekToNextMediaItem();
            else if (dir < 0 && player.hasPreviousMediaItem() && player.getCurrentPosition() <= 3000) player.seekToPreviousMediaItem();
            else if (dir < 0) player.seekTo(0);
            else showCenter("Ini video terakhir", 900, -1);
            showControls(true);
        }

        // ---------------- RASIO, ARAH, ZOOM ----------------

        void setAspect(int idx) {
            aspectIdx = clamp(idx, 0, ASPECT_NAMES.length - 1);
            prefs.edit().putInt("aspect", aspectIdx).apply();
            applyAspect();
            aspectBtn.setText(ASPECT_NAMES[aspectIdx]);
            showCenter("Rasio: " + ASPECT_NAMES[aspectIdx], 900, -1);
            showControls(true);
        }

        void applyAspect() {
            if (pv == null || root == null) return;
            int W = root.getWidth(), H = root.getHeight();
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) pv.getLayoutParams();
            int w = ViewGroup.LayoutParams.MATCH_PARENT, hh = ViewGroup.LayoutParams.MATCH_PARENT;
            int mode;
            switch (aspectIdx) {
                case 1: mode = AspectRatioFrameLayout.RESIZE_MODE_FILL; break;
                case 2: mode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM; break;
                case 3:
                case 4: {
                    mode = AspectRatioFrameLayout.RESIZE_MODE_FILL;
                    float r = aspectIdx == 3 ? 16f / 9f : 4f / 3f;
                    if (W > 0 && H > 0) {
                        if ((float) W / H > r) {
                            hh = H;
                            w = Math.round(H * r);
                        } else {
                            w = W;
                            hh = Math.round(W / r);
                        }
                    }
                    break;
                }
                default: mode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
            }
            pv.setResizeMode(mode);
            if (lp.width != w || lp.height != hh) {
                lp.width = w;
                lp.height = hh;
                lp.gravity = Gravity.CENTER;
                pv.setLayoutParams(lp);
            }
        }

        void cycleAspect() {
            setAspect((aspectIdx + 1) % ASPECT_NAMES.length);
        }

        void cycleOrient() {
            orientIdx = (orientIdx + 1) % ORIENT.length;
            setRequestedOrientation(ORIENT[orientIdx]);
            orientBtn.setText(ORIENT_NAMES[orientIdx]);
            prefs.edit().putInt("orient", orientIdx).apply();
            showCenter("Arah: " + ORIENT_NAMES[orientIdx], 900, -1);
            showControls(true);
        }

        void setZoom(float z) {
            zoom = Math.max(1f, Math.min(4f, z));
            View s = pv.getVideoSurfaceView();
            if (s != null) {
                s.setScaleX(zoom);
                s.setScaleY(zoom);
            }
            if (gMode == G_ZOOM) showCenter("Zoom " + Math.round(zoom * 100) + "%", 0, Math.round((zoom - 1f) * 100f / 3f));
        }

        // ---------------- MENU FUTURISTIK ----------------

        void showFuturisticMenu(String title, final List<String> items, final List<Runnable> actions) {
            final Dialog dlg = new Dialog(this);
            dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(pill(0xF50A1024, 16, Ui.ION));
            card.setPadding(dp(16), dp(14), dp(16), dp(14));

            TextView hdr = label(16, Ui.ION);
            hdr.setTypeface(Ui.DISPLAY);
            hdr.setText(title);
            hdr.setPadding(dp(4), 0, dp(4), dp(10));
            card.addView(hdr);

            View div = new View(this);
            div.setBackgroundColor(Ui.LINE);
            card.addView(div, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

            ScrollView sv = new ScrollView(this);
            LinearLayout list = new LinearLayout(this);
            list.setOrientation(LinearLayout.VERTICAL);
            list.setPadding(0, dp(6), 0, 0);

            for (int i = 0; i < items.size(); i++) {
                final int idx = i;
                String labelTxt = items.get(i);
                boolean active = labelTxt.contains("\u2713");
                TextView row = new TextView(this);
                row.setText(labelTxt);
                row.setTextSize(14);
                row.setTypeface(active ? Ui.MEDIUM : Ui.BODY);
                row.setTextColor(active ? Ui.ION : Ui.TEXT);
                row.setBackground(pill(active ? 0xCC0E3B46 : 0xAA121A35, 10, active ? Ui.ION : Ui.LINE));
                row.setPadding(dp(14), dp(11), dp(14), dp(11));
                row.setOnClickListener(v -> {
                    dlg.dismiss();
                    actions.get(idx).run();
                });
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rlp.topMargin = dp(6);
                list.addView(row, rlp);
            }
            sv.addView(list);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            card.addView(sv, slp);

            dlg.setContentView(card);
            if (dlg.getWindow() != null) {
                dlg.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                int w = Math.min(dp(360), Math.round(getResources().getDisplayMetrics().widthPixels * 0.88f));
                dlg.getWindow().setLayout(w, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
            dlg.show();
        }

        void moreMenu() {
            final List<String> names = new ArrayList<>();
            final List<Runnable> acts = new ArrayList<>();
            addItem(names, acts, "Kecepatan putar (" + speedLabel(SPEEDS[speedIdx]) + ")", this::chooseSpeed);
            addItem(names, acts, "Timer tidur" + (sleepAt > 0 ? " (" + Math.max(1, (sleepAt - System.currentTimeMillis()) / 60000) + " mnt lagi)"
                    : (player.getPauseAtEndOfMediaItems() ? " (akhir video)" : "")), this::sleepMenu);
            addItem(names, acts, abA < 0 ? "Ulangi A-B: tandai titik A" : (abB < 0 ? "Ulangi A-B: tandai titik B" : "Ulangi A-B: matikan"), this::abStep);
            String[] rep = {"Mati", "Ulangi video ini", "Ulangi semua"};
            addItem(names, acts, "Mode ulang: " + rep[player.getRepeatMode()], this::cycleRepeat);
            addItem(names, acts, "Acak daftar putar: " + (player.getShuffleModeEnabled() ? "aktif" : "mati"), () -> {
                player.setShuffleModeEnabled(!player.getShuffleModeEnabled());
                showCenter(player.getShuffleModeEnabled() ? "Acak aktif" : "Acak mati", 900, -1);
            });
            if (uris.size() > 1) addItem(names, acts, "Daftar putar (" + uris.size() + " video)", this::playlistMenu);
            addItem(names, acts, "Lompat ke waktu ...", this::jumpDialog);
            addItem(names, acts, "Rasio layar (" + ASPECT_NAMES[aspectIdx] + ")", () -> {
                List<String> an = new ArrayList<>();
                List<Runnable> aa = new ArrayList<>();
                for (int i = 0; i < ASPECT_NAMES.length; i++) {
                    final int ii = i;
                    addItem(an, aa, ASPECT_NAMES[i] + (i == aspectIdx ? "  \u2713" : ""), () -> setAspect(ii));
                }
                showFuturisticMenu("Rasio Layar", an, aa);
            });
            addItem(names, acts, "Reset zoom (" + Math.round(zoom * 100) + "%)", () -> {
                setZoom(1f);
                showCenter("Zoom 100%", 800, -1);
            });
            addItem(names, acts, "Tangkap layar", this::screenshot);
            addItem(names, acts, "Tandai sudah ditonton", () -> {
                if (curUri != null) WatchStore.setDone(this, curUri, true);
                showCenter("Ditandai sudah ditonton", 900, -1);
            });
            addItem(names, acts, "Info video", this::infoDialog);
            showFuturisticMenu("Kontrol Lanjutan", names, acts);
        }

        static void addItem(List<String> names, List<Runnable> acts, String n, Runnable r) {
            names.add(n);
            acts.add(r);
        }

        void setSpeed(int w) {
            speedIdx = clamp(w, 0, SPEEDS.length - 1);
            if (player != null) player.setPlaybackSpeed(SPEEDS[speedIdx]);
            String lbl = speedLabel(SPEEDS[speedIdx]);
            speedBtn.setText(lbl);
            speedBtn.setBackground(pill(speedIdx == 3 ? 0xAA121A35 : 0xCC0E3B46, 14, speedIdx == 3 ? Ui.LINE : Ui.ION));
            showCenter("Kecepatan " + lbl, 900, -1);
        }

        void chooseSpeed() {
            List<String> names = new ArrayList<>();
            List<Runnable> acts = new ArrayList<>();
            for (int i = 0; i < SPEEDS.length; i++) {
                final int ii = i;
                addItem(names, acts, speedLabel(SPEEDS[i]) + (i == speedIdx ? "  \u2713" : ""), () -> setSpeed(ii));
            }
            showFuturisticMenu("Kecepatan Putar", names, acts);
        }

        static String speedLabel(float s) {
            return (s == Math.round(s) ? String.valueOf((int) s) : String.valueOf(s)) + "x";
        }

        void sleepMenu() {
            final int[] mins = {0, 15, 30, 45, 60, 90, -1};
            String[] labels = {"Mati", "15 menit", "30 menit", "45 menit", "60 menit", "90 menit", "Di akhir video ini"};
            List<String> names = new ArrayList<>();
            List<Runnable> acts = new ArrayList<>();
            for (int i = 0; i < mins.length; i++) {
                final int m = mins[i];
                addItem(names, acts, labels[i], () -> {
                    h.removeCallbacks(sleeper);
                    sleepAt = 0;
                    player.setPauseAtEndOfMediaItems(false);
                    if (m > 0) {
                        sleepAt = System.currentTimeMillis() + m * 60000L;
                        h.postDelayed(sleeper, m * 60000L);
                        showCenter("Dijeda dalam " + m + " menit", 1200, -1);
                    } else if (m < 0) {
                        player.setPauseAtEndOfMediaItems(true);
                        showCenter("Dijeda di akhir video ini", 1200, -1);
                    } else {
                        showCenter("Timer tidur mati", 900, -1);
                    }
                });
            }
            showFuturisticMenu("Timer Tidur", names, acts);
        }

        void abStep() {
            if (player == null) return;
            long p = player.getCurrentPosition();
            if (abA < 0) {
                abA = p;
                showCenter("Titik A: " + fmt(p), 1000, -1);
            } else if (abB < 0) {
                if (p <= abA + 500) {
                    showCenter("Titik B harus setelah A", 1000, -1);
                    return;
                }
                abB = p;
                player.seekTo(abA);
                showCenter("Ulangi " + fmt(abA) + " - " + fmt(abB), 1200, -1);
            } else {
                abA = abB = -1;
                showCenter("Ulangi A-B mati", 900, -1);
            }
            updateAbButton();
        }

        void updateAbButton() {
            if (abBtn != null) {
                if (abA < 0) {
                    abBtn.setVisibility(View.GONE);
                } else {
                    abBtn.setText(abB < 0 ? "A " + fmt(abA) + " \u2022 Tandai B" : "A-B Aktif \u2715");
                    abBtn.setVisibility(View.VISIBLE);
                }
            }
            if (abQuickBtn != null) {
                boolean active = abA >= 0;
                abQuickBtn.setText(abA < 0 ? "A-B" : (abB < 0 ? "A \u2192 B" : "A-B \u2713"));
                abQuickBtn.setTextColor(active ? Ui.AMBER : Ui.TEXT);
                abQuickBtn.setBackground(pill(active ? 0xCC2A1F0A : 0x88121A35, 10, active ? Ui.AMBER : Ui.LINE));
            }
        }

        void cycleRepeat() {
            int m = (player.getRepeatMode() + 1) % 3;
            player.setRepeatMode(m);
            prefs.edit().putInt("repeat", m).apply();
            updateRepeatButton();
            showCenter(new String[]{"Ulang mati", "Ulangi video ini", "Ulangi semua"}[m], 900, -1);
        }

        void updateRepeatButton() {
            if (repeatBtn == null || player == null) return;
            int m = player.getRepeatMode();
            repeatBtn.setText(new String[]{"Ulang", "Ulang 1", "Ulang \u221E"}[m]);
            boolean on = m != Player.REPEAT_MODE_OFF;
            repeatBtn.setTextColor(on ? Ui.ION : Ui.TEXT);
            repeatBtn.setBackground(pill(on ? 0xCC0E3B46 : 0x88121A35, 10, on ? Ui.ION : Ui.LINE));
        }

        void playlistMenu() {
            List<String> names = new ArrayList<>();
            List<Runnable> acts = new ArrayList<>();
            int cur = player.getCurrentMediaItemIndex();
            for (int i = 0; i < uris.size(); i++) {
                final int ii = i;
                WatchStore.Entry e = WatchStore.get(this, uris.get(i));
                String mark = i == cur ? "\u25B6 " : (e != null && e.done ? "\u2713 " : (e != null && e.percent() > 0 ? e.percent() + "%  " : ""));
                String suffix = i == cur ? "  \u2713" : "";
                addItem(names, acts, mark + titles.get(i) + suffix, () -> {
                    saveCurrent();
                    player.seekTo(ii, 0);
                });
            }
            showFuturisticMenu("Daftar Putar (" + uris.size() + ")", names, acts);
        }

        void jumpDialog() {
            final Dialog dlg = new Dialog(this);
            dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(pill(0xF50A1024, 16, Ui.ION));
            card.setPadding(dp(18), dp(16), dp(18), dp(16));

            TextView hdr = label(16, Ui.ION);
            hdr.setTypeface(Ui.DISPLAY);
            hdr.setText("Lompat ke Waktu");
            card.addView(hdr);

            final EditText in = new EditText(this);
            in.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME);
            in.setHint("mis. 1:23:45 atau 12:30");
            in.setText(fmt(player.getCurrentPosition()));
            in.setSelectAllOnFocus(true);
            in.setTextColor(Ui.TEXT);
            in.setHintTextColor(Ui.MUTED);
            in.setBackground(pill(0xFF121A35, 10, Ui.LINE));
            in.setPadding(dp(12), dp(10), dp(12), dp(10));
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ilp.topMargin = dp(12);
            card.addView(in, ilp);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = dp(14);

            TextView cancel = hudPillBtn("Batal", 0xFF121A35, Ui.LINE, Ui.MUTED, 14);
            cancel.setOnClickListener(v -> dlg.dismiss());
            row.addView(cancel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            TextView go = hudPillBtn("Lompat", Ui.ION, Ui.ION, Ui.ABYSS, 14);
            go.setTypeface(Ui.DISPLAY);
            go.setOnClickListener(v -> {
                long t = parseTime(in.getText().toString());
                long dur = player.getDuration();
                if (t < 0 || (dur > 0 && t > dur)) {
                    showCenter("Waktu tidak valid", 1000, -1);
                    return;
                }
                dlg.dismiss();
                player.seekTo(t);
            });
            LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            glp.leftMargin = dp(10);
            row.addView(go, glp);
            card.addView(row, rlp);

            dlg.setContentView(card);
            if (dlg.getWindow() != null) {
                dlg.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                dlg.getWindow().setLayout(Math.min(dp(340), Math.round(getResources().getDisplayMetrics().widthPixels * 0.85f)),
                        ViewGroup.LayoutParams.WRAP_CONTENT);
            }
            dlg.show();
        }

        static long parseTime(String s) {
            try {
                String[] p = s.trim().split("[:.]");
                long t = 0;
                for (String x : p) t = t * 60 + Long.parseLong(x.trim());
                return t * 1000;
            } catch (Exception e) {
                return -1;
            }
        }

        void infoDialog() {
            StringBuilder sb = new StringBuilder();
            int i = player.getCurrentMediaItemIndex();
            sb.append(i >= 0 && i < titles.size() ? titles.get(i) : "Video").append("\n\n");
            long d = player.getDuration();
            sb.append("Durasi: ").append(d > 0 ? fmt(d) : "-").append("\n");
            Format v = player.getVideoFormat(), a = player.getAudioFormat();
            if (v != null) {
                sb.append("Video: ").append(v.width).append("x").append(v.height);
                if (v.frameRate > 0) sb.append(", ").append(Math.round(v.frameRate)).append(" fps");
                if (v.sampleMimeType != null) sb.append(", ").append(v.sampleMimeType.replace("video/", ""));
                if (v.bitrate > 0) sb.append(", ").append(v.bitrate / 1000).append(" kbps");
                sb.append("\n");
            }
            if (a != null) {
                sb.append("Audio: ");
                if (a.sampleMimeType != null) sb.append(a.sampleMimeType.replace("audio/", ""));
                if (a.channelCount > 0) sb.append(", ").append(a.channelCount).append(" ch");
                if (a.sampleRate > 0) sb.append(", ").append(a.sampleRate).append(" Hz");
                if (a.bitrate > 0) sb.append(", ").append(a.bitrate / 1000).append(" kbps");
                sb.append("\n");
            }
            if (curUri != null) sb.append("\n").append(curUri);

            final Dialog dlg = new Dialog(this);
            dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(pill(0xF50A1024, 16, Ui.ION));
            card.setPadding(dp(18), dp(16), dp(18), dp(16));

            TextView hdr = label(16, Ui.ION);
            hdr.setTypeface(Ui.DISPLAY);
            hdr.setText("Info Video");
            card.addView(hdr);

            TextView body = label(13, Ui.TEXT);
            body.setText(sb.toString());
            body.setLineSpacing(0, 1.25f);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            blp.topMargin = dp(10);
            card.addView(body, blp);

    TextView ok = hudPillBtn("Tutup", Ui.ION_DEEP, Ui.ION, Ui.ION, 14);
    ok.setOnClickListener(btnView -> dlg.dismiss());
            LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            olp.topMargin = dp(14);
            card.addView(ok, olp);

            dlg.setContentView(card);
            if (dlg.getWindow() != null) {
                dlg.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                dlg.getWindow().setLayout(Math.min(dp(360), Math.round(getResources().getDisplayMetrics().widthPixels * 0.88f)),
                        ViewGroup.LayoutParams.WRAP_CONTENT);
            }
            dlg.show();
        }

        // ---------------- AUDIO ----------------

        void audioMenu() {
            final List<String> names = new ArrayList<>();
            final List<Runnable> acts = new ArrayList<>();
            int n = 0;
            for (final Tracks.Group g : player.getCurrentTracks().getGroups()) {
                if (g.getType() != C.TRACK_TYPE_AUDIO) continue;
                for (int k = 0; k < g.length; k++) {
                    if (!g.isTrackSupported(k)) continue;
                    n++;
                    final int kk = k;
                    addItem(names, acts, trackName(g.getTrackFormat(k), n) + (g.isTrackSelected(k) ? "  \u2713" : ""),
                            () -> selectTrack(C.TRACK_TYPE_AUDIO, g, kk));
                }
            }
            addItem(names, acts, "Equalizer ...", this::eqMenu);
            addItem(names, acts, "Penguat suara: " + (100 + boostMb * 100 / MAX_BOOST_MB) + "%", this::boostMenu);
            showFuturisticMenu(n > 1 ? "Audio" : "Audio (1 trek)", names, acts);
        }

        void selectTrack(int type, Tracks.Group g, int idx) {
            TrackSelectionParameters.Builder pb = player.getTrackSelectionParameters().buildUpon();
            if (g == null) {
                pb.setTrackTypeDisabled(type, true);
            } else {
                pb.setTrackTypeDisabled(type, false)
                        .setOverrideForType(new TrackSelectionOverride(g.getMediaTrackGroup(), idx));
            }
            player.setTrackSelectionParameters(pb.build());
        }

        static String trackName(Format f, int n) {
            StringBuilder sb = new StringBuilder(f.label != null && !f.label.isEmpty() ? f.label : "Trek " + n);
            if (f.language != null && !f.language.isEmpty() && !"und".equals(f.language)) {
                sb.append(" (").append(new Locale(f.language).getDisplayLanguage()).append(")");
            }
            if (f.channelCount > 0) sb.append(" \u00B7 ").append(f.channelCount).append(" ch");
            return sb.toString();
        }

        void attachAudioEffects(int sessionId) {
            releaseAudioEffects();
            if (sessionId == C.AUDIO_SESSION_ID_UNSET) return;
            try {
                boost = new LoudnessEnhancer(sessionId);
                boost.setTargetGain(boostMb);
                boost.setEnabled(boostMb > 0);
            } catch (Exception e) {
                boost = null;
            }
            if (eqPreset >= 0) applyEq(eqPreset);
        }

        void releaseAudioEffects() {
            try { if (boost != null) boost.release(); } catch (Exception ignored) { }
            try { if (eq != null) eq.release(); } catch (Exception ignored) { }
            boost = null;
            eq = null;
        }

        void setBoost(int mb) {
            boostMb = Math.max(0, Math.min(MAX_BOOST_MB, mb));
            try {
                if (boost == null && player != null) boost = new LoudnessEnhancer(player.getAudioSessionId());
                if (boost != null) {
                    boost.setTargetGain(boostMb);
                    boost.setEnabled(boostMb > 0);
                }
            } catch (Exception ignored) { }
        }

        void boostMenu() {
            final int[] vals = {0, 250, 500, 750, 1000};
            String[] labels = {"100% (mati)", "125%", "150%", "175%", "200%"};
            List<String> names = new ArrayList<>();
            List<Runnable> acts = new ArrayList<>();
            for (int i = 0; i < vals.length; i++) {
                final int v = vals[i];
                final String lb = labels[i];
                addItem(names, acts, lb + (boostMb == v ? "  \u2713" : ""), () -> {
                    setBoost(v);
                    showCenter("Penguat suara " + lb, 900, 100 + v * 100 / MAX_BOOST_MB);
                });
            }
            showFuturisticMenu("Penguat Suara", names, acts);
        }

        void eqMenu() {
            Equalizer e = eq;
            try {
                if (e == null) e = new Equalizer(0, player.getAudioSessionId());
                short n = e.getNumberOfPresets();
                List<String> names = new ArrayList<>();
                List<Runnable> acts = new ArrayList<>();
                addItem(names, acts, "Mati" + (eqPreset < 0 ? "  \u2713" : ""), () -> {
                    eqPreset = -1;
                    try { if (eq != null) eq.setEnabled(false); } catch (Exception ignored) { }
                    showCenter("Equalizer mati", 900, -1);
                });
                for (short i = 0; i < n; i++) {
                    final short ii = i;
                    final String pname = e.getPresetName(i);
                    addItem(names, acts, pname + (i == eqPreset ? "  \u2713" : ""), () -> {
                        applyEq(ii);
                        showCenter("Equalizer: " + pname, 900, -1);
                    });
                }
                if (eq == null) e.release();
                showFuturisticMenu("Equalizer", names, acts);
            } catch (Exception ex) {
                showCenter("Equalizer tidak tersedia di HP ini", 1500, -1);
            }
        }

        void applyEq(int preset) {
            try {
                if (eq == null) eq = new Equalizer(0, player.getAudioSessionId());
                eq.usePreset((short) preset);
                eq.setEnabled(true);
                eqPreset = preset;
            } catch (Exception e) {
                eqPreset = -1;
            }
        }

        // ---------------- SUBTITLE ----------------

        void subtitleMenu() {
            final List<String> names = new ArrayList<>();
            final List<Runnable> acts = new ArrayList<>();
            boolean off = player.getTrackSelectionParameters().disabledTrackTypes.contains(C.TRACK_TYPE_TEXT);
            addItem(names, acts, "Mati" + (off ? "  \u2713" : ""), () -> selectTrack(C.TRACK_TYPE_TEXT, null, -1));
            int n = 0;
            for (final Tracks.Group g : player.getCurrentTracks().getGroups()) {
                if (g.getType() != C.TRACK_TYPE_TEXT) continue;
                for (int k = 0; k < g.length; k++) {
                    if (!g.isTrackSupported(k)) continue;
                    n++;
                    final int kk = k;
                    addItem(names, acts, trackName(g.getTrackFormat(k), n) + (g.isTrackSelected(k) && !off ? "  \u2713" : ""),
                            () -> selectTrack(C.TRACK_TYPE_TEXT, g, kk));
                }
            }
            addItem(names, acts, "Muat subtitle (.srt .vtt .ass) ...", this::pickSubtitle);
            addItem(names, acts, "Ukuran teks: " + SUB_SIZE_NAMES[subSizeIdx], () -> {
                List<String> sn = new ArrayList<>();
                List<Runnable> sa = new ArrayList<>();
                for (int i = 0; i < SUB_SIZE_NAMES.length; i++) {
                    final int ii = i;
                    addItem(sn, sa, SUB_SIZE_NAMES[i] + (i == subSizeIdx ? "  \u2713" : ""), () -> {
                        subSizeIdx = ii;
                        prefs.edit().putInt("subSize", ii).apply();
                        applySubtitleStyle();
                    });
                }
                showFuturisticMenu("Ukuran Subtitle", sn, sa);
            });
            addItem(names, acts, "Latar hitam teks: " + (subBackground ? "aktif" : "mati"), () -> {
                subBackground = !subBackground;
                prefs.edit().putBoolean("subBg", subBackground).apply();
                applySubtitleStyle();
            });
            showFuturisticMenu(n == 0 ? "Subtitle (tanpa bawaan)" : "Subtitle", names, acts);
        }

        void applySubtitleStyle() {
            SubtitleView sv = pv == null ? null : pv.getSubtitleView();
            if (sv == null) return;
            sv.setApplyEmbeddedStyles(true);
            sv.setFractionalTextSize(SUB_SIZES[subSizeIdx]);
            sv.setStyle(new CaptionStyleCompat(Color.WHITE, subBackground ? 0xAA000000 : Color.TRANSPARENT, Color.TRANSPARENT,
                    subBackground ? CaptionStyleCompat.EDGE_TYPE_NONE : CaptionStyleCompat.EDGE_TYPE_OUTLINE, Color.BLACK, null));
        }

        void pickSubtitle() {
            Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            it.addCategory(Intent.CATEGORY_OPENABLE);
            it.setType("*/*");
            try {
                AppLock.expectExternal();
                startActivityForResult(it, REQ_SUBTITLE);
            } catch (Exception e) {
                showCenter("Pemilih file tidak tersedia", 1200, -1);
            }
        }

        @Override
        protected void onActivityResult(int req, int res, Intent data) {
            super.onActivityResult(req, res, data);
            if (req != REQ_SUBTITLE || res != RESULT_OK || data == null || data.getData() == null || player == null) return;
            Uri su = data.getData();
            String name = displayName(su).toLowerCase(Locale.ROOT);
            String mime;
            if (name.endsWith(".srt")) mime = MimeTypes.APPLICATION_SUBRIP;
            else if (name.endsWith(".vtt")) mime = MimeTypes.TEXT_VTT;
            else if (name.endsWith(".ass") || name.endsWith(".ssa")) mime = MimeTypes.TEXT_SSA;
            else {
                showCenter("Format subtitle tidak dikenal.\nGunakan .srt, .vtt, atau .ass", 2500, -1);
                return;
            }
            int idx = player.getCurrentMediaItemIndex();
            MediaItem cur = player.getCurrentMediaItem();
            if (cur == null || idx < 0) return;
            List<MediaItem.SubtitleConfiguration> subs = new ArrayList<>();
            if (cur.localConfiguration != null) subs.addAll(cur.localConfiguration.subtitleConfigurations);
            subs.add(new MediaItem.SubtitleConfiguration.Builder(su)
                    .setMimeType(mime)
                    .setLabel(displayName(su))
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build());
            long pos = player.getCurrentPosition();
            boolean playing = player.isPlaying();
            player.replaceMediaItem(idx, mediaItem(uris.get(idx), titles.get(idx), subs));
            player.seekTo(idx, pos);
            player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build());
            if (playing) player.play();
            showCenter("Subtitle dimuat", 1000, -1);
        }

        // ---------------- TANGKAP LAYAR ----------------

        void screenshot() {
            View s = pv.getVideoSurfaceView();
            if (!(s instanceof SurfaceView) || s.getWidth() <= 0 || s.getHeight() <= 0) {
                showCenter("Tangkap layar tidak tersedia", 1200, -1);
                return;
            }
            if (Build.VERSION.SDK_INT < 29) {
                showCenter("Tangkap layar butuh Android 10 ke atas", 1500, -1);
                return;
            }
            Format v = player.getVideoFormat();
            int w = v != null && v.width > 0 ? v.width : s.getWidth(), hh = v != null && v.height > 0 ? v.height : s.getHeight();
            if ((long) w * hh > 3840L * 2160L) {
                w = s.getWidth();
                hh = s.getHeight();
            }
            final Bitmap bmp = Bitmap.createBitmap(w, hh, Bitmap.Config.ARGB_8888);
            try {
                PixelCopy.request((SurfaceView) s, bmp, r -> {
                    if (r != PixelCopy.SUCCESS) {
                        showCenter("Tangkap layar gagal", 1200, -1);
                        return;
                    }
                    saveScreenshot(bmp);
                }, h);
            } catch (Exception e) {
                showCenter("Tangkap layar gagal", 1200, -1);
            }
        }

        void saveScreenshot(Bitmap bmp) {
            String name = "Filecoster_" + System.currentTimeMillis() + ".jpg";
            try {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                cv.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
                cv.put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Filecoster");
                Uri u = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                if (u == null) throw new IllegalStateException("tidak bisa membuat file");
                try (OutputStream os = getContentResolver().openOutputStream(u)) {
                    if (os == null) throw new IllegalStateException("tidak bisa menulis file");
                    bmp.compress(Bitmap.CompressFormat.JPEG, 92, os);
                }
                showCenter("Tersimpan di Pictures/Filecoster", 1500, -1);
            } catch (Exception e) {
                showCenter("Tangkap layar gagal: " + e.getMessage(), 2000, -1);
            } finally {
                bmp.recycle();
            }
        }

        // ---------------- KUNCI & KONTROL ----------------

        void setLocked(boolean on) {
            locked = on;
            if (floatLockBtn != null) {
                floatLockBtn.setText(on ? "\uD83D\uDD13" : "\uD83D\uDD12");
                if (floatLockBtn.getBackground() instanceof GradientDrawable) {
                    GradientDrawable g = (GradientDrawable) floatLockBtn.getBackground();
                    g.setColor(on ? 0xCC0E3B46 : 0x4D0A1024);
                    g.setStroke(dp(1.5f), on ? Ui.ION : 0x6626325E);
                }
            }
            if (on) {
                releaseSpeedHold();
                if (quickFloatBar != null) quickFloatBar.setVisibility(View.GONE);
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
                showControls(false);
                showCenter("Layar dikunci", 900, -1);
                unlockBtn.setVisibility(View.VISIBLE);
                h.removeCallbacks(unlockHider);
                h.postDelayed(unlockHider, 2500);
            } else {
                setRequestedOrientation(ORIENT[orientIdx]);
                unlockBtn.setVisibility(View.GONE);
                if (quickFloatBar != null) quickFloatBar.setVisibility(View.VISIBLE);
                showControls(true);
            }
        }

        void fadeIn(View v) {
            if (v.getVisibility() != View.VISIBLE) {
                v.setAlpha(0f);
                v.setVisibility(View.VISIBLE);
                v.animate().alpha(1f).setDuration(180).withEndAction(null).start();
            } else if (v.getAlpha() < 0.99f) {
                v.animate().alpha(1f).setDuration(180).withEndAction(null).start();
            }
        }

        void fadeOut(final View v) {
            if (v.getVisibility() != View.VISIBLE) return;
            v.animate().alpha(0f).setDuration(180).withEndAction(() -> v.setVisibility(View.GONE)).start();
        }

        void showControls(boolean on) {
            controlsShown = on && !locked;
            if (controlsShown) {
                fadeIn(topBar);
                fadeIn(bottomBar);
            } else {
                fadeOut(topBar);
                fadeOut(bottomBar);
            }
            h.removeCallbacks(hider);
            if (controlsShown) {
                updateBattery();
                if (player != null && player.isPlaying()) h.postDelayed(hider, HIDE_MS);
            }
            updateHudPositions();
            immersive();
        }

        void updateHudContent(TextView text, View track, View fill, String s, int percent) {
            text.setText(s);
            if (percent >= 0) {
                int p = clamp(percent, 0, 100);
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) fill.getLayoutParams();
                lp.weight = Math.max(1, p);
                fill.setLayoutParams(lp);
                if (track.getVisibility() != View.VISIBLE) track.setVisibility(View.VISIBLE);
            } else if (track.getVisibility() != View.GONE) {
                track.setVisibility(View.GONE);
            }
        }

        void showCenter(String s, long ms, int percent) {
            updateTopHudPosition(hudBox);
            updateHudContent(hudText, hudBarTrack, hudBarFill, s, percent);
            fadeIn(hudBox);
            h.removeCallbacks(hudHider);
            if (ms > 0) h.postDelayed(hudHider, ms);
        }

        void updateTopHudPosition(View hud) {
            if (hud == null || !(hud.getLayoutParams() instanceof FrameLayout.LayoutParams)) return;
            int margin;
            if (controlsShown && topBar != null && topBar.getVisibility() == View.VISIBLE) {
                int hBar = topBar.getHeight();
                margin = (hBar > 0 ? hBar : dp(70)) + dp(6);
            } else {
                margin = dp(16);
            }
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) hud.getLayoutParams();
            if (lp.topMargin != margin) {
                lp.topMargin = margin;
                hud.setLayoutParams(lp);
            }
        }

        void updateHudPositions() {
            updateTopHudPosition(hudBox);
            updateTopHudPosition(seekHud);
        }

        void showSeekHud(String s, long ms, int percent) {
            updateTopHudPosition(seekHud);
            updateHudContent(seekHudText, seekHudBarTrack, seekHudBarFill, s, percent);
            fadeIn(seekHud);
            h.removeCallbacks(seekHudHider);
            if (ms > 0) h.postDelayed(seekHudHider, ms);
        }

        // ---------------- GESTUR ----------------

        boolean onTouch(View v, MotionEvent e) {
            if (locked) {
                if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                    unlockBtn.setVisibility(View.VISIBLE);
                    h.removeCallbacks(unlockHider);
                    h.postDelayed(unlockHider, 2500);
                }
                return true;
            }
            sgd.onTouchEvent(e);
            if (e.getPointerCount() > 1 || gMode == G_ZOOM) {
                if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    gMode = G_NONE;
                    h.removeCallbacks(hudHider);
                    h.postDelayed(hudHider, 600);
                }
                return true;
            }
            gd.onTouchEvent(e);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getX();
                    downY = e.getY();
                    gMode = G_NONE;
                    seekTarget = -1;
                    startPos = player == null ? 0 : player.getCurrentPosition();
                    startBright = currentBrightness();
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (speedHeld) break;
                    float dx = e.getX() - downX, dy = e.getY() - downY;
                    if (gMode == G_NONE && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                        if (Math.abs(dx) > Math.abs(dy)) gMode = G_SEEK;
                        else if (downX < v.getWidth() / 2f) gMode = G_BRIGHT;
                    }
                    if (gMode == G_BRIGHT) {
                        float b = Math.max(0.01f, Math.min(1f, startBright - dy / (v.getHeight() * 0.7f)));
                        WindowManager.LayoutParams lp = getWindow().getAttributes();
                        lp.screenBrightness = b;
                        getWindow().setAttributes(lp);
                        int pct = Math.round(b * 100);
                        showCenter("\u2600 Kecerahan " + pct + "%", 0, pct);
                    } else if (gMode == G_SEEK && player != null && player.isCurrentMediaItemSeekable()) {
                        long dur = player.getDuration();
                        if (dur > 0) {
                            long delta = Math.round(dx / v.getWidth() * Math.min(dur, 120000L));
                            seekTarget = Math.max(0, Math.min(dur, startPos + delta));
                            long s = delta / 1000;
                            showCenter(fmt(seekTarget) + "  (" + (s >= 0 ? "+" : "") + s + " dtk)", 0, (int) (seekTarget * 100 / dur));
                        }
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    releaseSpeedHold();
                    if (gMode == G_SEEK && seekTarget >= 0 && player != null) player.seekTo(seekTarget);
                    if (gMode != G_NONE) {
                        h.removeCallbacks(hudHider);
                        h.postDelayed(hudHider, 600);
                    }
                    gMode = G_NONE;
                    break;
                default:
                    break;
            }
            return true;
        }

        float currentBrightness() {
            float b = getWindow().getAttributes().screenBrightness;
            if (b >= 0) return b;
            try {
                return Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS) / 255f;
            } catch (Exception e) {
                return 0.5f;
            }
        }

        void immersive() {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }

        static String fmt(long ms) {
            long s = Math.max(0, ms / 1000);
            long hh = s / 3600, mm = (s % 3600) / 60, ss = s % 60;
            return hh > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", hh, mm, ss) : String.format(Locale.ROOT, "%02d:%02d", mm, ss);
        }

        // ---------------- SIKLUS HIDUP ----------------

        @Override
        public void onWindowFocusChanged(boolean focus) {
            super.onWindowFocusChanged(focus);
            if (focus) immersive();
        }

        void redirectToPin() {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
            finish();
        }

        @Override
        protected void onStart() {
            super.onStart();
            if (PinLock.isSet(this) && (AppLock.needLock || !AppLock.unlocked)
                    && !Intent.ACTION_VIEW.equals(getIntent().getAction())) {
                saveCurrent();
                if (player != null) player.pause();
                redirectToPin();
            }
        }

        @Override
        public void onConfigurationChanged(Configuration newConfig) {
            super.onConfigurationChanged(newConfig);
            layoutQuickFloatBar(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE);
        }

        @Override
        protected void onResume() {
            super.onResume();
            if (privacyCurtain != null) privacyCurtain.setVisibility(View.GONE);
            if (pv != null) pv.setVisibility(View.VISIBLE);
        }

        @Override
        protected void onPause() {
            if (privacyCurtain != null) {
                privacyCurtain.bringToFront();
                privacyCurtain.setVisibility(View.VISIBLE);
            }
            if (pv != null) pv.setVisibility(View.INVISIBLE);
            super.onPause();
        }

        @Override
        protected void onStop() {
            super.onStop();
            releaseSpeedHold();
            saveCurrent();
            if (player != null) player.pause();
        }

        @Override
        protected void onDestroy() {
            h.removeCallbacksAndMessages(null);
            saveCurrent();
            releaseAudioEffects();
            if (player != null) {
                player.release();
                player = null;
            }
            super.onDestroy();
        }

        @Override
        public void onBackPressed() {
            if (locked) {
                unlockBtn.setVisibility(View.VISIBLE);
                h.removeCallbacks(unlockHider);
                h.postDelayed(unlockHider, 2500);
                return;
            }
            super.onBackPressed();
        }

        static boolean isLocalUri(Uri uri) {
            if (uri == null) return false;
            String s = uri.getScheme();
            return "file".equalsIgnoreCase(s) || "content".equalsIgnoreCase(s);
        }

        static final class Fmp4SeekableExtractor implements Extractor {
            final Context ctx;
            final Uri uri;
            final Extractor delegate;

            Fmp4SeekableExtractor(Context ctx, Uri uri, Extractor delegate) {
                this.ctx = ctx.getApplicationContext();
                this.uri = uri;
                this.delegate = delegate;
            }

            @Override
            public boolean sniff(ExtractorInput input) throws IOException {
                return delegate.sniff(input);
            }

            @Override
            public void init(ExtractorOutput output) {
                delegate.init(new ExtractorOutput() {
                    @Override
                    public TrackOutput track(int id, int type) {
                        return output.track(id, type);
                    }

                    @Override
                    public void endTracks() {
                        output.endTracks();
                    }

                    @Override
                    public void seekMap(SeekMap seekMap) {
                        if (seekMap != null && !seekMap.isSeekable()) {
                            SeekMap fixed = buildFmp4SeekMap(ctx, uri);
                            if (fixed != null) {
                                output.seekMap(fixed);
                                return;
                            }
                        }
                        output.seekMap(seekMap);
                    }
                });
            }

            @Override
            public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException {
                return delegate.read(input, seekPosition);
            }

            @Override
            public void seek(long position, long timeUs) {
                delegate.seek(position, timeUs);
            }

            @Override
            public void release() {
                delegate.release();
            }

            @Override
            public Extractor getUnderlyingImplementation() {
                return delegate.getUnderlyingImplementation();
            }
        }

        static final class MoofSeekMap implements SeekMap {
            final long[] timesUs;
            final long[] offsets;
            final long durationUs;

            MoofSeekMap(long[] timesUs, long[] offsets, long durationUs) {
                this.timesUs = timesUs;
                this.offsets = offsets;
                this.durationUs = durationUs;
            }

            @Override
            public boolean isSeekable() {
                return true;
            }

            @Override
            public long getDurationUs() {
                return durationUs;
            }

            @Override
            public SeekPoints getSeekPoints(long timeUs) {
                int idx = Arrays.binarySearch(timesUs, timeUs);
                if (idx < 0) idx = Math.max(0, (-idx - 1) - 1);
                SeekPoint p = new SeekPoint(timesUs[idx], offsets[idx]);
                if (timesUs[idx] < timeUs && idx + 1 < timesUs.length) {
                    return new SeekPoints(p, new SeekPoint(timesUs[idx + 1], offsets[idx + 1]));
                }
                return new SeekPoints(p);
            }
        }

        static SeekMap buildFmp4SeekMap(Context ctx, Uri uri) {
            try (ParcelFileDescriptor pfd = ctx.getContentResolver().openFileDescriptor(uri, "r")) {
                if (pfd == null) return null;
                try (FileInputStream fis = new FileInputStream(pfd.getFileDescriptor())) {
                    FileChannel ch = fis.getChannel();
                    long len = ch.size();
                    if (len < 16) return null;

                    SparseLongArray trackTimescales = new SparseLongArray();
                    List<Long> moofTimes = new ArrayList<>();
                    List<Long> moofOffsets = new ArrayList<>();
                    ByteBuffer hdr = ByteBuffer.allocate(16);
                    long pos = 0;

                    while (pos + 8 <= len) {
                        hdr.clear();
                        hdr.limit(8);
                        ch.position(pos);
                        if (!readFully(ch, hdr)) break;
                        long size = hdr.getInt(0) & 0xFFFFFFFFL;
                        int type = hdr.getInt(4);
                        int hdrSize = 8;
                        if (size == 1) {
                            if (pos + 16 > len) break;
                            hdr.clear();
                            hdr.limit(8);
                            if (!readFully(ch, hdr)) break;
                            size = hdr.getLong(0);
                            hdrSize = 16;
                        } else if (size == 0) {
                            size = len - pos;
                        }
                        if (size < hdrSize || pos + size > len) break;

                        if (type == 0x6D6F6F76 && size <= 8 * 1024 * 1024) { // moov
                            ByteBuffer buf = ByteBuffer.allocate((int) (size - hdrSize));
                            if (readFully(ch, buf)) {
                                parseMoovTimescales(buf.array(), 0, buf.capacity(), trackTimescales);
                            }
                        } else if (type == 0x6D6F6F66 && size <= 2 * 1024 * 1024) { // moof
                            ByteBuffer buf = ByteBuffer.allocate((int) (size - hdrSize));
                            if (readFully(ch, buf)) {
                                long tUs = parseMoofTimeUs(buf.array(), 0, buf.capacity(), trackTimescales);
                                if (tUs >= 0 && (moofTimes.isEmpty() || tUs > moofTimes.get(moofTimes.size() - 1))) {
                                    moofTimes.add(tUs);
                                    moofOffsets.add(pos);
                                }
                            }
                        }
                        pos += size;
                    }

                    if (moofTimes.size() < 2) return null;
                    int n = moofTimes.size();
                    long[] times = new long[n];
                    long[] offsets = new long[n];
                    for (int i = 0; i < n; i++) {
                        times[i] = moofTimes.get(i);
                        offsets[i] = moofOffsets.get(i);
                    }
                    long avgStep = (times[n - 1] - times[0]) / Math.max(1, n - 1);
                    long durUs = times[n - 1] + Math.max(1_000_000L, avgStep);
                    return new MoofSeekMap(times, offsets, durUs);
                }
            } catch (Exception ignored) {
                return null;
            }
        }

        static boolean readFully(FileChannel ch, ByteBuffer dst) throws IOException {
            while (dst.hasRemaining()) {
                if (ch.read(dst) < 0) return false;
            }
            return true;
        }

        static int readIntBE(byte[] b, int off) {
            return ((b[off] & 0xFF) << 24) | ((b[off + 1] & 0xFF) << 16)
                    | ((b[off + 2] & 0xFF) << 8) | (b[off + 3] & 0xFF);
        }

        static long readLongBE(byte[] b, int off) {
            return ((long) readIntBE(b, off) << 32) | (readIntBE(b, off + 4) & 0xFFFFFFFFL);
        }

        static void parseMoovTimescales(byte[] b, int start, int end, SparseLongArray out) {
            int p = start;
            while (p + 8 <= end) {
                long size = readIntBE(b, p) & 0xFFFFFFFFL;
                int type = readIntBE(b, p + 4);
                if (size < 8 || p + size > end) break;
                if (type == 0x7472616B) { // trak
                    int[] idHolder = new int[]{-1};
                    long[] tsHolder = new long[]{0};
                    parseTrak(b, p + 8, (int) (p + size), idHolder, tsHolder);
                    if (idHolder[0] > 0 && tsHolder[0] > 0) out.put(idHolder[0], tsHolder[0]);
                }
                p += (int) size;
            }
        }

        static void parseTrak(byte[] b, int start, int end, int[] trackId, long[] timescale) {
            int p = start;
            while (p + 8 <= end) {
                long size = readIntBE(b, p) & 0xFFFFFFFFL;
                int type = readIntBE(b, p + 4);
                if (size < 8 || p + size > end) break;
                int body = p + 8;
                int boxEnd = (int) (p + size);
                if (type == 0x746B6864 && body + 16 <= boxEnd) { // tkhd
                    int ver = b[body] & 0xFF;
                    int idOff = body + (ver == 1 ? 20 : 12);
                    if (idOff + 4 <= boxEnd) trackId[0] = readIntBE(b, idOff);
                } else if (type == 0x6D646961) { // mdia
                    parseTrak(b, body, boxEnd, trackId, timescale);
                } else if (type == 0x6D646864 && body + 16 <= boxEnd) { // mdhd
                    int ver = b[body] & 0xFF;
                    int tsOff = body + (ver == 1 ? 20 : 12);
                    if (tsOff + 4 <= boxEnd) timescale[0] = readIntBE(b, tsOff) & 0xFFFFFFFFL;
                }
                p = boxEnd;
            }
        }

        static long parseMoofTimeUs(byte[] b, int start, int end, SparseLongArray timescales) {
            int p = start;
            while (p + 8 <= end) {
                long size = readIntBE(b, p) & 0xFFFFFFFFL;
                int type = readIntBE(b, p + 4);
                if (size < 8 || p + size > end) break;
                int boxEnd = (int) (p + size);
                if (type == 0x74726166) { // traf
                    int trackId = -1;
                    long decodeTime = -1;
                    int tp = p + 8;
                    while (tp + 8 <= boxEnd) {
                        long tSize = readIntBE(b, tp) & 0xFFFFFFFFL;
                        int tType = readIntBE(b, tp + 4);
                        if (tSize < 8 || tp + tSize > boxEnd) break;
                        int tBody = tp + 8;
                        int tEnd = (int) (tp + tSize);
                        if (tType == 0x74666864 && tBody + 8 <= tEnd) { // tfhd
                            trackId = readIntBE(b, tBody + 4);
                        } else if (tType == 0x74666474 && tBody + 8 <= tEnd) { // tfdt
                            int ver = b[tBody] & 0xFF;
                            if (ver == 1 && tBody + 12 <= tEnd) decodeTime = readLongBE(b, tBody + 4);
                            else if (ver == 0) decodeTime = readIntBE(b, tBody + 4) & 0xFFFFFFFFL;
                        }
                        tp = tEnd;
                    }
                    if (trackId > 0 && decodeTime >= 0) {
                        long ts = timescales.get(trackId, 0);
                        if (ts > 0) return (decodeTime * 1_000_000L) / ts;
                    }
                }
                p = boxEnd;
            }
            return -1;
        }
    }
