package com.app.bubble;

import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The core Service handling the Modern Keyboard logic.
 * Manages Switching layers, Predictions, and Emoji interactions.
 */
public class BubbleKeyboardService extends InputMethodService implements KeyboardView.OnKeyboardActionListener {

    // Main Views
    private LinearLayout mainLayout;
    private KeyboardView kv;
    private View candidateView;
    private LinearLayout candidateContainer;
    private View emojiPaletteView;

    // Keyboards
    private Keyboard keyboardQwerty;
    private Keyboard keyboardSymbols;

    // State
    private boolean isCaps = false;
    private boolean isEmojiVisible = false;

    // Long Press Logic for Space Key
    private Handler longPressHandler = new Handler(Looper.getMainLooper());
    private boolean isSpaceLongPressed = false;
    private static final int LONG_PRESS_DELAY = 500; // 500ms for long press

    private Runnable spaceLongPressRunnable = new Runnable() {
        @Override
        public void run() {
            isSpaceLongPressed = true;
            // Trigger System Input Method Picker
            InputMethodManager ime = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (ime != null) {
                ime.showInputMethodPicker();
            }
        }
    };

    @Override
    public View onCreateInputView() {
        // 1. Create the Main Container (Vertical)
        mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);

        LayoutInflater inflater = getLayoutInflater();

        // 2. Add Candidate View (Predictions) - Top
        candidateView = inflater.inflate(R.layout.candidate_view, null);
        candidateContainer = candidateView.findViewById(R.id.candidate_container);
        mainLayout.addView(candidateView);

        // 3. Add Keyboard View - Middle
        // Note: We use a generic layout that contains the KeyboardView
        // Ensure you have a layout_real_keyboard.xml or create the view programmatically
        kv = (KeyboardView) inflater.inflate(R.layout.layout_real_keyboard, null);
        
        // Load Layouts
        keyboardQwerty = new Keyboard(this, R.xml.qwerty);
        keyboardSymbols = new Keyboard(this, R.xml.symbols);
        
        kv.setKeyboard(keyboardQwerty);
        kv.setOnKeyboardActionListener(this);
        kv.setPreviewEnabled(false); // Disable the popup character preview (modern look)
        mainLayout.addView(kv);

        // 4. Add Emoji Palette - Hidden by default
        emojiPaletteView = inflater.inflate(R.layout.layout_emoji_palette, null);
        emojiPaletteView.setVisibility(View.GONE);
        
        // Initialize Emoji Grid logic
        EmojiUtils.setupEmojiGrid(this, emojiPaletteView, new EmojiUtils.EmojiListener() {
            @Override
            public void onEmojiClick(String emoji) {
                getCurrentInputConnection().commitText(emoji, 1);
            }
        });
        
        // Setup Emoji Palette Buttons
        setupEmojiControlButtons();
        
        mainLayout.addView(emojiPaletteView);

        return mainLayout;
    }

    private void setupEmojiControlButtons() {
        // Back Button (Return to ABC)
        View btnBack = emojiPaletteView.findViewById(R.id.btn_back_to_abc);
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleEmojiPalette();
                }
            });
        }
        
        // Delete Button inside Emoji View
        View btnDel = emojiPaletteView.findViewById(R.id.btn_emoji_backspace);
        if (btnDel != null) {
            btnDel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    handleBackspace();
                }
            });
        }
    }

    // =========================================================
    // KEY HANDLING
    // =========================================================

    @Override
    public void onKey(int primaryCode, int[] keyCodes) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        switch (primaryCode) {
            case Keyboard.KEYCODE_DELETE: // -5
                handleBackspace();
                break;

            case Keyboard.KEYCODE_SHIFT: // -1
                isCaps = !isCaps;
                keyboardQwerty.setShifted(isCaps);
                kv.invalidateAllKeys();
                break;

            case Keyboard.KEYCODE_DONE: // -4 (Enter)
                ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
                break;

            case -2: // "?123" Mode Switch
                if (kv.getKeyboard() == keyboardQwerty) {
                    kv.setKeyboard(keyboardSymbols);
                } else {
                    kv.setKeyboard(keyboardQwerty);
                }
                break;

            case -100: // Emoji Toggle
                toggleEmojiPalette();
                break;

            case 32: // Space
                // If long press triggered, do nothing (picker already shown)
                if (!isSpaceLongPressed) {
                    ic.commitText(" ", 1);
                }
                break;

            default:
                char code = (char) primaryCode;
                if (Character.isLetter(code) && isCaps) {
                    code = Character.toUpperCase(code);
                }
                ic.commitText(String.valueOf(code), 1);
                updateCandidates(String.valueOf(code));
        }
    }

    private void handleBackspace() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.deleteSurroundingText(1, 0);
            updateCandidates("");
        }
    }

    // =========================================================
    // VIEW SWITCHING
    // =========================================================

    private void toggleEmojiPalette() {
        if (emojiPaletteView.getVisibility() == View.GONE) {
            // Show Emojis, Hide Keyboard
            kv.setVisibility(View.GONE);
            candidateView.setVisibility(View.GONE); // Hide suggestions too
            emojiPaletteView.setVisibility(View.VISIBLE);
            isEmojiVisible = true;
        } else {
            // Show Keyboard, Hide Emojis
            emojiPaletteView.setVisibility(View.GONE);
            kv.setVisibility(View.VISIBLE);
            candidateView.setVisibility(View.VISIBLE);
            isEmojiVisible = false;
        }
    }

    // =========================================================
    // TOUCH EVENTS (Long Press Space)
    // =========================================================

    @Override
    public void onPress(int primaryCode) {
        // Space Key Code is 32
        if (primaryCode == 32) {
            isSpaceLongPressed = false;
            longPressHandler.postDelayed(spaceLongPressRunnable, LONG_PRESS_DELAY);
        }
    }

    @Override
    public void onRelease(int primaryCode) {
        if (primaryCode == 32) {
            longPressHandler.removeCallbacks(spaceLongPressRunnable);
        }
    }

    // =========================================================
    // PREDICTION (CANDIDATE VIEW)
    // =========================================================

    private void updateCandidates(String lastChar) {
        // In a real production app, this would query a dictionary database.
        // Here, we provide "Simulated" intelligent suggestions.
        
        if (candidateContainer == null) return;
        candidateContainer.removeAllViews();
        
        // Dummy suggestions logic
        String[] suggestions;
        if (lastChar.equals("t")) {
            suggestions = new String[]{"the", "that", "this"};
        } else if (lastChar.equals("a")) {
            suggestions = new String[]{"and", "are", "about"};
        } else {
             suggestions = new String[]{"I", "You", "The"};
        }

        for (final String word : suggestions) {
            TextView tv = new TextView(this);
            tv.setText(word);
            tv.setTextSize(18);
            tv.setPadding(40, 20, 40, 20);
            tv.setBackgroundResource(android.R.drawable.list_selector_background);
            
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    getCurrentInputConnection().commitText(word + " ", 1);
                }
            });
            
            candidateContainer.addView(tv);
        }
    }

    // Unused overrides
    @Override public void onText(CharSequence text) {}
    @Override public void swipeLeft() {}
    @Override public void swipeRight() {}
    @Override public void swipeDown() {}
    @Override public void swipeUp() {}
}