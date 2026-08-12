package com.wellyearn.app;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HelpPdfViewerActivity extends AppCompatActivity {

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private ParcelFileDescriptor fileDescriptor;
    private PdfRenderer renderer;
    private Bitmap displayedBitmap;
    private int currentPage;
    private ImageView imagePage;
    private TextView textPage;
    private ProgressBar progressBar;
    private Button buttonPrevious;
    private Button buttonNext;
    private volatile boolean destroyed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help_pdf_viewer);
        imagePage = findViewById(R.id.imagePdfPage);
        textPage = findViewById(R.id.textPageNumber);
        progressBar = findViewById(R.id.progressPdf);
        buttonPrevious = findViewById(R.id.buttonPrevious);
        buttonNext = findViewById(R.id.buttonNext);
        String title = getIntent().getStringExtra(android.content.Intent.EXTRA_TITLE);
        ((TextView) findViewById(R.id.textPdfTitle)).setText(
                title == null ? "帮助文档" : title);

        findViewById(R.id.buttonBack).setOnClickListener(v -> finish());
        buttonPrevious.setOnClickListener(v -> renderPage(currentPage - 1));
        buttonNext.setOnClickListener(v -> renderPage(currentPage + 1));
        openDocument(getIntent().getData());
    }

    private void openDocument(Uri uri) {
        if (uri == null) {
            Toast.makeText(this, "PDF 地址无效", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        setLoading(true);
        ioExecutor.execute(() -> {
            try {
                fileDescriptor = getContentResolver().openFileDescriptor(uri, "r");
                if (fileDescriptor == null) throw new IOException("无法读取PDF文件");
                renderer = new PdfRenderer(fileDescriptor);
                if (renderer.getPageCount() <= 0) throw new IOException("PDF没有可显示页面");
                renderPageOnWorker(0);
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (destroyed) return;
                    setLoading(false);
                    Toast.makeText(this, "无法打开PDF：" + safeMessage(error),
                            Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });
    }

    private void renderPage(int pageIndex) {
        if (renderer == null || pageIndex < 0 || pageIndex >= renderer.getPageCount()) return;
        setLoading(true);
        buttonPrevious.setEnabled(false);
        buttonNext.setEnabled(false);
        ioExecutor.execute(() -> {
            try {
                renderPageOnWorker(pageIndex);
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (destroyed) return;
                    setLoading(false);
                    Toast.makeText(this, "PDF页面显示失败：" + safeMessage(error),
                            Toast.LENGTH_LONG).show();
                    updateNavigation();
                });
            }
        });
    }

    private void renderPageOnWorker(int pageIndex) {
        PdfRenderer activeRenderer = renderer;
        if (destroyed || activeRenderer == null) return;
        Bitmap bitmap;
        try (PdfRenderer.Page page = activeRenderer.openPage(pageIndex)) {
            int targetWidth = Math.max(960, getResources().getDisplayMetrics().widthPixels - 56);
            float scale = targetWidth / (float) Math.max(1, page.getWidth());
            int targetHeight = Math.max(1, Math.round(page.getHeight() * scale));
            bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(android.graphics.Color.WHITE);
            Matrix matrix = new Matrix();
            matrix.setScale(scale, scale);
            page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        }
        runOnUiThread(() -> {
            if (destroyed) {
                bitmap.recycle();
                return;
            }
            Bitmap previous = displayedBitmap;
            displayedBitmap = bitmap;
            currentPage = pageIndex;
            imagePage.setImageBitmap(bitmap);
            if (previous != null && previous != bitmap) previous.recycle();
            setLoading(false);
            updateNavigation();
        });
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void updateNavigation() {
        if (renderer == null) return;
        int pageCount = renderer.getPageCount();
        textPage.setText((currentPage + 1) + " / " + pageCount);
        buttonPrevious.setEnabled(currentPage > 0);
        buttonNext.setEnabled(currentPage + 1 < pageCount);
    }

    private static String safeMessage(Throwable error) {
        return error == null || error.getMessage() == null ? "未知错误" : error.getMessage();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        ioExecutor.execute(() -> {
            if (renderer != null) renderer.close();
            if (fileDescriptor != null) {
                try {
                    fileDescriptor.close();
                } catch (IOException ignored) {
                }
            }
        });
        ioExecutor.shutdown();
        imagePage.setImageDrawable(null);
        if (displayedBitmap != null) displayedBitmap.recycle();
        super.onDestroy();
    }
}
