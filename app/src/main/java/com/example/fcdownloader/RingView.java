    package com.example.fcdownloader;

    import android.animation.ValueAnimator;
    import android.content.Context;
    import android.graphics.BlurMaskFilter;
    import android.graphics.Canvas;
    import android.graphics.DashPathEffect;
    import android.graphics.Paint;
    import android.graphics.RectF;
    import android.view.View;
    import android.view.animation.LinearInterpolator;

    /**
     * Cincin pemindai futuristik: radar HUD berputar saat memindai, lalu menjadi pengukur kemajuan
     * (ion = selesai, amber = sedang diunduh, merah = gagal).
     */
    public class RingView extends View {
        private float done, active, failed;
        private boolean spinning;
        private float sweep;
        private String big = "0", small = "";
        private ValueAnimator anim;

        private final Paint outerHud = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint innerHud = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint tBig = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint tSmall = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final RectF outerRect = new RectF();
        private final RectF innerRect = new RectF();

        public RingView(Context c) {
            super(c);
            setLayerType(LAYER_TYPE_SOFTWARE, null);
            outerHud.setStyle(Paint.Style.STROKE);
            outerHud.setColor(0x554FE3D4);
            outerHud.setPathEffect(new DashPathEffect(new float[]{10f, 8f}, 0));
            innerHud.setStyle(Paint.Style.STROKE);
            innerHud.setColor(0x334FE3D4);
            track.setStyle(Paint.Style.STROKE);
            track.setColor(Ui.LINE);
            arc.setStyle(Paint.Style.STROKE);
            arc.setStrokeCap(Paint.Cap.ROUND);
            glow.setStyle(Paint.Style.STROKE);
            glow.setStrokeCap(Paint.Cap.ROUND);
            tick.setStrokeCap(Paint.Cap.ROUND);
            tBig.setTypeface(Ui.DISPLAY);
            tBig.setColor(Ui.TEXT);
            tBig.setTextAlign(Paint.Align.CENTER);
            tSmall.setTypeface(Ui.MEDIUM);
            tSmall.setColor(Ui.ION);
            tSmall.setTextAlign(Paint.Align.CENTER);
        }

        public void setProgress(float done, float active, float failed) {
            this.done = clamp(done);
            this.active = clamp(active);
            this.failed = clamp(failed);
            invalidate();
        }

        public void setCenter(String big, String small) {
            this.big = big;
            this.small = small;
            invalidate();
        }

        public void setSpinning(boolean on) {
            if (on == spinning) return;
            spinning = on;
            if (on) {
                anim = ValueAnimator.ofFloat(0, 360);
                anim.setDuration(1500);
                anim.setRepeatCount(ValueAnimator.INFINITE);
                anim.setInterpolator(new LinearInterpolator());
                anim.addUpdateListener(a -> {
                    sweep = (float) a.getAnimatedValue();
                    invalidate();
                });
                anim.start();
            } else if (anim != null) {
                anim.cancel();
                anim = null;
            }
            invalidate();
        }

        private static float clamp(float v) {
            return Math.max(0, Math.min(1, v));
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (anim != null) anim.cancel();
        }

        @Override
        protected void onMeasure(int w, int h) {
            int size = MeasureSpec.getSize(w);
            int max = Ui.dp(getContext(), 210);
            if (size == 0 || size > max) size = max;
            setMeasuredDimension(size, size);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float cx = w / 2f, cy = h / 2f;
            float stroke = Ui.dp(getContext(), 11);
            float pad = stroke + Ui.dp(getContext(), 12);
            rect.set(pad, pad, w - pad, h - pad);

            float oPad = pad - Ui.dp(getContext(), 9);
            outerRect.set(oPad, oPad, w - oPad, h - oPad);
            outerHud.setStrokeWidth(Ui.dp(getContext(), 1.5f));
            c.drawOval(outerRect, outerHud);

            float iPad = pad + Ui.dp(getContext(), 12);
            innerRect.set(iPad, iPad, w - iPad, h - iPad);
            innerHud.setStrokeWidth(Ui.dp(getContext(), 1f));
            c.drawOval(innerRect, innerHud);

            track.setStrokeWidth(stroke);
            c.drawOval(rect, track);

            // penanda sudut HUD (0, 90, 180, 270)
            float rOuter = (w / 2f) - oPad + Ui.dp(getContext(), 3);
            float rInner = (w / 2f) - oPad - Ui.dp(getContext(), 3);
            tick.setColor(Ui.ION);
            tick.setStrokeWidth(Ui.dp(getContext(), 2));
            for (int i = 0; i < 4; i++) {
                double rad = Math.toRadians(i * 90);
                float sx = cx + (float) Math.cos(rad) * rInner;
                float sy = cy + (float) Math.sin(rad) * rInner;
                float ex = cx + (float) Math.cos(rad) * rOuter;
                float ey = cy + (float) Math.sin(rad) * rOuter;
                c.drawLine(sx, sy, ex, ey, tick);
            }

            arc.setStrokeWidth(stroke);
            glow.setStrokeWidth(stroke * 1.45f);
            glow.setMaskFilter(new BlurMaskFilter(Ui.dp(getContext(), 9), BlurMaskFilter.Blur.NORMAL));

            if (spinning) {
                glow.setColor(0x774FE3D4);
                c.drawArc(rect, sweep - 90, 95, false, glow);
                arc.setColor(Ui.ION);
                c.drawArc(rect, sweep - 90, 95, false, arc);

                arc.setColor(0x99FFC857);
                c.drawArc(rect, sweep + 110, 45, false, arc);
            } else {
                float start = -90;
                if (done > 0) {
                    float a = done * 360f;
                    glow.setColor(0x664FE3D4);
                    c.drawArc(rect, start, a, false, glow);
                    arc.setColor(Ui.ION);
                    c.drawArc(rect, start, a, false, arc);
                    start += a;
                }
                if (active > 0) {
                    float a = active * 360f;
                    glow.setColor(0x55FFC857);
                    c.drawArc(rect, start, a, false, glow);
                    arc.setColor(Ui.AMBER);
                    c.drawArc(rect, start, a, false, arc);
                    start += a;
                }
                if (failed > 0) {
                    float a = failed * 360f;
                    arc.setColor(Ui.PULSE);
                    c.drawArc(rect, start, a, false, arc);
                }
            }

            tBig.setTextSize(w * 0.21f);
            tSmall.setTextSize(w * 0.072f);
            float baseY = cy - (tBig.descent() + tBig.ascent()) / 2f - (small.isEmpty() ? 0 : w * 0.045f);
            c.drawText(big, cx, baseY, tBig);
            if (!small.isEmpty()) c.drawText(small.toUpperCase(), cx, baseY + w * 0.115f, tSmall);
        }
    }
