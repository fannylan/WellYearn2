package com.wellyearn.app;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.wellyearn.app.database.AppDatabase;
import com.wellyearn.app.usb.UsbSerialHelper;

import java.io.ByteArrayOutputStream;

public class MainActivity extends AppCompatActivity {

    private static final int POLL_TIMEOUT_MS = 5000;
    private static final int MAX_CONNECT_RETRY = 5;

    private ProgressBar bootProgressBar;
    private CardView cardDetect, cardReport, cardHelp;

    private UsbSerialHelper usbHelper;
    private Handler mainHandler;
    private Runnable pollTimeoutRunnable;
    private boolean isPolling = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initUsbSerial();
        startBootSequence();
        initDatabase();
    }

    private void initViews() {
        bootProgressBar = findViewById(R.id.bootProgressBar);
        cardDetect = findViewById(R.id.cardDetect);
        cardReport = findViewById(R.id.cardReport);
        cardHelp = findViewById(R.id.cardHelp);

        // 直接启用所有功能按钮
        cardDetect.setEnabled(true);
        cardReport.setEnabled(true);
        cardHelp.setEnabled(true);
        cardDetect.setAlpha(1.0f);
        cardReport.setAlpha(1.0f);
        cardHelp.setAlpha(1.0f);

        cardDetect.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ModeSelectActivity.class);
            startActivity(intent);
        });

        cardReport.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ReportSearchActivity.class);
            startActivity(intent);
        });

        cardHelp.setOnClickListener(v -> {
            Toast.makeText(this, "帮助运维模块开发中", Toast.LENGTH_SHORT).show();
        });
    }

    private void initUsbSerial() {
        mainHandler = new Handler(Looper.getMainLooper());
        usbHelper = new UsbSerialHelper(this);

        // 设置字节数据监听（用于协议解析和显示接收数据）
        usbHelper.setByteDataListener(data -> {
            // 显示接收到的原始数据（十六进制）
            String hexStr = bytesToHex(data);
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "接收: " + hexStr, Toast.LENGTH_SHORT).show());
            // 解析状态
            parseDeviceStatus(data);
        });

        usbHelper.scanAndConnect();
        startConnectionCheck(0);
    }

    private void startConnectionCheck(int attempt) {
        if (attempt >= MAX_CONNECT_RETRY) {
            Toast.makeText(this, "USB设备未连接，请检查", Toast.LENGTH_SHORT).show();
            return;
        }
        mainHandler.postDelayed(() -> {
            if (usbHelper.isConnected()) {
                sendPollCommand();
            } else {
                startConnectionCheck(attempt + 1);
            }
        }, 1000 * (attempt + 1));
    }

    /**
     * 构造协议帧（包含转义）
     */
    private byte[] buildFrame(byte[] commandId, byte[] body) {
        int bodyLen = (body == null) ? 0 : body.length;
        byte[] header = new byte[4 + bodyLen];
        System.arraycopy(commandId, 0, header, 0, 2);
        int attr = bodyLen & 0x03FF;
        header[2] = (byte) ((attr >> 8) & 0xFF);
        header[3] = (byte) (attr & 0xFF);
        if (body != null && bodyLen > 0) {
            System.arraycopy(body, 0, header, 4, bodyLen);
        }

        byte checksum = 0;
        for (byte b : header) checksum ^= b;

        byte[] dataToEscape = new byte[header.length + 1];
        System.arraycopy(header, 0, dataToEscape, 0, header.length);
        dataToEscape[header.length] = checksum;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0x7E);
        for (byte b : dataToEscape) {
            if (b == 0x7E) {
                baos.write(0x7D);
                baos.write(0x02);
            } else if (b == 0x7D) {
                baos.write(0x7D);
                baos.write(0x01);
            } else {
                baos.write(b);
            }
        }
        baos.write(0x7E);
        return baos.toByteArray();
    }

    /**
     * 发送状态轮询命令 (0x0A0A)
     */
    private void sendPollCommand() {
        if (!usbHelper.isConnected()) {
            return;
        }
        byte[] cmdId = {(byte) 0x0A, (byte) 0x0A};
        byte[] frame = buildFrame(cmdId, null);

        // 显示发送内容
        String sendHex = bytesToHex(frame);
        runOnUiThread(() -> Toast.makeText(MainActivity.this, "发送: " + sendHex, Toast.LENGTH_SHORT).show());

        usbHelper.sendBytes(frame);

        isPolling = true;
        if (pollTimeoutRunnable != null) {
            mainHandler.removeCallbacks(pollTimeoutRunnable);
        }
        pollTimeoutRunnable = () -> {
            if (isPolling) {
                isPolling = false;
                runOnUiThread(() -> {
                    setProgressBarColor(Color.parseColor("#9E9E9E"));
                    Toast.makeText(MainActivity.this, "设备状态获取超时", Toast.LENGTH_SHORT).show();
                });
            }
        };
        mainHandler.postDelayed(pollTimeoutRunnable, POLL_TIMEOUT_MS);
    }

    /**
     * 解析设备状态回应 (0x1A0A)
     */
    private void parseDeviceStatus(byte[] data) {
        byte[] raw = removeEscape(data);
        if (raw == null || raw.length < 8) return;
        if (raw[0] != 0x7E || raw[raw.length - 1] != 0x7E) return;

        int msgId = ((raw[1] & 0xFF) << 8) | (raw[2] & 0xFF);
        if (msgId != 0x1A0A) return;

        // 显示接收到的完整帧（去除转义后）
        String recvHex = bytesToHex(raw);
        runOnUiThread(() -> Toast.makeText(MainActivity.this, "接收回应: " + recvHex, Toast.LENGTH_SHORT).show());

        int status = raw[5] & 0xFF;
        isPolling = false;
        mainHandler.removeCallbacks(pollTimeoutRunnable);

        runOnUiThread(() -> {
            updateProgressColor(status);
            String statusMsg = getStatusText(status);
            Toast.makeText(MainActivity.this, "设备状态: " + statusMsg, Toast.LENGTH_SHORT).show();
        });
    }

    private byte[] removeEscape(byte[] data) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (int i = 0; i < data.length; i++) {
            if (data[i] == 0x7D && i + 1 < data.length) {
                if (data[i + 1] == 0x01) {
                    baos.write(0x7D);
                    i++;
                } else if (data[i + 1] == 0x02) {
                    baos.write(0x7E);
                    i++;
                } else {
                    baos.write(data[i]);
                }
            } else {
                baos.write(data[i]);
            }
        }
        return baos.toByteArray();
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    private String getStatusText(int status) {
        switch (status) {
            case 0: return "初始化中";
            case 1: return "空闲已就位";
            case 2: return "检测中";
            case 3: return "校准中";
            case 4: return "故障";
            default: return "未知";
        }
    }

    private void updateProgressColor(int status) {
        int color;
        switch (status) {
            case 0: color = Color.parseColor("#9E9E9E"); break;
            case 1: color = Color.parseColor("#4CAF50"); break;
            case 2: color = Color.parseColor("#9C27B0"); break;
            case 3: color = Color.parseColor("#FFEB3B"); break;
            case 4: color = Color.parseColor("#F44336"); break;
            default: color = Color.parseColor("#9E9E9E");
        }
        setProgressBarColor(color);
    }

    private void setProgressBarColor(int color) {
        bootProgressBar.getProgressDrawable().setColorFilter(color, PorterDuff.Mode.SRC_IN);
        bootProgressBar.setProgress(100);
    }

    private void startBootSequence() {
        bootProgressBar.setProgress(100);
        bootProgressBar.getProgressDrawable().setColorFilter(
                ContextCompat.getColor(this, android.R.color.darker_gray),
                PorterDuff.Mode.SRC_IN
        );
    }

    private void initDatabase() {
        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            DefaultAdminProvisioner.ensureDefaultSuperAdmin(db.adminDao());
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (usbHelper != null) {
            usbHelper.disconnect();
        }
        if (pollTimeoutRunnable != null) {
            mainHandler.removeCallbacks(pollTimeoutRunnable);
        }
    }
}
