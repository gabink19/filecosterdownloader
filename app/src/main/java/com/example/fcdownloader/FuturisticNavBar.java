package com.example.fcdownloader;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;

/**
 * Navigasi bawah futuristik (Cyber Dock HUD) dengan ikon vektor custom,
 * kapsul neon yang bergeser mulus, dan efek glow berdenyut.
 */
public class FuturisticNavBar extends View {

    public interface OnTabSelectListener {
        void onTabSelected(int index);
    }

    private static final String[] LABELS = {"Unduh", "Video Saya", "Riwayat", "Lainnya"};
    private int selectedIndex = 0;
    private float animPos = 0f;
    private int pressedIndex = -1;
    private float pressScale = 1f;
    private float pulsePhase = 0f;
    private final String[] badges = {"", "", "", ""};

    private ValueAnimator posAnim;
    private ValueAnimator pressAnim;
    private ValueAnimator pulseAnim;
    private OnTabSelectListener listener;

    private final Paint dockFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dockStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint laserGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint laserLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgeBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgeText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cornerHud = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF dockRect = new RectF();
    private final RectF pillRect = new RectF();
    private final RectF badgeRect = new RectF();
    private final Path path = new Path();

    public FuturisticNavBar(Context c) {
        super(c);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        setClickable(true);

        dockStroke.setStyle(Paint.Style.STROKE);
        dockStroke.setStrokeWidth(Ui.dp(c, 1.2f));
        dockStroke.setColor(0x8826325E);

        pillGlow.setStyle(Paint.Style.FILL);
        pillGlow.setColor(0x444FE3D4);
        pillGlow.setMaskFilter(new BlurMaskFilter(Ui.dp(c, 12), BlurMaskFilter.Blur.NORMAL));

        pillFill.setStyle(Paint.Style.FILL);
        pillFill.setColor(0xCC0E3B46);

        pillStroke.setStyle(Paint.Style.STROKE);
        pillStroke.setStrokeWidth(Ui.dp(c, 1.3f));
        pillStroke.setColor(0xBB4FE3D4);

        laserGlow.setStyle(Paint.Style.STROKE);
        laserGlow.setStrokeCap(Paint.Cap.ROUND);
        laserGlow.setStrokeWidth(Ui.dp(c, 6));
        laserGlow.setColor(0x994FE3D4);
        laserGlow.setMaskFilter(new BlurMaskFilter(Ui.dp(c, 6), BlurMaskFilter.Blur.NORMAL));

        laserLine.setStyle(Paint.Style.STROKE);
        laserLine.setStrokeCap(Paint.Cap.ROUND);
        laserLine.setStrokeWidth(Ui.dp(c, 2.5f));
        laserLine.setColor(Ui.ION);

        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        iconPaint.setStrokeJoin(Paint.Join.ROUND);

        iconGlow.setStyle(Paint.Style.STROKE);
        iconGlow.setStrokeCap(Paint.Cap.ROUND);
        iconGlow.setStrokeJoin(Paint.Join.ROUND);
        iconGlow.setColor(0x774FE3D4);
        iconGlow.setMaskFilter(new BlurMaskFilter(Ui.dp(c, 5), BlurMaskFilter.Blur.NORMAL));

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Ui.MEDIUM);

        badgeBg.setStyle(Paint.Style.FILL);
        badgeText.setTextAlign(Paint.Align.CENTER);
        badgeText.setTypeface(Ui.DISPLAY);
        badgeText.setColor(Ui.ABYSS);

