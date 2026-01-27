package com.smartbell.pro;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class WidgetConfigActivity extends Activity {

    int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set the result to CANCELED. This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED);

        setContentView(R.layout.activity_widget_config);

        // Find the widget id from the intent.
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            mAppWidgetId = extras.getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        // If this activity was started with an invalid widget ID, finish with an error.
        if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        ListView listView = findViewById(R.id.contact_list);
        List<ContactItem> contactItems = loadContacts();

        // Add "Everyone" option
        contactItems.add(0, new ContactItem(null, "全員 (一斉呼出)"));

        ArrayAdapter<ContactItem> adapter = new ArrayAdapter<ContactItem>(this, android.R.layout.simple_list_item_1,
                contactItems);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            ContactItem item = contactItems.get(position);
            saveWidgetPrefs(item);

            // Push widget update
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
            CallWidget.updateAppWidget(this, appWidgetManager, mAppWidgetId);

            // Make sure we pass back the original appWidgetId
            Intent resultValue = new Intent();
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
            setResult(RESULT_OK, resultValue);
            finish();
        });
    }

    private List<ContactItem> loadContacts() {
        List<ContactItem> items = new ArrayList<>();
        SharedPreferences prefs = getSharedPreferences("WidgetPrefs", MODE_PRIVATE);
        String json = prefs.getString("contacts_list", "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                items.add(new ContactItem(obj.getString("id"), obj.getString("name")));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return items;
    }

    private void saveWidgetPrefs(ContactItem item) {
        SharedPreferences.Editor prefs = getSharedPreferences("WidgetPrefs", MODE_PRIVATE).edit();
        prefs.putString("targetId_" + mAppWidgetId, item.id);
        prefs.putString("targetName_" + mAppWidgetId, item.name);
        prefs.apply();
    }

    static class ContactItem {
        String id;
        String name;

        ContactItem(String id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
