CLARYS - CONFIGURACIÓN DE DESCRIPCIONES CON IA

La aplicación ya genera una descripción local del pedido aunque la IA
no esté configurada. Para mejorar la redacción mediante OpenAI se debe
activar la Edge Function incluida en este proyecto.

1. BASE DE DATOS

Ejecutar en Supabase SQL Editor el archivo:

supabase_order_ai_description.sql

2. SECRETO DE OPENAI

Configurar OPENAI_API_KEY como secreto de la Edge Function. La clave
debe permanecer en Supabase y nunca debe copiarse en local.properties,
BuildConfig, Java ni dentro del APK.

Opcionalmente se puede configurar OPENAI_MODEL. Si se omite, la función
usa gpt-4o-mini.

Ejemplo mediante Supabase CLI:

supabase secrets set OPENAI_API_KEY=SU_CLAVE_PRIVADA
supabase secrets set OPENAI_MODEL=gpt-4o-mini

3. DESPLEGAR LA FUNCIÓN

supabase functions deploy generate-order-description

4. RESULTADO

Al abrir un pedido, Clarys muestra inmediatamente un resumen local y
solicita una versión mejorada a la Edge Function. Si OpenAI no está
disponible, el pedido continúa funcionando con el resumen local.

La función envía a OpenAI únicamente el nombre del cliente, los productos,
las cantidades, los precios y el estado del inventario. No envía el número
de teléfono. La aplicación usa la Responses API mediante una petición POST
realizada desde Supabase, no desde el dispositivo Android.
