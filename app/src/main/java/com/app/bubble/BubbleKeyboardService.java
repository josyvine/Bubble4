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
 * Manages Switching layers, Predictions, Emoji interactions, Professional Clipboard, Translation, and OCR Tools.
 * UPDATED: Added Icon Hiding, Auto-Correct, and Next-Word Prediction.
 */
public class BubbleKeyboardService extends InputMethodService implements KeyboardView.OnKeyboardActionListener {

    // Main Views
    private LinearLayout mainLayout;
    private KeyboardView kv;
    private View candidateView;
    private LinearLayout candidateContainer;
    private LinearLayout toolbarContainer; // New Container for icons
    private View emojiPaletteView;
    
    // Professional Clipboard Views
    private View clipboardPaletteView;
    private ClipboardUiManager clipboardUiManager;

    // Translation Views & Logic
    private View translationPanelView;
    private TranslationUiManager translationUiManager;
    private boolean isTranslationMode = false;
    private StringBuilder translationBuffer = new StringBuilder();

    // Buttons
    private ImageButton btnClipboard;
    private ImageButton btnKeyboardSwitch;
    private ImageButton btnTranslate;
    private ImageButton btnBubbleLauncher; 
    private ImageButton btnOcrCopy;       

    // Keyboards
    private Keyboard keyboardQwerty;
    private Keyboard keyboardSymbols;

    // State
    private boolean isCaps = false;
    private boolean isEmojiVisible = false;
    private StringBuilder currentWord = new StringBuilder(); 
    
    // NEW: Track history for Next-Word Prediction
    private String lastCommittedWord = null; 

