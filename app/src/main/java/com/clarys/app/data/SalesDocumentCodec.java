package com.clarys.app.data;

import com.clarys.app.model.SalesDocument;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Formato local versionado; persiste precios y textos, no referencias mutables al catalogo. */
public final class SalesDocumentCodec {
    private SalesDocumentCodec() { }
    public static JSONArray encodeItems(List<SalesDocument.Line> items) throws JSONException {
        JSONArray rows = new JSONArray();
        for (SalesDocument.Line line : items) {
            rows.put(new JSONObject().put("description", line.getDescription())
                    .put("quantity", line.getQuantity()).put("price", line.getUnitPrice()));
        }
        return rows;
    }
    public static List<SalesDocument.Line> decodeItems(JSONArray rows) throws JSONException {
        if (rows.length() > SalesDocument.MAX_ITEMS) { throw new JSONException("Demasiados conceptos"); }
        List<SalesDocument.Line> items = new ArrayList<>();
        for (int index = 0; index < rows.length(); index++) {
            JSONObject row = rows.getJSONObject(index);
            items.add(new SalesDocument.Line(row.getString("description"), row.getInt("quantity"), row.getLong("price")));
        }
        return items;
    }
    public static String encode(SalesDocument document) throws JSONException {
        return new JSONObject().put("version", 1).put("id", document.getId())
                .put("created", document.getCreatedAt()).put("workshop", document.getWorkshopId())
                .put("business", document.getBusinessName()).put("businessPhone", document.getBusinessPhone())
                .put("currency", document.getCurrency()).put("name", document.getCustomerName())
                .put("phone", document.getCustomerPhone()).put("customerId", document.getCustomerId())
                .put("email", document.getCustomerEmail()).put("source", document.getSourceKey())
                .put("sourceLabel", document.getSourceLabel()).put("notes", document.getNotes())
                .put("type", document.getType().name()).put("payment", document.getPayment().name())
                .put("discount", document.getDiscount()).put("items", encodeItems(document.getItems())).toString();
    }
    public static SalesDocument decode(String text) throws JSONException {
        JSONObject row = new JSONObject(text);
        if (row.getInt("version") != 1) { throw new JSONException("Versión de documento desconocida"); }
        return new SalesDocument.Builder(row.getString("workshop"), row.getString("business"),
                row.getString("businessPhone"), row.getString("currency"))
                .identity(row.getString("id"), row.getLong("created"))
                .customer(row.getString("name"), row.getString("phone"), row.getString("customerId"), row.getString("email"))
                .type(SalesDocument.Type.valueOf(row.getString("type")))
                .payment(SalesDocument.Payment.valueOf(row.getString("payment")))
                .source(row.getString("source"), row.getString("sourceLabel")).notes(row.getString("notes"))
                .discount(row.getLong("discount")).items(decodeItems(row.getJSONArray("items"))).build();
    }
}
