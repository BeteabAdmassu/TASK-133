package com.eaglepoint.console.export;

import com.opencsv.CSVWriter;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class CsvExporter {

    public Path write(List<Map<String, Object>> rows, List<String> columns, Path outputPath) throws Exception {
        Files.createDirectories(outputPath.getParent());
        try (CSVWriter writer = new CSVWriter(new FileWriter(outputPath.toFile()))) {
            // Write header
            writer.writeNext(columns.toArray(new String[0]));

            // Write data
            for (Map<String, Object> row : rows) {
                String[] values = new String[columns.size()];
                for (int i = 0; i < columns.size(); i++) {
                    Object v = row.get(columns.get(i));
                    values[i] = v != null ? v.toString() : "";
                }
                writer.writeNext(values);
            }
        }
        return outputPath;
    }
}
