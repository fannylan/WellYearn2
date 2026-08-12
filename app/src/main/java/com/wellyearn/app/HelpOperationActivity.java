package com.wellyearn.app;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class HelpOperationActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help_operation);

        findViewById(R.id.buttonBack).setOnClickListener(v -> finish());
        findViewById(R.id.buttonHelp).setOnClickListener(v ->
                startActivity(new Intent(this, HelpPdfListActivity.class)));
        findViewById(R.id.buttonMaintenance).setOnClickListener(v ->
                startActivity(new Intent(this, MaintenanceLoginActivity.class)));
    }
}
