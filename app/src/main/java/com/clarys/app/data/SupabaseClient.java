package com.clarys.app.data;

import android.os.Handler;
import android.os.Looper;

import com.clarys.app.BuildConfig;

import org.json.JSONObject;

import java.io.IOException;

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

        Request request =
                baseBuilder(
                        path,
                        authenticated
                )
                        .get()
                        .build();

        executeString(
                request,
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

        Request request =
                baseBuilder(
                        path,
                        authenticated
                )
                        .post(
                                RequestBody.create(
                                        body.toString(),
                                        JSON
                                )
                        )
                        .build();

        executeString(
                request,
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

        Request request =
                baseBuilder(
                        path,
                        authenticated
                )
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
                        .build();

        executeString(
                request,
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

        Request request =
                baseBuilder(
                        path,
                        authenticated
                )
                        .patch(
                                RequestBody.create(
                                        body.toString(),
                                        JSON
                                )
                        )
                        .build();

        executeString(
                request,
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

        Request request =
                baseBuilder(
                        path,
                        authenticated
                )
                        .delete()
                        .build();

        executeString(
                request,
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

        Request request =
                new Request.Builder()
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
                        .build();

        executeString(
                request,
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


        String token =
                authenticated
                        && session.isAuthenticated()

                        ? session.getAccessToken()

                        : BuildConfig.SUPABASE_PUBLISHABLE_KEY;


        return new Request.Builder()
                .url(url)

                .addHeader(
                        "apikey",
                        BuildConfig.SUPABASE_PUBLISHABLE_KEY
                )

                .addHeader(
                        "Authorization",
                        "Bearer " + token
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