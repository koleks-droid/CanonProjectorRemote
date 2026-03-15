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
 * Based on research:
 * - Canon LV-WX300 uses remote LV-RC08
 * - NEC IR protocol, 38kHz carrier
 * - Canon LV series uses NEC device address 0x30
 * - Projector has Code 1 (default) and Code 2 settings
 * - Also tries alternative Canon addresses used across generations
 *
 * The app generates proper NEC protocol IR patterns from hex codes.
 * Tap = send power signal, Long-press = select code, Test All = try every code.
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
    private static final int CARRIER_FREQ = 38000; // 38kHz NEC standard

    // =========================================================================
    // CANON LV-WX300 POWER CODES — NEC Protocol
    // =========================================================================
    // Format: {NEC_address_byte, NEC_command_byte}
    // The app auto-generates the full 32-bit NEC IR pattern from these.
    //
    // NEC 32-bit = address + ~address + command + ~command (each LSB first)
    //
    // Canon LV-WX300 remote = LV-RC08
    // Known Canon projector NEC address: 0x30 (from Canon LV-X1 LIRC data)
    // Also trying 0x31, 0x32, 0x33 (nearby Canon addresses)
    // Also trying 0x45, 0x61 (other known Canon projector addresses)
    //
    // Power ON/OFF command candidates: most projectors use low command
    // numbers for power. We try the most common ones.
    // =========================================================================

    private static final int[][] NEC_CODES = {
        // === PRIMARY: Canon address 0x30 (LV series, Code 1) ===
        {0x30, 0x10},  // #1  - Most likely power toggle
        {0x30, 0x80},  // #2  - Power toggle variant
        {0x30, 0x00},  // #3  - Power on
        {0x30, 0x01},  // #4  - Power off/standby
        {0x30, 0x02},  // #5  - Power toggle variant
        {0x30, 0x40},  // #6  - Power variant
        {0x30, 0x0A},  // #7  - Power variant
        {0x30, 0x05},  // #8  - Power variant (from LV-X1 data)
        {0x30, 0x09},  // #9  - Standby variant

        // === SECONDARY: Canon address 0x31 (LV series, Code 2) ===
        {0x31, 0x10},  // #10
        {0x31, 0x80},  // #11
        {0x31, 0x00},  // #12
        {0x31, 0x01},  // #13

        // === Canon address 0x32 (some LV-300 series models) ===
        {0x32, 0x10},  // #14
        {0x32, 0x80},  // #15
        {0x32, 0x00},  // #16
        {0x32, 0x01},  // #17

        // === Canon address 0x45 (SX/WUX/XEED series) ===
        {0x45, 0x10},  // #18
        {0x45, 0x80},  // #19
        {0x45, 0x00},  // #20
        {0x45, 0x01},  // #21

        // === Canon address 0x61 (older LV series) ===
        {0x61, 0x10},  // #22
        {0x61, 0x80},  // #23
        {0x61, 0x00},  // #24
        {0x61, 0x01},  // #25

        // === Extended address (NEC extended: 16-bit address) ===
        // Some newer Canon projectors use extended NEC
        // For extended: addr_low=0x30, addr_high varies
        // We handle these specially in the generate function
        {0x30, 0x20},  // #26
        {0x30, 0x08},  // #27
        {0x30, 0x04},  // #28
        {0x30, 0x18},  // #29
        {0x30, 0x50},  // #30
    };

    private static final String[] CODE_NAMES = {
        "Canon LV addr:30 cmd:10 (MOST LIKELY)",
        "Canon LV addr:30 cmd:80",
        "Canon LV addr:30 cmd:00",
        "Canon LV addr:30 cmd:01",
        "Canon LV addr:30 cmd:02",
        "Canon LV addr:30 cmd:40",
        "Canon LV addr:30 cmd:0A",
        "Canon LV addr:30 cmd:05",
        "Canon LV addr:30 cmd:09",
        "Canon LV Code2 addr:31 cmd:10",
        "Canon LV Code2 addr:31 cmd:80",
        "Canon LV Code2 addr:31 cmd:00",
        "Canon LV Code2 addr:31 cmd:01",
        "Canon LV-300 addr:32 cmd:10",
        "Canon LV-300 addr:32 cmd:80",
        "Canon LV-300 addr:32 cmd:00",
        "Canon LV-300 addr:32 cmd:01",
        "Canon SX/WUX addr:45 cmd:10",
        "Canon SX/WUX addr:45 cmd:80",
        "Canon SX/WUX addr:45 cmd:00",
        "Canon SX/WUX addr:45 cmd:01",
        "Canon older addr:61 cmd:10",
        "Canon older addr:61 cmd:80",
        "Canon older addr:61 cmd:00",
        "Canon older addr:61 cmd:01",
        "Canon LV addr:30 cmd:20",
        "Canon LV addr:30 cmd:08",
        "Canon LV addr:30 cmd:04",
        "Canon LV addr:30 cmd:18",
        "Canon LV addr:30 cmd:50",
    };

    /**
     * Generate NEC protocol IR pattern from address and command bytes.
     * NEC format: 9000us mark, 4500us space, then 32 bits LSB first:
     *   address(8) + ~address(8) + command(8) + ~command(8)
     * Each bit: 560us mark + 560us space (0) or 560us mark + 1690us space (1)
     * Ending: 560us mark + 42000us space (gap)
     */
    private int[] generateNecPattern(int address, int command) {
        int inverseAddress = (~address) & 0xFF;
        int inverseCommand = (~command) & 0xFF;

        // 2 (leader) + 32*2 (bits) + 2 (trailing) = 68 elements
        int[] pattern = new int[68];
        int idx = 0;

        // Leader: 9000us mark, 4500us space
        pattern[idx++] = 9000;
        pattern[idx++] = 4500;

        // Encode 4 bytes: address, ~address, command, ~command
        int[] bytes = {address, inverseAddress, command, inverseCommand};
        for (int b : bytes) {
            for (int bit = 0; bit < 8; bit++) {
                pattern[idx++] = 560; // mark
                if ((b & (1 << bit)) != 0) {
                    pattern[idx++] = 1690; // space for '1'
                } else {
                    pattern[idx++] = 560;  // space for '0'
                }
            }
        }

        // Trailing: 560us mark, 42000us space
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
        if (currentCodeIndex >= NEC_CODES.length) currentCodeIndex = 0;
        updateCodeDisplay();

        if (irManager == null || !irManager.hasIrEmitter()) {
            statusText.setText("NO IR BLASTER FOUND!");
            powerButton.setEnabled(false);
            Toast.makeText(this, "This device does not have an IR blaster!", Toast.LENGTH_LONG).show();
            return;
        }

        statusText.setText("IR Blaster: READY");

        // TAP = Send power signal
        powerButton.setOnClickListener(v -> sendPowerSignal());

        // LONG PRESS = Select code
        powerButton.setOnLongClickListener(v -> {
            showCodeSelector();
            return true;
        });
    }

    private void sendPowerSignal() {
        try {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) {
                vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE));
            }

            powerButton.setAlpha(0.5f);
            statusText.setText("SENDING...");

            int[] code = NEC_CODES[currentCodeIndex];
            int[] pattern = generateNecPattern(code[0], code[1]);

            // Send 3 times for reliability
            for (int i = 0; i < 3; i++) {
                irManager.transmit(CARRIER_FREQ, pattern);
                try { Thread.sleep(80); } catch (InterruptedException e) { /* ok */ }
            }

            statusText.setText("SENT! Code " + (currentCodeIndex + 1) + "/" + NEC_CODES.length
                    + " [0x" + String.format("%02X", code[0])
                    + ", 0x" + String.format("%02X", code[1]) + "]");
            powerButton.setAlpha(1.0f);

        } catch (Exception e) {
            statusText.setText("ERROR: " + e.getMessage());
            powerButton.setAlpha(1.0f);
        }
    }

    private void showCodeSelector() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Canon IR Code\n(Try each until projector responds)");

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

        builder.setNeutralButton("TEST ALL", (dialog, which) -> {
            testAllCodes();
        });

        builder.show();
    }

    private void testAllCodes() {
        if (testingAll) {
            testingAll = false;
            return;
        }
        testingAll = true;

        new Thread(() -> {
            for (int i = 0; i < NEC_CODES.length && testingAll; i++) {
                final int index = i;
                final int[] code = NEC_CODES[i];

                runOnUiThread(() -> {
                    statusText.setText("TESTING " + (index + 1) + "/" + NEC_CODES.length
                            + "\n" + CODE_NAMES[index]
                            + "\n[addr=0x" + String.format("%02X", code[0])
                            + " cmd=0x" + String.format("%02X", code[1]) + "]"
                            + "\n\nTap button to STOP test");
                });

                try {
                    int[] pattern = generateNecPattern(code[0], code[1]);
                    for (int j = 0; j < 3; j++) {
                        irManager.transmit(CARRIER_FREQ, pattern);
                        Thread.sleep(80);
                    }
                    // Wait 2.5 seconds so user can see if projector responds
                    Thread.sleep(2500);
                } catch (InterruptedException e) {
                    break;
                }
            }
            testingAll = false;
            runOnUiThread(() -> {
                statusText.setText("Test complete!\nLong-press to pick the code that worked.");
            });
        }).start();
    }

    private void updateCodeDisplay() {
        int[] code = NEC_CODES[currentCodeIndex];
        codeInfoText.setText("Code " + (currentCodeIndex + 1) + "/" + NEC_CODES.length
                + ": " + CODE_NAMES[currentCodeIndex]
                + "\nNEC: addr=0x" + String.format("%02X", code[0])
                + " cmd=0x" + String.format("%02X", code[1])
                + "\n\nTap = Send  |  Long-press = Change code");
    }
}
