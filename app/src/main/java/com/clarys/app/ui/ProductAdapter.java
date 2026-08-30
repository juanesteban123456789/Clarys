package com.clarys.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.clarys.app.R;
import com.clarys.app.data.SupabaseStore;
import com.clarys.app.model.Product;

import java.util.ArrayList;
import java.util.List;

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ProductViewHolder> {
    public interface ProductActionListener {
        void onPrimaryAction(Product product);

        void onSecondaryAction(Product product);
    }

    private SupabaseStore store;
    private final ProductActionListener listener;
    private final String primaryLabel;
    private final String secondaryLabel;
    private final boolean showAdminInfo;
    private List<Product> products = new ArrayList<>();

    public ProductAdapter(String primaryLabel, String secondaryLabel, boolean showAdminInfo,
            ProductActionListener listener) {
        this.primaryLabel = primaryLabel;
        this.secondaryLabel = secondaryLabel;
        this.showAdminInfo = showAdminInfo;
        this.listener = listener;
    }

    public void submitList(List<Product> newProducts) {
        products = new ArrayList<>(newProducts);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ProductViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_product, parent, false);
        if (store == null) {
            store = SupabaseStore.getInstance(parent.getContext());
        }
        return new ProductViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProductViewHolder holder, int position) {
        Product product = products.get(position);
        holder.name.setText(product.getName());
        String internalCode =
                product.getInternalCode() == null
                        ? ""
                        : product.getInternalCode().trim();

        holder.category.setText(
                product.getCategory()
                        + (internalCode.isEmpty()
                        ? ""
                        : " | Código " + internalCode)
        );
        holder.price.setText(store.formatMoney(product.getSalePrice()));
        holder.description.setText(product.getDescription());
        holder.stock.setText(buildStockLabel(product));
        holder.extra.setText(showAdminInfo
                ? "Costo " + store.formatMoney(product.getPurchasePrice())
                        + " | Margen " + store.formatMoney(product.getPotentialProfit())
                        + " | Vendidas " + product.getSoldUnits()
                : buildPublicExtra(product));
        if (product.getImageUrl().isEmpty()) {
            holder.image.setVisibility(View.GONE);
            Glide.with(holder.image).clear(holder.image);
        } else {
            holder.image.setVisibility(View.VISIBLE);
            Glide.with(holder.image)
                    .load(product.getImageUrl())
                    .centerCrop()
                    .into(holder.image);
        }
        holder.primaryButton.setText(primaryLabel);
        holder.secondaryButton.setText(secondaryLabel);
        holder.primaryButton.setOnClickListener(view -> listener.onPrimaryAction(product));
        holder.secondaryButton.setOnClickListener(view -> listener.onSecondaryAction(product));
    }

    private String buildStockLabel(Product product) {
        if (!product.isActive()) {
            return "Inactivo";
        }
        if (product.isOutOfStock()) {
            return "Agotado";
        }
        if (product.isLowStock()) {
            return "Stock bajo: " + product.getStock();
        }
        return "Stock: " + product.getStock();
    }

    private String buildPublicExtra(Product product) {
        String workshop = product.getWorkshopName().isEmpty() ? "" : " | Taller " + product.getWorkshopName();
        String whatsapp = product.getWorkshopWhatsapp().isEmpty() ? "" : " | WhatsApp " + product.getWorkshopWhatsapp();
        return "Tallas " + product.getSizes() + " | Colores " + product.getColors() + workshop + whatsapp;
    }

    @Override
    public int getItemCount() {
        return products.size();
    }

    static class ProductViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView category;
        final TextView price;
        final TextView description;
        final TextView stock;
        final TextView extra;
        final ImageView image;
        final Button primaryButton;
        final Button secondaryButton;

        ProductViewHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.imageProduct);
            name = itemView.findViewById(R.id.textProductName);
            category = itemView.findViewById(R.id.textProductCategory);
            price = itemView.findViewById(R.id.textProductPrice);
            description = itemView.findViewById(R.id.textProductDescription);
            stock = itemView.findViewById(R.id.textProductStock);
            extra = itemView.findViewById(R.id.textProductExtra);
            primaryButton = itemView.findViewById(R.id.buttonProductPrimary);
            secondaryButton = itemView.findViewById(R.id.buttonProductSecondary);
        }
    }
}
