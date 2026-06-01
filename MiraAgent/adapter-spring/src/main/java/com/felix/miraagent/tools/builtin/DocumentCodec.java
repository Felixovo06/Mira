package com.felix.miraagent.tools.builtin;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 文档读写编解码：读用 Tika（全格式 → 纯文本），写用 POI（docx/xlsx）/ 直写（txt/md/csv）。
 * <p>编辑遵循「读出文本 → 模型改 → 写回」的稳健路线，复杂排版会简化，专注主流文档内容场景。
 */
final class DocumentCodec {

    private DocumentCodec() {
    }

    /** 支持写入的格式（按扩展名）。 */
    enum WriteFormat {
        DOCX, XLSX, TXT, MD, CSV;

        static WriteFormat fromPath(String path) {
            String lower = path.toLowerCase(Locale.ROOT);
            int dot = lower.lastIndexOf('.');
            String ext = dot >= 0 ? lower.substring(dot + 1) : "";
            return switch (ext) {
                case "docx" -> DOCX;
                case "xlsx" -> XLSX;
                case "md", "markdown" -> MD;
                case "csv" -> CSV;
                case "txt", "" -> TXT;
                default -> throw new IllegalArgumentException(
                        "Unsupported write format '." + ext + "'. Supported: docx, xlsx, txt, md, csv");
            };
        }
    }

    // ---- 读 ----

    /** Tika 自动识别格式抽取纯文本；超过 maxChars 截断并标注。 */
    static String extractText(Path file, int maxChars) throws Exception {
        AutoDetectParser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler(-1); // -1 = 不限，由下方手动截断
        Metadata metadata = new Metadata();
        try (InputStream in = Files.newInputStream(file)) {
            parser.parse(in, handler, metadata, new ParseContext());
        }
        String text = handler.toString().strip();
        if (text.length() > maxChars) {
            return text.substring(0, maxChars) + "\n...[truncated]";
        }
        return text;
    }

    // ---- 写 ----

    /** 按格式写入文件，返回人类可读的结果摘要。 */
    static String write(Path file, WriteFormat format, String content) throws Exception {
        String text = content == null ? "" : content;
        return switch (format) {
            case TXT, MD -> {
                Files.writeString(file, text, StandardCharsets.UTF_8);
                yield "wrote " + text.length() + " chars";
            }
            case CSV -> {
                Files.writeString(file, text, StandardCharsets.UTF_8);
                yield "wrote " + countLines(text) + " line(s)";
            }
            case DOCX -> writeDocx(file, text);
            case XLSX -> writeXlsx(file, text);
        };
    }

    /** docx：按行渲染，# / ## / ### 转标题，- 或 * 转项目符号，其余为正文段落。 */
    private static String writeDocx(Path file, String content) throws Exception {
        int paragraphs = 0;
        try (XWPFDocument doc = new XWPFDocument()) {
            for (String raw : content.split("\n", -1)) {
                String line = raw.stripTrailing();
                XWPFParagraph p = doc.createParagraph();
                String trimmed = line.strip();
                if (trimmed.startsWith("### ")) {
                    addHeading(p, trimmed.substring(4), 13);
                } else if (trimmed.startsWith("## ")) {
                    addHeading(p, trimmed.substring(3), 15);
                } else if (trimmed.startsWith("# ")) {
                    addHeading(p, trimmed.substring(2), 18);
                } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                    p.setIndentationLeft(360);
                    XWPFRun run = p.createRun();
                    run.setText("• " + trimmed.substring(2));
                } else {
                    XWPFRun run = p.createRun();
                    run.setText(line);
                }
                paragraphs++;
            }
            try (OutputStream out = Files.newOutputStream(file)) {
                doc.write(out);
            }
        }
        return "wrote docx with " + paragraphs + " paragraph(s)";
    }

    private static void addHeading(XWPFParagraph p, String text, int size) {
        XWPFRun run = p.createRun();
        run.setBold(true);
        run.setFontSize(size);
        run.setText(text.strip());
    }

    /** xlsx：把 CSV 文本写成单 sheet，支持双引号包裹的字段。 */
    private static String writeXlsx(Path file, String content) throws Exception {
        List<List<String>> rows = parseCsv(content);
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Sheet1");
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r);
                List<String> cells = rows.get(r);
                for (int c = 0; c < cells.size(); c++) {
                    Cell cell = row.createCell(c);
                    cell.setCellValue(cells.get(c));
                }
            }
            try (OutputStream out = Files.newOutputStream(file)) {
                wb.write(out);
            }
        }
        return "wrote xlsx with " + rows.size() + " row(s)";
    }

    /** 极简 CSV 解析：支持双引号字段与字段内逗号、转义双引号("")。 */
    private static List<List<String>> parseCsv(String content) {
        List<List<String>> rows = new ArrayList<>();
        for (String line : content.split("\n", -1)) {
            line = line.stripTrailing();
            if (line.isEmpty() && rows.isEmpty()) {
                continue;
            }
            List<String> cells = new ArrayList<>();
            StringBuilder cur = new StringBuilder();
            boolean inQuotes = false;
            for (int i = 0; i < line.length(); i++) {
                char ch = line.charAt(i);
                if (inQuotes) {
                    if (ch == '"') {
                        if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                            cur.append('"');
                            i++;
                        } else {
                            inQuotes = false;
                        }
                    } else {
                        cur.append(ch);
                    }
                } else if (ch == '"') {
                    inQuotes = true;
                } else if (ch == ',') {
                    cells.add(cur.toString());
                    cur.setLength(0);
                } else {
                    cur.append(ch);
                }
            }
            cells.add(cur.toString());
            rows.add(cells);
        }
        return rows;
    }

    private static int countLines(String text) {
        if (text.isEmpty()) {
            return 0;
        }
        return (int) text.lines().count();
    }
}
