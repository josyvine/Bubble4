package com.app.bubble;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles "Type Memory", Dictionary Suggestions, Next-Word Prediction, and Auto-Correction.
 */
public class PredictionEngine {

    private static PredictionEngine instance;
    private SharedPreferences prefs;
    private Set<String> userDictionary;
    // Map to store PreviousWord -> List of Likely Next Words
    private Map<String, List<String>> bigramMap; 
    
    private static final String PREFS_NAME = "BubbleDict";
    private static final String KEY_WORDS = "UserWords";
    private static final String KEY_BIGRAMS = "UserBigrams";

    private final String[] BASE_DICT = {
        "the", "and", "that", "have", "for", "not", "with", "you", "this", "but", "his", "from",
        "they", "we", "say", "her", "she", "or", "an", "will", "my", "one", "all", "would",
        "there", "their", "what", "so", "up", "out", "if", "about", "who", "get", "which",
        "go", "me", "when", "make", "can", "like", "time", "no", "just", "him", "know",
        "take", "people", "into", "year", "your", "good", "some", "could", "them", "see",
        "other", "than", "then", "now", "look", "only", "come", "its", "over", "think",
        "also", "back", "after", "use", "two", "how", "our", "work", "first", "well",
        "way", "even", "new", "want", "because", "any", "these", "give", "day", "most",
        "apple", "application", "app", "bubble", "keyboard", "translate", "love", "are"
    };

    private PredictionEngine(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        userDictionary = new HashSet<>();
        bigramMap = new HashMap<>();
        
        // Load saved words
        Set<String> saved = prefs.getStringSet(KEY_WORDS, new HashSet<String>());
        if (saved != null) {
            userDictionary.addAll(saved);
        }
        Collections.addAll(userDictionary, BASE_DICT);
        
        // Load Bigrams (Context History)
        loadBigrams();
    }

    public static synchronized PredictionEngine getInstance(Context context) {
        if (instance == null) {
            instance = new PredictionEngine(context);
        }
        return instance;
    }

    /**
     * Returns a list of suggestions that start with the given prefix.
     */
    public List<String> getSuggestions(String prefix) {
        List<String> results = new ArrayList<>();
        if (prefix == null || prefix.isEmpty()) return results;

        String check = prefix.toLowerCase();

        for (String word : userDictionary) {
            if (word.toLowerCase().startsWith(check)) {
                if (!word.equalsIgnoreCase(check)) {
                    results.add(word);
                }
            }
        }
        Collections.sort(results);
        if (results.size() > 5) return results.subList(0, 5);
        return results;
    }

    /**
     * NEW: Returns suggestions based on the PREVIOUS word (Context).
     * E.g. input "I" -> returns ["love", "am", "will"]
     */
    public List<String> getNextWordSuggestions(String previousWord) {
        if (previousWord == null) return new ArrayList<>();
        String key = previousWord.toLowerCase().trim();
        
        if (bigramMap.containsKey(key)) {
            return new ArrayList<>(bigramMap.get(key));
        }
        return new ArrayList<>();
    }

    /**
     * Learns a new word when the user types Space/Enter.
     */
    public void learnWord(String word) {
        if (word == null || word.trim().length() < 2) return;
        String cleanWord = word.trim();
        
        if (!userDictionary.contains(cleanWord)) {
            userDictionary.add(cleanWord);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putStringSet(KEY_WORDS, userDictionary);
            editor.apply();
        }
    }

    /**
     * NEW: Learns the relationship between two words.
     * E.g. "I", "love" -> Remembers that "love" follows "I".
     */
    public void learnNextWord(String prev, String current) {
        if (prev == null || current == null || prev.isEmpty() || current.isEmpty()) return;
        
        String key = prev.toLowerCase().trim();
        String value = current.trim();
        
        List<String> list = bigramMap.get(key);
        if (list == null) {
            list = new ArrayList<>();
            bigramMap.put(key, list);
        }
        
        // Add to front of list (Most Recent)
        if (list.contains(value)) {
            list.remove(value);
        }
        list.add(0, value);
        
        // Keep list small
        if (list.size() > 5) list.remove(list.size() - 1);
        
        saveBigrams();
    }

    /**
     * NEW: Auto-Correction Logic.
     * Finds the closest dictionary word to the typo.
     * Uses Levenshtein Distance.
     */
    public String getBestMatch(String typo) {
        if (typo == null || typo.length() < 3) return null;
        String bestWord = null;
        int minDistance = Integer.MAX_VALUE;
        String target = typo.toLowerCase();

        for (String dictWord : userDictionary) {
            int distance = calculateLevenshteinDistance(target, dictWord.toLowerCase());
            
            // Threshold: Distance must be small (1 or 2 edits)
            // And word length difference shouldn't be huge
            if (distance < minDistance && distance <= 2 && Math.abs(dictWord.length() - target.length()) <= 2) {
                minDistance = distance;
                bestWord = dictWord;
            }
        }
        
        // Only return if we found a very close match
        if (minDistance == 0) return null; // Exact match, no correction needed
        return bestWord;
    }

    // Standard Levenshtein Algorithm
    private int calculateLevenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= s2.length(); j++) dp[0][j] = j;

        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[s1.length()][s2.length()];
    }

    // --- Persistence for Bigrams ---
    
    private void saveBigrams() {
        // Simple serialization: key:val1,val2|key2:val1...
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : bigramMap.entrySet()) {
            sb.append(entry.getKey()).append(":");
            sb.append(TextUtils.join(",", entry.getValue()));
            sb.append("|");
        }
        prefs.edit().putString(KEY_BIGRAMS, sb.toString()).apply();
    }

    private void loadBigrams() {
        String raw = prefs.getString(KEY_BIGRAMS, "");
        if (!raw.isEmpty()) {
            String[] entries = raw.split("\\|");
            for (String entry : entries) {
                String[] parts = entry.split(":");
                if (parts.length == 2) {
                    String key = parts[0];
                    String[] values = parts[1].split(",");
                    bigramMap.put(key, new ArrayList<>(Arrays.asList(values)));
                }
            }
        }
    }
}