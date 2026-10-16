package com.clarys.app.util;

import com.clarys.app.model.Product;
import com.clarys.app.model.ProductVariant;
import com.clarys.app.model.Sale;
import java.util.List;

/** CSV compatible con Excel; protege celdas de texto contra formulas inyectadas. */
public final class CsvExporter {
    private CsvExporter() { }
    public static String cell(String value) {
        String text=value == null ? "" : value;
        String trimmed=text.replaceFirst("^\\s+","");
        if(!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0))>=0) { text="'"+text; }
        return "\"" + text.replace("\"","\"\"") + "\"";
    }
    public static String inventory(List<Product> products) {
        StringBuilder csv=new StringBuilder("\ufeffCódigo;Producto;Categoría;Variante;Total;Reservado;Disponible;Costo;Precio\r\n");
        for(Product product:products) {
            if(product.getVariants().isEmpty()) { inventoryRow(csv,product,"",product.getTotalStock(),product.getReservedStock()); }
            else {
                for(ProductVariant variant:product.getVariants()) {
                    inventoryRow(csv,product,variant.getLabel(),variant.getStock(),variant.getReservedStock());
                }
            }
        }
        return csv.toString();
    }
    private static void inventoryRow(StringBuilder csv,Product product,String variant,int total,int reserved) {
        csv.append(cell(product.getInternalCode())).append(';').append(cell(product.getName())).append(';')
                .append(cell(product.getCategory())).append(';').append(cell(variant)).append(';')
                .append(total).append(';').append(reserved).append(';').append(total-reserved).append(';')
                .append(product.getPurchasePrice()).append(';').append(product.getSalePrice()).append("\r\n");
    }
    public static String sales(List<Sale> sales) {
        StringBuilder csv=new StringBuilder("\ufeffVenta;Fecha;Cliente;Estado;Subtotal;Descuento;Total\r\n");
        for(Sale sale:sales) {
            csv.append(sale.getId()).append(';').append(cell(SalesPeriod.display(sale.getCreatedAt()))).append(';')
                    .append(cell(sale.getCustomerName())).append(';').append(cell(sale.getStatus())).append(';')
                    .append(sale.getSubtotal()).append(';').append(sale.getDiscount()).append(';')
                    .append(sale.getTotal()).append("\r\n");
        }
        return csv.toString();
    }
}
