package com.wellyearn.app;

import android.graphics.Paint;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.wellyearn.app.database.model.ReportSearchResult;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class ReportSearchAdapter extends RecyclerView.Adapter<ReportSearchAdapter.ReportViewHolder> {

    interface Listener {
        void onSelectionChanged(int selectedCount);
        void onPdfClick(ReportSearchResult report);
    }

    private final Listener listener;
    private final List<ReportSearchResult> reports = new ArrayList<>();
    private final Set<Long> selectedReportIds = new HashSet<>();
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);

    ReportSearchAdapter(Listener listener) {
        this.listener = listener;
    }

    void setReports(List<ReportSearchResult> newReports) {
        reports.clear();
        if (newReports != null) reports.addAll(newReports);
        selectedReportIds.clear();
        notifyDataSetChanged();
        listener.onSelectionChanged(0);
    }

    List<ReportSearchResult> getSelectedReports() {
        List<ReportSearchResult> selected = new ArrayList<>();
        for (ReportSearchResult report : reports) {
            if (selectedReportIds.contains(report.reportId)) selected.add(report);
        }
        return selected;
    }

    @NonNull
    @Override
    public ReportViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_report_search, parent, false);
        return new ReportViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReportViewHolder holder, int position) {
        ReportSearchResult report = reports.get(position);
        holder.patientName.setText(safe(report.patientName));
        holder.reportDate.setText(dateFormat.format(report.reportDate));
        holder.reportType.setText(ReportTypeMapper.displayName(report.reportType));

        holder.checkBox.setOnCheckedChangeListener(null);
        holder.checkBox.setChecked(selectedReportIds.contains(report.reportId));
        holder.checkBox.setOnCheckedChangeListener((buttonView, checked) -> {
            if (checked) {
                selectedReportIds.add(report.reportId);
            } else {
                selectedReportIds.remove(report.reportId);
            }
            listener.onSelectionChanged(selectedReportIds.size());
        });

        boolean hasPdf = !TextUtils.isEmpty(report.pdfUri);
        holder.pdfLink.setEnabled(hasPdf);
        holder.pdfLink.setPaintFlags(hasPdf
                ? holder.pdfLink.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG
                : holder.pdfLink.getPaintFlags() & ~Paint.UNDERLINE_TEXT_FLAG);
        holder.pdfLink.setText(hasPdf
                ? (TextUtils.isEmpty(report.pdfFileName) ? "打开PDF" : report.pdfFileName)
                : "无PDF");
        holder.pdfLink.setTextColor(hasPdf ? 0xFF1565C0 : 0xFF888888);
        holder.pdfLink.setOnClickListener(hasPdf ? v -> listener.onPdfClick(report) : null);
    }

    @Override
    public int getItemCount() {
        return reports.size();
    }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "--" : value.trim();
    }

    static final class ReportViewHolder extends RecyclerView.ViewHolder {
        final CheckBox checkBox;
        final TextView patientName;
        final TextView reportDate;
        final TextView reportType;
        final TextView pdfLink;

        ReportViewHolder(@NonNull View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.checkReport);
            patientName = itemView.findViewById(R.id.textPatientName);
            reportDate = itemView.findViewById(R.id.textReportDate);
            reportType = itemView.findViewById(R.id.textReportType);
            pdfLink = itemView.findViewById(R.id.textPdfLink);
        }
    }
}
