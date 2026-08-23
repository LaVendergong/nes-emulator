package nes.android;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import nes.save.SaveStore;

/** 槽位列表。有档的格子可读取；保存可覆盖。打开时暂停。 */
final class SaveSlots {
    private SaveSlots() {}

    static void show(MainActivity host) {
        if (host.romName() == null) {
            host.toast("先打开 ROM");
            return;
        }
        boolean wasPaused = host.pauseForDialog();
        boolean[] loaded = {false};
        Dialog dialog = new Dialog(host);
        dialog.setTitle("存档 — " + host.romName());

        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(host, 8);
        root.setPadding(pad, pad, pad, pad);

        LinearLayout top = new LinearLayout(host);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView limitLabel = new TextView(host);
        limitLabel.setText("槽位上限");
        TextView limitValue = new TextView(host);
        limitValue.setTypeface(Typeface.MONOSPACE);
        limitValue.setPadding(dp(host, 12), 0, dp(host, 12), 0);
        Button minus = small(host, "−");
        Button plus = small(host, "+");
        TextView hint = new TextView(host);
        hint.setText("  有档可读取；保存可覆盖。");
        top.addView(limitLabel);
        top.addView(minus);
        top.addView(limitValue);
        top.addView(plus);
        top.addView(hint);
        root.addView(top);

        LinearLayout list = new LinearLayout(host);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(host);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Runnable[] rebuild = {null};
        rebuild[0] = () -> {
            int limit = SaveStore.slotLimit();
            limitValue.setText(Integer.toString(limit));
            list.removeAllViews();
            for (int i = 1; i <= limit; i++) {
                list.addView(row(host, dialog, loaded, rebuild[0], i));
            }
        };
        minus.setOnClickListener(v -> {
            SaveStore.setSlotLimit(SaveStore.slotLimit() - 1);
            rebuild[0].run();
        });
        plus.setOnClickListener(v -> {
            SaveStore.setSlotLimit(SaveStore.slotLimit() + 1);
            rebuild[0].run();
        });
        rebuild[0].run();

        dialog.setContentView(root);
        dialog.setOnDismissListener(d -> host.endDialog(wasPaused, loaded[0]));
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(dp(host, 520), ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    private static LinearLayout row(
            MainActivity host, Dialog dialog, boolean[] loaded, Runnable rebuild, int slot) {
        SaveStore.Slot info = SaveStore.meta(host.romName(), slot);
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(host, 4);
        row.setPadding(pad, pad, pad, pad);

        TextView index = new TextView(host);
        index.setText(String.format("%02d", slot));
        index.setTypeface(Typeface.MONOSPACE);
        index.setPadding(0, 0, dp(host, 8), 0);
        row.addView(index);

        ImageView thumb = new ImageView(host);
        thumb.setBackgroundColor(0xFF333333);
        thumb.setLayoutParams(new LinearLayout.LayoutParams(dp(host, 96), dp(host, 90)));
        thumb.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (!info.empty && info.width > 0 && info.height > 0) {
            Bitmap bmp = Bitmap.createBitmap(info.width, info.height, Bitmap.Config.ARGB_8888);
            bmp.setPixels(info.thumb, 0, info.width, 0, 0, info.width, info.height);
            thumb.setImageBitmap(bmp);
        }
        row.addView(thumb);

        TextView text = new TextView(host);
        text.setText(info.empty ? "空槽" : SaveStore.formatTime(info.time));
        text.setPadding(dp(host, 8), 0, dp(host, 8), 0);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (!info.empty) {
            Button load = small(host, "读取");
            Runnable doLoad = () -> {
                try {
                    host.loadSlot(slot);
                    loaded[0] = true;
                    dialog.dismiss();
                } catch (Exception e) {
                    host.toast(e.getMessage() == null ? "无法读档" : e.getMessage());
                }
            };
            load.setOnClickListener(v -> doLoad.run());
            thumb.setOnClickListener(v -> doLoad.run());
            text.setOnClickListener(v -> doLoad.run());
            row.addView(load);
        }
        Button save = small(host, info.empty ? "保存" : "覆盖");
        save.setOnClickListener(v -> {
            try {
                host.writeSlot(slot);
                rebuild.run();
            } catch (Exception e) {
                host.toast(e.getMessage() == null ? "无法保存" : e.getMessage());
            }
        });
        row.addView(save);
        return row;
    }

    private static Button small(MainActivity host, String label) {
        Button b = new Button(host, null, android.R.attr.buttonStyleSmall);
        b.setText(label);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        b.setMinHeight(dp(host, 36));
        b.setMinimumHeight(dp(host, 36));
        return b;
    }

    private static int dp(MainActivity host, int value) {
        return Math.round(value * host.getResources().getDisplayMetrics().density);
    }
}
