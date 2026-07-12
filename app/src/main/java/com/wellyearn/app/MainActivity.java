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
import com.wellyearn.app.database.entity.Admin;
import com.wellyearn.app.usb.UsbSerialHelper;

import java.io.ByteArrayOutputStream;

public class MainActivity extends AppCompatActivity {

    private static final int POLL_TIMEOUT_MS = 10000;   // 轮询超时5秒
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
        startBootSequence();   // 初始灰色进度条
        initDatabase();
    }

    private void initViews() {
        bootProgressBar = findViewById(R.id.bootProgressBar);
        cardDetect = findViewById(R.id.cardDetect);
        cardReport = findViewById(R.id.cardReport);
        cardHelp = findViewById(R.id.cardHelp);

        // 直接启用所有功能按钮，无需等待设备就绪
        cardDetect.setEnabled(true);
        cardReport.setEnabled(true);
        cardHelp.setEnabled(true);
        cardDetect.setAlpha(1.0f);
        cardReport.setAlpha(1.0f);
        cardHelp.setAlpha(1.0f);

        // 点击事件
        cardDetect.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ModeSelectActivity.class);
            startActivity(intent);
        });

        cardReport.setOnClickListener(v -> {
            Toast.makeText(this, "报告检索模块开发中", Toast.LENGTH_SHORT).show();
        });

        cardHelp.setOnClickListener(v -> {
            Toast.makeText(this, "帮助运维模块开发中", Toast.LENGTH_SHORT).show();
        });
    }

    private void initUsbSerial() {
        mainHandler = new Handler(Looper.getMainLooper());
        usbHelper = new UsbSerialHelper(this);

        // 设置字节数据监听（用于解析协议帧）
        usbHelper.setByteDataListener(data -> parseDeviceStatus(data));

        // 尝试连接USB设备
        usbHelper.scanAndConnect();

        // 延迟检测连接状态，重试机制
        startConnectionCheck(0);
    }

    /**
     * 重试检测USB连接，最多尝试 MAX_CONNECT_RETRY 次
     */
    private void startConnectionCheck(int attempt) {
        if (attempt >= MAX_CONNECT_RETRY) {
            Toast.makeText(this, "USB设备未连接，请检查", Toast.LENGTH_SHORT).show();
            return;
        }
        mainHandler.postDelayed(() -> {
            if (usbHelper.isConnected()) {
                // 连接成功，发送轮询命令
                sendPollCommand();
            } else {
                // 未连接，继续重试
                startConnectionCheck(attempt + 1);
            }
        }, 1000 * (attempt + 1));  // 递增延迟
    }

    /**
     * 构造协议帧（包含转义）
     * @param commandId 2字节命令ID（大端）
     * @param body      消息体（可为null）
     * @return 完整帧（含起始结束符0x7E）
     */
    private byte[] buildFrame(byte[] commandId, byte[] body) {
        // 消息头：ID(2) + 属性(2) + 可选分包项(0)
        int bodyLen = (body == null) ? 0 : body.length;
        byte[] header = new byte[4 + bodyLen];
        System.arraycopy(commandId, 0, header, 0, 2);
        // 属性：低10位为消息体长度
        int attr = bodyLen & 0x03FF;
        header[2] = (byte) ((attr >> 8) & 0xFF);
        header[3] = (byte) (attr & 0xFF);
        if (body != null && bodyLen > 0) {
            System.arraycopy(body, 0, header, 4, bodyLen);
        }

        // 校验：从header第0字节异或到最后1字节
        byte checksum = 0;
        for (byte b : header) {
            checksum ^= b;
        }

        // 构造待转义数据：header + checksum
        byte[] dataToEscape = new byte[header.length + 1];
        System.arraycopy(header, 0, dataToEscape, 0, header.length);
        dataToEscape[header.length] = checksum;

        // 转义处理并添加起始/结束符
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0x7E); // 起始符
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
        baos.write(0x7E); // 结束符
        return baos.toByteArray();
    }

    /**
     * 发送状态轮询命令 (消息ID 0x0A0A)
     */
    private void sendPollCommand() {
        if (!usbHelper.isConnected()) {
            return;
        }
        byte[] cmdId = {(byte) 0x0A, (byte) 0x0A};
        byte[] frame = buildFrame(cmdId, null);
        usbHelper.sendBytes(frame);

        // 设置超时计时器
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
     * 解析设备状态回应
     * @param data 原始字节数据（包含转义）
     */
    private void parseDeviceStatus(byte[] data) {
        // 去转义，并提取完整帧
        byte[] raw = removeEscape(data);
        if (raw == null || raw.length < 8) return;

        // 检查帧头尾
        if (raw[0] != 0x7E || raw[raw.length - 1] != 0x7E) return;

        // 提取消息ID（第1、2字节，大端）
        int msgId = ((raw[1] & 0xFF) << 8) | (raw[2] & 0xFF);
        if (msgId != 0x1A0A) return; // 不是状态回应

        // 提取状态：消息体第1字节（索引5，因为头4字节+属性2字节，但属性已占2字节，所以偏移4+1=5？需计算）
        // 结构：起始符(1) + ID(2) + 属性(2) + 消息体(n) + 校验(1) + 结束符(1)
        // 所以消息体从索引5开始（0-based），第一个字节就是状态
        if (raw.length < 6) return;
        int status = raw[5] & 0xFF;

        // 取消超时
        isPolling = false;
        mainHandler.removeCallbacks(pollTimeoutRunnable);

        runOnUiThread(() -> {
            updateProgressColor(status);
            String statusMsg = getStatusText(status);
            Toast.makeText(MainActivity.this, "设备状态: " + statusMsg, Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * 去除转义字符
     */
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
            case 0: color = Color.parseColor("#9E9E9E"); break; // 灰色
            case 1: color = Color.parseColor("#4CAF50"); break; // 绿色
            case 2: color = Color.parseColor("#9C27B0"); break; // 紫色
            case 3: color = Color.parseColor("#FFEB3B"); break; // 黄色
            case 4: color = Color.parseColor("#F44336"); break; // 红色
            default: color = Color.parseColor("#9E9E9E");
        }
        setProgressBarColor(color);
    }

    private void setProgressBarColor(int color) {
        bootProgressBar.getProgressDrawable().setColorFilter(color, PorterDuff.Mode.SRC_IN);
        bootProgressBar.setProgress(100);
    }

    private void startBootSequence() {
        // 初始灰色
        bootProgressBar.setProgress(100);
        bootProgressBar.getProgressDrawable().setColorFilter(
                ContextCompat.getColor(this, android.R.color.darker_gray),
                PorterDuff.Mode.SRC_IN
        );
    }

    private void initDatabase() {
        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            if (db.adminDao().getAdminCount() == 0) {
                Admin admin = new Admin();
                admin.setUsername("admin");
                admin.setPassword("admin123");
                admin.setRole("超级管理员");
                admin.setName("系统管理员");
                admin.setPhone("0755-12345678");
                admin.setEmail("admin@wellyearn.com");
                admin.setCreatedTime(System.currentTimeMillis());
                db.adminDao().insert(admin);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "已创建默认管理员", Toast.LENGTH_SHORT).show());
            }
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