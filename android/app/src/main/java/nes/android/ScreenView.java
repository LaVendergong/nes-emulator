package nes.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import nes.ppu.Ppu;

/** 只 blit 256×240。 */
public final class ScreenView extends View {
    private final Bitmap bitmap = Bitmap.createBitmap(Ppu.WIDTH, Ppu.HEIGHT, Bitmap.Config.ARGB_8888);
    private final int[] pixels = new int[Ppu.WIDTH * Ppu.HEIGHT];
    private final Rect dest = new Rect();

    public ScreenView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    void blit(int[] framebuffer) {
        int n = Math.min(pixels.length, framebuffer.length);
        System.arraycopy(framebuffer, 0, pixels, 0, n);
        synchronized (bitmap) {
            bitmap.setPixels(pixels, 0, Ppu.WIDTH, 0, 0, Ppu.WIDTH, Ppu.HEIGHT);
        }
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int vw = getWidth();
        int vh = getHeight();
        int scale = Math.max(1, Math.min(vw / Ppu.WIDTH, vh / Ppu.HEIGHT));
        int w = Ppu.WIDTH * scale;
        int h = Ppu.HEIGHT * scale;
        dest.set((vw - w) / 2, (vh - h) / 2, (vw - w) / 2 + w, (vh - h) / 2 + h);
        synchronized (bitmap) {
            canvas.drawBitmap(bitmap, null, dest, null);
        }
    }
}
