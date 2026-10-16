CLARYS - DESCRIPCIONES AUTOMÁTICAS DE PEDIDOS

La aplicación genera descripciones administrativas mediante reglas locales.
No utiliza OpenAI, no requiere créditos y no envía información a servicios
de inteligencia artificial externos.

1. BASE DE DATOS

Ejecutar en Supabase SQL Editor el archivo:

supabase_order_description.sql

2. DESPLEGAR LA FUNCIÓN

supabase functions deploy generate-order-description

3. RESULTADO

Al abrir un pedido, Clarys muestra inmediatamente un resumen local y
solicita a la Edge Function una versión basada en el inventario actual. Si la
función no está disponible, el pedido continúa usando el resumen generado en
el dispositivo Android. El resultado incluye cliente, productos, cantidades,
total, disponibilidad de stock y las verificaciones previas a la aprobación.

Los secretos OPENIA_API_SECRET y OPENAI_MODEL ya no son necesarios. Pueden
eliminarse manualmente de Supabase porque esta función no los consulta.
