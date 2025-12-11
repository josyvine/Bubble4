package com.app.bubble;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages the In-Keyboard Translation Interface.
 * Handles Language Selection, UI updates, and API calls.
 */
public class TranslationUiManager {

    private Context context;
    private View rootView;
    private TranslationListener listener;
    
    // UI Elements
    private Spinner spinnerSource;
    private Spinner spinnerTarget;
    private TextView btnSwap;
    private TextView inputPreview;
    private ImageButton btnClose;

    // Logic Variables
    private String sourceLangCode = "en"; // Default English
    private String targetLangCode = "es"; // Default Spanish
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Handler handler = new Handler(Looper.getMainLooper());

    public interface TranslationListener {
        void onTranslationResult(String translatedText);
        void onCloseTranslation();
    }

    public TranslationUiManager(Context context, View rootView, TranslationListener listener) {
        this.context = context;
        this.rootView = rootView;
        this.listener = listener;
        setupViews();
    }

    private void setupViews() {
        spinnerSource = rootView.findViewById(R.id.spinner_source_lang);
        spinnerTarget = rootView.findViewById(R.id.spinner_target_lang);
        btnSwap = rootView.findViewById(R.id.btn_swap_lang);
        inputPreview = rootView.findViewById(R.id.translation_input_preview);
        btnClose = rootView.findViewById(R.id.btn_close_translate);

        // 1. Setup Spinners with All Languages
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            context, 
            android.R.layout.simple_spinner_item, 
            LanguageUtils.LANGUAGE_NAMES
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        
        spinnerSource.setAdapter(adapter);
        spinnerTarget.setAdapter(adapter);

        // 2. Set Default Selections (English -> Spanish)
        spinnerSource.setSelection(LanguageUtils.getIndexForCode("en"));
        spinnerTarget.setSelection(LanguageUtils.getIndexForCode("es"));

        // 3. Spinner Listeners
        spinnerSource.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                sourceLangCode = LanguageUtils.getCode(position);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerTarget.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                targetLangCode = LanguageUtils.getCode(position);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // 4. Swap Button Logic
        btnSwap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int srcIndex = spinnerSource.getSelectedItemPosition();
                int tgtIndex = spinnerTarget.getSelectedItemPosition();
                
                spinnerSource.setSelection(tgtIndex);
                spinnerTarget.setSelection(srcIndex);
                // Variables update automatically via onItemSelected listeners
            }
        });

        // 5. Close Button
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onCloseTranslation();
            }
        });
    }

    /**
     * Called by BubbleKeyboardService when user types a character.
     * Updates the white preview box.
     */
    public void updateInputPreview(String text) {
        if (inputPreview != null) {
            if (text == null || text.isEmpty()) {
                inputPreview.setText("");
                inputPreview.setHint("Type here to translate...");
            } else {
                inputPreview.setText(text);
            }
        }
    }

    /**
     * Called by BubbleKeyboardService when user presses Enter/Done.
     * Triggers the API call.
     */
    public void performTranslation(final String textToTranslate) {
        if (textToTranslate == null || textToTranslate.trim().isEmpty()) return;

        // Visual feedback
        inputPreview.setHint("Translating...");

        executor.execute(new Runnable() {
            @Override
            public void run() {
                // Use the existing TranslateApi
                final String result = TranslateApi.translate(sourceLangCode, targetLangCode, textToTranslate);
                
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (result != null) {
                            // Send back to Service to type it out
                            listener.onTranslationResult(result);
                            // Clear preview for next sentence
                            updateInputPreview("");
                        } else {
                            Toast.makeText(context, "Translation Error", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });
    }
}