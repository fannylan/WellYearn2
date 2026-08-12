package com.wellyearn.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.wellyearn.app.database.entity.OperationLog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class OperationLogAdapter extends RecyclerView.Adapter<OperationLogAdapter.Holder> {

    private final List<OperationLog> logs = new ArrayList<>();
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);

    void submit(List<OperationLog> newLogs) {
        logs.clear();
        if (newLogs != null) logs.addAll(newLogs);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_operation_log, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        OperationLog log = logs.get(position);
        holder.action.setText(safe(log.action) + (log.success ? "  ·  成功" : "  ·  失败"));
        holder.action.setTextColor(log.success ? 0xFF047857 : 0xFFB91C1C);
        holder.operator.setText("操作人：" + safe(log.operatorUsername)
                + "    时间：" + dateFormat.format(new Date(log.operationTime)));
        String report = safe(log.reportFileName);
        holder.detail.setText(("--".equals(report) ? "" : "报告：" + report + "    ")
                + "详情：" + safe(log.detail));
    }

    @Override
    public int getItemCount() {
        return logs.size();
    }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "--" : value;
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView action;
        final TextView operator;
        final TextView detail;

        Holder(@NonNull View itemView) {
            super(itemView);
            action = itemView.findViewById(R.id.textLogAction);
            operator = itemView.findViewById(R.id.textLogOperator);
            detail = itemView.findViewById(R.id.textLogDetail);
        }
    }
}
