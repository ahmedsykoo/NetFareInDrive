package com.ahmed.netfare;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the on-screen text of the InDrive driver app via AccessibilityService,
 * extracts the trip fare and distance, subtracts the driver's commission
 * percentage, and shows the resulting net EGP/km in a floating overlay badge.
 *
 * NOTE: InDrive does not expose a public API, so this relies on scanning
 * visible text on screen with regex patterns. If InDrive changes its UI
 * wording/format, the FARE_PATTERN / DISTANCE_PATTERN below may need tweaking.
 * Send Ahmed's Claude a sample of the exact text InDrive shows (e.g. "125.50 EGP"
 * or "3.2 km") to refine the patterns if detection stops working.
 */
public class FareAccessibilityService extends AccessibilityService {

    private static final String TAG = "NetFareService";

    // Package name InDrive's driver app is commonly published under.
    // Kept as a soft filter only - if it doesn't match, we still scan the
    // foreground app's text so this keeps working even if the package differs.
    private static final String INDRIVE_PACKAGE_HINT = "sinet.startup.inDriver";

    // Matches numbers followed by EGP/E£/جنيه/ج.م
    private static final Pattern FARE_PATTERN = Pattern.compile(
            "(\\d{1,6}(?:[.,]\\d{1,2})?)\\s*(EGP|E£|LE|ج\\.م|جنيه)",
            Pattern.CASE_INSENSITIVE);

    // Matches numbers followed by km/كم
    private static final Pattern DISTANCE_PATTERN = Pattern.compile(
            "(\\d{1,4}(?:[.,]\\d{1,2})?)\\s*(km|كم)",
            Pattern.CASE_INSENSITIVE);

    private WindowManager windowManager;
    private View overlayView;
    private TextView badgeText;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        showOverlay();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        List<String> allText = new ArrayList<>();
        collectText(root, allText);

        Double fare = findFirstMatch(allText, FARE_PATTERN);
        Double distance = findFirstMatch(allText, DISTANCE_PATTERN);

        if (fare != null && distance != null && distance > 0) {
            float commission = getCommissionPercent();
            double net = fare * (1 - (commission / 100.0));
            double netPerKm = net / distance;
            updateBadge(String.format(Locale.US, "%.2f ج.م/كم", netPerKm));
        }
    }

    private void collectText(AccessibilityNodeInfo node, List<String> out) {
        if (node == null) return;
        CharSequence text = node.getText();
        if (text != null && text.length() > 0) {
            out.add(text.toString());
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collectText(child, out);
                child.recycle();
            }
        }
    }

    private Double findFirstMatch(List<String> textList, Pattern pattern) {
        for (String s : textList) {
            Matcher m = pattern.matcher(s);
            if (m.find()) {
                String num = m.group(1).replace(",", ".");
                try {
                    return Double.parseDouble(num);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return null;
    }

    private float getCommissionPercent() {
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getFloat(MainActivity.KEY_COMMISSION, 15f);
    }

    private void showOverlay() {
        if (overlayView != null) return;

        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_badge, null);
        badgeText = overlayView.findViewById(R.id.badgeText);

        int overlayType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 20;
        params.y = 150;

        makeDraggable(overlayView, params);

        try {
            windowManager.addView(overlayView, params);
        } catch (Exception e) {
            Log.e(TAG, "Could not add overlay - check SYSTEM_ALERT_WINDOW permission", e);
        }
    }

    private void makeDraggable(View view, WindowManager.LayoutParams params) {
        view.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case android.view.MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(view, params);
                        return true;
                }
                return false;
            }
        });
    }

    private void updateBadge(String text) {
        if (badgeText != null) {
            badgeText.post(() -> badgeText.setText(text));
        }
    }

    @Override
    public void onInterrupt() {
        // no-op
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception ignored) {
            }
        }
    }
}
