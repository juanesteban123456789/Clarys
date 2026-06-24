package com.clarys.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.clarys.app.R;
import com.clarys.app.model.InventoryMovement;

import java.util.ArrayList;
import java.util.List;

public class InventoryMovementAdapter extends RecyclerView.Adapter<InventoryMovementAdapter.MovementViewHolder> {
    private List<InventoryMovement> movements = new ArrayList<>();

    public void submitList(List<InventoryMovement> newMovements) {
        movements = new ArrayList<>(newMovements);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MovementViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_movement, parent, false);
        return new MovementViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MovementViewHolder holder, int position) {
        InventoryMovement movement = movements.get(position);
        holder.title.setText(movement.getType() + " | " + movement.getProductName());
        holder.detail.setText("Cantidad " + movement.getQuantity()
                + " | " + movement.getPreviousStock() + " -> " + movement.getNewStock());
        holder.reason.setText(movement.getReason());
    }

    @Override
    public int getItemCount() {
        return movements.size();
    }

    static class MovementViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView detail;
        final TextView reason;

        MovementViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.textMovementTitle);
            detail = itemView.findViewById(R.id.textMovementDetail);
            reason = itemView.findViewById(R.id.textMovementReason);
        }
    }
}
