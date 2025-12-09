package com.app.bubble;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

// ML Kit Imports
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.nio.ByteBuffer;

public class FloatingTranslatorService extends Service {

    private static FloatingTranslatorService sInstance;

    private WindowManager windowManager;
    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;

    private int screenWidth, screenHeight, screenDensity;
    private Handler handler = new Handler(Looper.getMainLooper());

    // --- THE ACCUMULATOR ---
    // This holds the text from Page 1 + Page 2 + Page 3...
    private StringBuilder globalTextAccumulator = new StringBuilder();

    // Floating Bubble Views (The small icon that opens the overlay)
    private View floatingBubbleView;
    private WindowManager.LayoutParams bubbleParams;
    
    // Drag-to-Close Target
    private View closeTargetView;
    private WindowManager.LayoutParams closeTargetParams;
    private boolean isBubbleOverCloseTarget = false;
    private int closeRegionHeight;

    public static FloatingTranslatorService getInstance() {
        return sInstance;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;

        // 1. Start Foreground Service (Required for Screen Capture)
        startMyForeground();

        // 2. Initialize Managers
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        // 3. Get Screen Metrics
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;

        // 4. Show the UI
        showFloatingBubble();
        setupCloseTarget();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            // A. Handle MediaProjection Permission Result
            if (intent.hasExtra("resultCode")) {
                int resultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED);
                Intent data = intent.getParcelableExtra("data");
                if (mediaProjectionManager != null && resultCode == Activity.RESULT_OK && data != null) {
                    mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
                    
                    // Handle unexpected stop
                    mediaProjection.registerCallback(new MediaProjection.Callback() {
                        @Override
                        public void onStop() {
                            super.onStop();
                            mediaProjection = null;
                            if (imageReader != null) imageReader.close();
                        }
                    }, handler);
                }
            }

            // B. Handle Commands from Overlay (TwoLineOverlayService)
            String action = intent.getAction();
            
