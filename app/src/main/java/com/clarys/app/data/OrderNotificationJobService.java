package com.clarys.app.data;

import android.app.job.JobParameters;
import android.app.job.JobService;
import com.clarys.app.model.OrderRequest;
import java.util.List;

public class OrderNotificationJobService extends JobService {
    private JobParameters activeJob;
    @Override public boolean onStartJob(JobParameters parameters) {
        SupabaseStore store=SupabaseStore.getInstance(this);
        if(!store.isAuthenticated() || !OrderNotifications.isEnabled(this)) {
            OrderNotifications.cancel(this);
            return false;
        }
        String workshopId = store.getWorkshopId();
        if (workshopId == null || workshopId.isEmpty()) { return false; }
        activeJob = parameters;
        store.refreshOrderRequests(new StoreCallback<List<OrderRequest>>() {
            @Override public void onSuccess(List<OrderRequest> orders) {
                if(activeJob != parameters) { return; }
                activeJob = null;
                OrderNotifications.process(OrderNotificationJobService.this,workshopId,orders);
                jobFinished(parameters,false);
            }
            @Override public void onError(String message) {
                if(activeJob == parameters) { activeJob = null; jobFinished(parameters,true); }
            }
        });
        return true;
    }
    @Override public boolean onStopJob(JobParameters parameters) {
        if (activeJob == parameters) { activeJob = null; }
        return true;
    }
}
