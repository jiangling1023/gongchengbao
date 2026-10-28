package com.safetyrepair;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 导出工具：在不引入任何第三方库的前提下，使用系统 API 生成报表。
 *
 * <ul>
 *   <li>Excel：UTF-8 BOM + 逗号分隔文本（扩展名 .xls），Excel 可直接打开且中文不乱码。</li>
 *   <li>PDF：android.graphics.pdf.PdfDocument 绘制多页表格（表头 + 行，行高固定，超出自动分页，长文本截断）。</li>
 * </ul>
 *
 * <p>导出流程：
 * <ol>
 *   <li>调用方通过 {@link #prepare(String, String[], List)} 暂存数据，再用 {@link #buildExcelIntent(String)}
 *   / {@link #buildPdfIntent(String)} 取得 SAF Intent，由 Fragment 自行 {@code startActivityForResult}。</li>
 *   <li>用户在 SAF 选择器中选择保存位置后，在 Fragment 的 onActivityResult 中调用 {@link #commit(Activity, int, Intent)}。</li>
 *   <li>实际写入在后台线程执行，避免主线程阻塞；结果通过 Toast 在主线程提示。</li>
 * </ol>
 * 全程使用 SAF，无需外部存储权限。
 */
public final class Exporter {
    private static final String BOM = "﻿";

    private static String pendingFormat; // "excel" | "pdf"
    private static String[] pendingHeaders;
    private static List<String[]> pendingRows;

    private Exporter() {
        // 工具类，禁止实例化
    }

    /** 暂存待导出数据（在发起 SAF 之前调用）。 */
    public static void prepare(String format, String[] headers, List<String[]> rows) {
        pendingFormat = format;
        pendingHeaders = headers;
        pendingRows = rows;
    }

    /** 构造 Excel 导出 Intent（SAF 选保存位置）。 */
    public static Intent buildExcelIntent(String title) {
        return buildIntent(title, "application/vnd.ms-excel");
    }

    /** 构造 PDF 导出 Intent（SAF 选保存位置）。 */
    public static Intent buildPdfIntent(String title) {
        return buildIntent(title, "application/pdf");
    }

    private static Intent buildIntent(String title, String mime) {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_TITLE, title);
        return i;
    }

    /**
     * 在 Fragment.onActivityResult 中调用，将报表写入用户选择的文件。
     * 若用户取消或无 Uri，仅清理暂存数据，不抛异常。
     */
    public static void commit(Activity act, int resultCode, Intent data) {
        if (act == null || resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            clearPending();
            return;
        }
        Uri uri = data.getData();
        String[] headers = pendingHeaders;
        List<String[]> rows = pendingRows;
        String format = pendingFormat;
        clearPending();
        if ("pdf".equals(format)) {
            writePdf(act, uri, headers, rows);
        } else {
            writeExcel(act, uri, headers, rows);
        }
    }

    private static void clearPending() {
        pendingFormat = null;
        pendingHeaders = null;
        pendingRows = null;
    }

    // -------------------------- Excel --------------------------

    private static void writeExcel(final Activity act, final Uri uri, final String[] headers, final List<String[]> rows) {
        new Thread(() -> {
            try {
                StringBuilder sb = new StringBuilder();
                sb.append(BOM);
                if (headers != null) {
                    sb.append(joinCsv(headers)).append("\n");
                }
                if (rows != null) {
                    for (String[] r : rows) {
                        sb.append(joinCsv(r)).append("\n");
                    }
                }
                OutputStream os = act.getContentResolver().openOutputStream(uri);
                if (os == null) {
                    throw new IOException("无法打开输出流");
                }
                os.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                os.close();
                toast(act, "已导出 Excel：" + nameOf(uri));
            } catch (Exception e) {
                toast(act, "导出 Excel 失败：" + e.getMessage());
            }
        }).start();
    }

    private static String joinCsv(String[] arr) {
        StringBuilder sb = new StringBuilder();
        if (arr != null) {
            for (int i = 0; i < arr.length; i++) {
                if (i > 0) {
                    sb.append(",");
                }
                sb.append(csvCell(arr[i]));
            }
        }
        return sb.toString();
    }

    /** CSV 单元格转义：含逗号/引号/换行的用双引号包裹，内部引号加倍。 */
    private static String csvCell(String s) {
        if (s == null) {
            s = "";
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    // -------------------------- PDF --------------------------

    private static void writePdf(final Activity act, final Uri uri, final String[] headers, final List<String[]> rows) {
        new Thread(() -> {
            try {
                final int pageW = 595; // A4 @72dpi，单位 pt
                final int pageH = 842;
                final int margin = 36;
                final int titleY = 32;
                final int subY = 48;
                final int tableTop = 64;
                final int headerH = 22;
                final int rowH = 20;
                final int contentW = pageW - 2 * margin;
                String[] hs = (headers != null) ? headers : new String[0];
                int n = Math.max(hs.length, 1);
                float[] colW = new float[n];
                for (int i = 0; i < n; i++) {
                    colW[i] = (float) contentW / n;
                }

                PdfDocument doc = new PdfDocument();

                Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                textPaint.setColor(Color.BLACK);
                textPaint.setTextSize(8);

                Paint headerText = new Paint(Paint.ANTI_ALIAS_FLAG);
                headerText.setColor(Color.WHITE);
                headerText.setTextSize(8);
                headerText.setTypeface(Typeface.DEFAULT_BOLD);

                Paint headerBg = new Paint();
                headerBg.setColor(Color.parseColor("#1f6feb"));

                Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                titlePaint.setColor(Color.BLACK);
                titlePaint.setTextSize(14);
                titlePaint.setTypeface(Typeface.DEFAULT_BOLD);

                int y = tableTop;
                PdfDocument.Page page = doc.startPage(new PdfDocument.PageInfo.Builder(pageW, pageH, 1).create());
                Canvas canvas = page.getCanvas();
                canvas.drawText("安防维修排查 - 导出报表", margin, titleY, titlePaint);
                int count = (rows == null) ? 0 : rows.size();
                String when = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
                canvas.drawText("共 " + count + " 条记录  ·  生成时间 " + when, margin, subY, textPaint);
                drawHeader(canvas, margin, y, colW, hs, headerBg, headerText, headerH);
                y += headerH;

                if (rows != null) {
                    for (String[] row : rows) {
                        if (y + rowH > pageH - margin) {
                            doc.finishPage(page);
                            page = doc.startPage(new PdfDocument.PageInfo.Builder(pageW, pageH, doc.getPages().size() + 1).create());
                            canvas = page.getCanvas();
                            y = tableTop;
                            drawHeader(canvas, margin, y, colW, hs, headerBg, headerText, headerH);
                            y += headerH;
                        }
                        drawRow(canvas, margin, y, colW, row, textPaint, rowH);
                        y += rowH;
                    }
                }
                doc.finishPage(page);

                OutputStream os = act.getContentResolver().openOutputStream(uri);
                if (os == null) {
                    throw new IOException("无法打开输出流");
                }
                doc.writeTo(os);
                doc.close();
                os.close();
                toast(act, "已导出 PDF：" + nameOf(uri));
            } catch (Exception e) {
                toast(act, "导出 PDF 失败：" + e.getMessage());
            }
        }).start();
    }

    private static void drawHeader(Canvas c, int margin, int y, float[] colW, String[] hs,
                                   Paint bg, Paint tp, int h) {
        c.drawRect(margin, y, margin + sum(colW), y + h, bg);
        float x = margin;
        for (int i = 0; i < colW.length; i++) {
            String t = (i < hs.length) ? hs[i] : "";
            c.drawText(fit(t, tp, colW[i] - 4), x + 2, y + h - 7, tp);
            x += colW[i];
        }
    }

    private static void drawRow(Canvas c, int margin, int y, float[] colW, String[] row, Paint p, int h) {
        float x = margin;
        for (int i = 0; i < colW.length; i++) {
            String t = (row != null && i < row.length) ? row[i] : "";
            c.drawText(fit(t, p, colW[i] - 4), x + 2, y + h - 6, p);
            x += colW[i];
        }
    }

    private static float sum(float[] a) {
        float s = 0;
        for (float v : a) {
            s += v;
        }
        return s;
    }

    /** 按列宽截断文本，超出加省略号。 */
    private static String fit(String s, Paint p, float maxW) {
        if (s == null) {
            s = "";
        }
        if (p.measureText(s) <= maxW) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() > 1 && p.measureText(sb.toString() + "…") > maxW) {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString() + "…";
    }

    // -------------------------- 辅助 --------------------------

    private static String nameOf(Uri uri) {
        try {
            String p = uri.getLastPathSegment();
            return (p == null) ? uri.toString() : p;
        } catch (Exception e) {
            return uri.toString();
        }
    }

    private static void toast(final Activity act, final String msg) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (act != null) {
                Toast.makeText(act, msg, Toast.LENGTH_LONG).show();
            }
        });
    }
}
