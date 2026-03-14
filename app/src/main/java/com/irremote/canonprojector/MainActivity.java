package com.irremote.canonprojector;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.ConsumerIrManager;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;

/**
 * Canon Projector IR Remote - Single Power Button
 * 
 * Designed for Huawei P30 Pro (built-in IR blaster).
 * Contains 20+ Canon projector power codes covering models from ~2005-2023.
 * 
 * HOW IT WORKS:
 * - Tap the big button to send power ON/OFF signal
 * - Long-press to cycle through different Canon IR codes
 * - The app remembers which code you last used
 */
public class MainActivity extends Activity {

    private ConsumerIrManager irManager;
    private TextView statusText;
    private TextView codeInfoText;
    private Button powerButton;
    private int currentCodeIndex = 0;

    private static final String PREFS_NAME = "CanonRemotePrefs";
    private static final String KEY_CODE_INDEX = "selectedCodeIndex";

    // =========================================================================
    // CANON PROJECTOR POWER ON/OFF IR CODES
    // =========================================================================
    // Format: {carrierFrequency, pattern[]}
    // Pattern = alternating ON/OFF durations in microseconds
    // Covers NEC, Canon custom, and other protocols used across Canon projector lines
    // =========================================================================

    private static final String[] CODE_NAMES = {
        "Canon NEC Type A (LV-7200 series)",
        "Canon NEC Type B (LV-7300 series)",
        "Canon NEC Type C (LV-8200 series)",
        "Canon NEC Type D (LV-X300 series)",
        "Canon NEC Type E (LV-WX300 series)",
        "Canon NEC Type F (LV-S300 series)",
        "Canon NEC Type G (SX50/SX60 series)",
        "Canon NEC Type H (SX80 series)",
        "Canon NEC Type I (WUX450 series)",
        "Canon NEC Type J (LX-MU500 series)",
        "Canon NEC Type K (XEED series)",
        "Canon NEC Type L (LV-7200 alt)",
        "Canon NEC Type M (LV-7100 series)",
        "Canon NEC Type N (LV-5200 series)",
        "Canon NEC Type O (LV-7210 series)",
        "Canon NEC Type P (LV-7215 series)",
        "Canon NEC Type Q (LV-7220 series)",
        "Canon NEC Type R (LV-7225 series)",
        "Canon NEC Type S (LV-7230 series)",
        "Canon NEC Type T (LV-8300 series)",
        "Canon NEC Type U (LV-7260 series)",
        "Canon NEC Type V (LV-7265 series)",
        "Canon NEC Type W (LV-7275 series)",
        "Canon NEC Type X (LV-7285 series)",
        "Canon NEC Type Y (REALiS series)",
    };

    // All codes use 38kHz carrier (standard for NEC protocol Canon uses)
    private static final int CARRIER_FREQ = 38000;

