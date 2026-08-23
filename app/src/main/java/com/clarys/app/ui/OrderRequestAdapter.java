package com.clarys.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.clarys.app.R;
import com.clarys.app.model.OrderRequest;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OrderRequestAdapter
        extends RecyclerView.Adapter<OrderRequestAdapter.OrderViewHolder> {

    public interface OrderActionListener {
        void onOrderAction(OrderRequest order);
    }

    private final List<OrderRequest> orders =
            new ArrayList<>();

    private final OrderActionListener listener;


    public OrderRequestAdapter(
            OrderActionListener listener) {

        this.listener = listener;
    }


    public void submitList(
            List<OrderRequest> newOrders) {

        orders.clear();

        if (newOrders != null) {
            orders.addAll(newOrders);
        }

        notifyDataSetChanged();
    }


    @NonNull
    @Override
    public OrderViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType) {

        View view =
                LayoutInflater
                        .from(parent.getContext())
                        .inflate(
                                R.layout.item_order_request,
                                parent,
                                false
                        );

        return new OrderViewHolder(view);
    }


    @Override
    public void onBindViewHolder(
            @NonNull OrderViewHolder holder,
            int position) {

        OrderRequest order =
                orders.get(position);

        holder.title.setText(
                "Pedido #" + order.getId()
        );

        holder.customer.setText(
                order.getCustomerName()
                        + (
                        order.getCustomerPhone().isEmpty()
                                ? ""
                                : " · " + order.getCustomerPhone()
                )
        );

        holder.units.setText(
                order.getTotalUnits()
                        + (order.getTotalUnits() == 1
                        ? " unidad"
                        : " unidades")
        );

        holder.total.setText(
                formatMoney(order.getTotal())
        );

        holder.status.setText(
                statusLabel(
                        order.getStatus()
                )
        );

        holder.openButton.setOnClickListener(view -> {

            if (listener != null) {
                listener.onOrderAction(order);
            }
        });
    }


    @Override
    public int getItemCount() {
        return orders.size();
    }


    private String statusLabel(
            String status) {

        if (OrderRequest.STATUS_PENDING.equals(status)) {
            return "PENDIENTE";
        }

        if (OrderRequest.STATUS_APPROVED.equals(status)) {
            return "APROBADO";
        }

        if (OrderRequest.STATUS_REJECTED.equals(status)) {
            return "DENEGADO";
        }

        if (OrderRequest.STATUS_COMPLETED.equals(status)) {
            return "COMPLETADO";
        }

        return "PEDIDO";
    }


    private String formatMoney(
            int value) {

        NumberFormat formatter =
                NumberFormat.getCurrencyInstance(
                        new Locale("es", "CO")
                );

        formatter.setMaximumFractionDigits(0);

        return formatter.format(value);
    }


    static class OrderViewHolder
            extends RecyclerView.ViewHolder {

        final TextView title;
        final TextView customer;
        final TextView units;
        final TextView total;
        final TextView status;
        final View openButton;


        OrderViewHolder(
                @NonNull View itemView) {

            super(itemView);

            title =
                    itemView.findViewById(
                            R.id.textOrderTitle
                    );

            customer =
                    itemView.findViewById(
                            R.id.textOrderCustomer
                    );

            units =
                    itemView.findViewById(
                            R.id.textOrderUnits
                    );

            total =
                    itemView.findViewById(
                            R.id.textOrderTotal
                    );

            status =
                    itemView.findViewById(
                            R.id.textOrderStatus
                    );

            openButton =
                    itemView.findViewById(
                            R.id.buttonOrderManage
                    );
        }
    }
}