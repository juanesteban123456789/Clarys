package com.clarys.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Spinner;
import android.widget.CheckBox;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.ImageView;
import androidx.appcompat.app.AlertDialog;

import androidx.appcompat.widget.AppCompatButton;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.clarys.app.data.StoreCallback;
import com.clarys.app.data.SupabaseStore;
import com.clarys.app.model.CatalogSection;
import com.clarys.app.model.Product;
import com.clarys.app.ui.CatalogProductAdapter;
import com.clarys.app.util.CatalogOrganizer;
import com.clarys.app.util.CatalogFilters;
import com.clarys.app.ui.VariantPicker;

import java.util.ArrayList;
import java.util.List;

/** Catálogo público organizado en carruseles horizontales por categoría. */
public class CatalogActivity extends BaseScreenActivity {

    public static final String EXTRA_ADMIN_CATALOG = "adminCatalog";
    public static final String EXTRA_OPENED_FROM_ADMIN = "openedFromAdmin";

    private SupabaseStore store;
    private EditText searchInput;
    private ScrollView catalogScroll;
    private LinearLayout categoryChips;
    private LinearLayout categorySections;
    private LinearLayout resultsContainer;
    private TextView resultsTitle;
    private TextView resultsCount;
    private TextView emptyState;
    private ProgressBar catalogProgress;
    private AppCompatButton catalogStateAction;
    private View searchButton;
    private CatalogProductAdapter resultsAdapter;
    private List<CatalogSection> currentSections = new ArrayList<>();
    private boolean adminCatalog;
    private boolean openedFromAdmin;
    private CatalogFilters filters = new CatalogFilters();
    private boolean favoritesOnly;
    private boolean loading;
    private boolean suppressSearch;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private final Runnable searchTask = this::searchProducts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_catalog);

        store = SupabaseStore.getInstance(this);
        resolveCatalogContext();
        bindViews();
        setupHeader(R.id.buttonHeaderHome, R.id.buttonHeaderBack);
        configureHeader();
        setupSearch();
        setupBottomNavigation();
        setupResultsGrid();
        setupFilters();
        renderAccessControls();
        findViewById(R.id.catalogContent).requestFocus();
    }

    private void resolveCatalogContext() {
        boolean requestedAdminCatalog = getIntent().getBooleanExtra(EXTRA_ADMIN_CATALOG, false);
        adminCatalog = requestedAdminCatalog
                && BuildConfig.IS_ADMIN_APP
                && AppModeManager.isAdminMode(this)
                && store.isAuthenticated();
        openedFromAdmin = BuildConfig.IS_ADMIN_APP
                && getIntent().getBooleanExtra(EXTRA_OPENED_FROM_ADMIN, false)
                && AppModeManager.isAdminMode(this)
                && store.isAuthenticated();

        if (requestedAdminCatalog && !adminCatalog) {
            AppModeManager.enterClientMode(this);
        }
    }

    private void bindViews() {
        searchInput = findViewById(R.id.inputCatalogSearch);
        catalogScroll = findViewById(R.id.scrollCatalog);
        categoryChips = findViewById(R.id.containerCategoryChips);
        categorySections = findViewById(R.id.containerCatalogSections);
        resultsContainer = findViewById(R.id.containerCatalogResults);
        resultsTitle = findViewById(R.id.textCatalogResultsTitle);
        resultsCount = findViewById(R.id.textCatalogResultsCount);
        emptyState = findViewById(R.id.textCatalogEmpty);
        catalogProgress = findViewById(R.id.progressCatalog);
        catalogStateAction = findViewById(R.id.buttonCatalogStateAction);
        searchButton = findViewById(R.id.buttonCatalogSearch);
    }

    private void configureHeader() {
        if (!adminCatalog && !openedFromAdmin) {
            findViewById(R.id.buttonHeaderBack).setVisibility(View.GONE);
            findViewById(R.id.buttonHeaderHome).setVisibility(View.GONE);
        }
    }

    private void setupSearch() {
        searchButton.setOnClickListener(view -> searchProducts());
        searchInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchProducts();
                return true;
            }
            return false;
        });
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after) { }
            @Override public void onTextChanged(CharSequence s,int start,int before,int count) {
                if (suppressSearch || loading) { return; }
                searchHandler.removeCallbacks(searchTask);
                searchHandler.postDelayed(searchTask,300);
            }
            @Override public void afterTextChanged(Editable text) { }
        });
    }

    private void setupFilters() {
        findViewById(R.id.buttonCatalogHelp).setOnClickListener(view -> {
            new AlertDialog.Builder(this).setTitle("Clarys")
                    .setItems(new String[]{"Ayuda y contacto","Privacidad"},(dialog,index) -> {
                        Intent intent=new Intent(this,HelpActivity.class);
                        intent.putExtra("privacy",index==1);
                        startActivity(intent);
                    }).show();
        });
        findViewById(R.id.buttonCatalogFilters).setOnClickListener(view -> showFilters());
        ImageView favorites = findViewById(R.id.buttonCatalogFavorites);
        favorites.setOnClickListener(view -> {
            favoritesOnly = !favoritesOnly;
            favorites.setImageResource(favoritesOnly ? android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off);
            searchProducts();
        });
        Spinner sort = findViewById(R.id.spinnerCatalogSort);
        sort.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Nombre","Precio menor","Precio mayor","Más recientes"}));
        sort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id) {
                filters.sort=position;
                if (!loading && !store.getProducts().isEmpty()) { searchProducts(); }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    private List<Product> filteredProducts(String query) {
        List<Product> products = filters.apply(store.getProducts(),query);
        if (favoritesOnly) { products.removeIf(product -> !store.isFavorite(product.getId())); }
        return products;
    }

    private void showFilters() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18),dp(10),dp(18),dp(10));
        java.util.Set<String> categories = new java.util.TreeSet<>();
        java.util.Set<String> sizes = new java.util.TreeSet<>();
        java.util.Set<String> colors = new java.util.TreeSet<>();
        for (Product product : store.getProducts()) {
            categories.add(product.getCategory());
            for (String value : product.getSizes().split(",")) { if (!value.trim().isEmpty()) { sizes.add(value.trim()); } }
            for (String value : product.getColors().split(",")) { if (!value.trim().isEmpty()) { colors.add(value.trim()); } }
        }
        Spinner category = filterSpinner(form,"Categoría",categories,filters.category);
        Spinner size = filterSpinner(form,"Talla",sizes,filters.size);
        Spinner color = filterSpinner(form,"Color",colors,filters.color);
        EditText minimum = priceInput(form,"Precio mínimo",filters.minimumPrice == 0 ? "" : String.valueOf(filters.minimumPrice));
        EditText maximum = priceInput(form,"Precio máximo",filters.maximumPrice == Integer.MAX_VALUE ? "" : String.valueOf(filters.maximumPrice));
        CheckBox available = new CheckBox(this);
        available.setText("Solo productos disponibles");
        available.setChecked(filters.availableOnly);
        form.addView(available);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Filtrar catálogo").setView(scroll)
                .setNegativeButton("Cancelar",null).setNeutralButton("Restablecer",(d,w)-> {
                    filters = new CatalogFilters();
                    ((Spinner)findViewById(R.id.spinnerCatalogSort)).setSelection(0);
                    favoritesOnly=false;
                    ((ImageView)findViewById(R.id.buttonCatalogFavorites)).setImageResource(android.R.drawable.btn_star_big_off);
                    showCategoryOverview();
                }).setPositiveButton("Aplicar",null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            try {
                int low = minimum.getText().toString().isEmpty() ? 0 : Integer.parseInt(minimum.getText().toString());
                int high = maximum.getText().toString().isEmpty() ? Integer.MAX_VALUE : Integer.parseInt(maximum.getText().toString());
                if (high < low) { maximum.setError("El máximo debe ser mayor que el mínimo"); return; }
                filters.category=category.getSelectedItemPosition()==0 ? "" : category.getSelectedItem().toString();
                filters.size=size.getSelectedItemPosition()==0 ? "" : size.getSelectedItem().toString();
                filters.color=color.getSelectedItemPosition()==0 ? "" : color.getSelectedItem().toString();
                filters.minimumPrice=low; filters.maximumPrice=high; filters.availableOnly=available.isChecked();
                searchProducts(); dialog.dismiss();
            } catch(NumberFormatException error) { maximum.setError("Precio fuera del rango permitido"); }
        }));
        dialog.show();
    }

    private Spinner filterSpinner(LinearLayout form,String label,java.util.Set<String> choices,String selected) {
        TextView text = new TextView(this); text.setText(label); form.addView(text);
        List<String> values = new ArrayList<>(); values.add("Todas"); values.addAll(choices);
        Spinner spinner = new Spinner(this);
        spinner.setMinimumHeight(dp(48));
        spinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values));
        spinner.setSelection(Math.max(0,values.indexOf(selected)));
        form.addView(spinner);
        return spinner;
    }

    private EditText priceInput(LinearLayout form,String label,String value) {
        EditText input = new EditText(this);
        input.setHint(label); input.setText(value);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(9)});
        input.setMinimumHeight(dp(48));
        form.addView(input); return input;
    }

    private void setupResultsGrid() {
        RecyclerView results = findViewById(R.id.recyclerCatalogResults);
        results.setLayoutManager(new GridLayoutManager(this, 2));
        resultsAdapter = new CatalogProductAdapter(adminCatalog, true, productActions());
        results.setAdapter(resultsAdapter);
        findViewById(R.id.buttonBackToCategories)
                .setOnClickListener(view -> resetCatalogHome());
    }

    private void setupBottomNavigation() {
        findViewById(R.id.buttonBottomCategories)
                .setOnClickListener(view -> resetCatalogHome());

        TextView cartButton = findViewById(R.id.buttonBottomCart);
        cartButton.setOnClickListener(view -> {
            if (adminCatalog) {
                openScreen(ProductListActivity.class);
            } else {
                Intent intent = new Intent(this, CartActivity.class);
                intent.putExtra(EXTRA_OPENED_FROM_ADMIN, openedFromAdmin);
                startActivity(intent);
            }
        });

        TextView ordersButton = findViewById(R.id.buttonBottomOrders);
        ordersButton.setOnClickListener(view -> {
            if (adminCatalog) {
                openAdminOrders();
            } else {
                openClientOrders();
            }
        });

        TextView panelButton = findViewById(R.id.buttonBottomProfile);
        panelButton.setVisibility(openedFromAdmin ? View.VISIBLE : View.GONE);
        if (openedFromAdmin) {
            panelButton.setOnClickListener(view -> openAdminDashboard());
        }
    }

    private void renderAccessControls() {
        findViewById(R.id.buttonGoToSale).setVisibility(View.GONE);
        TextView cartButton = findViewById(R.id.buttonBottomCart);
        TextView ordersButton = findViewById(R.id.buttonBottomOrders);
        cartButton.setText(adminCatalog
                ? R.string.catalog_admin_products
                : R.string.catalog_cart);
        ordersButton.setText(adminCatalog
                ? R.string.catalog_admin_orders
                : R.string.catalog_orders);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if ((adminCatalog || openedFromAdmin)
                && (!store.isAuthenticated() || !AppModeManager.isAdminMode(this))) {
            adminCatalog = false;
            openedFromAdmin = false;
            AppModeManager.enterClientMode(this);
            renderAccessControls();
            findViewById(R.id.buttonBottomProfile).setVisibility(View.GONE);
        }

        loadCatalog();
    }

    private void loadCatalog() {
        showCatalogLoading();
        store.refreshProducts(adminCatalog, new StoreCallback<List<Product>>() {
            @Override
            public void onSuccess(List<Product> result) {
                loading=false;
                showCategoryOverview();
            }

            @Override
            public void onError(String message) {
                loading=false;
                if (store.searchProducts("", "Todos").isEmpty()) {
                    showCatalogError(message);
                } else {
                    showMessage(message);
                    showCategoryOverview();
                }
            }
        });
    }

    private void showCategoryOverview() {
        if (loading) { return; }
        List<Product> products = filteredProducts("");
        suppressSearch=true;
        searchInput.setText("");
        suppressSearch=false;
        currentSections = CatalogOrganizer.groupByCategory(products);
        resultsContainer.setVisibility(View.GONE);
        categorySections.setVisibility(products.isEmpty() ? View.GONE : View.VISIBLE);
        emptyState.setVisibility(products.isEmpty() ? View.VISIBLE : View.GONE);
        emptyState.setText(R.string.catalog_empty);
        catalogProgress.setVisibility(View.GONE);
        catalogStateAction.setVisibility(View.GONE);
        searchButton.setEnabled(true);
        renderCategoryChips(null);
        renderCategorySections();
        catalogScroll.post(() -> catalogScroll.scrollTo(0, 0));
    }

    private void renderCategoryChips(String selectedCategory) {
        categoryChips.removeAllViews();
        categoryChips.addView(createCategoryChip(
                getString(R.string.catalog_all),
                selectedCategory == null,
                this::showCategoryOverview));

        for (CatalogSection section : currentSections) {
            String category = section.getCategory();
            categoryChips.addView(createCategoryChip(
                    category,
                    category.equalsIgnoreCase(selectedCategory == null ? "" : selectedCategory),
                    () -> showCategoryProducts(section)));
        }
    }

    private TextView createCategoryChip(String label, boolean selected, Runnable action) {
        TextView chip = new TextView(this);
        int horizontalPadding = dp(15);
        chip.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(44)));
        ((LinearLayout.LayoutParams) chip.getLayoutParams()).setMarginEnd(dp(8));
        chip.setBackgroundResource(selected
                ? R.drawable.bg_category_chip_active
                : R.drawable.bg_category_chip);
        chip.setGravity(android.view.Gravity.CENTER);
        chip.setPadding(horizontalPadding, 0, horizontalPadding, 0);
        chip.setText(label);
        chip.setTextColor(getColor(selected ? R.color.clarys_light : R.color.clarys_mid));
        chip.setTextSize(12);
        chip.setTypeface(chip.getTypeface(), android.graphics.Typeface.BOLD);
        chip.setOnClickListener(view -> action.run());
        return chip;
    }

    private void renderCategorySections() {
        categorySections.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (CatalogSection section : currentSections) {
            View sectionView = inflater.inflate(
                    R.layout.item_catalog_category_section,
                    categorySections,
                    false);
            TextView title = sectionView.findViewById(R.id.textCategoryTitle);
            TextView count = sectionView.findViewById(R.id.textCategoryCount);
            AppCompatButton seeMore = sectionView.findViewById(R.id.buttonCategoryMore);
            RecyclerView products = sectionView.findViewById(R.id.recyclerCategoryProducts);

            title.setText(section.getCategory());
            int productCount = section.getProducts().size();
            count.setText(getResources().getQuantityString(
                    R.plurals.catalog_product_count,
                    productCount,
                    productCount));
            seeMore.setOnClickListener(view -> showCategoryProducts(section));

            products.setLayoutManager(
                    new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
            CatalogProductAdapter adapter =
                    new CatalogProductAdapter(adminCatalog, false, productActions());
            products.setAdapter(adapter);
            adapter.submitList(section.getProducts());
            categorySections.addView(sectionView);
        }
    }

    private void showCategoryProducts(CatalogSection section) {
        renderCategoryChips(section.getCategory());
        showResults(
                section.getCategory(),
                section.getProducts(),
                getResources().getQuantityString(
                        R.plurals.catalog_product_count,
                        section.getProducts().size(),
                        section.getProducts().size()));
    }

    private void searchProducts() {
        if (loading) { return; }
        String query = searchInput.getText().toString().trim();
        if (query.isEmpty() && filters.sort==0 && !favoritesOnly) {
            showCategoryOverview();
            return;
        }

        List<Product> matches = filteredProducts(query);
        renderCategoryChips("");
        showResults(
                favoritesOnly ? "Favoritos" : query.isEmpty() ? "Productos" : getString(R.string.catalog_results_for, query),
                matches,
                getResources().getQuantityString(
                        R.plurals.catalog_match_count,
                        matches.size(),
                        matches.size()));
    }

    private void showResults(String title, List<Product> products, String countLabel) {
        categorySections.setVisibility(View.GONE);
        resultsContainer.setVisibility(View.VISIBLE);
        resultsTitle.setText(title);
        resultsCount.setText(countLabel);
        resultsAdapter.submitList(products);
        emptyState.setText(R.string.catalog_no_results);
        emptyState.setVisibility(products.isEmpty() ? View.VISIBLE : View.GONE);
        catalogProgress.setVisibility(View.GONE);
        searchButton.setEnabled(true);
        catalogStateAction.setVisibility(products.isEmpty() ? View.VISIBLE : View.GONE);
        catalogStateAction.setText("Limpiar búsqueda");
        catalogStateAction.setOnClickListener(view -> showCategoryOverview());
        catalogScroll.post(() -> catalogScroll.smoothScrollTo(0, resultsContainer.getTop()));
    }

    private void showCatalogLoading() {
        loading=true;
        categorySections.setVisibility(View.GONE);
        resultsContainer.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
        catalogStateAction.setVisibility(View.GONE);
        catalogProgress.setVisibility(View.VISIBLE);
        searchButton.setEnabled(false);
    }

    private void showCatalogError(String message) {
        loading=false;
        currentSections = new ArrayList<>();
        categorySections.setVisibility(View.GONE);
        resultsContainer.setVisibility(View.GONE);
        catalogProgress.setVisibility(View.GONE);
        String detail=message==null ? "" : message.toLowerCase(java.util.Locale.ROOT);
        boolean updating=detail.contains("reserved_stock") || detail.contains("variants")
                || detail.contains("column") || detail.contains("schema");
        emptyState.setText(updating
                ? "El catálogo necesita una actualización del servicio. Intenta nuevamente más tarde."
                : "No pudimos cargar el catálogo. Revisa tu conexión e inténtalo nuevamente.");
        emptyState.setVisibility(View.VISIBLE);
        catalogStateAction.setText("Reintentar");
        catalogStateAction.setOnClickListener(view -> loadCatalog());
        catalogStateAction.setVisibility(View.VISIBLE);
        searchButton.setEnabled(true);
        renderCategoryChips(null);
    }

    private CatalogProductAdapter.ProductActionListener productActions() {
        return new CatalogProductAdapter.ProductActionListener() {
            @Override
            public void onOpenProduct(Product product) {
                openProductDetail(product.getId());
            }

            @Override
            public void onSecondaryAction(Product product) {
                if (adminCatalog) {
                    openProductEditor(product);
                } else {
                    addProductToCart(product);
                }
            }
        };
    }

    private void openProductDetail(int productId) {
        Intent intent = new Intent(this, ProductDetailActivity.class);
        intent.putExtra("productId", productId);
        intent.putExtra(EXTRA_ADMIN_CATALOG, adminCatalog);
        intent.putExtra(EXTRA_OPENED_FROM_ADMIN, openedFromAdmin);
        startActivity(intent);
    }

    private void openProductEditor(Product product) {
        if (!adminCatalog || !AppModeManager.isAdminMode(this) || !store.isAuthenticated()) {
            showMessage("Acceso administrativo requerido");
            return;
        }
        Intent intent = new Intent(this, ProductFormActivity.class);
        intent.putExtra("productId", product.getId());
        startActivity(intent);
    }

    private void addProductToCart(Product product) {
        VariantPicker.show(this,product,variantId -> {
            if (store.addToCart(product.getId(),variantId,1)) {
                showMessage("Producto agregado al carrito");
            } else { showMessage("Sin unidades disponibles o el carrito pertenece a otro taller"); }
        });
    }

    @Override protected void onDestroy() {
        searchHandler.removeCallbacks(searchTask);
        super.onDestroy();
    }

    private void openClientOrders() {
        Intent intent = new Intent(this, ClientOrdersActivity.class);
        intent.putExtra(EXTRA_OPENED_FROM_ADMIN, openedFromAdmin);
        startActivity(intent);
    }

    private void openAdminOrders() {
        if (!adminCatalog || !AppModeManager.isAdminMode(this) || !store.isAuthenticated()) {
            showMessage("Acceso administrativo requerido");
            return;
        }
        Intent intent = new Intent(this, ReportsActivity.class);
        intent.putExtra("reportSection", "orders");
        intent.putExtra("requestStatus", "pending");
        startActivity(intent);
    }

    private void openAdminDashboard() {
        navigateHome();
    }

    @Override protected void handleBackNavigation() {
        if (!BuildConfig.IS_ADMIN_APP && searchInput != null && (resultsContainer.getVisibility() == View.VISIBLE
                || !searchInput.getText().toString().isEmpty() || favoritesOnly || !filters.category.isEmpty()
                || !filters.size.isEmpty() || !filters.color.isEmpty() || filters.minimumPrice != 0
                || filters.maximumPrice != Integer.MAX_VALUE || filters.availableOnly || filters.sort != 0)) {
            resetCatalogHome();
            return;
        }
        super.handleBackNavigation();
    }

    @Override protected void navigateHome() {
        if (!BuildConfig.IS_ADMIN_APP) { resetCatalogHome(); }
        else { super.navigateHome(); }
    }

    private void resetCatalogHome() {
        searchHandler.removeCallbacks(searchTask);
        filters = new CatalogFilters();
        favoritesOnly = false;
        ((ImageView) findViewById(R.id.buttonCatalogFavorites)).setImageResource(android.R.drawable.btn_star_big_off);
        suppressSearch = true;
        searchInput.setText("");
        suppressSearch = false;
        ((Spinner) findViewById(R.id.spinnerCatalogSort)).setSelection(0);
        showCategoryOverview();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected boolean isPublicScreen() {
        return true;
    }

    @Override
    protected boolean isAdminContext() {
        return adminCatalog
                && AppModeManager.isAdminMode(this)
                && store != null
                && store.isAuthenticated();
    }
}
