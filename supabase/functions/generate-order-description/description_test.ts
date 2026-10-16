import { buildDescription, type DescriptionItem } from "./description.ts";

function item(overrides: Partial<DescriptionItem> = {}): DescriptionItem {
  return {
    product: "Camiseta",
    quantity: 2,
    unit_price: 35000,
    subtotal: 70000,
    current_stock: 10,
    active: true,
    stock_is_enough: true,
    ...overrides,
  };
}

Deno.test("genera una descripción con total y stock suficiente", () => {
  const description = buildDescription("Juanito", [item()], 70000);

  assertIncludes(
    description,
    "Juanito está solicitando 2 unidades de Camiseta",
  );
  assertIncludes(description, "70.000");
  assertIncludes(description, "inventario disponible es suficiente");
});

Deno.test("informa stock insuficiente, inactivo y desconocido", () => {
  const description = buildDescription(
    "Juanito",
    [
      item({ current_stock: 1, stock_is_enough: false }),
      item({ product: "Pantalón", active: false, stock_is_enough: false }),
      item({
        product: "Vestido",
        current_stock: null,
        active: null,
        stock_is_enough: null,
      }),
    ],
    175000,
  );

  assertIncludes(description, "Camiseta (solicita 2 y hay 1)");
  assertIncludes(description, "Pantalón no está activo");
  assertIncludes(description, "verificar manualmente el stock de Vestido");
});

function assertIncludes(actual: string, expected: string): void {
  if (!actual.includes(expected)) {
    throw new Error(`No se encontró \"${expected}\" en \"${actual}\"`);
  }
}
