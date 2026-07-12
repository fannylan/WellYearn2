package com.wellyearn.app.usb;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.List;

/**
 * USB串口通信助手类，支持发送字节数组和回调字节数据
 */
public class UsbSerialHelper {
    private static final String TAG = "UsbSerialHelper";
    private static final String ACTION_USB_PERMISSION = "com.wellyearn.app.USB_PERMISSION";

    private Context context;
    private UsbManager usbManager;
    private UsbSerialDriver driver;
    private UsbSerialPort port;
    private SerialInputOutputManager ioManager;
    private OnDataReceivedListener dataListener;
    private OnDeviceReadyListener readyListener;
    private ByteDataListener byteDataListener;

    private boolean isConnected = false;
    private String receivedData = "";

    // 回调接口
    public interface OnDataReceivedListener {
        void onDataReceived(String data);
    }

    public interface OnDeviceReadyListener {
        void onDeviceReady(String data);
    }

    public interface ByteDataListener {
        void onByteDataReceived(byte[] data);
    }

    public UsbSerialHelper(Context context) {
        this.context = context;
        this.usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        registerUsbReceiver();
    }

    private void registerUsbReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        ContextCompat.registerReceiver(context, usbReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            connectDevice(device);
                        }
                    } else {
                        Log.d(TAG, "USB权限被拒绝");
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    requestPermission(device);
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                disconnect();
            }
        }
    };

    /**
     * 请求USB设备权限
     */
    public void requestPermission(UsbDevice device) {
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 0, new Intent(ACTION_USB_PERMISSION),
                PendingIntent.FLAG_IMMUTABLE
        );
        usbManager.requestPermission(device, pendingIntent);
    }

    /**
     * 扫描并连接USB串口设备
     */
    public void scanAndConnect() {
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber()
                .findAllDrivers(usbManager);

        if (availableDrivers.isEmpty()) {
            Log.d(TAG, "未检测到USB串口设备");
            return;
        }

        driver = availableDrivers.get(0);
        UsbDevice device = driver.getDevice();

        if (!usbManager.hasPermission(device)) {
            requestPermission(device);
        } else {
            connectDevice(device);
        }
    }

    private void connectDevice(UsbDevice device) {
        try {
            driver = UsbSerialProber.getDefaultProber().probeDevice(device);
            if (driver == null) {
                Log.d(TAG, "无法找到对应驱动");
                return;
            }

            port = driver.getPorts().get(0);
            port.open(usbManager.openDevice(device));

            // 配置串口参数 - 根据STM32配置调整
            port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

            isConnected = true;

            // 启动数据监听
            ioManager = new SerialInputOutputManager(port, new SerialInputOutputManager.Listener() {
                @Override
                public void onNewData(byte[] data) {
                    // 1) 字符串回调（兼容旧版）
                    String received = new String(data);
                    receivedData = received;
                    Log.d(TAG, "接收到数据: " + received);

                    if (dataListener != null) {
                        dataListener.onDataReceived(received);
                    }

                    // 2) 字节回调（用于协议解析）
                    if (byteDataListener != null) {
                        byteDataListener.onByteDataReceived(data);
                    }

                    // 3) 设备就绪回调（当收到非空数据时触发，保留旧逻辑）
                    if (readyListener != null && !received.isEmpty()) {
                        readyListener.onDeviceReady(received);
                    }
                }

                @Override
                public void onRunError(Exception e) {
                    Log.e(TAG, "串口读取错误", e);
                }
            });

            ioManager.start();
            Log.d(TAG, "USB串口连接成功");

        } catch (IOException e) {
            Log.e(TAG, "连接USB设备失败", e);
            isConnected = false;
        }
    }

    /**
     * 发送字节数组数据到STM32
     */
    public void sendBytes(byte[] data) {
        if (port != null && isConnected) {
            try {
                port.write(data, 1000);
                Log.d(TAG, "发送字节数据: " + bytesToHex(data));
            } catch (IOException e) {
                Log.e(TAG, "发送字节数据失败", e);
            }
        } else {
            Log.w(TAG, "USB未连接，无法发送数据");
        }
    }

    /**
     * 发送字符串数据（保留）
     */
    public void sendData(String data) {
        if (port != null && isConnected) {
            try {
                byte[] bytes = data.getBytes();
                port.write(bytes, 1000);
                Log.d(TAG, "发送数据: " + data);
            } catch (IOException e) {
                Log.e(TAG, "发送数据失败", e);
            }
        }
    }

    /**
     * 断开连接
     */
    public void disconnect() {
        isConnected = false;
        if (ioManager != null) {
            ioManager.stop();
            ioManager = null;
        }
        if (port != null) {
            try {
                port.close();
            } catch (IOException e) {
                Log.e(TAG, "关闭串口失败", e);
            }
            port = null;
        }
        try {
            context.unregisterReceiver(usbReceiver);
        } catch (IllegalArgumentException e) {
            // 接收器未注册
        }
    }

    public void setOnDataReceivedListener(OnDataReceivedListener listener) {
        this.dataListener = listener;
    }

    public void setOnDeviceReadyListener(OnDeviceReadyListener listener) {
        this.readyListener = listener;
    }

    public void setByteDataListener(ByteDataListener listener) {
        this.byteDataListener = listener;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public String getReceivedData() {
        return receivedData;
    }

    // 辅助方法：字节数组转十六进制字符串（用于调试）
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString();
    }
}