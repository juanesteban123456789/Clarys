package com.clarys.app.model;

import com.clarys.app.util.TextSanitizer;

/** Transicion registrada por Supabase, con fecha y responsable autenticado. */
public final class OrderEvent {
    private final String status;
    private final String actorId;
    private final String createdAt;
    public OrderEvent(String status, String actorId, String createdAt) {
        this.status=TextSanitizer.emptyIfNull(status);
        this.actorId=TextSanitizer.emptyIfNull(actorId);
        this.createdAt=TextSanitizer.emptyIfNull(createdAt);
    }
    public String getStatus() { return status; }
    public String getActorId() { return actorId; }
    public String getCreatedAt() { return createdAt; }
}
