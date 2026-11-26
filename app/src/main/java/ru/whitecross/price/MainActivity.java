package ru.whitecross.price;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.provider.MediaStore;
import android.widget.FrameLayout;
import java.io.OutputStream;
import android.graphics.Color;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.json.JSONObject;

public class MainActivity extends Activity {
    private WebView web;
    private WebView printView; // must stay referenced until printing finishes
    private FrameLayout frame;
    private static final int A4_CSS_WIDTH = 794;            // invoice page width in CSS px (A4 at 96 dpi)
    private static final int A4_W_PT = 595, A4_H_PT = 842;  // PDF page size in points

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        final int black = Color.BLACK; // same black as the wcross.ru header and the page header
        WebView.enableSlowWholeDocumentDraw(); // lets the invoice WebView draw its whole page into the PDF; must precede any WebView
        web = new WebView(this);
        web.setBackgroundColor(black); // no white flash before the splash screen paints
        // Android 15 draws apps edge-to-edge: black strip under the status bar, white strip under the
        // navigation bar / keyboard, page in between.
        final LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        final View top = new View(this), bottom = new View(this);
        top.setBackgroundColor(black);
        bottom.setBackgroundColor(Color.WHITE);
        root.addView(top, new LinearLayout.LayoutParams(-1, 0));
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(bottom, new LinearLayout.LayoutParams(-1, 0));
        frame = new FrameLayout(this);
        frame.addView(root, new FrameLayout.LayoutParams(-1, -1));
        setContentView(frame);
        if (Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                Insets ime = insets.getInsets(WindowInsets.Type.ime());
                top.setLayoutParams(new LinearLayout.LayoutParams(-1, bars.top));
                bottom.setLayoutParams(new LinearLayout.LayoutParams(-1, Math.max(bars.bottom, ime.bottom)));
                root.setPadding(bars.left, 0, bars.right, 0);
                return WindowInsets.CONSUMED;
            });
            // white status-bar icons on black, dark navigation icons on white
            getWindow().getInsetsController().setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        } else {
            getWindow().setStatusBarColor(black);
        }
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setTextZoom(100); // Samsung font-size setting would otherwise scale the page text and break the layout
        s.setAllowFileAccess(true); // the downloaded photo pack lives in the app's files dir (see downloadPhotos)
        web.addJavascriptInterface(new Bridge(), "Android");
        web.loadUrl("file:///android_asset/index.html");
    }

    /** Back button walks back through the app's screens, then exits. */
    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    private void writePdf(WebView view, int widthPx, int heightPx, String fileName) {
        PdfDocument doc = new PdfDocument();
        try {
            float scale = (float) A4_W_PT / widthPx;
            int pageHeightPx = Math.round(A4_H_PT / scale);
            // shortcut: pages are cut at fixed heights, so a long document may split a table row; fine for typical 1–2 page documents
            for (int top = 0, n = 1; top < heightPx; top += pageHeightPx, n++) {
                PdfDocument.Page page = doc.startPage(new PdfDocument.PageInfo.Builder(A4_W_PT, A4_H_PT, n).create());
                Canvas c = page.getCanvas();
                c.scale(scale, scale);
                c.translate(0, -top);
                view.draw(c);
                doc.finishPage(page);
            }
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName + ".pdf");
            v.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/WHITECROSS");
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new java.io.IOException("не удалось создать файл в «Загрузках»");
            try (OutputStream out = getContentResolver().openOutputStream(uri)) { doc.writeTo(out); }
            Intent send = new Intent(Intent.ACTION_SEND).setType("application/pdf")
                    .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, "Отправить документ"));
            web.evaluateJavascript("onPdf(null," + JSONObject.quote(fileName + ".pdf") + ")", null);
        } catch (Exception e) {
            web.evaluateJavascript("onPdf(" + JSONObject.quote(String.valueOf(e.getMessage())) + ",null)", null);
        } finally {
            doc.close();
            frame.removeView(view);
        }
    }

    class Bridge {
        /** Opens the Android print dialog for an HTML page (there the user picks "Save as PDF" or a printer). */
        @JavascriptInterface
        public void print(final String html, final String title) {
            runOnUiThread(() -> {
                printView = new WebView(MainActivity.this);
                printView.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
                        pm.print(title, view.createPrintDocumentAdapter(title),
                                new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build());
                    }
                });
                printView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
            });
        }

        /**
         * Builds a PDF from the invoice/quote HTML right in the app, saves it to Downloads/WHITECROSS and opens "Send".
         * Answers via window.onPdf(error, fileName).
         */
        @JavascriptInterface
        public void pdf(final String html, final String fileName) {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT < 29) { print(html, fileName); return; } // no MediaStore Downloads before Android 10
                final float density = getResources().getDisplayMetrics().density;
                final int widthPx = Math.round(A4_CSS_WIDTH * density);
                final WebView pv = new WebView(MainActivity.this);
                pv.setBackgroundColor(Color.WHITE);
                pv.setTranslationX(-widthPx * 2); // laid out and attached, but off-screen
                frame.addView(pv, new FrameLayout.LayoutParams(widthPx, Math.round(1123 * density)));
                printView = pv;
                pv.setWebViewClient(new WebViewClient() {
                    boolean done;
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        if (done) return;
                        done = true;
                        // grow the view to the full document, give pictures a moment, then draw page by page
                        view.postDelayed(() -> {
                            int heightPx = Math.max(1, Math.round(view.getContentHeight() * density));
                            view.setLayoutParams(new FrameLayout.LayoutParams(widthPx, heightPx));
                            view.postDelayed(() -> writePdf(view, widthPx, heightPx, fileName), 400);
                        }, 600);
                    }
                });
                pv.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null);
            });
        }

        /** file:// folder with the unpacked photo pack; the page builds photo URLs from it. */
        @JavascriptInterface
        public String photoDir() {
            return "file://" + new File(getFilesDir(), "photos").getAbsolutePath() + "/";
        }

        /**
         * Downloads the photo pack (one zip) and unpacks it into files/photos, replacing the old one only
         * when the whole download succeeded. Reports via window.onPhotos(percent, error, done).
         */
        @JavascriptInterface
        public void downloadPhotos(final String url) {
            new Thread(() -> {
                File dir = new File(getFilesDir(), "photos"), tmp = new File(getFilesDir(), "photos.part");
                try {
                    deleteTree(tmp);
                    if (!tmp.mkdirs()) throw new IOException("нет места в памяти телефона");
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(30000);
                    c.setReadTimeout(60000);
                    if (c.getResponseCode() != 200) throw new IOException("сервер ответил " + c.getResponseCode());
                    final long total = c.getContentLengthLong();
                    final long[] read = {0};
                    final int[] shown = {-1};
                    InputStream counting = new FilterInputStream(c.getInputStream()) {
                        @Override public int read(byte[] b, int off, int len) throws IOException {
                            int n = super.read(b, off, len);
                            if (n > 0 && total > 0) {
                                read[0] += n;
                                int pct = (int) (read[0] * 100 / total);
                                if (pct != shown[0]) { shown[0] = pct; report(Math.min(pct, 99) + ",null,false"); }
                            }
                            return n;
                        }
                    };
                    byte[] buf = new byte[64 * 1024];
                    try (ZipInputStream zip = new ZipInputStream(counting)) {
                        for (ZipEntry e; (e = zip.getNextEntry()) != null; ) {
                            // flat names only (e.g. 55883-0.webp): never write outside the photos folder
                            if (e.isDirectory() || !e.getName().matches("[A-Za-z0-9_-]+\\.webp")) continue;
                            try (FileOutputStream out = new FileOutputStream(new File(tmp, e.getName()))) {
                                for (int n; (n = zip.read(buf)) > 0; ) out.write(buf, 0, n);
                            }
                        }
                    }
                    deleteTree(dir);
                    if (!tmp.renameTo(dir)) throw new IOException("не удалось сохранить фото");
                    report("100,null,true");
                } catch (Exception e) {
                    deleteTree(tmp);
                    report("0," + JSONObject.quote(String.valueOf(e.getMessage())) + ",false");
                }
            }).start();
        }
    }

    private void report(String args) {
        web.post(() -> web.evaluateJavascript("onPhotos(" + args + ")", null));
    }

    private static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }
}
