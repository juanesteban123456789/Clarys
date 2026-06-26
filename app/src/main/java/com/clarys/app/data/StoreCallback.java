package com.clarys.app.data;

public interface StoreCallback<T> {
    void onSuccess(T result);

    void onError(String message);
}
