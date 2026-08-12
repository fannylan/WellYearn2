package com.wellyearn.app;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HelpPdfListActivity extends AppCompatActivity {

    private static final int REQUEST_HELP_DIRECTORY = 2301;
    private static final String PREFERENCES = "help_documents";
    private static final String KEY_DIRECTORY_URI = "directory_uri";

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private HelpPdfAdapter adapter;
    private TextView textFolder;
    private TextView textEmpty;
    private Uri directoryUri;

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
        findViewById(R.id.buttonChooseFolder).setOnClickListener(v -> chooseHelpDirectory());
        findViewById(R.id.buttonRefresh).setOnClickListener(v -> loadDocuments());

        String savedUri = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
                .getString(KEY_DIRECTORY_URI, "");
        if (!TextUtils.isEmpty(savedUri)) directoryUri = Uri.parse(savedUri);
        loadDocuments();
    }

    private void chooseHelpDirectory() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_HELP_DIRECTORY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_HELP_DIRECTORY || resultCode != Activity.RESULT_OK
                || data == null || data.getData() == null) return;

        directoryUri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(
                    directoryUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException error) {
            Toast.makeText(this, "无法保存文件夹访问授权，请重新选择", Toast.LENGTH_LONG).show();
            return;
        }
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
                .putString(KEY_DIRECTORY_URI, directoryUri.toString())
                .apply();
        loadDocuments();
    }

    private void loadDocuments() {
        if (directoryUri == null) {
            textFolder.setText("帮助文件夹：尚未选择");
            adapter.submit(Collections.emptyList());
            textEmpty.setText("请点击“选择帮助文件夹”，授权后将自动展示其中的 PDF 文件。\n系统会记住该文件夹。 ");
            textEmpty.setVisibility(View.VISIBLE);
            return;
        }

        textFolder.setText("帮助文件夹：" + directoryUri.getLastPathSegment());
        textEmpty.setText("正在读取帮助文档…");
        textEmpty.setVisibility(View.VISIBLE);
        ioExecutor.execute(() -> {
            try {
                List<HelpPdfItem> items = queryPdfDocuments(directoryUri);
                runOnUiThread(() -> {
                    adapter.submit(items);
                    textEmpty.setText("该帮助文件夹中没有 PDF 文件");
                    textEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    adapter.submit(Collections.emptyList());
                    textEmpty.setText("无法读取帮助文件夹，请重新选择并授权");
                    textEmpty.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private List<HelpPdfItem> queryPdfDocuments(Uri treeUri) {
        List<HelpPdfItem> items = new ArrayList<>();
        ContentResolver resolver = getContentResolver();
        String documentId = DocumentsContract.getTreeDocumentId(treeUri);
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId);
        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
        };
        try (Cursor cursor = resolver.query(childrenUri, projection, null, null, null)) {
            if (cursor == null) return items;
            int idIndex = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameIndex = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int mimeIndex = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_MIME_TYPE);
            int sizeIndex = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_SIZE);
            int modifiedIndex = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED);
            while (cursor.moveToNext()) {
                String name = cursor.getString(nameIndex);
                String mime = cursor.getString(mimeIndex);
                if (!("application/pdf".equals(mime)
                        || (name != null && name.toLowerCase().endsWith(".pdf")))) continue;
                Uri uri = DocumentsContract.buildDocumentUriUsingTree(
                        treeUri, cursor.getString(idIndex));
                items.add(new HelpPdfItem(
                        name == null ? "未命名帮助文档.pdf" : name,
                        uri,
                        cursor.isNull(sizeIndex) ? 0L : cursor.getLong(sizeIndex),
                        cursor.isNull(modifiedIndex) ? 0L : cursor.getLong(modifiedIndex)));
            }
        }
        items.sort(Comparator.comparing(item -> item.name));
        return items;
    }

    private void openPdf(HelpPdfItem item) {
        Intent intent = new Intent(this, HelpPdfViewerActivity.class);
        intent.setData(item.uri);
        intent.putExtra(Intent.EXTRA_TITLE, item.name);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(intent);
        } catch (SecurityException error) {
            Toast.makeText(this, "PDF 访问授权已失效，请重新选择帮助文件夹", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        ioExecutor.shutdown();
        super.onDestroy();
    }

    static final class HelpPdfItem {
        final String name;
        final Uri uri;
        final long size;
        final long modified;

        HelpPdfItem(String name, Uri uri, long size, long modified) {
            this.name = name;
            this.uri = uri;
            this.size = size;
            this.modified = modified;
        }
    }
}
