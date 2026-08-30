package com.clarys.app.data;

import android.os.Handler;
import android.os.Looper;

import com.clarys.app.BuildConfig;

import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Centraliza las peticiones HTTP hacia Supabase Auth,
 * REST API y Storage.
 */
public class SupabaseClient {

    private static final MediaType JSON =
            MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client =
            new OkHttpClient();

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    private final SupabaseSession session;
    private final Object refreshLock = new Object();
    private final List<StoreCallback<Boolean>> refreshCallbacks = new ArrayList<>();
    private boolean refreshInProgress;

    private interface RequestFactory {
        Request create();
    }


    public SupabaseClient(
            SupabaseSession session) {

        this.session = session;
    }


    // =============================================================
    // AUTH
    // =============================================================

    /**
     * Ejecuta peticiones de autenticación contra Supabase Auth.
     */
    public void auth(
            String path,
            JSONObject body,
            StoreCallback<JSONObject> callback) {

        String url =
                BuildConfig.SUPABASE_URL
                        + "/auth/v1/"
                        + path;

        Request request =
                new Request.Builder()
                        .url(url)
                        .addHeader(
                                "apikey",
                                BuildConfig.SUPABASE_PUBLISHABLE_KEY
                        )
                        .addHeader(
                                "Content-Type",
                                "application/json"
                        )
                        .post(
                                RequestBody.create(
                                        body.toString(),
                                        JSON
                                )
                        )
                        .build();

        executeJsonObject(
                request,
                callback
        );
    }

    /**
     * Verifica la sesión almacenada y la renueva de forma preventiva. Las
     * solicitudes simultáneas comparten una sola renovación porque Supabase
     * rota el refresh token después de usarlo.
     */
    public void ensureValidSession(boolean forceRefresh,
                                   StoreCallback<Boolean> callback) {
        if (!session.isAuthenticated()) {
            callback.onSuccess(false);
            return;
        }

        if (!forceRefresh && !session.shouldRefreshSession()) {
            callback.onSuccess(true);
            return;
        }

        refreshSession(callback);
    }

    private void refreshSession(StoreCallback<Boolean> callback) {
        synchronized (refreshLock) {
            refreshCallbacks.add(callback);
            if (refreshInProgress) {
                return;
            }
            refreshInProgress = true;
        }

        String refreshToken = session.getRefreshToken();
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            session.clear();
            completeRefresh(false, null);
            return;
        }

