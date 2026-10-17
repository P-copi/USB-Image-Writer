package com.pcopi.bydphonespace;

import android.content.Context;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.widget.Button;

/** Same press feedback used by Dorm Camera, without extra AndroidX dependency. */
public class PressFeedbackButton extends Button {
    public PressFeedbackButton(Context context) { super(context); }
    public PressFeedbackButton(Context context, AttributeSet attrs) { super(context, attrs); }
    public PressFeedbackButton(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (isEnabled()) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                animate().scaleX(0.95f).scaleY(0.95f).setDuration(70L).start();
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                animate().scaleX(1f).scaleY(1f).setDuration(110L).start();
            }
        }
        return super.onTouchEvent(event);
    }

    @Override public boolean performClick() {
        if (isEnabled()) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        return super.performClick();
    }
}
