package com.wellyearn.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HelpPdfListActivity extends AppCompatActivity {

    private static final String HELP_ASSET_DIRECTORY = "help";

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private HelpPdfAdapter adapter;
    private TextView textFolder;
    private TextView textEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help_pdf_list);

        textFolder = findViewById(R.id.textHelpFolder);
        textEmpty = findViewById(R.id.textEmpty);
        RecyclerView recyclerView = findViewById(R.id.recyclerHelpPdfs);
        adapter = new HelpPdfAdapter(this::openPdf);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        findViewById(R.id.buttonBack).setOnClickListener(v -> finish());
        loadDocuments();
    }

    private void loadDocuments() {
        textFolder.setText("资源目录：app/src/main/assets/help");
        textEmpty.setText("正在读取帮助文档…");
        textEmpty.setVisibility(View.VISIBLE);
        ioExecutor.execute(() -> {
            List<HelpPdfItem> items = new ArrayList<>();
            String errorMessage = null;
            try {
                String[] fileNames = getAssets().list(HELP_ASSET_DIRECTORY);
                if (fileNames != null) {
                    for (String fileName : fileNames) {
                        if (fileName == null
                                || !fileName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                            continue;
                        }
                        items.add(new HelpPdfItem(
                                fileName,
                                HELP_ASSET_DIRECTORY + "/" + fileName));
                    }
                }
                items.sort(Comparator.comparing(
                        item -> item.name,
                        String.CASE_INSENSITIVE_ORDER));
            } catch (IOException error) {
                errorMessage = "无法读取应用资源中的帮助文件";
            }

            String finalErrorMessage = errorMessage;
            runOnUiThread(() -> {
                adapter.submit(items);
                textFolder.setText(String.format(
                        Locale.CHINA,
                        "资源目录：app/src/main/assets/help（共 %d 份PDF）",
                        items.size()));
                if (finalErrorMessage != null) {
                    textEmpty.setText(finalErrorMessage);
                    textEmpty.setVisibility(View.VISIBLE);
                } else if (items.isEmpty()) {
                    textEmpty.setText(
                            "帮助资源目录中暂无PDF文件。\n请将PDF导入 app/src/main/assets/help 后重新打包安装。 ");
                    textEmpty.setVisibility(View.VISIBLE);
                } else {
                    textEmpty.setVisibility(View.GONE);
                }
            });
        });
    }

    private void openPdf(HelpPdfItem item) {
        Intent intent = new Intent(this, HelpPdfViewerActivity.class);
        intent.putExtra(Intent.EXTRA_TITLE, item.name);
        intent.putExtra(HelpPdfViewerActivity.EXTRA_ASSET_PATH, item.assetPath);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        ioExecutor.shutdown();
        super.onDestroy();
    }

    static final class HelpPdfItem {
        final String name;
        final String assetPath;

        HelpPdfItem(String name, String assetPath) {
            this.name = name;
            this.assetPath = assetPath;
        }
    }
}
