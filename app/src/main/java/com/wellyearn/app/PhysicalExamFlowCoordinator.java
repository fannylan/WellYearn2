package com.wellyearn.app;

import android.content.Intent;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.wellyearn.app.usb.UsbSerialHelper;

/** Coordinates ordered tests started from {@link PhysicalExamActivity}. */
final class PhysicalExamFlowCoordinator {

    static final String EXTRA_FLOW_ENABLED = "physicalExamFlowEnabled";
    static final String EXTRA_GASTROINTESTINAL_REPORT_ID = "gastrointestinalReportId";
    static final String EXTRA_RED_BLOOD_CELL_REPORT_ID = "redBloodCellReportId";
    static final String EXTRA_RESPIRATORY_REPORT_ID = "respiratoryReportId";
    static final String EXTRA_PHYSICAL_EXAM_REPORT_ID = "physicalExamReportId";

    private PhysicalExamFlowCoordinator() {
    }

    static Class<?> activityFor(PhysicalExamSelectionRouter.Detection detection) {
        if (detection == PhysicalExamSelectionRouter.Detection.GASTROINTESTINAL) {
            return Test1Activity.class;
        }
        if (detection == PhysicalExamSelectionRouter.Detection.RED_BLOOD_CELL) {
            return Test2Activity.class;
        }
        if (detection == PhysicalExamSelectionRouter.Detection.RESPIRATORY) {
            return Test3Activity.class;
        }
        throw new IllegalArgumentException("检测类型不能为空");
    }

    static long reportIdFor(
            PhysicalExamSelectionRouter.Detection detection,
            long gastrointestinalReportId,
            long redBloodCellReportId,
            long respiratoryReportId) {
        if (detection == PhysicalExamSelectionRouter.Detection.GASTROINTESTINAL) {
            return gastrointestinalReportId;
        }
        if (detection == PhysicalExamSelectionRouter.Detection.RED_BLOOD_CELL) {
            return redBloodCellReportId;
        }
        if (detection == PhysicalExamSelectionRouter.Detection.RESPIRATORY) {
            return respiratoryReportId;
        }
        return -1L;
    }

    static void putFlowState(
            Intent intent,
            long gastrointestinalReportId,
            long redBloodCellReportId,
            long respiratoryReportId) {
        putFlowState(
                intent,
                gastrointestinalReportId,
                redBloodCellReportId,
                respiratoryReportId,
                -1L);
    }

    static void putFlowState(Intent intent, long physicalExamReportId) {
        putFlowState(intent, -1L, -1L, -1L, physicalExamReportId);
    }

    static void putFlowState(
            Intent intent,
            long gastrointestinalReportId,
            long redBloodCellReportId,
            long respiratoryReportId,
            long physicalExamReportId) {
        intent.putExtra(EXTRA_FLOW_ENABLED, true);
        intent.putExtra(EXTRA_GASTROINTESTINAL_REPORT_ID, gastrointestinalReportId);
        intent.putExtra(EXTRA_RED_BLOOD_CELL_REPORT_ID, redBloodCellReportId);
        intent.putExtra(EXTRA_RESPIRATORY_REPORT_ID, respiratoryReportId);
        intent.putExtra(EXTRA_PHYSICAL_EXAM_REPORT_ID, physicalExamReportId);
    }

