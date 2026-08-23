package nes.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import java.util.concurrent.atomic.AtomicInteger;

/** 虚拟手柄。bit 固定：A=1 B=2 Select=4 Start=8 方向 16/32/64/128。可拖位置，不换功能。 */
public final class PadView extends View {
    static final int A = 1;
    static final int B = 2;
    static final int SELECT = 4;
    static final int START = 8;
    static final int UP = 16;
    static final int DOWN = 32;
    static final int LEFT = 64;
    static final int RIGHT = 128;
    private static final int[] BITS = {0, A, B, SELECT, START};
    private static final String[] LABELS = {"方向", "A", "B", "选择", "开始"};
    private static final float GUTTER = 0.22f;

    private final AtomicInteger buttons = new AtomicInteger();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF[] box = new RectF[PadMap.COUNT];
    private final float[] tmpHalf = new float[2];
    private PadMap map = new PadMap();
    private boolean editing;
    private int drag = -1;
    private float dragDx;
    private float dragDy;

    public PadView(Context context, AttributeSet attrs) {
        super(context, attrs);
        for (int i = 0; i < box.length; i++) {
            box[i] = new RectF();
        }
        fill.setStyle(Paint.Style.FILL);
        text.setColor(0xFFFFFFFF);
        text.setTextAlign(Paint.Align.CENTER);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setColor(0xFFE8C36A);
        stroke.setStrokeWidth(4);
        setBackgroundColor(0x00000000);
    }

    int mask() {
        return editing ? 0 : buttons.get();
    }

    void bind(PadMap map) {
        this.map = map;
        layoutButtons();
        invalidate();
    }

    void setEditing(boolean value) {
        editing = value;
        drag = -1;
        buttons.set(0);
        invalidate();
    }

    boolean editing() {
        return editing;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        layoutButtons();
    }

    private void layoutButtons() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        for (int i = 0; i < PadMap.COUNT; i++) {
            place(i, map.x[i] * w, map.y[i] * h);
        }
    }

    private void place(int id, float cx, float cy) {
        half(id, tmpHalf);
        cx = clampX(cx, tmpHalf[0]);
        cy = clampY(cy, tmpHalf[1]);
        box[id].set(cx - tmpHalf[0], cy - tmpHalf[1], cx + tmpHalf[0], cy + tmpHalf[1]);
        map.x[id] = cx / getWidth();
        map.y[id] = cy / getHeight();
    }

    private void half(int id, float[] out) {
        float w = getWidth();
        float h = getHeight();
        float col = w * GUTTER;
        if (id == PadMap.DPAD) {
            float size = Math.min(col * 0.92f, h * 0.50f);
            out[0] = out[1] = size / 2f;
            return;
        }
        if (id == PadMap.SELECT || id == PadMap.START) {
            out[0] = Math.min(col * 0.42f, 66);
            out[1] = Math.min(h * 0.07f, 22);
            return;
        }
        float br = Math.min(col, h) * 0.16f;
        out[0] = out[1] = br;
    }

    private float clampX(float cx, float hw) {
        float w = getWidth();
        float gutter = w * GUTTER;
        float mid = w * 0.5f;
        if (cx < mid) {
            return clamp(cx, hw, gutter - hw);
        }
        return clamp(cx, w - gutter + hw, w - hw);
    }

    private float clampY(float cy, float hh) {
        float top = hh + getResources().getDisplayMetrics().density * 52;
        return clamp(cy, top, getHeight() - hh);
    }

    private static float clamp(float v, float lo, float hi) {
        if (lo > hi) {
            return (lo + hi) / 2f;
        }
        return Math.max(lo, Math.min(hi, v));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (editing) {
            return editTouch(event);
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            boolean hit = buttons.get() != 0;
            buttons.set(0);
            invalidate();
            return hit;
        }
        int mask = 0;
        int skip = action == MotionEvent.ACTION_POINTER_UP ? event.getActionIndex() : -1;
        boolean any = false;
        for (int i = 0; i < event.getPointerCount(); i++) {
            if (i == skip) {
                continue;
            }
            int bit = hit(event.getX(i), event.getY(i));
            if (bit != 0) {
                any = true;
            }
            mask |= bit;
        }
        buttons.set(mask);
        invalidate();
        return any || buttons.get() != 0;
    }

    private boolean editTouch(MotionEvent event) {
        int action = event.getActionMasked();
        float x = event.getX();
        float y = event.getY();
        if (action == MotionEvent.ACTION_DOWN) {
            drag = hitId(x, y);
            if (drag < 0) {
                return false;
            }
            dragDx = x - box[drag].centerX();
            dragDy = y - box[drag].centerY();
            invalidate();
            return true;
        }
        if (drag < 0) {
            return false;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            place(drag, x - dragDx, y - dragDy);
            invalidate();
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            drag = -1;
            invalidate();
            return true;
        }
        return true;
    }

    private int hitId(float x, float y) {
        for (int i = PadMap.COUNT - 1; i >= 0; i--) {
            if (box[i].contains(x, y)) {
                return i;
            }
        }
        return -1;
    }

    private int hit(float x, float y) {
        int m = 0;
        RectF dpad = box[PadMap.DPAD];
        if (dpad.contains(x, y)) {
            float cx = dpad.centerX();
            float cy = dpad.centerY();
            float dead = dpad.width() * 0.12f;
            if (y < cy - dead) {
                m |= UP;
            }
            if (y > cy + dead) {
                m |= DOWN;
            }
            if (x < cx - dead) {
                m |= LEFT;
            }
            if (x > cx + dead) {
                m |= RIGHT;
            }
        }
        for (int i = 1; i < PadMap.COUNT; i++) {
            if (box[i].contains(x, y)) {
                m |= BITS[i];
            }
        }
        return m;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int held = buttons.get();
        for (int i = 0; i < PadMap.COUNT; i++) {
            boolean on = i == PadMap.DPAD
                    ? (held & (UP | DOWN | LEFT | RIGHT)) != 0
                    : (held & BITS[i]) != 0;
            drawRound(canvas, box[i], on || (editing && drag == i), LABELS[i]);
            if (editing) {
                canvas.drawRoundRect(box[i], 24, 24, stroke);
            }
        }
    }

    private void drawRound(Canvas canvas, RectF box, boolean on, String label) {
        fill.setColor(on ? 0xFF4A90D9 : 0xCC333333);
        canvas.drawRoundRect(box, 24, 24, fill);
        text.setTextSize(Math.max(12, box.height() * 0.28f));
        canvas.drawText(label, box.centerX(), box.centerY() + text.getTextSize() * 0.35f, text);
    }
}
