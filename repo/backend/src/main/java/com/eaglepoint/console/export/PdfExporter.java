package com.eaglepoint.console.export;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class PdfExporter {

    public Path write(List<Map<String, Object>> rows, List<String> columns, String title, Path outputPath) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            PDType1Font titleFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font bodyFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            float margin = 50;
            float yStart = page.getMediaBox().getHeight() - margin;
            float rowHeight = 15;
            float colWidth = columns.isEmpty() ? 100 : (page.getMediaBox().getWidth() - 2 * margin) / columns.size();

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                // Title
                contentStream.beginText();
                contentStream.setFont(titleFont, 14);
                contentStream.newLineAtOffset(margin, yStart);
                contentStream.showText(title != null ? title : "Export");
                contentStream.endText();

                float y = yStart - 30;

                // Headers
                contentStream.setFont(titleFont, 9);
                for (int i = 0; i < columns.size(); i++) {
                    contentStream.beginText();
                    contentStream.newLineAtOffset(margin + i * colWidth, y);
                    String colText = columns.get(i);
                    if (colText.length() > 15) colText = colText.substring(0, 12) + "...";
                    contentStream.showText(colText);
                    contentStream.endText();
                }
                y -= rowHeight;

                // Data rows
                contentStream.setFont(bodyFont, 8);
                for (Map<String, Object> row : rows) {
                    if (y < margin) break;
                    for (int i = 0; i < columns.size(); i++) {
                        Object value = row.get(columns.get(i));
                        String text = value != null ? value.toString() : "";
                        if (text.length() > 15) text = text.substring(0, 12) + "...";
                        contentStream.beginText();
                        contentStream.newLineAtOffset(margin + i * colWidth, y);
                        contentStream.showText(text);
                        contentStream.endText();
                    }
                    y -= rowHeight;
                }
            }

            Files.createDirectories(outputPath.getParent());
            document.save(outputPath.toFile());
        }
        return outputPath;
    }
}
