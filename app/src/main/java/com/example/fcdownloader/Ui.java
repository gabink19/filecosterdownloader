package com.example.fcdownloader;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

/** Palet & komponen tampilan "panel kendali" bertema samudra malam. */
final class Ui {
    // palet
    static final int ABYSS = Color.parseColor("#0A1024");   // latar
    static final int PANEL = Color.parseColor("#121A35");   // kartu
    static final int PANEL_HI = Color.parseColor("#18224A");
    static final int LINE = Color.parseColor("#26325E");    // garis/track
    static final int ION = Color.parseColor("#4FE3D4");     // aksi utama, selesai
    static final int ION_DEEP = Color.parseColor("#0E3B46");
    static final int AMBER = Color.parseColor("#FFC857");   // sedang berjalan
    static final int PULSE = Color.parseColor("#FF5C8A");   // gagal / berhenti
    static final int TEXT = Color.parseColor("#E6ECFF");
    static final int MUTED = Color.parseColor("#8A95BE");

    static final Typeface DISPLAY = Typeface.create("sans-serif-condensed", Typeface.BOLD);
    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    static final Typeface BODY = Typeface.create("sans-serif", Typeface.NORMAL);

    private Ui() { }

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    static GradientDrawable box(Context c, int fill, float radiusDp, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, radiusDp));
        if (stroke != 0) g.setStroke(dp(c, 1), stroke);
        return g;
    }

    static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
    }

    static LinearLayout.LayoutParams fill(Context c, int topDp) {
        LinearLayout.LayoutParams p = lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(c, topDp);
        return p;
    }

    static LinearLayout col(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    /** Kartu panel dengan sudut agak tegas; kartu utama memakai garis tepi. */
    static LinearLayout card(Context c, boolean outlined) {
        LinearLayout l = col(c);
        l.setBackground(box(c, PANEL, 14, outlined ? LINE : 0));
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        return l;
    }

    static TextView text(Context c, String s, float sp, int color, Typeface tf) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        return t;
    }

    static TextView title(Context c, String s) {
        TextView t = text(c, s, 17, TEXT, DISPLAY);
        t.setLetterSpacing(0.02f);
        return t;
    }

    static TextView hint(Context c, String s) {
        TextView t = text(c, s, 12.5f, MUTED, BODY);
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    static EditText input(Context c, String hintText) {
        EditText e = new EditText(c);
        e.setHint(hintText);
        e.setSingleLine(true);
        e.setTextSize(15);
        e.setTextColor(TEXT);
        e.setHintTextColor(Color.parseColor("#5B6690"));
        e.setTypeface(BODY);
        e.setBackground(box(c, ABYSS, 10, LINE));
        int h = dp(c, 12);
        e.setPadding(h, dp(c, 11), h, dp(c, 11));
        return e;
    }

    /** Tombol utama: blok ion terang dengan teks gelap. */
    static TextView primary(Context c, String s) {
        TextView b = text(c, s, 16, ABYSS, DISPLAY);
        b.setLetterSpacing(0.03f);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(c, 54));
        b.setBackground(box(c, ION, 12, 0));
        b.setClickable(true);
        b.setFocusable(true);
        return b;
    }

    static void stylePrimary(Context c, TextView b, int fill, int textColor) {
        b.setBackground(box(c, fill, 12, 0));
        b.setTextColor(textColor);
    }

    /** Tombol sekunder bergaris. */
    static TextView ghost(Context c, String s) {
        TextView b = text(c, s, 14, ION, MEDIUM);
        b.setGravity(Gravity.CENTER);
        int h = dp(c, 14);
        b.setPadding(h, dp(c, 10), h, dp(c, 10));
        b.setBackground(box(c, Color.TRANSPARENT, 10, LINE));
        b.setClickable(true);
        b.setFocusable(true);
        return b;
    }

    static TextView chip(Context c) {
        TextView t = text(c, "", 11.5f, MUTED, MEDIUM);
        int h = dp(c, 9);
        t.setPadding(h, dp(c, 3), h, dp(c, 4));
        t.setSingleLine(true);
        return t;
    }

    static void setChip(Context c, TextView t, String s, int color) {
        t.setText(s);
        t.setTextColor(color);
        t.setBackground(box(c, (color & 0x00FFFFFF) | 0x22000000, 20, (color & 0x00FFFFFF) | 0x66000000));
    }

    static CheckBox check(Context c, String s, boolean on) {
        CheckBox b = new CheckBox(c);
        b.setText(s);
        b.setTextColor(TEXT);
        b.setTextSize(14);
        b.setTypeface(BODY);
        b.setChecked(on);
        b.setButtonTintList(ColorStateList.valueOf(ION));
        return b;
    }

    static Spinner spinner(Context c, String[] items) {
        Spinner s = new Spinner(c, Spinner.MODE_DROPDOWN);
        s.setAdapter(new ArrayAdapter<String>(c, android.R.layout.simple_list_item_1, items) {
            @Override
            public View getView(int pos, View v, ViewGroup parent) {
                TextView t = (TextView) super.getView(pos, v, parent);
                t.setTextColor(TEXT);
                t.setTextSize(14);
                t.setTypeface(BODY);
                t.setSingleLine(true);
                t.setEllipsize(TextUtils.TruncateAt.END);
                t.setPadding(dp(c, 12), dp(c, 10), dp(c, 28), dp(c, 10));
                return t;
            }

            @Override
            public View getDropDownView(int pos, View v, ViewGroup parent) {
                TextView t = (TextView) super.getDropDownView(pos, v, parent);
                t.setTextColor(TEXT);
                t.setTextSize(14);
                t.setTypeface(BODY);
                t.setSingleLine(false);
                t.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
                return t;
            }
        });
        s.setBackground(box(c, ABYSS, 10, LINE));
        s.setPopupBackgroundDrawable(box(c, PANEL_HI, 10, LINE));
        return s;
    }
}
