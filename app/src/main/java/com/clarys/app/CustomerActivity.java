package com.clarys.app;

import android.os.Bundle;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.clarys.app.data.StoreCallback;
import com.clarys.app.data.SupabaseStore;
import com.clarys.app.ui.CustomerAdapter;

public class CustomerActivity extends BaseScreenActivity {
    private SupabaseStore store;
    private CustomerAdapter customerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customers);
        store = SupabaseStore.getInstance(this);

        setupHeader(R.id.buttonHeaderHome, R.id.buttonHeaderBack);
        bindNavigation(R.id.buttonCustomerNewSale, SaleActivity.class);

        RecyclerView customersList = findViewById(R.id.recyclerCustomers);
        customersList.setLayoutManager(new LinearLayoutManager(this));
        customerAdapter = new CustomerAdapter();
        customersList.setAdapter(customerAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        store.refreshCustomers(new StoreCallback<java.util.List<com.clarys.app.model.Customer>>() {
            @Override
            public void onSuccess(java.util.List<com.clarys.app.model.Customer> result) {
                customerAdapter.submitList(result);
                ((TextView) findViewById(R.id.textCustomerCount)).setText(String.valueOf(result.size()));
            }

            @Override
            public void onError(String message) {
                showMessage(message);
                customerAdapter.submitList(store.getCustomers());
                ((TextView) findViewById(R.id.textCustomerCount)).setText(String.valueOf(store.getCustomers().size()));
            }
        });
    }
}
