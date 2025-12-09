package com.app.bubble;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Manages clipboard history for the custom keyboard.
 * Stores the last 10 copied items.
 */
public class ClipboardManagerHelper {

    private static ClipboardManagerHelper instance;
    private ClipboardManager systemClipboard;
    private SharedPreferences prefs;
    private List<String> clipHistory;
    
    private static final String PREFS_NAME = "BubbleClipboardPrefs";
    private static final String KEY_HISTORY = "ClipHistoryString";
    private static final int MAX_HISTORY_SIZE = 10;
    private static final String DELIMITER = "#####"; // Simple delimiter for saving list

    private ClipboardManagerHelper(Context context) {
        systemClipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        clipHistory = new ArrayList<>();
        
        loadHistory();
        syncWithSystemClipboard();
    }

    public static synchronized ClipboardManagerHelper getInstance(Context context) {
        if (instance == null) {
            instance = new ClipboardManagerHelper(context);
        }
        return instance;
    }

    /**
     * Checks if the system clipboard has new text and adds it to our history.
     */
    public void syncWithSystemClipboard() {
        if (systemClipboard != null && systemClipboard.hasPrimaryClip()) {
            ClipData.Item item = systemClipboard.getPrimaryClip().getItemAt(0);
            if (item != null && item.getText() != null) {
                addClip(item.getText().toString());
            }
        }
    }

    /**
     * Adds a text to the history (Top of the list).
     * Removes duplicates and keeps size limited.
     */
    public void addClip(String text) {
        if (text == null || text.trim().isEmpty()) return;

        // Remove if already exists (to move it to top)
        if (clipHistory.contains(text)) {
            clipHistory.remove(text);
        }

        // Add to top
        clipHistory.add(0, text);

        // Trim size
        if (clipHistory.size() > MAX_HISTORY_SIZE) {
            clipHistory.remove(clipHistory.size() - 1);
        }

        saveHistory();
    }

    public List<String> getHistory() {
        syncWithSystemClipboard(); // Ensure we have the very latest
        return new ArrayList<>(clipHistory);
    }

    public void clearHistory() {
        clipHistory.clear();
        saveHistory();
    }

    // --- Persistence Logic ---

    private void saveHistory() {
        // Simple join to save as string
        String joined = TextUtils.join(DELIMITER, clipHistory);
        prefs.edit().putString(KEY_HISTORY, joined).apply();
    }

    private void loadHistory() {
        String saved = prefs.getString(KEY_HISTORY, "");
        if (!saved.isEmpty()) {
            String[] items = saved.split(DELIMITER);
            clipHistory.clear();
            clipHistory.addAll(Arrays.asList(items));
        }
    }
}