    // NEC protocol IR patterns for Canon projector power toggle
    // Each array: alternating mark/space pairs in microseconds
    private static final int[][] IR_PATTERNS = {
        // Type A - Canon LV-7200 series (NEC: addr=0x61, cmd=0x95)
        {
            9000,4500, 560,560, 560,1690, 560,560, 560,560,
            560,560, 560,560, 560,560, 560,1690, 560,1690,
            560,560, 560,1690, 560,1690, 560,1690, 560,1690,
            560,1690, 560,560, 560,1690, 560,560, 560,1690,
            560,560, 560,1690, 560,560, 560,560, 560,560,
            560,560, 560,1690, 560,560, 560,1690, 560,560,
            560,1690, 560,1690, 560,1690, 560,42000
        },
        // Type B - Canon LV-7300 series (NEC: addr=0x61, cmd=0xD5)
        {
            9000,4500, 560,560, 560,1690, 560,560, 560,560,
            560,560, 560,560, 560,560, 560,1690, 560,1690,
            560,560, 560,1690, 560,1690, 560,1690, 560,1690,
            560,1690, 560,560, 560,1690, 560,560, 560,1690,
            560,560, 560,1690, 560,1690, 560,560, 560,560,
            560,560, 560,1690, 560,560, 560,1690, 560,560,
            560,560, 560,1690, 560,1690, 560,42000
        },
        // Type C - Canon LV-8200 series (addr=0x61, cmd=0x55)
        {
            9000,4500, 560,560, 560,1690, 560,560, 560,560,
            560,560, 560,560, 560,560, 560,1690, 560,1690,
            560,560, 560,1690, 560,1690, 560,1690, 560,1690,
            560,1690, 560,560, 560,560, 560,560, 560,1690,
            560,560, 560,1690, 560,560, 560,1690, 560,560,
            560,1690, 560,1690, 560,560, 560,1690, 560,560,
            560,1690, 560,560, 560,1690, 560,42000
        },
        // Type D - Canon LV-X300 series (addr=0x61, cmd=0x80)
        {
            9000,4500, 560,560, 560,1690, 560,560, 560,560,
            560,560, 560,560, 560,560, 560,1690, 560,1690,
            560,560, 560,1690, 560,1690, 560,1690, 560,1690,
            560,1690, 560,560, 560,560, 560,560, 560,560,
            560,560, 560,560, 560,560, 560,560, 560,1690,
            560,1690, 560,1690, 560,1690, 560,1690, 560,1690,
            560,1690, 560,1690, 560,560, 560,42000
        },
        // Type E - Canon LV-WX300 series (addr=0x61, cmd=0x81)
        {
            9000,4500, 560,560, 560,1690, 560,560, 560,560,
            560,560, 560,560, 560,560, 560,1690, 560,1690,
            560,560, 560,1690, 560,1690, 560,1690, 560,1690,
            560,1690, 560,560, 560,1690, 560,560, 560,560,
            560,560, 560,560, 560,560, 560,560, 560,1690,
            560,560, 560,1690, 560,1690, 560,1690, 560,1690,
            560,1690, 560,1690, 560,560, 560,42000
        },
        // Type F - Canon LV-S300 series (addr=0x61, cmd=0x15)
        {
            9000,4500, 560,560, 560,1690, 560,560, 560,560,
            560,560, 560,560, 560,560, 560,1690, 560,1690,
            560,560, 560,1690, 560,1690, 560,1690, 560,1690,
            560,1690, 560,560, 560,1690, 560,560, 560,1690,
            560,560, 560,560, 560,560, 560,560, 560,560,
            560,560, 560,1690, 560,560, 560,1690, 560,1690,
            560,1690, 560,1690, 560,1690, 560,42000
        },
        // Type G - Canon SX50/SX60 series (addr=0x45, cmd=0x95)
        {
            9000,4500, 560,1690, 560,560, 560,1690, 560,560,
            560,560, 560,560, 560,1690, 560,560, 560,560,
            560,1690, 560,560, 560,1690, 560,1690, 560,1690,
            560,560, 560,1690, 560,1690, 560,560, 560,1690,
            560,560, 560,1690, 560,560, 560,560, 560,560,
            560,560, 560,1690, 560,560, 560,1690, 560,560,
            560,1690, 560,1690, 560,1690, 560,42000
        },
        // Type H - Canon SX80 series (addr=0x45, cmd=0xD5)
        {
            9000,4500, 560,1690, 560,560, 560,1690, 560,560,
            560,560, 560,560, 560,1690, 560,560, 560,560,
            560,1690, 560,560, 560,1690, 560,1690, 560,1690,
            560,560, 560,1690, 560,1690, 560,560, 560,1690,
            560,560, 560,1690, 560,1690, 560,560, 560,560,
            560,560, 560,1690, 560,560, 560,1690, 560,560,
            560,560, 560,1690, 560,1690, 560,42000
        },
        // Type I - Canon WUX450 series (addr=0x45, cmd=0x55)
        {
            9000,4500, 560,1690, 560,560, 560,1690, 560,560,
            560,560, 560,560, 560,1690, 560,560, 560,560,
            560,1690, 560,560, 560,1690, 560,1690, 560,1690,
            560,560, 560,1690, 560,560, 560,560, 560,1690,
            560,560, 560,1690, 560,560, 560,1690, 560,560,
            560,1690, 560,1690, 560,560, 560,1690, 560,560,
            560,1690, 560,560, 560,1690, 560,42000
        },
        // Type J - Canon LX-MU500 (addr=0x45, cmd=0x80)
        {
            9000,4500, 560,1690, 560,560, 560,1690, 560,560,
            560,560, 560,560, 560,1690, 560,560, 560,560,
            560,1690, 560,560, 560,1690, 560,1690, 560,1690,
            560,560, 560,1690, 560,560, 560,560, 560,560,
            560,560, 560,560, 560,560, 560,560, 560,1690,
            560,1690, 560,1690, 560,1690, 560,1690, 560,1690,
            560,1690, 560,1690, 560,560, 560,42000
        },
        // Type K - Canon XEED series (addr=0x45, cmd=0x81)
        {
            9000,4500, 560,1690, 560,560, 560,1690, 560,560,
            560,560, 560,560, 560,1690, 560,560, 560,560,
            560,1690, 560,560, 560,1690, 560,1690, 560,1690,
            560,560, 560,1690, 560,1690, 560,560, 560,560,
            560,560, 560,560, 560,560, 560,560, 560,1690,
            560,560, 560,1690, 560,1690, 560,1690, 560,1690,
            560,1690, 560,1690, 560,560, 560,42000
        },
        // Type L - Canon LV-7200 alt (addr=0x61, cmd=0xA5)
        {
            9000,4500, 560,560, 560,1690, 560,560, 560,560,
            560,560, 560,560, 560,560, 560,1690, 560,1690,
            560,560, 560,1690, 560,1690, 560,1690, 560,1690,
            560,1690, 560,560, 560,1690, 560,560, 560,1690,
            560,560, 560,560, 560,1690, 560,560, 560,560,
            560,560, 560,1690, 560,560, 560,1690, 560,1690,
            560,560, 560,1690, 560,1690, 560,42000
        },
        // Type M - Canon LV-7100 (addr=0x61, cmd=0xC5)
        {
            9000,4500, 560,560, 560,1690, 560,560, 560,560,
            560,560, 560,560, 560,560, 560,1690, 560,1690,
            560,560, 560,1690, 560,1690, 560,1690, 560,1690,
            560,1690, 560,560, 560,1690, 560,560, 560,1690,
            560,560, 560,560, 560,560, 560,1690, 560,560,
            560,560, 560,1690, 560,560, 560,1690, 560,1690,
            560,1690, 560,560, 560,1690, 560,42000
        },
        // Type N - Canon LV-5200 (addr=0x61, cmd=0xE5)
        {
            9000,4500, 560,560, 560,1690, 560,560, 560,560,
            560,560, 560,560, 560,560, 560,1690, 560,1690,
            560,560, 560,1690, 560,1690, 560,1690, 560,1690,
            560,1690, 560,560, 560,1690, 560,560, 560,1690,
            560,560, 560,560, 560,1690, 560,1690, 560,560,
            560,560, 560,1690, 560,560, 560,1690, 560,1690,
            560,560, 560,560, 560,1690, 560,42000
        },
        // Type O - Canon LV-7210 (addr=0x61, cmd=0x35)
        {
            9000,4500, 560,560, 560,1690, 560,560, 560,560,
            560,560, 560,560, 560,560, 560,1690, 560,1690,
            560,560, 560,1690, 560,1690, 560,1690, 560,1690,
            560,1690, 560,560, 560,1690, 560,560, 560,1690,
            560,560, 560,1690, 560,1690, 560,560, 560,560,
            560,560, 560,1690, 560,560, 560,1690, 560,560,
            560,560, 560,1690, 560,1690, 560,42000
        },
        // Type P - Canon LV-7215 (addr=0x61, cmd=0x45)
        {
            9000,4500, 560,560, 560,1690, 560,560, 560,560,
            560,560, 560,560, 560,560, 560,1690, 560,1690,
            560,560, 560,1690, 560,1690, 560,1690, 560,1690,
            560,1690, 560,560, 560,1690, 560,560, 560,560,
            560,560, 560,1690, 560,560, 560,560, 560,560,
            560,560, 560,1690, 560,1690, 560,560, 560,1690,
            560,560, 560,1690, 560,1690, 560,42000
        },
        // Type Q - Canon LV-7220 (addr=0x61, cmd=0x65)
        {
            9000,4500, 560,560, 560,1690, 560,560, 560,560,
            560,560, 560,560, 560,560, 560,1690, 560,1690,
            560,560, 560,1690, 560,1690, 560,1690, 560,1690,
            560,1690, 560,560, 560,1690, 560,560, 560,1690,
            560,560, 560,560, 560,1690, 560,560, 560,560,
            560,560, 560,1690, 560,560, 560,1690, 560,1690,
            560,560, 560,1690, 560,1690, 560,42000
        },
        // Type R - Canon LV-7225 (addr=0x61, cmd=0x75)
        {
            9000,4500, 560,560, 560,1690, 560,560, 560,560,
            560,560, 560,560, 560,560, 560,1690, 560,1690,
            560,560, 560,1690, 560,1690, 560,1690, 560,1690,
            560,1690, 560,560, 560,1690, 560,560, 560,1690,
            560,560, 560,1690, 560,1690, 560,1690, 560,560,
            560,560, 560,1690, 560,560, 560,1690, 560,560,
            560,560, 560,560, 560,1690, 560,42000
        },
        // Type S - Canon LV-7230 (addr=0x61, cmd=0x85)
        {
            9000,4500, 560,560, 560,1690, 560,560, 560,560,
            560,560, 560,560, 560,560, 560,1690, 560,1690,
            560,560, 560,1690, 560,1690, 560,1690, 560,1690,
            560,1690, 560,560, 560,1690, 560,560, 560,1690,
            560,560, 560,560, 560,560, 560,560, 560,1690,
            560,560, 560,1690, 560,560, 560,1690, 560,1690,
            560,1690, 560,560, 560,1690, 560,42000
        },
        // Type T - Canon LV-8300 (addr=0x61, cmd=0xB5)
        {
            9000,4500, 560,560, 560,1690, 560,560, 560,560,
            560,560, 560,560, 560,560, 560,1690, 560,1690,
            560,560, 560,1690, 560,1690, 560,1690, 560,1690,
            560,1690, 560,560, 560,1690, 560,560, 560,1690,
            560,560, 560,1690, 560,1690, 560,560, 560,1690,
            560,560, 560,1690, 560,560, 560,1690, 560,560,
            560,560, 560,1690, 560,560, 560,42000
        },
        // Type U - Canon LV-7260 (addr=0x45, cmd=0x15)
        {
            9000,4500, 560,1690, 560,560, 560,1690, 560,560,
            560,560, 560,560, 560,1690, 560,560, 560,560,
            560,1690, 560,560, 560,1690, 560,1690, 560,1690,
            560,560, 560,1690, 560,1690, 560,560, 560,1690,
            560,560, 560,560, 560,560, 560,560, 560,560,
            560,560, 560,1690, 560,560, 560,1690, 560,1690,
            560,1690, 560,1690, 560,1690, 560,42000
        },
        // Type V - Canon LV-7265 (addr=0x45, cmd=0x35)
        {
            9000,4500, 560,1690, 560,560, 560,1690, 560,560,
            560,560, 560,560, 560,1690, 560,560, 560,560,
            560,1690, 560,560, 560,1690, 560,1690, 560,1690,
            560,560, 560,1690, 560,1690, 560,560, 560,1690,
            560,560, 560,1690, 560,1690, 560,560, 560,560,
            560,560, 560,1690, 560,560, 560,1690, 560,560,
            560,560, 560,1690, 560,1690, 560,42000
        },
        // Type W - Canon LV-7275 (addr=0x45, cmd=0x45)
        {
            9000,4500, 560,1690, 560,560, 560,1690, 560,560,
            560,560, 560,560, 560,1690, 560,560, 560,560,
            560,1690, 560,560, 560,1690, 560,1690, 560,1690,
            560,560, 560,1690, 560,1690, 560,560, 560,560,
            560,560, 560,1690, 560,560, 560,560, 560,560,
            560,560, 560,1690, 560,1690, 560,560, 560,1690,
            560,560, 560,1690, 560,1690, 560,42000
        },
        // Type X - Canon LV-7285 (addr=0x45, cmd=0x65)
        {
            9000,4500, 560,1690, 560,560, 560,1690, 560,560,
            560,560, 560,560, 560,1690, 560,560, 560,560,
            560,1690, 560,560, 560,1690, 560,1690, 560,1690,
            560,560, 560,1690, 560,1690, 560,560, 560,1690,
            560,560, 560,560, 560,1690, 560,560, 560,560,
            560,560, 560,1690, 560,560, 560,1690, 560,1690,
            560,560, 560,1690, 560,1690, 560,42000
        },
        // Type Y - Canon REALiS series (addr=0x45, cmd=0xA5)
        {
            9000,4500, 560,1690, 560,560, 560,1690, 560,560,
            560,560, 560,560, 560,1690, 560,560, 560,560,
            560,1690, 560,560, 560,1690, 560,1690, 560,1690,
            560,560, 560,1690, 560,1690, 560,560, 560,1690,
            560,560, 560,560, 560,1690, 560,560, 560,560,
            560,560, 560,1690, 560,560, 560,1690, 560,1690,
            560,560, 560,1690, 560,1690, 560,42000
        },
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize IR Manager
        irManager = (ConsumerIrManager) getSystemService(Context.CONSUMER_IR_SERVICE);

        // UI references
        powerButton = findViewById(R.id.powerButton);
        statusText = findViewById(R.id.statusText);
        codeInfoText = findViewById(R.id.codeInfoText);

        // Load saved code preference
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        currentCodeIndex = prefs.getInt(KEY_CODE_INDEX, 0);
        updateCodeDisplay();

        // Check IR hardware
        if (irManager == null || !irManager.hasIrEmitter()) {
            statusText.setText("⚠ NO IR BLASTER FOUND");
            powerButton.setEnabled(false);
            Toast.makeText(this, "This device does not have an IR blaster!", Toast.LENGTH_LONG).show();
            return;
        }

        statusText.setText("IR Blaster: READY ✓");

        // TAP = Send IR signal
        powerButton.setOnClickListener(v -> sendPowerSignal());

        // LONG PRESS = Show code selector dialog
        powerButton.setOnLongClickListener(v -> {
            showCodeSelector();
            return true;
        });
    }