    // Long Press Logic 
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
        // 1. Create the Main Container
        mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);

        LayoutInflater inflater = getLayoutInflater();

        // 2. Add Candidate View
        candidateView = inflater.inflate(R.layout.candidate_view, mainLayout, false);
        candidateContainer = candidateView.findViewById(R.id.candidate_container);
        // FIX: Find the new container for icons
        toolbarContainer = candidateView.findViewById(R.id.toolbar_container);
        
        setupToolbarButtons();
        mainLayout.addView(candidateView);

        // 3. Add Translation Panel
        translationPanelView = inflater.inflate(R.layout.layout_translation_panel, mainLayout, false);
        translationPanelView.setVisibility(View.GONE);
        
        translationUiManager = new TranslationUiManager(this, translationPanelView, new TranslationUiManager.TranslationListener() {
            @Override
            public void onTranslationResult(String translatedText) {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    ic.commitText(translatedText, 1);
                    // Update history for prediction
                    lastCommittedWord = translatedText.trim();
                }
                translationBuffer.setLength(0);
            }

            @Override
            public void onCloseTranslation() {
                toggleTranslationMode();
            }
        });
        mainLayout.addView(translationPanelView);

        // 4. Add Keyboard View
        kv = (KeyboardView) inflater.inflate(R.layout.layout_real_keyboard, mainLayout, false);
        keyboardQwerty = new Keyboard(this, R.xml.qwerty);
        keyboardSymbols = new Keyboard(this, R.xml.symbols);
        kv.setKeyboard(keyboardQwerty);
        kv.setOnKeyboardActionListener(this);
        kv.setPreviewEnabled(false); 
        mainLayout.addView(kv);

        // 5. Add Emoji Palette
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

        // 6. Add Clipboard Palette
        clipboardPaletteView = inflater.inflate(R.layout.layout_clipboard_palette, mainLayout, false);
        clipboardPaletteView.setVisibility(View.GONE);
        
        clipboardUiManager = new ClipboardUiManager(this, clipboardPaletteView, new ClipboardUiManager.ClipboardListener() {
            @Override
            public void onPasteItem(String text) {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    ic.commitText(text, 1);
                    PredictionEngine.getInstance(BubbleKeyboardService.this).learnWord(text);
                    lastCommittedWord = text.trim(); // Update history
                }
                toggleClipboardPalette(); 
                updateCandidates("");
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
        btnClipboard = candidateView.findViewById(R.id.btn_clipboard);
        if (btnClipboard != null) {
            btnClipboard.setOnClickListener(v -> toggleClipboardPalette());
        }
        btnKeyboardSwitch = candidateView.findViewById(R.id.btn_keyboard_switch);
        if (btnKeyboardSwitch != null) {
            btnKeyboardSwitch.setOnClickListener(v -> {
                InputMethodManager ime = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (ime != null) ime.showInputMethodPicker();
            });
        }
        btnTranslate = candidateView.findViewById(R.id.btn_translate);
        if (btnTranslate != null) {
            btnTranslate.setOnClickListener(v -> toggleTranslationMode());
        }
        btnBubbleLauncher = candidateView.findViewById(R.id.btn_bubble_launcher);
        if (btnBubbleLauncher != null) {
            btnBubbleLauncher.setOnClickListener(v -> {
                Intent intent = new Intent(BubbleKeyboardService.this, FloatingTranslatorService.class);
                intent.setAction("ACTION_SHOW_BUBBLE");
                startService(intent);
            });
        }
        btnOcrCopy = candidateView.findViewById(R.id.btn_ocr_copy);
        if (btnOcrCopy != null) {
            btnOcrCopy.setOnClickListener(v -> {
                requestHideSelf(0);
                Intent intent = new Intent(BubbleKeyboardService.this, FloatingTranslatorService.class);
                intent.setAction("ACTION_TRIGGER_COPY_ONLY");
                startService(intent);
            });
        }
    }

    private void setupEmojiControlButtons() {
        View btnBack = emojiPaletteView.findViewById(R.id.btn_back_to_abc);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> toggleEmojiPalette());
        }
        View btnDel = emojiPaletteView.findViewById(R.id.btn_emoji_backspace);
        if (btnDel != null) {
            btnDel.setOnClickListener(v -> handleBackspace());
        }
    }

    // =========================================================
    // KEY HANDLING (ENHANCED)
    // =========================================================

    @Override
    public void onKey(int primaryCode, int[] keyCodes) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        // --- FEATURE 1: DYNAMIC ICON VISIBILITY ---
        // If typing letters, hide icons. If deleting or space, show icons.
        if (Character.isLetterOrDigit(primaryCode)) {
            if (toolbarContainer != null) toolbarContainer.setVisibility(View.GONE);
        } else if (primaryCode == 32 || primaryCode == 46 || currentWord.length() == 0) { 
            // Space, Dot, or Empty
            if (toolbarContainer != null) toolbarContainer.setVisibility(View.VISIBLE);
        }

        // Special Keys
        if (primaryCode == Keyboard.KEYCODE_DELETE) {
            if (isTranslationMode) {
                if (translationBuffer.length() > 0) {
                    translationBuffer.deleteCharAt(translationBuffer.length() - 1);
                    translationUiManager.updateInputPreview(translationBuffer.toString());
                }
            } else {
                handleBackspace();
            }
            return;
        }

        if (primaryCode == Keyboard.KEYCODE_SHIFT) {
            isCaps = !isCaps;
            keyboardQwerty.setShifted(isCaps);
            kv.invalidateAllKeys();
            return;
        }

        if (primaryCode == Keyboard.KEYCODE_DONE) { 
            if (isTranslationMode) {
                // Feature 4: Enter triggers translation (manual trigger backup)
                translationUiManager.performTranslation(translationBuffer.toString());
            } else {
                PredictionEngine.getInstance(this).learnWord(currentWord.toString());
                lastCommittedWord = currentWord.toString();
                currentWord.setLength(0); 
                updateCandidates("");
                ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
            }
            return;
        }

        if (primaryCode == -2) { 
            if (kv.getKeyboard() == keyboardQwerty) kv.setKeyboard(keyboardSymbols);
            else kv.setKeyboard(keyboardQwerty);
            return;
        }

        if (primaryCode == -100) { toggleEmojiPalette(); return; }

        if (primaryCode == -10) { 
            requestHideSelf(0);
            Intent intent = new Intent(BubbleKeyboardService.this, TwoLineOverlayService.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startService(intent);
            return;
        }

        // --- TYPING LOGIC ---
        if (primaryCode == 32) { // Space
            if (!isSpaceLongPressed) {
                if (isTranslationMode) {
                    translationBuffer.append(" ");
                    translationUiManager.updateInputPreview(translationBuffer.toString());
                } else {
                    // --- FEATURE 3: AUTO-CORRECTION ---
                    String typo = currentWord.toString();
                    String correction = PredictionEngine.getInstance(this).getBestMatch(typo);
                    
                    if (correction != null && !correction.equals(typo)) {
                        // Replace typo with correction
                        ic.deleteSurroundingText(typo.length(), 0);
                        ic.commitText(correction, 1);
                        // Save the corrected word as the one typed
                        currentWord.setLength(0);
                        currentWord.append(correction);
                    }

                    ic.commitText(" ", 1);
                    
                    // --- FEATURE 2: NEXT-WORD LEARNING ---
                    String justTyped = currentWord.toString();
                    PredictionEngine.getInstance(this).learnWord(justTyped);
                    
                    if (lastCommittedWord != null && !lastCommittedWord.isEmpty()) {
                        PredictionEngine.getInstance(this).learnNextWord(lastCommittedWord, justTyped);
                    }
                    
                    lastCommittedWord = justTyped;
                    currentWord.setLength(0); 
                    updateCandidates("");
                }
            }
            return;
        }

        // Characters
        char code = (char) primaryCode;
        if (Character.isLetter(code) && isCaps) {
            code = Character.toUpperCase(code);
        }

        if (isTranslationMode) {
            translationBuffer.append(code);
            translationUiManager.updateInputPreview(translationBuffer.toString());
            // Feature 4: Update UI Manager could have debouncer added in next file
        } else {
            ic.commitText(String.valueOf(code), 1);
            if (Character.isLetterOrDigit(code)) {
                currentWord.append(code);
                updateCandidates(currentWord.toString());
            } else {
                // Punctuation
                PredictionEngine.getInstance(this).learnWord(currentWord.toString());
                lastCommittedWord = currentWord.toString();
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
                // If empty, show icons again
                if (toolbarContainer != null) toolbarContainer.setVisibility(View.VISIBLE);
                updateCandidates("");
            }
        }
    }

    private void toggleEmojiPalette() {
        if (emojiPaletteView.getVisibility() == View.GONE) {
            kv.setVisibility(View.GONE);
            candidateView.setVisibility(View.GONE);
            clipboardPaletteView.setVisibility(View.GONE);
            translationPanelView.setVisibility(View.GONE);
            isTranslationMode = false;
            emojiPaletteView.setVisibility(View.VISIBLE);
        } else {
            resetToStandardKeyboard();
        }
    }

    private void toggleClipboardPalette() {
        if (clipboardPaletteView.getVisibility() == View.GONE) {
            kv.setVisibility(View.GONE);
            candidateView.setVisibility(View.GONE);
            emojiPaletteView.setVisibility(View.GONE);
            translationPanelView.setVisibility(View.GONE);
            isTranslationMode = false;
            clipboardPaletteView.setVisibility(View.VISIBLE);
            if (clipboardUiManager != null) clipboardUiManager.reloadHistory();
        } else {
            resetToStandardKeyboard();
        }
    }

    private void toggleTranslationMode() {
        if (translationPanelView.getVisibility() == View.GONE) {
            candidateView.setVisibility(View.GONE);
            clipboardPaletteView.setVisibility(View.GONE);
            emojiPaletteView.setVisibility(View.GONE);
            translationPanelView.setVisibility(View.VISIBLE);
            kv.setVisibility(View.VISIBLE);
            isTranslationMode = true;
            translationBuffer.setLength(0); 
            translationUiManager.updateInputPreview("");
        } else {
            resetToStandardKeyboard();
        }
    }

    private void resetToStandardKeyboard() {
        emojiPaletteView.setVisibility(View.GONE);
        clipboardPaletteView.setVisibility(View.GONE);
        translationPanelView.setVisibility(View.GONE);
        candidateView.setVisibility(View.VISIBLE);
        kv.setVisibility(View.VISIBLE);
        isTranslationMode = false;
    }

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
    // PREDICTION (ENHANCED)
    // =========================================================

    private void updateCandidates(String wordBeingTyped) {
        if (candidateContainer == null) return;
        
        candidateContainer.removeAllViews();
        List<String> suggestions;

        // --- FEATURE 2: CONTEXTUAL PREDICTION ---
        // If user isn't typing a word yet, show Next-Word suggestions based on history
        if (wordBeingTyped.isEmpty()) {
            if (lastCommittedWord != null) {
                suggestions = PredictionEngine.getInstance(this).getNextWordSuggestions(lastCommittedWord);
            } else {
                suggestions = PredictionEngine.getInstance(this).getSuggestions(""); 
            }
        } else {
            suggestions = PredictionEngine.getInstance(this).getSuggestions(wordBeingTyped);
        }

        // Populate Views
        for (final String word : suggestions) {
            TextView tv = new TextView(this);
            tv.setText(word);
            tv.setTextSize(18);
            tv.setPadding(40, 20, 40, 20);
            tv.setBackgroundResource(android.R.drawable.list_selector_background);
            
            tv.setOnClickListener(v -> {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    // Fix prediction overwrite logic
                    if (currentWord.length() > 0) {
                        ic.deleteSurroundingText(currentWord.length(), 0);
                    }
                    ic.commitText(word + " ", 1);
                    
                    // Learn
                    PredictionEngine.getInstance(this).learnWord(word);
                    if (lastCommittedWord != null) {
                        PredictionEngine.getInstance(this).learnNextWord(lastCommittedWord, word);
                    }
                    
                    lastCommittedWord = word;
                    currentWord.setLength(0);
                    updateCandidates("");
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