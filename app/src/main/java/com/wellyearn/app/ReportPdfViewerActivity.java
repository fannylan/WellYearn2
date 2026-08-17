package com.wellyearn.app;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Displays report PDFs inside the app as a continuously scrollable list of pages. */
public class ReportPdfViewerActivity extends AppCompatActivity {

    public static final String EXTRA_PDF_URI = "report_pdf_uri";
    public static final String EXTRA_PDF_TITLE = "report_pdf_title";

    private static final int MAX_RENDER_WIDTH_PX = 1600;
    private static final int MAX_RENDER_HEIGHT_PX = 4096;

    private final ExecutorService renderExecutor = Executors.newSingleThreadExecutor();
    private final Set<Integer> loadingPages =
            Collections.synchronizedSet(new HashSet<>());
    private final Set<Integer> failedPages =
            Collections.synchronizedSet(new HashSet<>());
    private final LruCache<Integer, Bitmap> pageCache = new LruCache<Integer, Bitmap>(48 * 1024) {
        @Override
        protected int sizeOf(@NonNull Integer key, @NonNull Bitmap bitmap) {
            return Math.max(1, bitmap.getAllocationByteCount() / 1024);
        }
    };

    private ParcelFileDescriptor fileDescriptor;
    private PdfRenderer pdfRenderer;
    private RecyclerView recyclerPages;
    private ProgressBar progressDocument;
    private TextView textPageCount;
    private TextView textDocumentError;
    private PdfPageAdapter pageAdapter;
    private volatile boolean destroyed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_pdf_viewer);

        recyclerPages = findViewById(R.id.recyclerPdfPages);
        progressDocument = findViewById(R.id.progressPdfDocument);
        textPageCount = findViewById(R.id.textPdfPageCount);
        textDocumentError = findViewById(R.id.textPdfDocumentError);
        TextView textTitle = findViewById(R.id.textPdfTitle);

        String title = getIntent().getStringExtra(EXTRA_PDF_TITLE);
        textTitle.setText(title == null || title.trim().isEmpty() ? "检测报告" : title);
        findViewById(R.id.buttonBack).setOnClickListener(view -> finish());

        pageAdapter = new PdfPageAdapter();
        recyclerPages.setLayoutManager(new LinearLayoutManager(this));
        recyclerPages.setAdapter(pageAdapter);

        openDocument(getIntent().getStringExtra(EXTRA_PDF_URI));
    }

    private void openDocument(String uriText) {
        if (uriText == null || uriText.trim().isEmpty()) {
            showDocumentError("PDF文件地址无效");
            return;
        }
        progressDocument.setVisibility(View.VISIBLE);
        renderExecutor.execute(() -> {
            try {
                fileDescriptor = getContentResolver().openFileDescriptor(Uri.parse(uriText), "r");
                if (fileDescriptor == null) throw new IOException("无法读取PDF文件");
                pdfRenderer = new PdfRenderer(fileDescriptor);
                int pageCount = pdfRenderer.getPageCount();
                if (pageCount <= 0) throw new IOException("PDF没有可显示页面");
                runOnUiThread(() -> {
                    if (destroyed) return;
                    progressDocument.setVisibility(View.GONE);
                    textPageCount.setText("共 " + pageCount + " 页");
                    pageAdapter.setPageCount(pageCount);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (!destroyed) showDocumentError("无法打开PDF：" + safeMessage(error));
                });
            }
        });
    }

    private void requestPage(int pageIndex) {
        if (destroyed || pdfRenderer == null || pageCache.get(pageIndex) != null
                || failedPages.contains(pageIndex) || !loadingPages.add(pageIndex)) {
            return;
        }
        renderExecutor.execute(() -> renderPage(pageIndex));
    }

    private void renderPage(int pageIndex) {
        Bitmap bitmap = null;
        try {
            if (destroyed || pdfRenderer == null) return;
            try (PdfRenderer.Page page = pdfRenderer.openPage(pageIndex)) {
                int availableWidth = Math.max(1,
                        getResources().getDisplayMetrics().widthPixels - dpToPx(32));
                int targetWidth = Math.min(MAX_RENDER_WIDTH_PX, availableWidth);
                float scale = targetWidth / (float) Math.max(1, page.getWidth());
                int targetHeight = Math.max(1, Math.round(page.getHeight() * scale));
                if (targetHeight > MAX_RENDER_HEIGHT_PX) {
                    targetHeight = MAX_RENDER_HEIGHT_PX;
                    scale = targetHeight / (float) Math.max(1, page.getHeight());
                    targetWidth = Math.max(1, Math.round(page.getWidth() * scale));
                }
                bitmap = Bitmap.createBitmap(
                        targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(Color.WHITE);
                Matrix matrix = new Matrix();
                matrix.setScale(scale, scale);
                page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            }

            Bitmap renderedBitmap = bitmap;
            bitmap = null;
            runOnUiThread(() -> {
                loadingPages.remove(pageIndex);
                if (destroyed) {
                    renderedBitmap.recycle();
                    return;
                }
                pageCache.put(pageIndex, renderedBitmap);
                pageAdapter.notifyItemChanged(pageIndex);
            });
        } catch (Exception error) {
            if (bitmap != null) bitmap.recycle();
            runOnUiThread(() -> {
                loadingPages.remove(pageIndex);
                if (destroyed) return;
                failedPages.add(pageIndex);
                pageAdapter.notifyItemChanged(pageIndex);
                Toast.makeText(
                        this,
                        "第 " + (pageIndex + 1) + " 页显示失败：" + safeMessage(error),
                        Toast.LENGTH_LONG).show();
            });
        }
    }

    private void showDocumentError(String message) {
        progressDocument.setVisibility(View.GONE);
        recyclerPages.setVisibility(View.GONE);
        textDocumentError.setText(message);
        textDocumentError.setVisibility(View.VISIBLE);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private static String safeMessage(Throwable error) {
        return error == null || error.getMessage() == null ? "未知错误" : error.getMessage();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        recyclerPages.setAdapter(null);
        renderExecutor.execute(() -> {
            if (pdfRenderer != null) pdfRenderer.close();
            if (fileDescriptor != null) {
                try {
                    fileDescriptor.close();
                } catch (IOException ignored) {
                }
            }
            for (Bitmap bitmap : pageCache.snapshot().values()) {
                if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            }
            pageCache.evictAll();
        });
        renderExecutor.shutdown();
        super.onDestroy();
    }

    private final class PdfPageAdapter extends RecyclerView.Adapter<PdfPageViewHolder> {

        private int pageCount;

        PdfPageAdapter() {
            setHasStableIds(true);
        }

        void setPageCount(int pageCount) {
            this.pageCount = pageCount;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public PdfPageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_report_pdf_page, parent, false);
            return new PdfPageViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PdfPageViewHolder holder, int position) {
            holder.textPageNumber.setText("第 " + (position + 1) + " 页");
            holder.imagePage.setImageDrawable(null);
            Bitmap cached = pageCache.get(position);
            boolean failed = failedPages.contains(position);
            if (cached != null && !cached.isRecycled()) {
                holder.imagePage.setImageBitmap(cached);
                holder.imagePage.setVisibility(View.VISIBLE);
                holder.progressPage.setVisibility(View.GONE);
                holder.textPageError.setVisibility(View.GONE);
            } else if (failed) {
                holder.imagePage.setVisibility(View.GONE);
                holder.progressPage.setVisibility(View.GONE);
                holder.textPageError.setVisibility(View.VISIBLE);
            } else {
                holder.imagePage.setVisibility(View.INVISIBLE);
                holder.progressPage.setVisibility(View.VISIBLE);
                holder.textPageError.setVisibility(View.GONE);
                requestPage(position);
            }
        }

        @Override
        public void onViewRecycled(@NonNull PdfPageViewHolder holder) {
            holder.imagePage.setImageDrawable(null);
            super.onViewRecycled(holder);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getItemCount() {
            return pageCount;
        }
    }

    private static final class PdfPageViewHolder extends RecyclerView.ViewHolder {

        final ImageView imagePage;
        final ProgressBar progressPage;
        final TextView textPageNumber;
        final TextView textPageError;

        PdfPageViewHolder(@NonNull View itemView) {
            super(itemView);
            imagePage = itemView.findViewById(R.id.imagePdfPage);
            progressPage = itemView.findViewById(R.id.progressPdfPage);
            textPageNumber = itemView.findViewById(R.id.textPdfPageNumber);
            textPageError = itemView.findViewById(R.id.textPdfPageError);
        }
    }
}
