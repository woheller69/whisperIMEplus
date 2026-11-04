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
    private final List<String> words = new ArrayList<>();
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

        EditText wordInput = findViewById(R.id.dictionary_word);
        EditText variantsInput = findViewById(R.id.dictionary_variants);
        Button addButton = findViewById(R.id.dictionary_add);
        ListView list = findViewById(R.id.dictionary_list);
        emptyView = findViewById(R.id.dictionary_empty);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rows);
        list.setAdapter(adapter);
        refresh();

        addButton.setOnClickListener(v -> {
            String word = wordInput.getText().toString().trim();
            if (word.isEmpty()) {
                wordInput.setError(getString(R.string.dictionary_required));
                return;
            }
            List<String> variants = new ArrayList<>();
            for (String variant : variantsInput.getText().toString().split(",")) {
                if (!variant.trim().isEmpty()) variants.add(variant.trim());
            }
            PersonalDictionary.add(this, word, variants);
            wordInput.setText("");
            variantsInput.setText("");
            wordInput.requestFocus();
            refresh();
        });

        list.setOnItemClickListener((parent, view, position, id) -> {
            String word = words.get(position);
            new AlertDialog.Builder(this)
                    .setMessage(getString(R.string.dictionary_delete_question, word))
                    .setPositiveButton(R.string.dictionary_delete, (dialog, which) -> {
                        PersonalDictionary.remove(this, word);
                        refresh();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        });
    }

    private void refresh() {
        words.clear();
        rows.clear();
        for (Map.Entry<String, List<String>> entry : PersonalDictionary.load(this).entrySet()) {
            words.add(entry.getKey());
            rows.add(entry.getValue().isEmpty()
                    ? entry.getKey()
                    : entry.getKey() + " ← " + String.join(", ", entry.getValue()));
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
