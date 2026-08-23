package com.clarys.app.data;

import com.clarys.app.model.CartItem;
import com.clarys.app.model.Customer;
import com.clarys.app.model.InventoryMovement;
import com.clarys.app.model.Product;
import com.clarys.app.model.Sale;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MockStore {
    private static final MockStore INSTANCE = new MockStore();

    private final List<Product> products = new ArrayList<>();
    private final List<CartItem> cart = new ArrayList<>();
    private final List<Sale> sales = new ArrayList<>();
    private final List<Customer> customers = new ArrayList<>();
    private final List<InventoryMovement> movements = new ArrayList<>();

    private int nextProductId = 1;
    private int nextSaleId = 1;
    private String businessName = "Taller Clarys";
    private String contactWhatsapp = "3001234567";
    private String currency = "COP";
    private int defaultMinStock = 5;

    private MockStore() {
        seed();
    }

    public static MockStore getInstance() {
        return INSTANCE;
    }

    private void seed() {
        addSeedProduct("Blusa bordada", "Blusa fresca con bordado frontal. Ideal para uso casual.",
                "Blusas", 38000, 65000, 12, 4, "S, M, L", "Blanco, Negro, Beige", "CLA-BLU-001", 8);
        addSeedProduct("Vestido casual", "Vestido liviano para uso diario con acabado suave.",
                "Vestidos", 54000, 89000, 8, 3, "S, M", "Azul, Rosa", "CLA-VES-002", 5);
        addSeedProduct("Conjunto lino", "Conjunto en lino para clima cálido y ocasiones casuales.",
                "Conjuntos", 78000, 120000, 4, 5, "M, L", "Arena, Blanco", "CLA-CON-003", 3);
        addSeedProduct("Falda midi", "Falda midi comoda para combinar con prendas basicas.",
                "Faldas", 33000, 58000, 0, 4, "S, M, L", "Negro, Verde", "CLA-FAL-004", 6);

        customers.add(new Customer("Cliente mostrador", "3001234567", 1, 123000));
        movements.add(new InventoryMovement("Blusa bordada", "Entrada", 12, 0, 12, "Carga inicial"));
        movements.add(new InventoryMovement("Falda midi", "Salida", 2, 2, 0, "Venta de prueba"));
    }

    private void addSeedProduct(String name, String description, String category, int purchasePrice,
            int salePrice, int stock, int minStock, String sizes, String colors, String sku,
            int soldUnits) {
        products.add(new Product(nextProductId++, name, description, category, purchasePrice,
                salePrice, stock, minStock, sizes, colors, sku, true, soldUnits));
    }

    public List<Product> getProducts() {
        return new ArrayList<>(products);
    }

    public List<Product> getActiveProducts() {
        List<Product> result = new ArrayList<>();
        for (Product product : products) {
            if (product.isActive()) {
                result.add(product);
            }
        }
        return result;
    }

    public List<Product> searchProducts(String query, String filter) {
        String cleanQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String cleanFilter = filter == null ? "Todos" : filter;
        List<Product> result = new ArrayList<>();

        for (Product product : products) {
            boolean matchesText = cleanQuery.isEmpty()
                    || product.getName().toLowerCase(Locale.ROOT).contains(cleanQuery)
                    || product.getCategory().toLowerCase(Locale.ROOT).contains(cleanQuery)
                    || product.getSku().toLowerCase(Locale.ROOT).contains(cleanQuery);
            boolean matchesFilter = "Todos".equals(cleanFilter)
                    || ("Stock bajo".equals(cleanFilter) && product.isLowStock())
                    || ("Agotados".equals(cleanFilter) && product.isOutOfStock())
                    || ("Inactivos".equals(cleanFilter) && !product.isActive());

            if (matchesText && matchesFilter) {
                result.add(product);
            }
        }
        return result;
    }

    public Product getProduct(int productId) {
        for (Product product : products) {
            if (product.getId() == productId) {
                return product;
            }
        }
        return null;
    }

    public Product saveProduct(Integer productId, String name, String description, String category,
            int purchasePrice, int salePrice, int stock, int minStock, String sizes, String colors,
            String sku, boolean active) {
        Product product = productId == null ? null : getProduct(productId);
        if (product == null) {
            product = new Product(nextProductId++, name, description, category, purchasePrice,
                    salePrice, stock, minStock, sizes, colors, sku, active, 0);
            products.add(product);
            movements.add(0, new InventoryMovement(product.getName(), "Entrada", stock, 0, stock,
                    "Creación de producto"));
            return product;
        }

        int previousStock = product.getStock();
        product.setName(name);
        product.setDescription(description);
        product.setCategory(category);
        product.setPurchasePrice(purchasePrice);
        product.setSalePrice(salePrice);
        product.setStock(stock);
        product.setMinStock(minStock);
        product.setSizes(sizes);
        product.setColors(colors);
        product.setSku(sku);
        product.setActive(active);

        if (previousStock != stock) {
            movements.add(0, new InventoryMovement(product.getName(), "Ajuste", Math.abs(stock - previousStock),
                    previousStock, stock, "Edición de producto"));
        }
        return product;
    }

    public boolean adjustStock(int productId, String type, int quantity, String reason) {
        Product product = getProduct(productId);
        if (product == null || quantity <= 0) {
            return false;
        }

        int previous = product.getStock();
        int next = previous;
        if ("Entrada".equals(type)) {
            next += quantity;
        } else if ("Salida".equals(type)) {
            next = Math.max(0, previous - quantity);
        } else {
            next = quantity;
        }

        product.setStock(next);
        movements.add(0, new InventoryMovement(product.getName(), type, quantity, previous, next,
                reason == null || reason.trim().isEmpty() ? "Movimiento manual" : reason.trim()));
        return true;
    }

    public boolean addToCart(int productId, int quantity) {
        Product product = getProduct(productId);
        if (product == null || !product.isActive() || product.getStock() <= 0) {
            return false;
        }

        int safeQuantity = Math.max(1, Math.min(quantity, product.getStock()));
        for (CartItem item : cart) {
            if (item.getProduct().getId() == productId) {
                int newQuantity = Math.min(product.getStock(), item.getQuantity() + safeQuantity);
                item.setQuantity(newQuantity);
                return true;
            }
        }
        cart.add(new CartItem(product, safeQuantity));
        return true;
    }

    public List<CartItem> getCartItems() {
        return new ArrayList<>(cart);
    }

    public void updateCartQuantity(int productId, int quantity) {
        for (int i = 0; i < cart.size(); i++) {
            CartItem item = cart.get(i);
            if (item.getProduct().getId() == productId) {
                if (quantity <= 0) {
                    cart.remove(i);
                } else {
                    item.setQuantity(Math.min(quantity, item.getProduct().getStock()));
                }
                return;
            }
        }
    }

    public int getCartTotal() {
        int total = 0;
        for (CartItem item : cart) {
            total += item.getSubtotal();
        }
        return total;
    }

    public void clearCart() {
        cart.clear();
    }

    public Sale confirmSale(String customerName, String phone, String paymentMethod, int discount,
            String status) {
        if (cart.isEmpty()) {
            return null;
        }

        List<CartItem> snapshot = new ArrayList<>();
        for (CartItem item : cart) {
            Product product = item.getProduct();
            int quantity = Math.min(item.getQuantity(), product.getStock());
            if (quantity <= 0) {
                continue;
            }
            snapshot.add(new CartItem(product, quantity));
        }

        if (snapshot.isEmpty()) {
            return null;
        }

        Sale sale = new Sale(nextSaleId++, safe(customerName, "Cliente mostrador"),
                safe(phone, contactWhatsapp), safe(paymentMethod, "Pendiente"),
                safe(status, "Confirmada"), discount, snapshot);

        for (CartItem item : snapshot) {
            Product product = item.getProduct();
            int previous = product.getStock();
            product.setStock(previous - item.getQuantity());
            product.addSoldUnits(item.getQuantity());
            movements.add(0, new InventoryMovement(product.getName(), "Salida", item.getQuantity(),
                    previous, product.getStock(), "Venta #" + sale.getId()));
        }

        registerCustomerSale(sale.getCustomerName(), sale.getCustomerPhone(), sale.getTotal());
        sales.add(0, sale);
        cart.clear();
        return sale;
    }

    private String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private void registerCustomerSale(String name, String phone, int total) {
        for (Customer customer : customers) {
            if (customer.getPhone().equals(phone)) {
                customer.registerSale(total);
                return;
            }
        }
        Customer customer = new Customer(name, phone, 0, 0);
        customer.registerSale(total);
        customers.add(0, customer);
    }

    public List<Sale> getSales() {
        return new ArrayList<>(sales);
    }

    public Sale getSale(int saleId) {
        for (Sale sale : sales) {
            if (sale.getId() == saleId) {
                return sale;
            }
        }
        return null;
    }

    public List<Customer> getCustomers() {
        return new ArrayList<>(customers);
    }

    public List<InventoryMovement> getMovements() {
        return new ArrayList<>(movements);
    }

    public int getLowStockCount() {
        int count = 0;
        for (Product product : products) {
            if (product.isLowStock() || product.isOutOfStock()) {
                count++;
            }
        }
        return count;
    }

    public int getTodaySalesTotal() {
        int total = 0;
        for (Sale sale : sales) {
            total += sale.getTotal();
        }
        return total;
    }

    public int getInventoryValue() {
        int total = 0;
        for (Product product : products) {
            total += product.getStock() * product.getPurchasePrice();
        }
        return total;
    }

    public Product getBestSeller() {
        if (products.isEmpty()) {
            return null;
        }
        return Collections.max(products, Comparator.comparingInt(Product::getSoldUnits));
    }

    public String formatMoney(int value) {
        NumberFormat formatter = NumberFormat.getCurrencyInstance(new Locale("es", "CO"));
        formatter.setMaximumFractionDigits(0);
        return formatter.format(value);
    }

    public String getBusinessName() {
        return businessName;
    }

    public void setBusinessName(String businessName) {
        this.businessName = safe(businessName, "Taller Clarys");
    }

    public String getContactWhatsapp() {
        return contactWhatsapp;
    }

    public void setContactWhatsapp(String contactWhatsapp) {
        this.contactWhatsapp = safe(contactWhatsapp, "3001234567");
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = safe(currency, "COP");
    }

    public int getDefaultMinStock() {
        return defaultMinStock;
    }

    public void setDefaultMinStock(int defaultMinStock) {
        this.defaultMinStock = Math.max(0, defaultMinStock);
    }
}
