// Eliminacion solicitada por el titular. El secreto administrativo solo existe en Supabase.
const headers = { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type" };
const response = (status: number, body: unknown) => new Response(JSON.stringify(body), { status, headers });

Deno.serve(async (request: Request) => {
  if (request.method === "OPTIONS") return new Response(null, { headers });
  if (request.method !== "POST") return response(405, { error: "Metodo no permitido" });
  const url = Deno.env.get("SUPABASE_URL");
  const key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !key) return response(503, { error: "Eliminacion de cuentas no configurada" });
  const authorization = request.headers.get("Authorization") ?? "";
  if (!authorization.startsWith("Bearer ")) return response(401, { error: "Sesion requerida" });
  try {
    const body = await request.json();
    if (body.confirmation !== "ELIMINAR") return response(400, { error: "Confirmacion requerida" });
    const userResponse = await fetch(url + "/auth/v1/user", { headers: { apikey: key, Authorization: authorization } });
    if (!userResponse.ok) return response(401, { error: "Inicia sesion nuevamente antes de eliminar la cuenta" });
    const user = await userResponse.json();
    const adminHeaders = { apikey: key, Authorization: "Bearer " + key, "Content-Type": "application/json" };
    if (Array.isArray(user.app_metadata?.providers) && user.app_metadata.providers.includes("email")) {
      if (typeof body.password !== "string" || !body.password) {
        return response(400, { error: "Confirma tu contraseña para eliminar la cuenta" });
      }
      const login = await fetch(url + "/auth/v1/token?grant_type=password", {
        method: "POST", headers: adminHeaders,
        body: JSON.stringify({ email: user.email, password: body.password }),
      });
      if (!login.ok || (await login.json()).user?.id !== user.id) {
        return response(403, { error: "Contraseña incorrecta" });
      }
    }
    const workshopResponse = await fetch(url + "/rest/v1/workshops?select=id&owner_user_id=eq."
      + encodeURIComponent(user.id), { headers: adminHeaders });
    if (!workshopResponse.ok) return response(502, { error: "No se pudo consultar el taller" });
    const workshops = await workshopResponse.json();
    // Primero elimina objetos para evitar archivos huerfanos tras borrar el usuario.
    for (const workshop of workshops) {
      for (const bucket of ["product-images", "order-receipts"]) {
        const pending: string[] = [workshop.id];
        while (pending.length) {
          const prefix = pending.pop()!;
          let offset = 0;
          const files: string[] = [];
          do {
            const listing = await fetch(url + "/storage/v1/object/list/" + bucket, {
              method: "POST", headers: adminHeaders, body: JSON.stringify({ prefix, limit: 100, offset }),
            });
            if (!listing.ok) return response(502, { error: "No se pudieron revisar los archivos del taller" });
            const objects = await listing.json();
            for (const object of objects) {
              if (object.name === "." || object.name === ".." || object.name.includes("/")) continue;
              const path = prefix + "/" + object.name;
              if (object.id) files.push(path); else pending.push(path);
            }
            offset += objects.length;
            if (objects.length < 100) break;
          } while (true);
          for (let start = 0; start < files.length; start += 100) {
            const deletion = await fetch(url + "/storage/v1/object/" + bucket, {
              method: "DELETE", headers: adminHeaders, body: JSON.stringify({ prefixes: files.slice(start, start + 100) }),
            });
            if (!deletion.ok) return response(502, { error: "No se pudieron eliminar todos los archivos; intenta nuevamente" });
          }
        }
      }
    }
    const deletion = await fetch(url + "/auth/v1/admin/users/" + encodeURIComponent(user.id),
      { method: "DELETE", headers: adminHeaders });
    if (!deletion.ok) return response(502, { error: "No se pudo eliminar la cuenta; intenta nuevamente" });
    return response(200, { deleted: true });
  } catch {
    return response(500, { error: "No se pudo procesar la eliminacion" });
  }
});
