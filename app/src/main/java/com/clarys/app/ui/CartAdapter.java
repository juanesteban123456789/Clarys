package com.clarys.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.clarys.app.R;
import com.clarys.app.data.SupabaseStore;
import com.clarys.app.model.CartItem;

import java.util.ArrayList;
import java.util.List;

public class CartAdapter extends RecyclerView.Adapter<CartAdapter.CartViewHolder> {
    public interface CartActionListener {
        void onQuantityChanged(CartItem item, int newQuantity);
    }

    private SupabaseStore store;
    private final CartActionListener listener;
    private List<CartItem> items = new ArrayList<>();

    public CartAdapter(CartActionListener listener) {
        this.listener = listener;
    }

    public void submitList(List<CartItem> newItems) {
        items = new ArrayList<>(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CartViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_cart, parent, false);
        if (store == null) {
            store = SupabaseStore.getInstance(parent.getContext());
        }
        return new CartViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CartViewHolder holder, int position) {
        CartItem item = items.get(position);
        holder.name.setText(item.getProduct().getName());
        holder.detail.setText(store.formatMoney(item.getProduct().getSalePrice())
                + " | disponible " + item.getProduct().getStock());
        holder.quantity.setText(String.valueOf(item.getQuantity()));
        holder.subtotal.setText(store.formatMoney(item.getSubtotal()));
        holder.minusButton.setOnClickListener(view -> listener.onQuantityChanged(item, item.getQuantity() - 1));
        holder.plusButton.setOnClickListener(view -> listener.onQuantityChanged(item, item.getQuantity() + 1));
        holder.removeButton.setOnClickListener(view -> listener.onQuantityChanged(item, 0));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class CartViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView detail;
        final TextView quantity;
        final TextView subtotal;
        final Button minusButton;
        final Button plusButton;
        final Button removeButton;

        CartViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.textCartName);
            detail = itemView.findViewById(R.id.textCartDetail);
            quantity = itemView.findViewById(R.id.textCartQuantity);
            subtotal = itemView.findViewById(R.id.textCartSubtotal);
            minusButton = itemView.findViewById(R.id.buttonCartMinus);
            plusButton = itemView.findViewById(R.id.buttonCartPlus);
            removeButton = itemView.findViewById(R.id.buttonCartRemove);
        }
    }
}
