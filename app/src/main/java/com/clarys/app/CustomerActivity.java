package com.clarys.app;

import android.os.Bundle;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.clarys.app.data.MockStore;
import com.clarys.app.ui.CustomerAdapter;

public class CustomerActivity extends BaseScreenActivity {
    private final MockStore store = MockStore.getInstance();
    private CustomerAdapter customerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customers);

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
        customerAdapter.submitList(store.getCustomers());
        ((TextView) findViewById(R.id.textCustomerCount)).setText(String.valueOf(store.getCustomers().size()));
    }
}
