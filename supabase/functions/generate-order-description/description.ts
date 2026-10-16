export type DescriptionItem = {
  product: string;
  quantity: number;
  unit_price: number;
  subtotal: number;
  current_stock: number | null;
  active: boolean | null;
  stock_is_enough: boolean | null;
};

/**
 * Construye un resumen administrativo usando únicamente los datos del pedido.
 */
export function buildDescription(
  customerName: unknown,
  items: DescriptionItem[],
  total: number,
  status?: string,
): string {
  const requestedProducts = items.map((item) =>
    `${item.quantity} ${item.quantity === 1 ? "unidad" : "unidades"} de ${
      safeText(item.product, "producto")
    }`
  );
  const insufficient = items
    .filter((item) => item.active !== false && item.stock_is_enough === false)
    .map((item) =>
      `${safeText(item.product, "producto")} (solicita ${item.quantity} y hay ${
        item.current_stock ?? 0
      })`
    );
  const inactive = items
    .filter((item) => item.active === false)
    .map((item) => safeText(item.product, "producto"));
  const unknown = items
    .filter((item) =>
      item.active !== false &&
      (item.active === null || item.current_stock === null)
    )
    .map((item) => safeText(item.product, "producto"));

  const inventoryMessages: string[] = [];

  if (insufficient.length > 0) {
    inventoryMessages.push(
      `El inventario es insuficiente para ${joinNatural(insufficient)}`,
    );
  }
  if (inactive.length > 0) {
    inventoryMessages.push(
      `${joinNatural(inactive)} ${
        inactive.length === 1 ? "no está activo" : "no están activos"
      }`,
    );
  }
  if (unknown.length > 0) {
    inventoryMessages.push(
      `se debe verificar manualmente el stock de ${joinNatural(unknown)}`,
    );
  }
  if (inventoryMessages.length === 0) {
    inventoryMessages.push(
      "El inventario disponible es suficiente para atender las cantidades solicitadas",
    );
  }
  if (status === "completed") {
    inventoryMessages.splice(0, inventoryMessages.length, "El pedido fue entregado y su salida de inventario quedó registrada");
  } else if (status === "rejected") {
    inventoryMessages.splice(0, inventoryMessages.length, "El pedido está cancelado; las reservas fueron liberadas");
  }

  return `${safeText(customerName, "El cliente")} está solicitando ${
    joinNatural(requestedProducts)
  }. ` +
    `El valor total estimado a pagar es ${formatCop(total)}. ` +
    `${inventoryMessages.join(". ")}. ` +
    (status === "completed" || status === "rejected" ? "Consulta el historial para revisar su gestión."
      : "Confirma el método de pago y acuerda la entrega antes de aprobar el pedido.");
}

function joinNatural(values: string[]): string {
  if (values.length === 0) return "los productos indicados";
  if (values.length === 1) return values[0];
  return `${values.slice(0, -1).join(", ")} y ${values.at(-1)}`;
}

function safeText(value: unknown, fallback: string): string {
  if (typeof value !== "string") return fallback;
  const normalized = value.trim();
  return normalized && normalized.toLowerCase() !== "null"
    ? normalized
    : fallback;
}

function formatCop(value: number): string {
  return new Intl.NumberFormat("es-CO", {
    style: "currency",
    currency: "COP",
    maximumFractionDigits: 0,
  }).format(Math.max(0, value));
}