            if ("ACTION_ADD_PAGE".equals(action)) {
                // The user clicked [ADD PAGE]. We must capture, crop, and save.
                Rect cropRect = intent.getParcelableExtra("RECT");
                if (cropRect != null) {
                    takeScreenshotAndExtract(cropRect);
                }
            } 
            else if ("ACTION_DONE".equals(action)) {
                // The user clicked [DONE]. We must finish up.
                finishAndShowResult();
            }
        }
        return START_STICKY;
    }

    // =========================================================
    // CORE LOGIC: CAPTURE -> CROP -> EXTRACT
    // =========================================================

    private void takeScreenshotAndExtract(final Rect cropRect) {
        if (mediaProjection == null) {
            Toast.makeText(this, "Screen Capture Permission missing. Restart app.", Toast.LENGTH_SHORT).show();
            // Try to re-request permission via MainActivity if needed
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra("AUTO_REQUEST_PERMISSION", true);
            startActivity(intent);
            return;
        }

        // 1. Setup ImageReader
        if (imageReader != null) imageReader.close();
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);

        // 2. Create Virtual Display (Take Screenshot)
        virtualDisplay = mediaProjection.createVirtualDisplay("ScreenCapture",
                screenWidth, screenHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, null);

        // 3. Listen for the image
        imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Image image = null;
                try {
                    image = reader.acquireLatestImage();
                    if (image != null) {
                        // 4. Convert Image to Bitmap
                        Image.Plane[] planes = image.getPlanes();
                        ByteBuffer buffer = planes[0].getBuffer();
                        int pixelStride = planes[0].getPixelStride();
                        int rowStride = planes[0].getRowStride();
                        int rowPadding = rowStride - pixelStride * screenWidth;

                        Bitmap fullBitmap = Bitmap.createBitmap(
                                screenWidth + rowPadding / pixelStride, 
                                screenHeight, 
                                Bitmap.Config.ARGB_8888
                        );
                        fullBitmap.copyPixelsFromBuffer(buffer);

                        // 5. CROP THE IMAGE
                        // Use the coordinates passed from the Overlay
                        int safeTop = Math.max(0, cropRect.top);
                        int safeHeight = Math.min(cropRect.height(), fullBitmap.getHeight() - safeTop);
                        
                        // Safety check: Ensure we have a valid area
                        if (safeHeight > 0 && screenWidth > 0) {
                            Bitmap cropped = Bitmap.createBitmap(fullBitmap, 0, safeTop, screenWidth, safeHeight);
                            
                            // Recycle the full bitmap immediately to free RAM
                            fullBitmap.recycle();
                            
                            // 6. Process OCR on the cropped slice
                            performOcrOnSlice(cropped);
                        } else {
                            fullBitmap.recycle();
                        }
                        
                        // Stop capturing immediately (Single Shot)
                        stopCapture();
                        image.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    stopCapture();
                }
            }
        }, handler);
    }

    private void stopCapture() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }

    private void performOcrOnSlice(Bitmap bitmap) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        recognizer.process(image)
            .addOnSuccessListener(new OnSuccessListener<Text>() {
                @Override
                public void onSuccess(Text visionText) {
                    StringBuilder pageText = new StringBuilder();

                    // --- GARBAGE FILTER ---
                    // Remove words that belong to our own UI
                    for (Text.TextBlock block : visionText.getTextBlocks()) {
                        String text = block.getText();
                        if (text.contains("ADD PAGE") || 
                            text.contains("DONE") || 
                            text.contains("Drag Lines") ||
                            text.contains("Bubble")) {
                            continue; 
                        }
                        pageText.append(text).append("\n");
                    }

                    // Append to the Global Accumulator
                    if (pageText.length() > 0) {
                        globalTextAccumulator.append(pageText).append("\n\n"); // Add newlines between pages
                        Toast.makeText(FloatingTranslatorService.this, "Page Added", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(FloatingTranslatorService.this, "No text found in selection", Toast.LENGTH_SHORT).show();
                    }
                }
            })
            .addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(Exception e) {
                    Toast.makeText(FloatingTranslatorService.this, "Text Read Failed", Toast.LENGTH_SHORT).show();
                }
            });
    }

    // =========================================================
    // FINISH LOGIC
    // =========================================================

    private void finishAndShowResult() {
        String finalText = globalTextAccumulator.toString().trim();

        if (finalText.isEmpty()) {
            Toast.makeText(this, "No text captured yet!", Toast.LENGTH_SHORT).show();
            return;
        }

        // 1. Copy to Clipboard
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("Bubble Copy", finalText);
            clipboard.setPrimaryClip(clip);
        }

        // 2. Prepare Debug/Result Activity
        // We pass data via static variables because Intent extras have size limits
        DebugActivity.sFilteredText = finalText;
        DebugActivity.sRawText = "Manual Multi-Page Capture";
        DebugActivity.sErrorLog = "";
        DebugActivity.sLastBitmap = null; // No need to pass bitmap for text result
        DebugActivity.sLastRect = null;

        // 3. Clear the Accumulator for next time
        globalTextAccumulator.setLength(0);

        // 4. Launch Result Screen
        Intent intent = new Intent(this, DebugActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    // =========================================================
    // UI: FLOATING BUBBLE & NOTIFICATION
    // =========================================================

    private void startMyForeground() {
        String CHANNEL_ID = "bubble_translator_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Bubble Service", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        Notification notification = builder
                .setContentTitle("Bubble Copy is Active")
                .setContentText("Tap to open app")
                .setSmallIcon(android.R.drawable.ic_menu_search)
                .setContentIntent(pendingIntent)
                .build();

        startForeground(1337, notification);
    }

    private void showFloatingBubble() {
        floatingBubbleView = LayoutInflater.from(this).inflate(R.layout.layout_floating_bubble, null);
        int type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ? 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        
        bubbleParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = 0;
        bubbleParams.y = 100;
        
        windowManager.addView(floatingBubbleView, bubbleParams);
        
        // Touch Listener to Drag Bubble or Open Overlay
        floatingBubbleView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private long lastClickTime = 0;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = bubbleParams.x;
                        initialY = bubbleParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        lastClickTime = System.currentTimeMillis();
                        closeTargetView.setVisibility(View.VISIBLE);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        bubbleParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        bubbleParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingBubbleView, bubbleParams);
                        
                        // Check drag to X
                        if (bubbleParams.y > (screenHeight - closeRegionHeight)) {
                            closeTargetView.setScaleX(1.3f); closeTargetView.setScaleY(1.3f);
                            isBubbleOverCloseTarget = true;
                        } else {
                            closeTargetView.setScaleX(1.0f); closeTargetView.setScaleY(1.0f);
                            isBubbleOverCloseTarget = false;
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        closeTargetView.setVisibility(View.GONE);
                        if (isBubbleOverCloseTarget) {
                            stopSelf(); // Kill service
                            return true;
                        }
                        // Click Detection
                        if (System.currentTimeMillis() - lastClickTime < 200) {
                            // Open the Two-Line Overlay
                            if (floatingBubbleView != null) floatingBubbleView.setVisibility(View.GONE);
                            Intent overlayIntent = new Intent(FloatingTranslatorService.this, TwoLineOverlayService.class);
                            overlayIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startService(overlayIntent);
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void setupCloseTarget() {
        closeTargetView = LayoutInflater.from(this).inflate(R.layout.layout_close_target, null);
        int type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ? 
                   WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        
        closeTargetParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );
        closeTargetParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        closeTargetParams.y = 50;
        windowManager.addView(closeTargetView, closeTargetParams);
        closeTargetView.setVisibility(View.GONE);
        closeRegionHeight = screenHeight / 5;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sInstance = null;
        if (mediaProjection != null) mediaProjection.stop();
        if (floatingBubbleView != null) windowManager.removeView(floatingBubbleView);
        if (closeTargetView != null) windowManager.removeView(closeTargetView);
    }
}