        try {
            JSONObject body = new JSONObject()
                    .put("refresh_token", refreshToken);

            auth("token?grant_type=refresh_token", body,
                    new StoreCallback<JSONObject>() {
                        @Override
                        public void onSuccess(JSONObject result) {
                            if (saveRefreshedSession(result)) {
                                completeRefresh(true, null);
                            } else {
                                session.clear();
                                completeRefresh(false, null);
                            }
                        }

                        @Override
                        public void onError(String message) {
                            if (isDefinitiveRefreshError(message)) {
                                session.clear();
                                completeRefresh(false, null);
                                return;
                            }
                            completeRefresh(false, message);
                        }
                    });
        } catch (Exception exception) {
            completeRefresh(false, "No se pudo renovar la sesión administrativa");
        }
    }

    private boolean saveRefreshedSession(JSONObject result) {
        String accessToken = result.optString("access_token", null);
        String refreshToken = result.optString("refresh_token", session.getRefreshToken());
        JSONObject user = result.optJSONObject("user");
        String userId = user == null
                ? session.getUserId()
                : user.optString("id", session.getUserId());
        long expiresAt = result.optLong("expires_at", 0L);

        if (expiresAt <= 0L) {
            long expiresIn = result.optLong("expires_in", 0L);
            if (expiresIn > 0L) {
                expiresAt = System.currentTimeMillis() / 1000L + expiresIn;
            }
        }

        if (expiresAt <= 0L) {
            expiresAt = JwtUtils.readExpirationEpochSeconds(accessToken);
        }

        if (!hasText(accessToken) || !hasText(refreshToken) || !hasText(userId)) {
            return false;
        }

        session.saveAuth(accessToken, refreshToken, userId, expiresAt);
        return true;
    }

    private void completeRefresh(boolean active, String errorMessage) {
        List<StoreCallback<Boolean>> callbacks;
        synchronized (refreshLock) {
            callbacks = new ArrayList<>(refreshCallbacks);
            refreshCallbacks.clear();
            refreshInProgress = false;
        }

        for (StoreCallback<Boolean> callback : callbacks) {
            if (errorMessage == null) {
                callback.onSuccess(active);
            } else {
                callback.onError(errorMessage);
            }
        }
    }


    // =============================================================
    // GET
    // =============================================================

    /**
     * Consulta recursos de Supabase REST usando GET.
     */
    public void get(
            String path,
            boolean authenticated,
            StoreCallback<String> callback) {

        executeWithSessionRetry(
                () -> baseBuilder(path, authenticated)
                        .get()
                        .build(),
                authenticated,
                callback
        );
    }


    // =============================================================
    // POST NORMAL
    // =============================================================

    /**
     * Crea registros o ejecuta funciones RPC.
     *
     * Usa return=representation porque algunas partes de la app
     * necesitan leer el registro creado.
     */
    public void post(
            String path,
            JSONObject body,
            boolean authenticated,
            StoreCallback<String> callback) {

        executeWithSessionRetry(
                () -> baseBuilder(path, authenticated)
                        .post(
                                RequestBody.create(
                                        body.toString(),
                                        JSON
                                )
                        )
                        .build(),
                authenticated,
                callback
        );
    }


    // =============================================================
    // POST SIN RETORNAR REGISTRO
    // =============================================================

    /**
     * Realiza un INSERT sin solicitar que Supabase devuelva
     * la fila insertada.
     *
     * Es especialmente útil para operaciones públicas donde
     * el usuario puede INSERTAR pero no tiene permiso SELECT.
     *
     * Ejemplo:
     * contact_requests creados desde el catálogo público.
     */
    public void postNoReturn(
            String path,
            JSONObject body,
            boolean authenticated,
            StoreCallback<String> callback) {

        executeWithSessionRetry(
                () -> baseBuilder(path, authenticated)
                        .header(
                                "Prefer",
                                "return=minimal"
                        )
                        .post(
                                RequestBody.create(
                                        body.toString(),
                                        JSON
                                )
                        )
                        .build(),
                authenticated,
                callback
        );
    }


    // =============================================================
    // PATCH
    // =============================================================

    /**
     * Actualiza registros existentes.
     */
    public void patch(
            String path,
            JSONObject body,
            boolean authenticated,
            StoreCallback<String> callback) {

        executeWithSessionRetry(
                () -> baseBuilder(path, authenticated)
                        .patch(
                                RequestBody.create(
                                        body.toString(),
                                        JSON
                                )
                        )
                        .build(),
                authenticated,
                callback
        );
    }


    // =============================================================
    // DELETE
    // =============================================================

    /**
     * Elimina recursos de Supabase REST.
     */
    public void delete(
            String path,
            boolean authenticated,
            StoreCallback<String> callback) {

        executeWithSessionRetry(
                () -> baseBuilder(path, authenticated)
                        .delete()
                        .build(),
                authenticated,
                callback
        );
    }


    // =============================================================
    // EDGE FUNCTIONS
    // =============================================================

    /**
     * Invoca una Edge Function de Supabase sin exponer secretos
     * de servicios externos dentro de la aplicación Android.
     */
    public void invokeFunction(
            String functionName,
            JSONObject body,
            boolean authenticated,
            StoreCallback<String> callback) {

        String url =
                BuildConfig.SUPABASE_URL
                        + "/functions/v1/"
                        + functionName;

        executeWithSessionRetry(
                () -> new Request.Builder()
                        .url(url)
                        .addHeader(
                                "apikey",
                                BuildConfig.SUPABASE_PUBLISHABLE_KEY
                        )
                        .addHeader(
                                "Authorization",
                                "Bearer " + tokenFor(authenticated)
                        )
                        .addHeader(
                                "Content-Type",
                                "application/json"
                        )
                        .addHeader(
                                "Accept",
                                "application/json"
                        )
                        .post(
                                RequestBody.create(
                                        body.toString(),
                                        JSON
                                )
                        )
                        .build(),
                authenticated,
                callback
        );
    }


    // =============================================================
    // STORAGE
    // =============================================================

    /**
     * Sube archivos binarios a Supabase Storage.
     */
    public void uploadStorageObject(
            String bucket,
            String path,
            byte[] data,
            String contentType,
            StoreCallback<String> callback) {

        String url =
                BuildConfig.SUPABASE_URL
                        + "/storage/v1/object/"
                        + bucket
                        + "/"
                        + path;

        executeWithSessionRetry(
                () -> new Request.Builder()
                        .url(url)
                        .addHeader(
                                "apikey",
                                BuildConfig.SUPABASE_PUBLISHABLE_KEY
                        )
                        .addHeader(
                                "Authorization",
                                "Bearer "
                                        + session.getAccessToken()
                        )
                        .addHeader(
                                "Content-Type",
                                contentType
                        )
                        .addHeader(
                                "x-upsert",
                                "true"
                        )
                        .post(
                                RequestBody.create(
                                        data,
                                        MediaType.get(contentType)
                                )
                        )
                        .build(),
                true,
                callback
        );
    }


    /**
     * Retorna la URL pública de un archivo de un bucket público.
     */
    public String publicStorageUrl(
            String bucket,
            String path) {

        return BuildConfig.SUPABASE_URL
                + "/storage/v1/object/public/"
                + bucket
                + "/"
                + path;
    }


    // =============================================================
    // REQUEST BASE
    // =============================================================

    /**
     * Construye una petición REST estándar.
     *
     * Por defecto utiliza return=representation porque existen
     * operaciones administrativas que necesitan recibir el registro.
     */
    private Request.Builder baseBuilder(
            String path,
            boolean authenticated) {

        String url =
                BuildConfig.SUPABASE_URL
                        + "/rest/v1/"
                        + path;


        return new Request.Builder()
                .url(url)

                .addHeader(
                        "apikey",
                        BuildConfig.SUPABASE_PUBLISHABLE_KEY
                )

                .addHeader(
                        "Authorization",
                        "Bearer " + tokenFor(authenticated)
                )

                .addHeader(
                        "Content-Type",
                        "application/json"
                )

                .addHeader(
                        "Accept",
                        "application/json"
                )

                .addHeader(
                        "Prefer",
                        "return=representation"
                );
    }

    private String tokenFor(boolean authenticated) {
        if (authenticated && session.isAuthenticated()) {
            return session.getAccessToken();
        }
        return BuildConfig.SUPABASE_PUBLISHABLE_KEY;
    }

    // =============================================================
    // SESSION-AWARE REQUESTS
    // =============================================================

    private void executeWithSessionRetry(RequestFactory requestFactory,
                                         boolean authenticated,
                                         StoreCallback<String> callback) {
        if (!authenticated) {
            executeRequest(requestFactory, false, false, callback);
            return;
        }

        ensureValidSession(false, new StoreCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean active) {
                if (!Boolean.TRUE.equals(active)) {
                    callback.onError(expiredSessionMessage());
                    return;
                }
                executeRequest(requestFactory, true, true, callback);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    private void executeRequest(RequestFactory requestFactory,
                                boolean authenticated,
                                boolean allowSessionRetry,
                                StoreCallback<String> callback) {
        Request request;
        try {
            request = requestFactory.create();
        } catch (Exception exception) {
            postError(callback, "No se pudo preparar la petición a Supabase");
            return;
        }

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException exception) {
                postError(callback,
                        "No se pudo conectar con Supabase: " + exception.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (Response safeResponse = response) {
                    String body = safeResponse.body() == null
                            ? ""
                            : safeResponse.body().string();

                    if (safeResponse.isSuccessful()) {
                        postSuccess(callback, body);
                        return;
                    }

                    if (authenticated
                            && allowSessionRetry
                            && isExpiredJwtResponse(safeResponse.code(), body)) {
                        mainHandler.post(() -> refreshAndRetry(requestFactory, callback));
                        return;
                    }

                    postError(callback, extractError(body, safeResponse.code()));
                }
            }
        });
    }

    private void refreshAndRetry(RequestFactory requestFactory,
                                 StoreCallback<String> callback) {
        ensureValidSession(true, new StoreCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean active) {
                if (!Boolean.TRUE.equals(active)) {
                    callback.onError(expiredSessionMessage());
                    return;
                }
                executeRequest(requestFactory, true, false, callback);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    private boolean isExpiredJwtResponse(int code, String body) {
        String normalized = body == null
                ? ""
                : body.toLowerCase(Locale.ROOT);
        boolean expiredMessage = normalized.contains("jwt expired")
                || normalized.contains("invalid jwt")
                || normalized.contains("token is expired")
                || normalized.contains("token has expired");
        return expiredMessage && (code == 400 || code == 401 || code == 403);
    }

    private boolean isDefinitiveRefreshError(String message) {
        String normalized = message == null
                ? ""
                : message.toLowerCase(Locale.ROOT);
        return normalized.contains("invalid refresh token")
                || normalized.contains("refresh token not found")
                || normalized.contains("refresh_token_not_found")
                || normalized.contains("refresh token has expired")
                || normalized.contains("session not found")
                || normalized.contains("already used")
                || normalized.contains("refresh_token_already_used")
                || normalized.contains("refresh token revoked");
    }

    private String expiredSessionMessage() {
        return "La sesión administrativa expiró. Inicia sesión nuevamente.";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }


    // =============================================================
    // RESPONSE JSON
    // =============================================================

    private void executeJsonObject(
            Request request,
            StoreCallback<JSONObject> callback) {

        executeString(
                request,
                new StoreCallback<String>() {

                    @Override
                    public void onSuccess(
                            String result) {

                        try {

                            callback.onSuccess(
                                    new JSONObject(result)
                            );

                        } catch (Exception exception) {

                            callback.onError(
                                    "Respuesta inválida de Supabase"
                            );
                        }
                    }


                    @Override
                    public void onError(
                            String message) {

                        callback.onError(
                                message
                        );
                    }
                }
        );
    }


    // =============================================================
    // RESPONSE STRING
    // =============================================================

    private void executeString(
            Request request,
            StoreCallback<String> callback) {

        client.newCall(
                request
        ).enqueue(
                new Callback() {

                    @Override
                    public void onFailure(
                            Call call,
                            IOException exception) {

                        postError(
                                callback,
                                "No se pudo conectar con Supabase: "
                                        + exception.getMessage()
                        );
                    }


                    @Override
                    public void onResponse(
                            Call call,
                            Response response)
                            throws IOException {

                        try (Response safeResponse = response) {

                            String body =
                                    safeResponse.body() == null
                                            ? ""
                                            : safeResponse
                                            .body()
                                            .string();


                            if (safeResponse.isSuccessful()) {

                                postSuccess(
                                        callback,
                                        body
                                );

                            } else {

                                postError(
                                        callback,
                                        extractError(
                                                body,
                                                safeResponse.code()
                                        )
                                );
                            }
                        }
                    }
                }
        );
    }


    // =============================================================
    // ERROR
    // =============================================================

    private String extractError(
            String body,
            int code) {

        try {

            JSONObject json =
                    new JSONObject(body);


            if (json.has("msg")) {

                return json.getString(
                        "msg"
                );
            }


            if (json.has("message")) {

                return json.getString(
                        "message"
                );
            }


            if (json.has(
                    "error_description"
            )) {

                return json.getString(
                        "error_description"
                );
            }


            if (json.has("details")) {

                String details =
                        json.optString(
                                "details",
                                ""
                        );

                if (!details.isEmpty()) {
                    return details;
                }
            }


            if (json.has("hint")) {

                String hint =
                        json.optString(
                                "hint",
                                ""
                        );

                if (!hint.isEmpty()) {
                    return hint;
                }
            }

        } catch (Exception ignored) {

            // La respuesta puede ser texto plano o vacía.
        }


        return "Supabase respondió con error "
                + code;
    }


    // =============================================================
    // MAIN THREAD
    // =============================================================

    private <T> void postSuccess(
            StoreCallback<T> callback,
            T result) {

        mainHandler.post(
                () ->
                        callback.onSuccess(
                                result
                        )
        );
    }


    private void postError(
            StoreCallback<?> callback,
            String message) {

        mainHandler.post(
                () ->
                        callback.onError(
                                message
                        )
        );
    }
}
