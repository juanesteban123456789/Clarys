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
import com.clarys.app.model.Sale;

import java.util.ArrayList;
import java.util.List;

public class SaleAdapter extends RecyclerView.Adapter<SaleAdapter.SaleViewHolder> {
    public interface SaleActionListener {
        void onOpenSale(Sale sale);
    }

    private SupabaseStore store;
    private final SaleActionListener listener;
    private List<Sale> sales = new ArrayList<>();

    public SaleAdapter(SaleActionListener listener) {
        this.listener = listener;
    }

    public void submitList(List<Sale> newSales) {
        sales = new ArrayList<>(newSales);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SaleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_sale, parent, false);
        if (store == null) {
            store = SupabaseStore.getInstance(parent.getContext());
        }
        return new SaleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SaleViewHolder holder, int position) {
        Sale sale = sales.get(position);
        holder.title.setText("Venta #" + sale.getId() + " | " + sale.getStatus());
        holder.customer.setText(sale.getCustomerName() + " | " + sale.getCustomerPhone());
        holder.total.setText(store.formatMoney(sale.getTotal()) + " | " + sale.getTotalUnits() + " und");
        holder.openButton.setOnClickListener(view -> listener.onOpenSale(sale));
    }

    @Override
    public int getItemCount() {
        return sales.size();
    }

    static class SaleViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView customer;
        final TextView total;
        final Button openButton;

        SaleViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.textSaleTitle);
            customer = itemView.findViewById(R.id.textSaleCustomer);
            total = itemView.findViewById(R.id.textSaleTotal);
            openButton = itemView.findViewById(R.id.buttonSaleOpen);
        }
    }
}
