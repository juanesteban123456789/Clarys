package com.clarys.app.ui;

import android.content.Context;
import androidx.appcompat.app.AlertDialog;
import com.clarys.app.model.Product;
import com.clarys.app.model.ProductVariant;
import java.util.List;

public final class VariantPicker {
    public interface Listener { void onSelect(String variantId); }
    private VariantPicker() { }

    public static void show(Context context, Product product, Listener listener) {
        List<ProductVariant> variants = product.getVariants();
        if (variants.isEmpty()) { listener.onSelect(""); return; }
        String[] labels = new String[variants.size()];
        for (int index=0; index<variants.size(); index++) {
            ProductVariant variant=variants.get(index);
            labels[index]=variant.getLabel()+" · disponibles "+variant.getAvailableStock();
        }
        new AlertDialog.Builder(context).setTitle("Selecciona talla y color")
                .setItems(labels,(dialog,index)-> listener.onSelect(variants.get(index).getId()))
                .setNegativeButton("Cancelar",null).show();
    }
}
