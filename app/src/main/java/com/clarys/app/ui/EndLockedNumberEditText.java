package com.clarys.app.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;

import androidx.appcompat.widget.AppCompatEditText;

/**
 * Campo numérico para montos que conserva el cursor al final.
 * Evita insertar cifras en medio del valor y mantiene la escritura de izquierda a derecha.
 */
public class EndLockedNumberEditText extends AppCompatEditText {

    public EndLockedNumberEditText(Context context) {
        super(context);
        configureSelection();
    }

    public EndLockedNumberEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        configureSelection();
    }

    public EndLockedNumberEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        configureSelection();
    }

    private void configureSelection() {
        setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        setTextDirection(TEXT_DIRECTION_LTR);
        setLongClickable(false);
        setTextIsSelectable(false);
    }

    @Override
    protected void onSelectionChanged(int selectionStart, int selectionEnd) {
        CharSequence value = getText();
        int end = value == null ? 0 : value.length();
        if (selectionStart != end || selectionEnd != end) {
            setSelection(end);
            return;
        }
        super.onSelectionChanged(selectionStart, selectionEnd);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean handled = super.onTouchEvent(event);
        if (event.getAction() == MotionEvent.ACTION_UP) {
            setSelection(length());
        }
        return handled;
    }
}