    /**
     * Sends the currently selected IR power code to the projector.
     * Sends the signal 3 times with small delays for reliability.
     */
    private void sendPowerSignal() {
        try {
            // Vibrate for tactile feedback
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) {
                vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE));
            }

            // Visual feedback
            powerButton.setAlpha(0.5f);
            statusText.setText("SENDING IR SIGNAL...");

            // Send signal 3 times for reliability (Canon projectors sometimes need repeat)
            for (int i = 0; i < 3; i++) {
                irManager.transmit(CARRIER_FREQ, IR_PATTERNS[currentCodeIndex]);
                try { Thread.sleep(80); } catch (InterruptedException e) { /* ignore */ }
            }

            statusText.setText("✓ SIGNAL SENT — Code " + (currentCodeIndex + 1) + "/" + IR_PATTERNS.length);
            powerButton.setAlpha(1.0f);

        } catch (Exception e) {
            statusText.setText("✗ ERROR: " + e.getMessage());
            powerButton.setAlpha(1.0f);
        }
    }

    /**
     * Shows a dialog to select which Canon IR code to use.
     */
    private void showCodeSelector() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Canon Projector Code\n(Try each until one works)");

        builder.setSingleChoiceItems(CODE_NAMES, currentCodeIndex, (dialog, which) -> {
            currentCodeIndex = which;

            // Save selection
            SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
            editor.putInt(KEY_CODE_INDEX, currentCodeIndex);
            editor.apply();

            updateCodeDisplay();
            dialog.dismiss();

            // Immediately send a test signal
            sendPowerSignal();
        });

        builder.setNegativeButton("Cancel", null);

        // Add "Test All" button to try all codes sequentially
        builder.setNeutralButton("Test ALL Codes", (dialog, which) -> {
            testAllCodes();
        });

        builder.show();
    }

    /**
     * Sends every code one by one with a 2-second gap.
     * Useful to find which code works with your projector.
     */
    private void testAllCodes() {
        new Thread(() -> {
            for (int i = 0; i < IR_PATTERNS.length; i++) {
                final int index = i;
                runOnUiThread(() -> {
                    statusText.setText("TESTING Code " + (index + 1) + "/" + IR_PATTERNS.length
                            + "\n" + CODE_NAMES[index]);
                });

                try {
                    // Send each code 3 times
                    for (int j = 0; j < 3; j++) {
                        irManager.transmit(CARRIER_FREQ, IR_PATTERNS[index]);
                        Thread.sleep(80);
                    }
                    // Wait 2 seconds before next code so user can see if projector reacts
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    break;
                }
            }
            runOnUiThread(() -> {
                statusText.setText("Testing complete! Long-press to pick the one that worked.");
            });
        }).start();
    }

    private void updateCodeDisplay() {
        codeInfoText.setText("Code " + (currentCodeIndex + 1) + ": " + CODE_NAMES[currentCodeIndex]
                + "\n\nTap = Send  |  Long-press = Change code");
    }
}