    static boolean advanceAfterCompletion(
            AppCompatActivity activity,
            UsbSerialHelper usbHelper,
            PhysicalExamSelectionRouter.Detection completedDetection) {
        Intent currentIntent = activity.getIntent();
        if (!currentIntent.getBooleanExtra(EXTRA_FLOW_ENABLED, false)) {
            return false;
        }

        boolean ch4 = currentIntent.getBooleanExtra("chkCH4", false);
        boolean h2 = currentIntent.getBooleanExtra("chkH2", false);
        boolean co = currentIntent.getBooleanExtra("chkCO", false);
        boolean no = currentIntent.getBooleanExtra("chkNO", false);
        PhysicalExamSelectionRouter.Detection nextDetection =
                PhysicalExamSelectionRouter.nextSelected(
                        completedDetection, ch4, h2, co, no);
        if (nextDetection == null) {
            return false;
        }

        if (usbHelper == null || !usbHelper.isConnected()) {
            Toast.makeText(activity, "USB未连接，无法启动下一项检测", Toast.LENGTH_LONG).show();
            return false;
        }

        new Thread(() -> {
            String command = PhysicalExamSelectionRouter.commandFor(nextDetection);
            usbHelper.sendBytes(hexStringToByteArray(command));
            // Close the current screen's serial port before the next screen connects to it.
            usbHelper.disconnect();

            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) {
                    return;
                }
                Intent nextIntent = createNextIntent(activity, nextDetection);
                Toast.makeText(
                        activity,
                        detectionName(completedDetection) + "数据接收完成，已启动"
                                + detectionName(nextDetection),
                        Toast.LENGTH_SHORT).show();
                activity.startActivity(nextIntent);
                activity.finish();
            });
        }, "physical-exam-next-command").start();
        return true;
    }

    private static Intent createNextIntent(
            AppCompatActivity activity,
            PhysicalExamSelectionRouter.Detection detection) {
        Intent source = activity.getIntent();
        long gastrointestinalReportId = source.getLongExtra(
                EXTRA_GASTROINTESTINAL_REPORT_ID, -1L);
        long redBloodCellReportId = source.getLongExtra(
                EXTRA_RED_BLOOD_CELL_REPORT_ID, -1L);
        long respiratoryReportId = source.getLongExtra(
                EXTRA_RESPIRATORY_REPORT_ID, -1L);
        long physicalExamReportId = source.getLongExtra(
                EXTRA_PHYSICAL_EXAM_REPORT_ID, -1L);

        Intent intent = new Intent(activity, activityFor(detection));
        intent.putExtra("patientId", source.getLongExtra("patientId", -1L));
        intent.putExtra(
                "reportId",
                reportIdFor(
                        detection,
                        gastrointestinalReportId,
                        redBloodCellReportId,
                        respiratoryReportId));
        intent.putExtra("patientName", source.getStringExtra("patientName"));
        intent.putExtra("specimenNo", source.getStringExtra("specimenNo"));
        intent.putExtra("substrate", source.getStringExtra("substrate"));
        intent.putExtra("patientAge", source.getIntExtra("patientAge", 0));
        intent.putExtra("hemoglobin", source.getFloatExtra("hemoglobin", 0f));
        intent.putExtra(Test2Activity.EXTRA_PHYSICAL_EXAM_MODE, true);
        intent.putExtra("chkCH4", source.getBooleanExtra("chkCH4", false));
        intent.putExtra("chkH2", source.getBooleanExtra("chkH2", false));
        intent.putExtra("chkCO", source.getBooleanExtra("chkCO", false));
        intent.putExtra("chkNO", source.getBooleanExtra("chkNO", false));
        putFlowState(
                intent,
                gastrointestinalReportId,
                redBloodCellReportId,
                respiratoryReportId,
                physicalExamReportId);
        return intent;
    }

    private static String detectionName(PhysicalExamSelectionRouter.Detection detection) {
        if (detection == PhysicalExamSelectionRouter.Detection.GASTROINTESTINAL) {
            return "胃肠道疾病检测";
        }
        if (detection == PhysicalExamSelectionRouter.Detection.RED_BLOOD_CELL) {
            return "红细胞寿命检测";
        }
        return "呼吸道疾病检测";
    }

    private static byte[] hexStringToByteArray(String value) {
        String[] hex = value.split(" ");
        byte[] bytes = new byte[hex.length];
        for (int i = 0; i < hex.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex[i], 16);
        }
        return bytes;
    }
}
