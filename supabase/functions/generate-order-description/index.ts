const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
};

type OrderItem = {
  product_id?: number;
  product_name?: string;
  quantity?: number;
  unit_price?: number;
};

type ProductStock = {
  id: number;
  name: string;
  stock: number;
  active: boolean;
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
  const openAiApiKey = Deno.env.get("OPENIA_API_SECRET") ?? "";
  const openAiModel = Deno.env.get("OPENAI_MODEL") ?? "gpt-4o-mini";

  if (!authorization.startsWith("Bearer ")) {
    return jsonResponse({ error: "Sesión administrativa requerida" }, 401);
  }

  if (!supabaseUrl || !supabaseAnonKey || !openAiApiKey) {
    return jsonResponse(
      { error: "La generación con IA no está configurada" },
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
        "&select=id,customer_name,items,workshop_id&limit=1",
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
          "&select=id,name,stock,active",
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

      return {
        product: item.product_name || inventory?.name || "Producto",
        quantity,
        unit_price: unitPrice,
        subtotal: quantity * unitPrice,
        current_stock: inventory?.stock ?? null,
        active: inventory?.active ?? null,
        stock_is_enough: inventory
          ? inventory.active && inventory.stock >= quantity
          : null,
      };
    });

    const total = normalizedItems.reduce((sum, item) => sum + item.subtotal, 0);

    const aiResponse = await fetch("https://api.openai.com/v1/responses", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${openAiApiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        model: openAiModel,
        store: false,
        max_output_tokens: 220,
        instructions:
          "Redacta en español colombiano un único párrafo breve para el administrador de un taller de confección. " +
          "Resume qué solicita el cliente, cantidades y total. Indica si el stock conocido es suficiente, insuficiente o si debe validarse. " +
          "Recomienda confirmar pago y entrega. No inventes datos ni incluyas saludos, títulos, Markdown o información personal adicional.",
        input: JSON.stringify({
          customer_name: order.customer_name || "Cliente",
          items: normalizedItems,
          total,
          currency: "COP",
        }),
      }),
    });

    if (!aiResponse.ok) {
      return jsonResponse({ error: "No se pudo generar la descripción" }, 502);
    }

    const aiPayload = await aiResponse.json();
    const description = extractOutputText(aiPayload).trim();

    if (!description) {
      return jsonResponse({ error: "La IA devolvió una respuesta vacía" }, 502);
    }

    return jsonResponse({ description }, 200);
  } catch (error) {
    console.error("generate-order-description", error);
    return jsonResponse({ error: "No se pudo procesar el pedido" }, 500);
  }
});

function extractOutputText(payload: Record<string, unknown>): string {
  if (typeof payload.output_text === "string") {
    return payload.output_text;
  }

  const output = Array.isArray(payload.output) ? payload.output : [];

  for (const item of output) {
    if (!item || typeof item !== "object") continue;
    const itemRecord = item as Record<string, unknown>;
    const content = Array.isArray(itemRecord.content) ? itemRecord.content : [];

    for (const block of content) {
      if (!block || typeof block !== "object") continue;
      const blockRecord = block as Record<string, unknown>;

      if (typeof blockRecord.text === "string") return blockRecord.text;
    }
  }

  return "";
}

function jsonResponse(body: Record<string, unknown>, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      ...corsHeaders,
      "Content-Type": "application/json; charset=utf-8",
    },
  });
}
