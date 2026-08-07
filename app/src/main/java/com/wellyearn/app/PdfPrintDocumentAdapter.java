package com.wellyearn.app;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

final class PdfPrintDocumentAdapter extends PrintDocumentAdapter {

    private final ContentResolver contentResolver;
    private final Uri pdfUri;
    private final String fileName;

    PdfPrintDocumentAdapter(ContentResolver contentResolver, Uri pdfUri, String fileName) {
        this.contentResolver = contentResolver;
        this.pdfUri = pdfUri;
        this.fileName = fileName;
    }

    @Override
    public void onLayout(
            PrintAttributes oldAttributes,
            PrintAttributes newAttributes,
            CancellationSignal cancellationSignal,
            LayoutResultCallback callback,
            Bundle extras) {
        if (cancellationSignal.isCanceled()) {
            callback.onLayoutCancelled();
            return;
        }
        PrintDocumentInfo info = new PrintDocumentInfo.Builder(fileName)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .build();
        callback.onLayoutFinished(info, !newAttributes.equals(oldAttributes));
    }

    @Override
    public void onWrite(
            PageRange[] pages,
            ParcelFileDescriptor destination,
            CancellationSignal cancellationSignal,
            WriteResultCallback callback) {
        new Thread(() -> {
            if (cancellationSignal.isCanceled()) {
                callback.onWriteCancelled();
                return;
            }
            try (InputStream input = contentResolver.openInputStream(pdfUri);
                 OutputStream output = new FileOutputStream(destination.getFileDescriptor())) {
                if (input == null) throw new IOException("无法读取PDF文件");
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (cancellationSignal.isCanceled()) {
                        callback.onWriteCancelled();
                        return;
                    }
                    output.write(buffer, 0, read);
                }
                output.flush();
                callback.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
            } catch (IOException error) {
                callback.onWriteFailed(error.getMessage());
            }
        }, "pdf-print-writer").start();
    }
}
