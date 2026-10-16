import { buildDescription } from "./description.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
};

type OrderItem = {
  product_id?: number;
  product_name?: string;
  quantity?: number;
  unit_price?: number;
  variant_id?: string;
  variant_label?: string;
};

type ProductStock = {
  id: number;
  name: string;
  stock: number;
  active: boolean;
  reserved_stock?: number;
  variants?: Array<{ id: string; stock: number; reserved_stock: number }>;
};

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (request.method !== "POST") {
    return jsonResponse({ error: "Método no permitido" }, 405);
  }

  const authorization = request.headers.get("Authorization") ?? "";
  const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
  const supabaseAnonKey = Deno.env.get("SUPABASE_ANON_KEY") ?? "";

  if (!authorization.startsWith("Bearer ")) {
    return jsonResponse({ error: "Sesión administrativa requerida" }, 401);
  }

  if (!supabaseUrl || !supabaseAnonKey) {
    return jsonResponse(
      { error: "La generación automática no está configurada" },
      503,
    );
  }

  try {
    const payload = await request.json();
    const orderId = Number(payload.order_id);

    if (!Number.isInteger(orderId) || orderId <= 0) {
      return jsonResponse({ error: "Pedido no válido" }, 400);
    }

    const authHeaders = {
      apikey: supabaseAnonKey,
      Authorization: authorization,
      Accept: "application/json",
    };

    const orderResponse = await fetch(
      `${supabaseUrl}/rest/v1/contact_requests?id=eq.${orderId}` +
        "&select=id,customer_name,items,workshop_id,status&limit=1",
      { headers: authHeaders },
    );

    if (!orderResponse.ok) {
      return jsonResponse({ error: "No se pudo consultar el pedido" }, 502);
    }

    const orders = await orderResponse.json();
    const order = Array.isArray(orders) ? orders[0] : null;

    if (!order) {
      return jsonResponse({ error: "Pedido no encontrado" }, 404);
    }

    const items: OrderItem[] = Array.isArray(order.items) ? order.items : [];

    if (items.length === 0) {
      return jsonResponse({ error: "El pedido no contiene productos" }, 400);
    }

    const productIds = [
      ...new Set(
        items
          .map((item) => Number(item.product_id))
          .filter((id) => Number.isInteger(id) && id > 0),
      ),
    ];

    let products: ProductStock[] = [];

    if (productIds.length > 0) {
      const productResponse = await fetch(
        `${supabaseUrl}/rest/v1/products?id=in.(${productIds.join(",")})` +
          "&select=id,name,stock,reserved_stock,variants,active",
        { headers: authHeaders },
      );

      if (productResponse.ok) {
        products = await productResponse.json();
      }
    }

    const stockById = new Map(
      products.map((product) => [Number(product.id), product]),
    );

    const normalizedItems = items.map((item) => {
      const productId = Number(item.product_id ?? 0);
      const quantity = Math.max(1, Number(item.quantity ?? 1));
      const unitPrice = Math.max(0, Number(item.unit_price ?? 0));
      const inventory = stockById.get(productId);
      const variant = item.variant_id
        ? inventory?.variants?.find((candidate) => candidate.id === item.variant_id)
        : null;
      const physical = item.variant_id ? variant?.stock : inventory?.stock;
      const reserved = item.variant_id ? variant?.reserved_stock : inventory?.reserved_stock;
      const ownQuantity = ["approved", "preparing"].includes(order.status)
        ? items.filter((candidate) => candidate.product_id === item.product_id
          && (candidate.variant_id ?? "") === (item.variant_id ?? ""))
          .reduce((sum, candidate) => sum + Number(candidate.quantity ?? 0), 0)
        : 0;
      const available = physical == null ? null
        : Math.min(physical, Math.max(0, physical - (reserved ?? 0) + ownQuantity));

      return {
        product: (item.product_name || inventory?.name || "Producto")
          + (item.variant_label ? " (" + item.variant_label + ")" : ""),
        quantity,
        unit_price: unitPrice,
        subtotal: quantity * unitPrice,
        current_stock: available,
        active: inventory?.active ?? null,
        stock_is_enough: inventory && available !== null
          ? inventory.active && available >= quantity
          : null,
      };
    });

    const total = normalizedItems.reduce((sum, item) => sum + item.subtotal, 0);
    const description = buildDescription(
      order.customer_name,
      normalizedItems,
      total,
      order.status,
    );

    return jsonResponse({ description, generator: "template" }, 200);
  } catch (error) {
    console.error("generate-order-description", error);
    return jsonResponse({ error: "No se pudo procesar el pedido" }, 500);
  }
});

function jsonResponse(body: Record<string, unknown>, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      ...corsHeaders,
      "Content-Type": "application/json; charset=utf-8",
    },
  });
}
