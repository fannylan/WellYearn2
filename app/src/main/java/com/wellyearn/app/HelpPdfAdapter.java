package com.wellyearn.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class HelpPdfAdapter extends RecyclerView.Adapter<HelpPdfAdapter.Holder> {

    interface Listener {
        void onPdfClick(HelpPdfListActivity.HelpPdfItem item);
    }

    private final List<HelpPdfListActivity.HelpPdfItem> items = new ArrayList<>();
    private final Listener listener;
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);

    HelpPdfAdapter(Listener listener) {
        this.listener = listener;
    }

    void submit(List<HelpPdfListActivity.HelpPdfItem> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_help_pdf, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        HelpPdfListActivity.HelpPdfItem item = items.get(position);
        holder.name.setText(item.name);
        String size = item.size <= 0 ? "未知大小"
                : String.format(Locale.CHINA, "%.1f MB", item.size / 1024d / 1024d);
        String modified = item.modified <= 0 ? "未知时间"
                : dateFormat.format(new Date(item.modified));
        holder.detail.setText(size + "  ·  " + modified);
        holder.itemView.setOnClickListener(v -> listener.onPdfClick(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView detail;

        Holder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.textPdfName);
            detail = itemView.findViewById(R.id.textPdfDetail);
        }
    }
}
