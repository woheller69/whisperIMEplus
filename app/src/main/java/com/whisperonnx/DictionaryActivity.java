package com.whisperonnx;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.whisperonnx.utils.PersonalDictionary;
import com.whisperonnx.utils.ThemeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DictionaryActivity extends AppCompatActivity {
    private final List<String> heardPhrases = new ArrayList<>();
    private final List<String> rows = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private TextView emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dictionary);
        ThemeUtils.setStatusBarAppearance(this);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) actionBar.setDisplayHomeAsUpEnabled(true);

        EditText heardInput = findViewById(R.id.dictionary_heard);
        EditText correctInput = findViewById(R.id.dictionary_correct);
        Button addButton = findViewById(R.id.dictionary_add);
        ListView list = findViewById(R.id.dictionary_list);
        emptyView = findViewById(R.id.dictionary_empty);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rows);
        list.setAdapter(adapter);
        refresh();

        addButton.setOnClickListener(v -> {
            String heard = heardInput.getText().toString().trim();
            String correct = correctInput.getText().toString().trim();
            if (heard.isEmpty()) {
                heardInput.setError(getString(R.string.dictionary_required));
                return;
            }
            if (correct.isEmpty()) {
                correctInput.setError(getString(R.string.dictionary_required));
                return;
            }
            PersonalDictionary.put(this, heard, correct);
            heardInput.setText("");
            heardInput.requestFocus();
            refresh();
        });

        list.setOnItemClickListener((parent, view, position, id) -> {
            String heard = heardPhrases.get(position);
            new AlertDialog.Builder(this)
                    .setMessage(getString(R.string.dictionary_delete_question, rows.get(position)))
                    .setPositiveButton(R.string.dictionary_delete, (dialog, which) -> {
                        PersonalDictionary.remove(this, heard);
                        refresh();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        });
    }

    private void refresh() {
        heardPhrases.clear();
        rows.clear();
        for (Map.Entry<String, String> entry : PersonalDictionary.load(this).entrySet()) {
            heardPhrases.add(entry.getKey());
            rows.add(entry.getKey() + " → " + entry.getValue());
        }
        adapter.notifyDataSetChanged();
        emptyView.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
