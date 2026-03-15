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

/**
 * Canon LV-WX300 Projector IR Remote — Power ON/OFF
 *
 * CORRECT IR CODE SOURCE: IRDB (probonopd/irdb on GitHub)
 * Canon Video Projector: device=129 (0x81), subdevice=6 (0x06)
 * Protocol: NEC Extended (16-bit address, 8-bit command)
 *
 * NEC Extended 32-bit frame:
 *   address_low(8) + address_high(8) + command(8) + ~command(8)
 *   All sent LSB first.
 */
public class MainActivity extends Activity {

    private ConsumerIrManager irManager;
    private TextView statusText;
    private TextView codeInfoText;
    private Button powerButton;
    private int currentCodeIndex = 0;
    private volatile boolean testingAll = false;

    private static final String PREFS_NAME = "CanonRemotePrefs";
    private static final String KEY_CODE_INDEX = "selectedCodeIndex";
    private static final int CARRIER_FREQ = 38000;

    private static final int CANON_ADDR_LOW  = 0x81;
    private static final int CANON_ADDR_HIGH = 0x06;

    private static final int[] POWER_COMMANDS = {
        0, 1, 2, 5, 8, 9, 10, 12, 15, 16,
        18, 20, 24, 32, 48, 64, 80, 96, 128, 160, 192, 255,
    };

    private static final String[] CODE_NAMES = {
        "Canon IRDB func:0 (LIKELY)",
        "Canon IRDB func:1 (LIKELY)",
        "Canon IRDB func:2",
        "Canon IRDB func:5 (LV-X1 power)",
        "Canon IRDB func:8",
        "Canon IRDB func:9",
        "Canon IRDB func:10",
        "Canon IRDB func:12",
        "Canon IRDB func:15",
        "Canon IRDB func:16",
        "Canon IRDB func:18",
        "Canon IRDB func:20",
        "Canon IRDB func:24",
        "Canon IRDB func:32",
        "Canon IRDB func:48",
        "Canon IRDB func:64",
        "Canon IRDB func:80",
        "Canon IRDB func:96",
        "Canon IRDB func:128",
        "Canon IRDB func:160",
        "Canon IRDB func:192",
        "Canon IRDB func:255",
    };

    private int[] generateNecExtendedPattern(int addrLow, int addrHigh, int command) {
        int inverseCommand = (~command) & 0xFF;
        int[] pattern = new int[68];
        int idx = 0;
        pattern[idx++] = 9000;
        pattern[idx++] = 4500;
        int[] bytes = {addrLow, addrHigh, command, inverseCommand};
        for (int b : bytes) {
            for (int bit = 0; bit < 8; bit++) {
                pattern[idx++] = 560;
                if ((b & (1 << bit)) != 0) {
                    pattern[idx++] = 1690;
                } else {
                    pattern[idx++] = 560;
                }
            }
        }
        pattern[idx++] = 560;
        pattern[idx++] = 42000;
        return pattern;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        irManager = (ConsumerIrManager) getSystemService(Context.CONSUMER_IR_SERVICE);
        powerButton = findViewById(R.id.powerButton);
        statusText = findViewById(R.id.statusText);
        codeInfoText = findViewById(R.id.codeInfoText);
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        currentCodeIndex = prefs.getInt(KEY_CODE_INDEX, 0);
        if (currentCodeIndex >= POWER_COMMANDS.length) currentCodeIndex = 0;
        updateCodeDisplay();
        if (irManager == null || !irManager.hasIrEmitter()) {
            statusText.setText("NO IR BLASTER FOUND!");
            powerButton.setEnabled(false);
            Toast.makeText(this, "No IR blaster on this device!", Toast.LENGTH_LONG).show();
            return;
        }
        statusText.setText("IR Blaster: READY");
        powerButton.setOnClickListener(v -> sendPowerSignal());
        powerButton.setOnLongClickListener(v -> { showCodeSelector(); return true; });
    }

    private void sendPowerSignal() {
        try {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) {
                vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE));
            }
            powerButton.setAlpha(0.5f);
            statusText.setText("SENDING...");
            int cmd = POWER_COMMANDS[currentCodeIndex];
            int[] pattern = generateNecExtendedPattern(CANON_ADDR_LOW, CANON_ADDR_HIGH, cmd);
            for (int i = 0; i < 3; i++) {
                irManager.transmit(CARRIER_FREQ, pattern);
                try { Thread.sleep(80); } catch (InterruptedException e) { }
            }
            statusText.setText("SENT! Code " + (currentCodeIndex + 1) + "/" + POWER_COMMANDS.length
                    + " [addr=0x8106 cmd=" + cmd + "]");
            powerButton.setAlpha(1.0f);
        } catch (Exception e) {
            statusText.setText("ERROR: " + e.getMessage());
            powerButton.setAlpha(1.0f);
        }
    }

    private void showCodeSelector() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Canon Power Code\n(IRDB addr: 0x81,0x06)");
        builder.setSingleChoiceItems(CODE_NAMES, currentCodeIndex, (dialog, which) -> {
            currentCodeIndex = which;
            SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
            editor.putInt(KEY_CODE_INDEX, currentCodeIndex);
            editor.apply();
            updateCodeDisplay();
            dialog.dismiss();
            sendPowerSignal();
        });
        builder.setNegativeButton("Cancel", null);
        builder.setNeutralButton("TEST ALL", (dialog, which) -> testAllCodes());
        builder.show();
    }

    private void testAllCodes() {
        if (testingAll) { testingAll = false; return; }
        testingAll = true;
        new Thread(() -> {
            for (int i = 0; i < POWER_COMMANDS.length && testingAll; i++) {
                final int index = i;
                final int cmd = POWER_COMMANDS[i];
                runOnUiThread(() -> statusText.setText(
                        "TESTING " + (index + 1) + "/" + POWER_COMMANDS.length
                        + "\n" + CODE_NAMES[index]
                        + "\nNEC Ext: addr=0x81,0x06 cmd=" + cmd
                        + "\n\nTap button to STOP"));
                try {
                    int[] pattern = generateNecExtendedPattern(CANON_ADDR_LOW, CANON_ADDR_HIGH, cmd);
                    for (int j = 0; j < 3; j++) {
                        irManager.transmit(CARRIER_FREQ, pattern);
                        Thread.sleep(80);
                    }
                    Thread.sleep(2500);
                } catch (InterruptedException e) { break; }
            }
            testingAll = false;
            runOnUiThread(() -> statusText.setText("Test complete!\nLong-press to select working code."));
        }).start();
    }

    private void updateCodeDisplay() {
        int cmd = POWER_COMMANDS[currentCodeIndex];
        codeInfoText.setText("Code " + (currentCodeIndex + 1) + "/" + POWER_COMMANDS.length
                + ": " + CODE_NAMES[currentCodeIndex]
                + "\nNEC Extended: addr=0x81,0x06 cmd=" + cmd
                + "\n\nTap = Send  |  Long-press = Change code");
    }
}
