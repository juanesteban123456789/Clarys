package com.clarys.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.clarys.app.R;
import com.clarys.app.data.SupabaseStore;
import com.clarys.app.model.Customer;

import java.util.ArrayList;
import java.util.List;

public class CustomerAdapter extends RecyclerView.Adapter<CustomerAdapter.CustomerViewHolder> {
    private SupabaseStore store;
    private List<Customer> customers = new ArrayList<>();

    public void submitList(List<Customer> newCustomers) {
        customers = new ArrayList<>(newCustomers);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CustomerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_customer, parent, false);
        if (store == null) {
            store = SupabaseStore.getInstance(parent.getContext());
        }
        return new CustomerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CustomerViewHolder holder, int position) {
        Customer customer = customers.get(position);
        holder.name.setText(customer.getName());
        holder.phone.setText(customer.getPhone());
        holder.summary.setText(customer.getTotalOrders() + " compras | "
                + store.formatMoney(customer.getTotalSpent()));
    }

    @Override
    public int getItemCount() {
        return customers.size();
    }

    static class CustomerViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView phone;
        final TextView summary;

        CustomerViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.textCustomerName);
            phone = itemView.findViewById(R.id.textCustomerPhone);
            summary = itemView.findViewById(R.id.textCustomerSummary);
        }
    }
}
