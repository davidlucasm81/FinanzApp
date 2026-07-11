package com.finanzapp.app.ui.family;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.finanzapp.app.data.model.Invitation;
import com.finanzapp.app.databinding.ItemJoinRequestBinding;

import java.util.ArrayList;
import java.util.List;

public class JoinRequestAdapter extends RecyclerView.Adapter<JoinRequestAdapter.ViewHolder> {
    private List<Invitation> requests = new ArrayList<>();
    private final OnActionListener listener;

    public interface OnActionListener {
        void onApprove(Invitation invitation);
        void onReject(Invitation invitation);
    }

    public JoinRequestAdapter(OnActionListener listener) {
        this.listener = listener;
    }

    public void setRequests(List<Invitation> requests) {
        this.requests = requests;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemJoinRequestBinding binding = ItemJoinRequestBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Invitation invitation = requests.get(position);
        holder.binding.tvUserId.setText("Usuario: " + invitation.getRequestedByUid());
        holder.binding.tvDate.setText(invitation.getCreatedAt().toDate().toString());

        holder.binding.btnApprove.setOnClickListener(v -> listener.onApprove(invitation));
        holder.binding.btnReject.setOnClickListener(v -> listener.onReject(invitation));
    }

    @Override
    public int getItemCount() {
        return requests.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemJoinRequestBinding binding;
        ViewHolder(ItemJoinRequestBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
