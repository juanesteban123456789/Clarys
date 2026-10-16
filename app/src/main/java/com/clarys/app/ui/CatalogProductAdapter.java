package com.clarys.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatButton;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.clarys.app.R;
import com.clarys.app.data.SupabaseStore;
import com.clarys.app.model.Product;
import com.clarys.app.util.CatalogOrganizer;

import java.util.ArrayList;
import java.util.List;

/** Muestra productos compactos en carruseles o cuadrículas del catálogo. */
public class CatalogProductAdapter
        extends RecyclerView.Adapter<CatalogProductAdapter.ProductViewHolder> {

    public interface ProductActionListener {
        void onOpenProduct(Product product);

        void onSecondaryAction(Product product);
    }

    private final ProductActionListener listener;
    private final boolean adminActions;
    private final boolean gridMode;
    private final String detailLabel;
    private final List<Product> products = new ArrayList<>();
    private SupabaseStore store;

    public CatalogProductAdapter(
            boolean adminActions,
            boolean gridMode,
            ProductActionListener listener) {
        this(adminActions, gridMode, null, listener);
    }

    public CatalogProductAdapter(
            boolean adminActions,
            boolean gridMode,
            String detailLabel,
            ProductActionListener listener) {
        this.adminActions = adminActions;
        this.gridMode = gridMode;
        this.detailLabel = detailLabel;
        this.listener = listener;
    }

    public void submitList(List<Product> newProducts) {
        int previousSize = products.size();
        products.clear();
        if (previousSize > 0) {
            notifyItemRangeRemoved(0, previousSize);
        }
        if (newProducts != null) {
            products.addAll(newProducts);
        }
        if (!products.isEmpty()) {
            notifyItemRangeInserted(0, products.size());
        }
    }

    @NonNull
    @Override
    public ProductViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_catalog_product, parent, false);
        store = SupabaseStore.getInstance(parent.getContext());

        if (gridMode) {
            int spacing = Math.round(6 * parent.getResources().getDisplayMetrics().density);
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(spacing, spacing, spacing, spacing);
            view.setLayoutParams(params);
        }

        return new ProductViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProductViewHolder holder, int position) {
        Product product = products.get(position);
        holder.name.setText(product.getName());
        holder.category.setText(CatalogOrganizer.formatCategoryLabel(product.getCategory()));
        holder.price.setText(store.formatMoney(product.getSalePrice()));
        holder.stock.setText(buildStockLabel(product));
        if (detailLabel != null && !detailLabel.trim().isEmpty()) {
            holder.detailButton.setText(detailLabel);
        }
        holder.workshop.setVisibility(product.getWorkshopName().isEmpty()
                ? View.GONE
                : View.VISIBLE);
        holder.workshop.setText(product.getWorkshopName());

        if (product.getImageUrl().isEmpty()) {
            Glide.with(holder.image).clear(holder.image);
            holder.image.setImageResource(R.drawable.logo_taller_clarys);
            holder.image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        } else {
            holder.image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Glide.with(holder.image)
                .load(product.getImageUrl())
                .placeholder(R.drawable.logo_taller_clarys)
                .error(R.drawable.logo_taller_clarys)
                .centerCrop()
                .into(holder.image);
        }

        holder.detailButton.setOnClickListener(view -> listener.onOpenProduct(product));
        holder.itemView.setOnClickListener(view -> listener.onOpenProduct(product));
        holder.secondaryButton.setOnClickListener(view -> listener.onSecondaryAction(product));

        if (adminActions) {
            holder.secondaryButton.setImageResource(R.drawable.ic_edit);
            holder.secondaryButton.setContentDescription("Editar " + product.getName());
            holder.secondaryButton.setEnabled(true);
            holder.secondaryButton.setAlpha(1f);
        } else {
            boolean available = product.isActive() && !product.isOutOfStock();
            holder.secondaryButton.setImageResource(R.drawable.ic_cart);
            holder.secondaryButton.setContentDescription("Agregar " + product.getName() + " al carrito");
            holder.secondaryButton.setEnabled(available);
            holder.secondaryButton.setAlpha(available ? 1f : 0.45f);
        }

        holder.secondaryButton.setOnClickListener(
                view -> listener.onSecondaryAction(product));
    }

    private String buildStockLabel(Product product) {
        if (adminActions) {
            return "Total " + product.getTotalStock() + " · reservado " + product.getReservedStock()
                    + " · libre " + product.getStock();
        }
        if (!product.isActive()) {
            return "Inactivo";
        }
        if (product.isOutOfStock()) {
            return "Agotado";
        }
        if (product.isLowStock()) {
            return "Últimas " + product.getStock();
        }
        return product.getStock() + " disponibles";
    }

    @Override
    public int getItemCount() {
        return products.size();
    }

    static class ProductViewHolder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView category;
        final TextView name;
        final TextView workshop;
        final TextView price;
        final TextView stock;
        final AppCompatButton detailButton;
        final ImageButton secondaryButton;

        ProductViewHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.imageCatalogProduct);
            category = itemView.findViewById(R.id.textCatalogCategory);
            name = itemView.findViewById(R.id.textCatalogProductName);
            workshop = itemView.findViewById(R.id.textCatalogWorkshop);
            price = itemView.findViewById(R.id.textCatalogPrice);
            stock = itemView.findViewById(R.id.textCatalogStock);
            detailButton = itemView.findViewById(R.id.buttonCatalogDetail);
            secondaryButton = itemView.findViewById(R.id.buttonCatalogSecondary);
        }
    }
}
