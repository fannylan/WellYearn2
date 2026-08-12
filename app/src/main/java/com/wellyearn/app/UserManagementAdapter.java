package com.wellyearn.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.wellyearn.app.database.entity.Admin;

import java.util.ArrayList;
import java.util.List;

final class UserManagementAdapter extends RecyclerView.Adapter<UserManagementAdapter.Holder> {

    interface Listener {
        void onEdit(Admin user);
        void onDelete(Admin user);
    }

    private final List<Admin> users = new ArrayList<>();
    private final Listener listener;

    UserManagementAdapter(Listener listener) {
        this.listener = listener;
    }

    void submit(List<Admin> newUsers) {
        users.clear();
        if (newUsers != null) users.addAll(newUsers);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_maintenance_user, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Admin user = users.get(position);
        holder.name.setText(user.getName() + "  ·  " + user.getRole());
        holder.account.setText("账号：" + user.getUsername() + "    手机："
                + safe(user.getPhone()));
        holder.permissions.setText("权限：" + MaintenancePermissions.describe(user));
        holder.edit.setOnClickListener(v -> listener.onEdit(user));
        holder.delete.setOnClickListener(v -> listener.onDelete(user));
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "--" : value;
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView account;
        final TextView permissions;
        final Button edit;
        final Button delete;

        Holder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.textUserName);
            account = itemView.findViewById(R.id.textUserAccount);
            permissions = itemView.findViewById(R.id.textUserPermissions);
            edit = itemView.findViewById(R.id.buttonEditUser);
            delete = itemView.findViewById(R.id.buttonDeleteUser);
        }
    }
}
