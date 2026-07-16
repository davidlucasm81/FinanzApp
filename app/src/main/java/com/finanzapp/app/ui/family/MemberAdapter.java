package com.finanzapp.app.ui.family;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.finanzapp.app.data.model.Invitation;
import com.finanzapp.app.data.model.Member;
import com.finanzapp.app.databinding.ItemMemberBinding;

import java.util.ArrayList;
import java.util.List;

public class MemberAdapter extends RecyclerView.Adapter<MemberAdapter.ViewHolder> {
    private List<MemberListItem> items = new ArrayList<>();
    private OnActionListener actionListener;

    public interface OnActionListener {
        void onCancel(Invitation invitation);
        void onApprove(Invitation invitation);
        void onReject(Invitation invitation);
        void onToggleRole(Member member, View anchor);
    }

    public void setOnActionListener(OnActionListener listener) {
        this.actionListener = listener;
    }

    public void setItems(List<MemberListItem> items, String currentUserRole, String currentUserUid) {
        this.items = items;
        this.userRole = currentUserRole;
        this.currentUid = currentUserUid;
        notifyDataSetChanged();
    }

    private String userRole = "member";
    private String currentUid = "";

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemMemberBinding binding = ItemMemberBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MemberListItem item = items.get(position);
        
        // Reset visibilities
        holder.binding.tvPendingTag.setVisibility(View.GONE);
        holder.binding.btnCancelInvite.setVisibility(View.GONE);
        holder.binding.layoutRequestActions.setVisibility(View.GONE);
        holder.binding.btnOptions.setVisibility(View.GONE);
        holder.binding.tvRole.setVisibility(View.VISIBLE);

        if (item.getType() == MemberListItem.TYPE_MEMBER) {
            Member m = item.getMember();
            holder.binding.tvName.setText(m.getDisplayName() != null ? m.getDisplayName() : "-");
            holder.binding.tvEmail.setText(m.getEmail() != null ? m.getEmail() : "-");
            holder.binding.tvRole.setText(m.getRole());

            boolean canManage = false;
            if (!m.getUid().equals(currentUid)) {
                if ("owner".equals(userRole)) {
                    canManage = true;
                } else if ("admin".equals(userRole) && "member".equals(m.getRole())) {
                    canManage = true;
                }
            }

            if (canManage) {
                holder.binding.btnOptions.setVisibility(View.VISIBLE);
                holder.binding.btnOptions.setOnClickListener(v -> {
                    if (actionListener != null) {
                        actionListener.onToggleRole(m, v);
                    }
                });
            }
        } else if (item.getType() == MemberListItem.TYPE_INVITATION) {
            Invitation i = item.getInvitation();
            boolean isAdminOrOwner = "admin".equals(userRole) || "owner".equals(userRole);

            // Para invitaciones por email, mostrar el email como nombre principal
            holder.binding.tvName.setText(i.getTargetEmail() != null ? i.getTargetEmail() : "-");
            holder.binding.tvEmail.setText("Invitación enviada");
            holder.binding.tvRole.setVisibility(View.GONE);
            
            holder.binding.tvPendingTag.setVisibility(View.VISIBLE);
            // Solo admin/owner pueden invitar, así que solo ellos pueden cancelar la invitación
            holder.binding.btnCancelInvite.setVisibility(isAdminOrOwner ? View.VISIBLE : View.GONE);

            if (isAdminOrOwner) {
                holder.binding.btnCancelInvite.setOnClickListener(v -> {
                    if (actionListener != null) {
                        actionListener.onCancel(i);
                    }
                });
            }
        } else if (item.getType() == MemberListItem.TYPE_REQUEST) {
            Invitation i = item.getInvitation();
            boolean isAdminOrOwner = "admin".equals(userRole) || "owner".equals(userRole);

            // Mostrar nombre o email si el nombre no está disponible
            String displayName = i.getRequesterName() != null && !i.getRequesterName().isEmpty()
                ? i.getRequesterName()
                : (i.getRequesterEmail() != null ? i.getRequesterEmail() : "Solicitud pendiente");
            holder.binding.tvName.setText(displayName);
            holder.binding.tvEmail.setText(i.getRequesterEmail() != null ? i.getRequesterEmail() : "-");
            holder.binding.tvRole.setVisibility(View.GONE);

            holder.binding.tvPendingTag.setVisibility(View.VISIBLE);
            // Solo admin/owner pueden aprobar/rechazar solicitudes de unión
            holder.binding.layoutRequestActions.setVisibility(isAdminOrOwner ? View.VISIBLE : View.GONE);

            if (isAdminOrOwner) {
                holder.binding.btnApprove.setOnClickListener(v -> {
                    if (actionListener != null) {
                        actionListener.onApprove(i);
                    }
                });

                holder.binding.btnReject.setOnClickListener(v -> {
                    if (actionListener != null) {
                        actionListener.onReject(i);
                    }
                });
            }
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemMemberBinding binding;
        ViewHolder(ItemMemberBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}