        cornerHud.setStyle(Paint.Style.STROKE);
        cornerHud.setStrokeCap(Paint.Cap.ROUND);
        cornerHud.setStrokeWidth(Ui.dp(c, 1.5f));
        cornerHud.setColor(0x664FE3D4);
    }

    public void setOnTabSelectListener(OnTabSelectListener l) {
        this.listener = l;
    }

    public void setBadge(int index, String text) {
        if (index < 0 || index >= badges.length) return;
        String clean = text == null ? "" : text.trim();
        if (!clean.equals(badges[index])) {
            badges[index] = clean;
            invalidate();
        }
    }

    public void setSelectedIndex(int idx, boolean animate) {
        if (idx < 0 || idx >= LABELS.length) return;
        selectedIndex = idx;
        if (posAnim != null) posAnim.cancel();
        if (!animate) {
            animPos = idx;
            invalidate();
            return;
        }
        posAnim = ValueAnimator.ofFloat(animPos, idx);
        posAnim.setDuration(340);
        posAnim.setInterpolator(new OvershootInterpolator(0.85f));
        posAnim.addUpdateListener(a -> {
            animPos = (float) a.getAnimatedValue();
            invalidate();
        });
        posAnim.start();
    }

    private void startPulse() {
        if (pulseAnim != null) return;
        pulseAnim = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));
        pulseAnim.setDuration(2600);
        pulseAnim.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnim.setInterpolator(new LinearInterpolator());
        pulseAnim.addUpdateListener(a -> {
            pulsePhase = (float) a.getAnimatedValue();
            invalidate();
        });
        pulseAnim.start();
    }

    private void stopPulse() {
        if (pulseAnim != null) {
            pulseAnim.cancel();
            pulseAnim = null;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startPulse();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopPulse();
        if (posAnim != null) posAnim.cancel();
        if (pressAnim != null) pressAnim.cancel();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        int h = Ui.dp(getContext(), 78);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        dockFill.setShader(new LinearGradient(0, 0, 0, h,
                0xF2151F42, 0xF80B1126, Shader.TileMode.CLAMP));
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        float w = getWidth();
        if (w <= 0) return false;
        float padH = Ui.dp(getContext(), 12);
        float innerW = w - padH * 2;
        float slotW = innerW / LABELS.length;
        int hit = (int) ((ev.getX() - padH) / slotW);
        hit = Math.max(0, Math.min(LABELS.length - 1, hit));

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressedIndex = hit;
                animatePress(0.86f);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (hit != pressedIndex) {
                    pressedIndex = hit;
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                int target = pressedIndex >= 0 ? pressedIndex : hit;
                pressedIndex = -1;
                animatePress(1f);
                setSelectedIndex(target, true);
                if (listener != null) listener.onTabSelected(target);
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                pressedIndex = -1;
                animatePress(1f);
                return true;
        }
        return super.onTouchEvent(ev);
    }

    private void animatePress(float to) {
        if (pressAnim != null) pressAnim.cancel();
        pressAnim = ValueAnimator.ofFloat(pressScale, to);
        pressAnim.setDuration(150);
        pressAnim.addUpdateListener(a -> {
            pressScale = (float) a.getAnimatedValue();
            invalidate();
        });
        pressAnim.start();
    }

    @Override
    protected void onDraw(Canvas c) {
        Context ctx = getContext();
        float w = getWidth(), h = getHeight();
        float padH = Ui.dp(ctx, 12);
        float padTop = Ui.dp(ctx, 6);
        float padBot = Ui.dp(ctx, 10);

        dockRect.set(padH, padTop, w - padH, h - padBot);
        float dockRad = Ui.dp(ctx, 22);
        c.drawRoundRect(dockRect, dockRad, dockRad, dockFill);
        c.drawRoundRect(dockRect, dockRad, dockRad, dockStroke);

        // Aksen sudut HUD futuristik pada dock
        float hudLen = Ui.dp(ctx, 14);
        c.drawLine(dockRect.left + dockRad * 0.7f, dockRect.top, dockRect.left + dockRad * 0.7f + hudLen, dockRect.top, cornerHud);
        c.drawLine(dockRect.right - dockRad * 0.7f - hudLen, dockRect.top, dockRect.right - dockRad * 0.7f, dockRect.top, cornerHud);

        float innerPad = Ui.dp(ctx, 5);
        float availW = dockRect.width() - innerPad * 2;
        float slotW = availW / LABELS.length;

        // Kapsul tab aktif yang bergeser mulus
        float pillLeft = dockRect.left + innerPad + animPos * slotW + Ui.dp(ctx, 3);
        float pillRight = pillLeft + slotW - Ui.dp(ctx, 6);
        float pillTop = dockRect.top + innerPad;
        float pillBot = dockRect.bottom - innerPad;
        pillRect.set(pillLeft, pillTop, pillRight, pillBot);
        float pillRad = Ui.dp(ctx, 17);

        float pulse = 0.75f + 0.25f * (float) Math.sin(pulsePhase);
        pillGlow.setAlpha((int) (68 * pulse));
        c.drawRoundRect(pillRect, pillRad, pillRad, pillGlow);
        c.drawRoundRect(pillRect, pillRad, pillRad, pillFill);
        pillStroke.setAlpha((int) (175 * pulse + 50));
        c.drawRoundRect(pillRect, pillRad, pillRad, pillStroke);

        // Garis laser neon di atas kapsul aktif
        float laserHalf = slotW * 0.22f;
        float pillCx = pillRect.centerX();
        float laserY = pillRect.top + Ui.dp(ctx, 1.2f);
        c.drawLine(pillCx - laserHalf, laserY, pillCx + laserHalf, laserY, laserGlow);
        c.drawLine(pillCx - laserHalf, laserY, pillCx + laserHalf, laserY, laserLine);

        // Gambar tiap item tab
        for (int i = 0; i < LABELS.length; i++) {
            float cx = dockRect.left + innerPad + (i + 0.5f) * slotW;
            float dist = Math.min(1f, Math.abs(animPos - i));
            float activeFrac = 1f - dist; // 1 = aktif, 0 = tidak aktif

            float scale = (1f + 0.10f * activeFrac) * (i == pressedIndex ? pressScale : 1f);
            float iconCy = dockRect.top + dockRect.height() * 0.37f - activeFrac * Ui.dp(ctx, 2.2f);

            int color = blendColor(Ui.MUTED, Ui.ION, activeFrac);
            int textColor = blendColor(Ui.MUTED, Ui.TEXT, activeFrac);

            c.save();
            c.scale(scale, scale, cx, iconCy);
            float r = Ui.dp(ctx, 9.5f);
            iconPaint.setStrokeWidth(Ui.dp(ctx, 1.9f + 0.35f * activeFrac));
            iconPaint.setColor(color);
            iconGlow.setStrokeWidth(Ui.dp(ctx, 3.8f));
            iconGlow.setAlpha((int) (130 * activeFrac * pulse));

            if (activeFrac > 0.15f) {
                drawTabIcon(c, i, cx, iconCy, r, iconGlow);
            }
            drawTabIcon(c, i, cx, iconCy, r, iconPaint);
            c.restore();

            // Label
            textPaint.setTextSize(Ui.dp(ctx, 10.5f + 0.6f * activeFrac));
            textPaint.setColor(textColor);
            textPaint.setTypeface(activeFrac > 0.5f ? Ui.DISPLAY : Ui.MEDIUM);
            float textY = dockRect.bottom - Ui.dp(ctx, 8.5f);
            c.drawText(LABELS[i], cx, textY, textPaint);

            // Badge jumlah (mis. jumlah video siap unduh)
            String b = badges[i];
            if (b != null && !b.isEmpty()) {
                badgeText.setTextSize(Ui.dp(ctx, 9.5f));
                float bw = Math.max(Ui.dp(ctx, 16), badgeText.measureText(b) + Ui.dp(ctx, 8));
                float bh = Ui.dp(ctx, 15);
                float bx = cx + Ui.dp(ctx, 8);
                float by = iconCy - Ui.dp(ctx, 12);
                badgeRect.set(bx, by, bx + bw, by + bh);
                badgeBg.setColor(i == 0 ? Ui.AMBER : Ui.ION);
                c.drawRoundRect(badgeRect, bh / 2f, bh / 2f, badgeBg);
                float bty = badgeRect.centerY() - (badgeText.descent() + badgeText.ascent()) / 2f;
                c.drawText(b, badgeRect.centerX(), bty, badgeText);
            }
        }
    }

    private void drawTabIcon(Canvas c, int index, float cx, float cy, float r, Paint p) {
        path.reset();
        switch (index) {
            case 0: // Unduh: panah cyber masuk ke baki heksagonal
                path.moveTo(cx, cy - r * 0.85f);
                path.lineTo(cx, cy + r * 0.35f);
                path.moveTo(cx - r * 0.5f, cy - r * 0.1f);
                path.lineTo(cx, cy + r * 0.42f);
                path.lineTo(cx + r * 0.5f, cy - r * 0.1f);
                // baki bawah
                path.moveTo(cx - r * 0.85f, cy + r * 0.4f);
                path.lineTo(cx - r * 0.65f, cy + r * 0.85f);
                path.lineTo(cx + r * 0.65f, cy + r * 0.85f);
                path.lineTo(cx + r * 0.85f, cy + r * 0.4f);
                c.drawPath(path, p);
                break;
            case 1: // Video Saya: bingkai layar futuristik + segitiga play
                RectF vr = new RectF(cx - r * 0.95f, cy - r * 0.72f, cx + r * 0.95f, cy + r * 0.72f);
                c.drawRoundRect(vr, r * 0.28f, r * 0.28f, p);
                path.moveTo(cx - r * 0.22f, cy - r * 0.34f);
                path.lineTo(cx + r * 0.38f, cy);
                path.lineTo(cx - r * 0.22f, cy + r * 0.34f);
                path.close();
                c.drawPath(path, p);
                break;
            case 2: // Riwayat: busur waktu orbit + jarum jam
                RectF hr = new RectF(cx - r * 0.85f, cy - r * 0.85f, cx + r * 0.85f, cy + r * 0.85f);
                c.drawArc(hr, -45, 285, false, p);
                // kepala panah di ujung atas kiri
                path.moveTo(cx - r * 0.88f, cy - r * 0.22f);
                path.lineTo(cx - r * 0.58f, cy - r * 0.62f);
                path.lineTo(cx - r * 0.22f, cy - r * 0.42f);
                // jarum jam
                path.moveTo(cx, cy - r * 0.42f);
                path.lineTo(cx, cy + r * 0.05f);
                path.lineTo(cx + r * 0.38f, cy + r * 0.26f);
                c.drawPath(path, p);
                break;
            case 3: // Lainnya: grid kontrol 2x2 cyber (3 kotak + 1 berlian)
                float g = r * 0.16f;
                float s = r * 0.68f;
                c.drawRoundRect(new RectF(cx - g - s, cy - g - s, cx - g, cy - g), r * 0.18f, r * 0.18f, p);
                c.drawRoundRect(new RectF(cx - g - s, cy + g, cx - g, cy + g + s), r * 0.18f, r * 0.18f, p);
                c.drawRoundRect(new RectF(cx + g, cy + g, cx + g + s, cy + g + s), r * 0.18f, r * 0.18f, p);
                // berlian di kanan atas
                float dcx = cx + g + s / 2f, dcy = cy - g - s / 2f, dr = s * 0.56f;
                path.moveTo(dcx, dcy - dr);
                path.lineTo(dcx + dr, dcy);
                path.lineTo(dcx, dcy + dr);
                path.lineTo(dcx - dr, dcy);
                path.close();
                c.drawPath(path, p);
                break;
        }
    }

    private static int blendColor(int c1, int c2, float ratio) {
        float r = Math.max(0f, Math.min(1f, ratio));
        float ir = 1f - r;
        int a = (int) (Color.alpha(c1) * ir + Color.alpha(c2) * r);
        int red = (int) (Color.red(c1) * ir + Color.red(c2) * r);
        int grn = (int) (Color.green(c1) * ir + Color.green(c2) * r);
        int blu = (int) (Color.blue(c1) * ir + Color.blue(c2) * r);
        return Color.argb(a, red, grn, blu);
    }
}
