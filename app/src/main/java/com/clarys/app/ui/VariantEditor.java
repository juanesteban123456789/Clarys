package com.clarys.app.ui;

import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.AppCompatButton;

import com.clarys.app.R;
import com.clarys.app.model.ProductVariant;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Editor de existencias por talla y color, conservando IDs de variantes reservadas. */
public final class VariantEditor {
    public interface Listener { void onSave(List<ProductVariant> variants); }
    private VariantEditor() { }

    public static void show(Context context, List<ProductVariant> current, Listener listener) {
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(context, 18);
        content.setPadding(padding, padding, padding, padding);
        List<Row> rows = new ArrayList<>();
        LinearLayout fields = new LinearLayout(context);
        fields.setOrientation(LinearLayout.VERTICAL);
        content.addView(fields);
        for (ProductVariant variant : current) { addRow(context, fields, rows, variant); }
        AppCompatButton add = new AppCompatButton(context);
        add.setText("Agregar combinación");
        add.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_add_product, 0, 0, 0);
        add.setOnClickListener(view -> {
            if (rows.size() < 100) {
                addRow(context, fields, rows, new ProductVariant(UUID.randomUUID().toString(), "", "", 0, 0));
            }
        });
        content.addView(add);
        ScrollView scroll = new ScrollView(context);
        scroll.addView(content);
        AlertDialog dialog = new AlertDialog.Builder(context).setTitle("Stock por talla y color")
                .setView(scroll).setNegativeButton("Cancelar", null).setPositiveButton("Guardar", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            List<ProductVariant> variants = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (Row row : rows) {
                String size = row.size.getText().toString().trim();
                String color = row.color.getText().toString().trim();
                String key = (size + ":" + color).toLowerCase(Locale.ROOT);
                if (size.isEmpty() || color.isEmpty() || !seen.add(key)) {
                    row.size.setError("Escribe talla/color y evita combinaciones repetidas");
                    return;
                }
                int stock;
                try { stock = Integer.parseInt(row.stock.getText().toString()); }
                catch (NumberFormatException exception) { row.stock.setError("Stock obligatorio"); return; }
                if (stock < row.variant.getReservedStock() || stock > 1000000) {
                    row.stock.setError("Debe cubrir la reserva y no superar 1.000.000");
                    return;
                }
                variants.add(new ProductVariant(row.variant.getId(), size, color, stock,
                        row.variant.getReservedStock()));
            }
            listener.onSave(variants);
            dialog.dismiss();
        }));
        dialog.show();
    }

    private static void addRow(Context context, LinearLayout fields, List<Row> rows, ProductVariant variant) {
        LinearLayout group = new LinearLayout(context);
        group.setOrientation(LinearLayout.VERTICAL);
        LinearLayout inputs = new LinearLayout(context);
        Row row = new Row();
        row.variant = variant;
        row.size = input(context, "Talla", variant.getSize(), false, 40);
        row.color = input(context, "Color", variant.getColor(), false, 60);
        row.stock = input(context, "Unidades", String.valueOf(variant.getStock()), true, 7);
        inputs.addView(row.size);
        inputs.addView(row.color);
        inputs.addView(row.stock);
        AppCompatImageButton remove = new AppCompatImageButton(context);
        remove.setImageResource(android.R.drawable.ic_menu_delete);
        remove.setContentDescription("Quitar combinación " + variant.getLabel());
        remove.setLayoutParams(new LinearLayout.LayoutParams(dp(context,48),dp(context,54)));
        remove.setEnabled(variant.getReservedStock()==0);
        remove.setOnClickListener(view -> { fields.removeView(group); rows.remove(row); });
        inputs.addView(remove);
        group.addView(inputs);
        if (variant.getReservedStock()>0) {
            TextView reserved = new TextView(context);
            reserved.setText("Reservadas: " + variant.getReservedStock());
            group.addView(reserved);
        }
        fields.addView(group);
        rows.add(row);
    }

    private static EditText input(Context context, String label, String value, boolean number, int max) {
        EditText input = new EditText(context);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,dp(context,54),1);
        params.setMarginEnd(dp(context,4));
        input.setLayoutParams(params);
        input.setHint(label);
        input.setContentDescription(label);
        input.setText(value);
        input.setSingleLine(true);
        input.setTextSize(12);
        input.setInputType(number ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(max)});
        return input;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
    private static final class Row {
        ProductVariant variant;
        EditText size;
        EditText color;
        EditText stock;
    }
}
