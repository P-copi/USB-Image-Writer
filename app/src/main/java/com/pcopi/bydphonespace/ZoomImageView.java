package com.pcopi.bydphonespace;

import android.content.Context;
import android.graphics.Matrix;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

public class ZoomImageView extends ImageView {
    public interface SwipeListener {
        void onSwipeLeft();
        void onSwipeRight();
    }

    private final Matrix matrix = new Matrix();
    private final ScaleGestureDetector scaleDetector;
    private float scale = 1f;
    private float lastX, lastY;
    private float downX, downY;
    private long downTime;
    private boolean dragging;
    private SwipeListener swipeListener;

    public ZoomImageView(Context context) { this(context, null); }

    public ZoomImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setScaleType(ScaleType.MATRIX);
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                float factor = detector.getScaleFactor();
                float next = scale * factor;
                if (next < 1f) factor = 1f / scale;
                if (next > 5f) factor = 5f / scale;
                scale *= factor;
                matrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
                setImageMatrix(matrix);
                return true;
            }
        });
    }

    public void setSwipeListener(SwipeListener listener) {
        this.swipeListener = listener;
    }

    public boolean isZoomed() {
        return scale > 1.04f;
    }

    public void resetZoom() {
        scale = 1f;
        matrix.reset();
        fitToView();
    }

    private void fitToView() {
        if (getDrawable() == null || getWidth() == 0 || getHeight() == 0) return;
        float dw = getDrawable().getIntrinsicWidth();
        float dh = getDrawable().getIntrinsicHeight();
        if (dw <= 0 || dh <= 0) return;
        float s = Math.min((float)getWidth()/dw, (float)getHeight()/dh);
        float dx = (getWidth() - dw*s) / 2f;
        float dy = (getHeight() - dh*s) / 2f;
        matrix.reset();
        matrix.postScale(s, s);
        matrix.postTranslate(dx, dy);
        setImageMatrix(matrix);
        scale = 1f;
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w,h,oldw,oldh);
        post(this::fitToView);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = event.getX();
                downY = lastY = event.getY();
                downTime = System.currentTimeMillis();
                dragging = true;
                return true;

            case MotionEvent.ACTION_MOVE:
                if (dragging && !scaleDetector.isInProgress() && scale > 1.04f) {
                    float dx = event.getX() - lastX;
                    float dy = event.getY() - lastY;
                    matrix.postTranslate(dx, dy);
                    setImageMatrix(matrix);
                }
                lastX = event.getX();
                lastY = event.getY();
                return true;

            case MotionEvent.ACTION_UP:
                if (!scaleDetector.isInProgress() && scale <= 1.04f && swipeListener != null) {
                    float dx = event.getX() - downX;
                    float dy = event.getY() - downY;
                    long elapsed = Math.max(1, System.currentTimeMillis() - downTime);
                    float velocity = Math.abs(dx) * 1000f / elapsed;
                    float minDistance = getResources().getDisplayMetrics().density * 72f;
                    boolean horizontal = Math.abs(dx) > Math.abs(dy) * 1.35f;
                    boolean fastEnough = Math.abs(dx) >= minDistance || velocity > 650f;
                    if (horizontal && fastEnough) {
                        if (dx < 0) swipeListener.onSwipeLeft();
                        else swipeListener.onSwipeRight();
                    }
                }
                dragging = false;
                return true;

            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                return true;
        }
        return true;
    }
}
