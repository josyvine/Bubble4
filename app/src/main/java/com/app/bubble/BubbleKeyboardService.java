package com.app.bubble;

import android.content.Context;
import android.content.Intent;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * The core Service handling the Modern Keyboard logic.
 * Manages Switching layers, Predictions, Emoji interactions, and the Copy Tool trigger.
 */
public class BubbleKeyboardService extends InputMethodService implements KeyboardView.OnKeyboardActionListener {

    // Main Views
    private LinearLayout mainLayout;
    private KeyboardView kv;
    private View candidateView;
    private LinearLayout candidateContainer;
    private View emojiPaletteView;
    private ImageButton btnClipboard; // New Clipboard Button

    // Keyboards
    private Keyboard keyboardQwerty;
    private Keyboard keyboardSymbols;

    // State
    private boolean isCaps = false;
    private boolean isEmojiVisible = false;
    private boolean isClipboardVisible = false;
    private StringBuilder currentWord = new StringBuilder(); // Track typing for predictions

    // Long Press Logic for Space Key (Switching Keyboards)
    private Handler longPressHandler = new Handler(Looper.getMainLooper());
    private boolean isSpaceLongPressed = false;
    private static final int LONG_PRESS_DELAY = 500; // 500ms hold time

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
        
        // Setup Clipboard Button (Issue #6)
        btnClipboard = candidateView.findViewById(R.id.btn_clipboard);
        if (btnClipboard != null) {
            btnClipboard.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleClipboardView();
                }
            });
        }
        
        mainLayout.addView(candidateView);

        // 3. Add Keyboard View - Middle
        // We inflate layout_real_keyboard which must contain the KeyBackground attribute for visual feedback
        kv = (KeyboardView) inflater.inflate(R.layout.layout_real_keyboard, null);
        
        // Load Layouts
        keyboardQwerty = new Keyboard(this, R.xml.qwerty);
        keyboardSymbols = new Keyboard(this, R.xml.symbols);
        
        kv.setKeyboard(keyboardQwerty);
        kv.setOnKeyboardActionListener(this);
        kv.setPreviewEnabled(false); // Disable popup preview for modern look
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
        
        // Setup Emoji Palette Buttons (Back/Delete)
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
                // Learn the word before sending enter (Issue #2)
                PredictionEngine.getInstance(this).learnWord(currentWord.toString());
                currentWord.setLength(0); // Reset
                updateCandidates("");
                
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

            case -10: // [COPY] BUTTON - Launches the Manual Copy Tool
                // Hide keyboard
                requestHideSelf(0);
                // Start Overlay Service
                Intent intent = new Intent(BubbleKeyboardService.this, TwoLineOverlayService.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startService(intent);
                break;

            case 32: // Space
                // FIX Issue #3: Only type space if we didn't just trigger the Long Press menu
                if (!isSpaceLongPressed) {
                    ic.commitText(" ", 1);
                    // Learn the word (Issue #2)
                    PredictionEngine.getInstance(this).learnWord(currentWord.toString());
                    currentWord.setLength(0); // Reset word tracking
                    updateCandidates("");
                }
                break;

            default:
                char code = (char) primaryCode;
                if (Character.isLetter(code) && isCaps) {
                    code = Character.toUpperCase(code);
                }
                ic.commitText(String.valueOf(code), 1);
                
                // Track typing for prediction (Issue #2)
                if (Character.isLetterOrDigit(code)) {
                    currentWord.append(code);
                    updateCandidates(currentWord.toString());
                } else {
                    currentWord.setLength(0);
                    updateCandidates("");
                }
        }
    }

    private void handleBackspace() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.deleteSurroundingText(1, 0);
            
            // Update prediction logic
            if (currentWord.length() > 0) {
                currentWord.deleteCharAt(currentWord.length() - 1);
                updateCandidates(currentWord.toString());
            } else {
                updateCandidates("");
            }
        }
    }

    // =========================================================
    // VIEW SWITCHING
    // =========================================================

    private void toggleEmojiPalette() {
        if (emojiPaletteView.getVisibility() == View.GONE) {
            // Show Emojis, Hide Keyboard
            kv.setVisibility(View.GONE);
            candidateView.setVisibility(View.GONE);
            emojiPaletteView.setVisibility(View.VISIBLE);
            
            // FIX Issue #5: Ensure layout params match keyboard height (approx 250dp)
            // This is also handled in XML, but we reset visibility here.
            isEmojiVisible = true;
        } else {
            // Show Keyboard, Hide Emojis
            emojiPaletteView.setVisibility(View.GONE);
            kv.setVisibility(View.VISIBLE);
            candidateView.setVisibility(View.VISIBLE);
            isEmojiVisible = false;
        }
    }

    // FIX Issue #6: Clipboard View Toggle
    private void toggleClipboardView() {
        if (candidateContainer == null) return;
        
        if (!isClipboardVisible) {
            // Show Clipboard History
            isClipboardVisible = true;
            candidateContainer.removeAllViews();
            
            List<String> history = ClipboardManagerHelper.getInstance(this).getHistory();
            
            if (history.isEmpty()) {
                TextView tv = new TextView(this);
                tv.setText("Clipboard Empty");
                tv.setPadding(20, 10, 20, 10);
                candidateContainer.addView(tv);
            } else {
                for (final String clip : history) {
                    TextView tv = new TextView(this);
                    // Show truncated text
                    String label = clip.length() > 20 ? clip.substring(0, 20) + "..." : clip;
                    tv.setText(label);
                    tv.setTextSize(16);
                    tv.setPadding(30, 15, 30, 15);
                    tv.setBackgroundResource(android.R.drawable.list_selector_background);
                    
                    tv.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            getCurrentInputConnection().commitText(clip, 1);
                            // Optional: switch back to predictions after paste
                            isClipboardVisible = false;
                            updateCandidates(currentWord.toString());
                        }
                    });
                    candidateContainer.addView(tv);
                }
            }
        } else {
            // Revert to Predictions
            isClipboardVisible = false;
            updateCandidates(currentWord.toString());
        }
    }

    // =========================================================
    // TOUCH EVENTS (Long Press Space Logic)
    // =========================================================

    @Override
    public void onPress(int primaryCode) {
        // Space Key Code is 32
        if (primaryCode == 32) {
            isSpaceLongPressed = false; // Reset flag
            longPressHandler.postDelayed(spaceLongPressRunnable, LONG_PRESS_DELAY);
        }
    }

    @Override
    public void onRelease(int primaryCode) {
        if (primaryCode == 32) {
            // FIX Issue #3: Cancel the timer immediately on release
            longPressHandler.removeCallbacks(spaceLongPressRunnable);
        }
    }

    // =========================================================
    // PREDICTION (CANDIDATE VIEW)
    // =========================================================

    private void updateCandidates(String wordBeingTyped) {
        if (candidateContainer == null || isClipboardVisible) return;
        
        candidateContainer.removeAllViews();
        
        // FIX Issue #2: Use Prediction Engine
        List<String> suggestions = PredictionEngine.getInstance(this).getSuggestions(wordBeingTyped);

        // If no suggestions (or empty word), maybe show punctuation or common words
        if (suggestions.isEmpty() && wordBeingTyped.isEmpty()) {
            suggestions = PredictionEngine.getInstance(this).getSuggestions(""); // Get top frequent words
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
                    // Learn this usage (boost frequency)
                    PredictionEngine.getInstance(BubbleKeyboardService.this).learnWord(word);
                    currentWord.setLength(0);
                    updateCandidates("");
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