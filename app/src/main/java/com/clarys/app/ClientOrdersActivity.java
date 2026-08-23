package com.clarys.app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.clarys.app.data.StoreCallback;
import com.clarys.app.data.SupabaseStore;
import com.clarys.app.model.CartItem;
import com.clarys.app.model.OrderRequest;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;


public class ClientOrdersActivity extends BaseScreenActivity {

    private static final String PREFS_NAME =
            "clarys_client_orders";

    private static final String KEY_LAST_PHONE =
            "last_phone";


    private SupabaseStore store;

    private EditText inputPhone;

    private AppCompatButton buttonSearch;

    private ProgressBar progress;

    private TextView textStatus;

    private TextView textEmpty;

    private RecyclerView recyclerOrders;

    private ClientOrdersAdapter adapter;


    // =========================================================
    // ON CREATE
    // =========================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_client_orders
        );


        store =
                SupabaseStore.getInstance(this);


        bindViews();

        setupNavigation();

        setupRecycler();

        restoreLastPhone();

        setupActions();

        renderInitialState();
    }


    // =========================================================
    // PANTALLA PÚBLICA
    // =========================================================

    @Override
    protected boolean isPublicScreen() {

        /*
         * El cliente NO necesita iniciar sesión
         * para consultar sus pedidos.
         */
        return true;
    }


    // =========================================================
    // VISTAS
    // =========================================================

    private void bindViews() {

        inputPhone =
                findViewById(
                        R.id.inputClientOrdersPhone
                );


        buttonSearch =
                findViewById(
                        R.id.buttonClientOrdersSearch
                );


        progress =
                findViewById(
                        R.id.progressClientOrders
                );


        textStatus =
                findViewById(
                        R.id.textClientOrdersStatus
                );


        textEmpty =
                findViewById(
                        R.id.textClientOrdersEmpty
                );


        recyclerOrders =
                findViewById(
                        R.id.recyclerClientOrders
                );
    }


    // =========================================================
    // NAVEGACIÓN
    // =========================================================

    private void setupNavigation() {

        View buttonBack =
                findViewById(
                        R.id.buttonHeaderBack
                );


        View buttonHome =
                findViewById(
                        R.id.buttonHeaderHome
                );


        if (buttonBack != null) {

            buttonBack.setOnClickListener(
                    view ->
                            getOnBackPressedDispatcher()
                                    .onBackPressed()
            );
        }


        /*
         * Esta es una pantalla de cliente.
         *
         * Por eso Inicio siempre regresa al catálogo,
         * independientemente de que exista una sesión
         * administrativa guardada.
         */
        if (buttonHome != null) {

            buttonHome.setOnClickListener(
                    view -> openClientCatalog()
            );
        }
    }


    private void openClientCatalog() {

        Intent intent =
                new Intent(
                        this,
                        CatalogActivity.class
                );


        /*
         * Garantizamos que el catálogo se abra
         * como catálogo de cliente.
         */
        intent.putExtra(
                "adminCatalog",
                false
        );


        intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        );


        startActivity(intent);
    }


    // =========================================================
    // RECYCLER
    // =========================================================

    private void setupRecycler() {

        recyclerOrders.setLayoutManager(
                new LinearLayoutManager(this)
        );


        adapter =
                new ClientOrdersAdapter();


        recyclerOrders.setAdapter(
                adapter
        );
    }


    // =========================================================
    // ACCIONES
    // =========================================================

    private void setupActions() {

        buttonSearch.setOnClickListener(
                view -> searchOrders()
        );


        /*
         * Si el usuario pulsa "buscar" desde el teclado,
         * también realizamos la consulta.
         */
        inputPhone.setOnEditorActionListener(
                (textView, actionId, event) -> {

                    searchOrders();

                    return true;
                }
        );
    }


    // =========================================================
    // BUSCAR PEDIDOS
    // =========================================================

    private void searchOrders() {

        String phone =
                inputPhone
                        .getText()
                        .toString()
                        .trim();


        // -----------------------------------------------------
        // VALIDACIÓN BÁSICA
        // -----------------------------------------------------

        if (phone.isEmpty()) {

            showMessage(
                    "Escribe tu número de teléfono"
            );

            inputPhone.requestFocus();

            return;
        }


        String digits =
                phone.replaceAll(
                        "[^0-9]",
                        ""
                );


        if (digits.length() < 7) {

            showMessage(
                    "Escribe un número de teléfono válido"
            );

            inputPhone.requestFocus();

            return;
        }


        hideKeyboard();

        setLoading(true);


        // =====================================================
        // AQUÍ SE LLAMA findClientOrdersByPhone()
        // =====================================================

        store.findClientOrdersByPhone(
                phone,

                new StoreCallback<List<OrderRequest>>() {

                    @Override
                    public void onSuccess(
                            List<OrderRequest> orders) {

                        setLoading(false);


                        saveLastPhone(
                                phone
                        );


                        if (orders == null
                                || orders.isEmpty()) {

                            showEmptyState();

                            return;
                        }


                        showOrders(
                                orders
                        );
                    }


                    @Override
                    public void onError(
                            String message) {

                        setLoading(false);

                        showErrorState(
                                message
                        );
                    }
                }
        );
    }


    // =========================================================
    // ESTADOS DE PANTALLA
    // =========================================================

    private void renderInitialState() {

        progress.setVisibility(
                View.GONE
        );


        recyclerOrders.setVisibility(
                View.GONE
        );


        textEmpty.setVisibility(
                View.GONE
        );


        textStatus.setVisibility(
                View.VISIBLE
        );


        textStatus.setText(
                "Ingresa el número utilizado al realizar tu pedido."
        );
    }


    private void setLoading(
            boolean loading) {

        buttonSearch.setEnabled(
                !loading
        );


        inputPhone.setEnabled(
                !loading
        );


        progress.setVisibility(
                loading
                        ? View.VISIBLE
                        : View.GONE
        );


        if (loading) {

            recyclerOrders.setVisibility(
                    View.GONE
            );


            textEmpty.setVisibility(
                    View.GONE
            );


            textStatus.setVisibility(
                    View.VISIBLE
            );


            textStatus.setText(
                    "Consultando pedidos..."
            );
        }
    }


    private void showOrders(
            List<OrderRequest> orders) {

        adapter.submitList(
                orders
        );


        textEmpty.setVisibility(
                View.GONE
        );


        recyclerOrders.setVisibility(
                View.VISIBLE
        );


        textStatus.setVisibility(
                View.VISIBLE
        );


        int count =
                orders.size();


        textStatus.setText(
                count == 1

                        ? "Encontramos 1 pedido asociado a este número."

                        : "Encontramos "
                        + count
                        + " pedidos asociados a este número."
        );
    }


    private void showEmptyState() {

        adapter.submitList(
                new ArrayList<>()
        );


        recyclerOrders.setVisibility(
                View.GONE
        );


        textStatus.setVisibility(
                View.GONE
        );


        textEmpty.setVisibility(
                View.VISIBLE
        );


        textEmpty.setText(
                "No encontramos pedidos asociados a este número."
        );
    }


    private void showErrorState(
            String message) {

        recyclerOrders.setVisibility(
                View.GONE
        );


        textEmpty.setVisibility(
                View.VISIBLE
        );


        textStatus.setVisibility(
                View.GONE
        );


        textEmpty.setText(
                message == null
                        || message.trim().isEmpty()

                        ? "No se pudieron consultar los pedidos."

                        : message
        );
    }


    // =========================================================
    // TELÉFONO RECIENTE
    // =========================================================

    private void saveLastPhone(
            String phone) {

        getSharedPreferences(
                PREFS_NAME,
                MODE_PRIVATE
        )
                .edit()
                .putString(
                        KEY_LAST_PHONE,
                        phone
                )
                .apply();
    }


    private void restoreLastPhone() {

        String phone =
                getSharedPreferences(
                        PREFS_NAME,
                        MODE_PRIVATE
                )
                        .getString(
                                KEY_LAST_PHONE,
                                ""
                        );


        if (phone != null
                && !phone.isEmpty()) {

            inputPhone.setText(
                    phone
            );


            inputPhone.setSelection(
                    phone.length()
            );
        }
    }


    // =========================================================
    // TECLADO
    // =========================================================

    private void hideKeyboard() {

        View focused =
                getCurrentFocus();


        if (focused == null) {
            return;
        }


        InputMethodManager manager =
                (InputMethodManager)
                        getSystemService(
                                Context.INPUT_METHOD_SERVICE
                        );


        if (manager != null) {

            manager.hideSoftInputFromWindow(
                    focused.getWindowToken(),
                    0
            );
        }
    }


    // =========================================================
    // ADAPTER CLIENTE
    // =========================================================

    private class ClientOrdersAdapter
            extends RecyclerView.Adapter<ClientOrderViewHolder> {

        private final List<OrderRequest> orders =
                new ArrayList<>();


        public void submitList(
                List<OrderRequest> newOrders) {

            orders.clear();


            if (newOrders != null) {

                orders.addAll(
                        newOrders
                );
            }


            notifyDataSetChanged();
        }


        @NonNull
        @Override
        public ClientOrderViewHolder onCreateViewHolder(
                @NonNull ViewGroup parent,
                int viewType) {

            LinearLayout card =
                    createOrderCard(
                            parent.getContext()
                    );


            return new ClientOrderViewHolder(
                    card
            );
        }


        @Override
        public void onBindViewHolder(
                @NonNull ClientOrderViewHolder holder,
                int position) {

            holder.bind(
                    orders.get(position)
            );
        }


        @Override
        public int getItemCount() {

            return orders.size();
        }
    }


    // =========================================================
    // VIEW HOLDER
    // =========================================================

    private class ClientOrderViewHolder
            extends RecyclerView.ViewHolder {

        private final TextView textNumber;

        private final TextView textDate;

        private final TextView textStatus;

        private final TextView textUnits;

        private final TextView textTotal;

        private final TextView textHint;

        private final LinearLayout layoutItems;


        private boolean expanded = false;


        public ClientOrderViewHolder(
                @NonNull View itemView) {

            super(itemView);


            LinearLayout root =
                    (LinearLayout) itemView;


            textNumber =
                    (TextView) root.getChildAt(0);


            textDate =
                    (TextView) root.getChildAt(1);


            textStatus =
                    (TextView) root.getChildAt(2);


            LinearLayout summary =
                    (LinearLayout) root.getChildAt(3);


            textUnits =
                    (TextView) summary.getChildAt(0);


            textTotal =
                    (TextView) summary.getChildAt(1);


            textHint =
                    (TextView) root.getChildAt(4);


            layoutItems =
                    (LinearLayout) root.getChildAt(5);
        }


        public void bind(
                OrderRequest order) {

            expanded = false;


            layoutItems.setVisibility(
                    View.GONE
            );


            textHint.setText(
                    "Toca para ver los productos"
            );


            textNumber.setText(
                    "Pedido #"
                            + order.getId()
            );


            textDate.setText(
                    formatOrderDate(
                            order.getCreatedAt()
                    )
            );


            textStatus.setText(
                    statusLabel(
                            order.getStatus()
                    )
            );


            textUnits.setText(
                    order.getTotalUnits()
                            + (
                            order.getTotalUnits() == 1
                                    ? " producto"
                                    : " productos"
                    )
            );


            textTotal.setText(
                    store.formatMoney(
                            order.getTotal()
                    )
            );


            renderOrderItems(
                    layoutItems,
                    order
            );


            itemView.setOnClickListener(
                    view -> {

                        expanded =
                                !expanded;


                        layoutItems.setVisibility(
                                expanded
                                        ? View.VISIBLE
                                        : View.GONE
                        );


                        textHint.setText(
                                expanded
                                        ? "Toca para ocultar los productos"
                                        : "Toca para ver los productos"
                        );
                    }
            );
        }
    }


    // =========================================================
    // CREAR CARD
    // =========================================================

    private LinearLayout createOrderCard(
            Context context) {

        LinearLayout root =
                new LinearLayout(
                        context
                );


        root.setOrientation(
                LinearLayout.VERTICAL
        );


        root.setPadding(
                dp(16),
                dp(16),
                dp(16),
                dp(16)
        );


        root.setBackgroundResource(
                R.drawable.bg_product_info_card
        );


        RecyclerView.LayoutParams rootParams =
                new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );


        rootParams.setMargins(
                0,
                dp(6),
                0,
                dp(6)
        );


        root.setLayoutParams(
                rootParams
        );


        // -----------------------------------------------------
        // NÚMERO
        // -----------------------------------------------------

        TextView number =
                createTextView(
                        context,
                        17,
                        true
                );


        number.setTextColor(
                ContextCompat.getColor(
                        context,
                        R.color.clarys_dark
                )
        );


        root.addView(
                number
        );


        // -----------------------------------------------------
        // FECHA
        // -----------------------------------------------------

        TextView date =
                createTextView(
                        context,
                        12,
                        false
                );


        date.setTextColor(
                ContextCompat.getColor(
                        context,
                        R.color.clarys_muted
                )
        );


        LinearLayout.LayoutParams dateParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );


        dateParams.topMargin =
                dp(3);


        date.setLayoutParams(
                dateParams
        );


        root.addView(
                date
        );


        // -----------------------------------------------------
        // ESTADO
        // -----------------------------------------------------

        TextView status =
                createTextView(
                        context,
                        12,
                        true
                );


        status.setTextColor(
                ContextCompat.getColor(
                        context,
                        R.color.clarys_mid
                )
        );


        status.setGravity(
                Gravity.CENTER
        );


        status.setBackgroundResource(
                R.drawable.bg_product_category
        );


        status.setPadding(
                dp(10),
                dp(6),
                dp(10),
                dp(6)
        );


        LinearLayout.LayoutParams statusParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );


        statusParams.topMargin =
                dp(10);


        status.setLayoutParams(
                statusParams
        );


        root.addView(
                status
        );


        // -----------------------------------------------------
        // RESUMEN
        // -----------------------------------------------------

        LinearLayout summary =
                new LinearLayout(
                        context
                );


        summary.setOrientation(
                LinearLayout.HORIZONTAL
        );


        LinearLayout.LayoutParams summaryParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );


        summaryParams.topMargin =
                dp(14);


        summary.setLayoutParams(
                summaryParams
        );


        TextView units =
                createTextView(
                        context,
                        13,
                        false
                );


        units.setTextColor(
                ContextCompat.getColor(
                        context,
                        R.color.clarys_muted
                )
        );


        units.setLayoutParams(
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                )
        );


        summary.addView(
                units
        );


        TextView total =
                createTextView(
                        context,
                        16,
                        true
                );


        total.setTextColor(
                ContextCompat.getColor(
                        context,
                        R.color.clarys_dark
                )
        );


        summary.addView(
                total
        );


        root.addView(
                summary
        );


        // -----------------------------------------------------
        // HINT
        // -----------------------------------------------------

        TextView hint =
                createTextView(
                        context,
                        11,
                        false
                );


        hint.setTextColor(
                ContextCompat.getColor(
                        context,
                        R.color.clarys_mid
                )
        );


        LinearLayout.LayoutParams hintParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );


        hintParams.topMargin =
                dp(12);


        hint.setLayoutParams(
                hintParams
        );


        root.addView(
                hint
        );


        // -----------------------------------------------------
        // ITEMS
        // -----------------------------------------------------

        LinearLayout items =
                new LinearLayout(
                        context
                );


        items.setOrientation(
                LinearLayout.VERTICAL
        );


        items.setVisibility(
                View.GONE
        );


        LinearLayout.LayoutParams itemsParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );


        itemsParams.topMargin =
                dp(12);


        items.setLayoutParams(
                itemsParams
        );


        root.addView(
                items
        );


        return root;
    }


    // =========================================================
    // PRODUCTOS DEL PEDIDO
    // =========================================================

    private void renderOrderItems(
            LinearLayout container,
            OrderRequest order) {

        container.removeAllViews();


        List<CartItem> items =
                order.getItems();


        if (items == null
                || items.isEmpty()) {

            TextView empty =
                    createTextView(
                            this,
                            12,
                            false
                    );


            empty.setText(
                    "No hay información de productos."
            );


            container.addView(
                    empty
            );


            return;
        }


        for (CartItem item : items) {

            if (item == null
                    || item.getProduct() == null) {

                continue;
            }


            LinearLayout row =
                    new LinearLayout(
                            this
                    );


            row.setOrientation(
                    LinearLayout.HORIZONTAL
            );


            row.setPadding(
                    0,
                    dp(7),
                    0,
                    dp(7)
            );


            TextView product =
                    createTextView(
                            this,
                            13,
                            false
                    );


            product.setText(
                    item.getProduct().getName()
                            + " × "
                            + item.getQuantity()
            );


            product.setTextColor(
                    ContextCompat.getColor(
                            this,
                            R.color.clarys_dark
                    )
            );


            product.setLayoutParams(
                    new LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f
                    )
            );


            row.addView(
                    product
            );


            TextView subtotal =
                    createTextView(
                            this,
                            13,
                            true
                    );


            subtotal.setText(
                    store.formatMoney(
                            item.getSubtotal()
                    )
            );


            subtotal.setTextColor(
                    ContextCompat.getColor(
                            this,
                            R.color.clarys_mid
                    )
            );


            row.addView(
                    subtotal
            );


            container.addView(
                    row
            );
        }
    }


    // =========================================================
    // TEXT VIEW AUXILIAR
    // =========================================================

    private TextView createTextView(
            Context context,
            int size,
            boolean bold) {

        TextView text =
                new TextView(
                        context
                );


        text.setTextSize(
                size
        );


        if (bold) {

            text.setTypeface(
                    text.getTypeface(),
                    android.graphics.Typeface.BOLD
            );
        }


        return text;
    }


    // =========================================================
    // ESTADOS
    // =========================================================

    private String statusLabel(
            String status) {

        if (OrderRequest.STATUS_PENDING
                .equalsIgnoreCase(status)) {

            return "PENDIENTE";
        }


        if (OrderRequest.STATUS_APPROVED
                .equalsIgnoreCase(status)) {

            return "APROBADO";
        }


        if (OrderRequest.STATUS_REJECTED
                .equalsIgnoreCase(status)) {

            return "DENEGADO";
        }


        if (OrderRequest.STATUS_COMPLETED
                .equalsIgnoreCase(status)) {

            return "COMPLETADO";
        }


        return status == null
                || status.trim().isEmpty()

                ? "PENDIENTE"

                : status.toUpperCase(
                Locale.ROOT
        );
    }


    // =========================================================
    // FECHA
    // =========================================================

    private String formatOrderDate(
            String value) {

        if (value == null
                || value.trim().isEmpty()) {

            return "";
        }


        String[] patterns = {

                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",

                "yyyy-MM-dd'T'HH:mm:ssXXX",

                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",

                "yyyy-MM-dd'T'HH:mm:ss'Z'"
        };


        for (String pattern : patterns) {

            try {

                SimpleDateFormat input =
                        new SimpleDateFormat(
                                pattern,
                                Locale.US
                        );


                Date date =
                        input.parse(
                                value
                        );


                if (date != null) {

                    SimpleDateFormat output =
                            new SimpleDateFormat(
                                    "dd/MM/yyyy • HH:mm",
                                    new Locale(
                                            "es",
                                            "CO"
                                    )
                            );


                    return output.format(
                            date
                    );
                }


            } catch (Exception ignored) {

            }
        }


        /*
         * Si Supabase cambia ligeramente el formato,
         * mostramos al menos la fecha.
         */
        if (value.length() >= 10) {

            return value.substring(
                    0,
                    10
            );
        }


        return value;
    }


    // =========================================================
    // DP
    // =========================================================

    private int dp(
            int value) {

        return Math.round(
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }
}