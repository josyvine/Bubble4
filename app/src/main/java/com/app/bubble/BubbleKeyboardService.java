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
 * Manages Switching layers, Predictions, Emoji interactions, and the Professional Clipboard.
 */
public class BubbleKeyboardService extends InputMethodService implements KeyboardView.OnKeyboardActionListener {

    // Main Views
    private LinearLayout mainLayout;
    private KeyboardView kv;
    private View candidateView;
    private LinearLayout candidateContainer;
    private View emojiPaletteView;
    
    // New Professional Clipboard Views
    private View clipboardPaletteView;
    private ClipboardUiManager clipboardUiManager;

    // Buttons
    private ImageButton btnClipboard;
    private ImageButton btnKeyboardSwitch;

    // Keyboards
    private Keyboard keyboardQwerty;
    private Keyboard keyboardSymbols;

    // State
    private boolean isCaps = false;
    private boolean isEmojiVisible = false;
    private StringBuilder currentWord = new StringBuilder(); // Track typing for predictions

    // Long Press Logic for Space Key (kept as backup, though button is primary now)
    private Handler longPressHandler = new Handler(Looper.getMainLooper());
    private boolean isSpaceLongPressed = false;
    private static final int LONG_PRESS_DELAY = 500; 

    private Runnable spaceLongPressRunnable = new Runnable() {
        @Override
        public void run() {
            isSpaceLongPressed = true;
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

        // 2. Add Candidate View (Predictions + Toolbar)
        candidateView = inflater.inflate(R.layout.candidate_view, mainLayout, false);
        candidateContainer = candidateView.findViewById(R.id.candidate_container);
        
        // Setup Toolbar Buttons
        setupToolbarButtons();
        
        mainLayout.addView(candidateView);

        // 3. Add Keyboard View - Middle
        kv = (KeyboardView) inflater.inflate(R.layout.layout_real_keyboard, mainLayout, false);
        keyboardQwerty = new Keyboard(this, R.xml.qwerty);
        keyboardSymbols = new Keyboard(this, R.xml.symbols);
        kv.setKeyboard(keyboardQwerty);
        kv.setOnKeyboardActionListener(this);
        kv.setPreviewEnabled(false); 
        mainLayout.addView(kv);

        // 4. Add Emoji Palette (Hidden by default)
        emojiPaletteView = inflater.inflate(R.layout.layout_emoji_palette, mainLayout, false);
        emojiPaletteView.setVisibility(View.GONE);
        EmojiUtils.setupEmojiGrid(this, emojiPaletteView, new EmojiUtils.EmojiListener() {
            @Override
            public void onEmojiClick(String emoji) {
                getCurrentInputConnection().commitText(emoji, 1);
            }
        });
        setupEmojiControlButtons();
        mainLayout.addView(emojiPaletteView);

        // 5. Add Professional Clipboard Palette (Hidden by default)
        clipboardPaletteView = inflater.inflate(R.layout.layout_clipboard_palette, mainLayout, false);
        clipboardPaletteView.setVisibility(View.GONE);
        
        // Initialize the UI Manager for Clipboard
        clipboardUiManager = new ClipboardUiManager(this, clipboardPaletteView, new ClipboardUiManager.ClipboardListener() {
            @Override
            public void onPasteItem(String text) {
                // Paste selected text and close clipboard
                getCurrentInputConnection().commitText(text, 1);
                toggleClipboardPalette(); 
                updateCandidates(currentWord.toString());
            }

            @Override
            public void onCloseClipboard() {
                toggleClipboardPalette();
            }
        });
        
        mainLayout.addView(clipboardPaletteView);

        return mainLayout;
    }

    private void setupToolbarButtons() {
        // Clipboard Button
        btnClipboard = candidateView.findViewById(R.id.btn_clipboard);
        if (btnClipboard != null) {
            btnClipboard.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleClipboardPalette();
                }
            });
        }

        // Keyboard Switcher Button (Fix for Spacebar issue)
        btnKeyboardSwitch = candidateView.findViewById(R.id.btn_keyboard_switch);
        if (btnKeyboardSwitch != null) {
            btnKeyboardSwitch.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    InputMethodManager ime = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (ime != null) {
                        ime.showInputMethodPicker();
                    }
                }
            });
        }
    }

    private void setupEmojiControlButtons() {
        View btnBack = emojiPaletteView.findViewById(R.id.btn_back_to_abc);
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleEmojiPalette();
                }
            });
        }
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
                PredictionEngine.getInstance(this).learnWord(currentWord.toString());
                currentWord.setLength(0); 
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
                requestHideSelf(0);
                Intent intent = new Intent(BubbleKeyboardService.this, TwoLineOverlayService.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startService(intent);
                break;

            case 32: // Space
                if (!isSpaceLongPressed) {
                    ic.commitText(" ", 1);
                    PredictionEngine.getInstance(this).learnWord(currentWord.toString());
                    currentWord.setLength(0); 
                    updateCandidates("");
                }
                break;

            default:
                char code = (char) primaryCode;
                if (Character.isLetter(code) && isCaps) {
                    code = Character.toUpperCase(code);
                }
                ic.commitText(String.valueOf(code), 1);
                
                if (Character.isLetterOrDigit(code)) {
                    currentWord.append(code);
                    updateCandidates(currentWord.toString());
                } else {
                    PredictionEngine.getInstance(this).learnWord(currentWord.toString());
                    currentWord.setLength(0);
                    updateCandidates("");
                }
        }
    }

    private void handleBackspace() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.deleteSurroundingText(1, 0);
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
            // Show Emojis
            kv.setVisibility(View.GONE);
            candidateView.setVisibility(View.GONE);
            clipboardPaletteView.setVisibility(View.GONE); // Ensure clipboard is closed
            emojiPaletteView.setVisibility(View.VISIBLE);
        } else {
            // Show Keyboard
            emojiPaletteView.setVisibility(View.GONE);
            clipboardPaletteView.setVisibility(View.GONE);
            kv.setVisibility(View.VISIBLE);
            candidateView.setVisibility(View.VISIBLE);
        }
    }

    private void toggleClipboardPalette() {
        if (clipboardPaletteView.getVisibility() == View.GONE) {
            // Show Clipboard
            kv.setVisibility(View.GONE);
            candidateView.setVisibility(View.GONE);
            emojiPaletteView.setVisibility(View.GONE);
            clipboardPaletteView.setVisibility(View.VISIBLE);
            
            // Reload data to show latest copied items
            if (clipboardUiManager != null) {
                clipboardUiManager.reloadHistory();
            }
        } else {
            // Show Keyboard
            clipboardPaletteView.setVisibility(View.GONE);
            emojiPaletteView.setVisibility(View.GONE);
            kv.setVisibility(View.VISIBLE);
            candidateView.setVisibility(View.VISIBLE);
        }
    }

    // =========================================================
    // TOUCH EVENTS
    // =========================================================

    @Override
    public void onPress(int primaryCode) {
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
    // PREDICTION
    // =========================================================

    private void updateCandidates(String wordBeingTyped) {
        if (candidateContainer == null) return;
        
        candidateContainer.removeAllViews();
        
        List<String> suggestions = PredictionEngine.getInstance(this).getSuggestions(wordBeingTyped);

        if (suggestions.isEmpty() && wordBeingTyped.isEmpty()) {
            suggestions = PredictionEngine.getInstance(this).getSuggestions(""); 
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
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null) {
                        if (currentWord.length() > 0) {
                            ic.deleteSurroundingText(currentWord.length(), 0);
                        }
                        ic.commitText(word + " ", 1);
                        PredictionEngine.getInstance(BubbleKeyboardService.this).learnWord(word);
                        currentWord.setLength(0);
                        updateCandidates("");
                    }
                }
            });
            candidateContainer.addView(tv);
        }
    }

    @Override public void onText(CharSequence text) {}
    @Override public void swipeLeft() {}
    @Override public void swipeRight() {}
    @Override public void swipeDown() {}
    @Override public void swipeUp() {}
}