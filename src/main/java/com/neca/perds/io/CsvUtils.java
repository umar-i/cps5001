package com.neca.perds.io;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CsvUtils {
    private CsvUtils() {}

    public static List<String> splitLine(String line) {
        Objects.requireNonNull(line, "line");

        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    boolean isEscapedQuote = i + 1 < line.length() && line.charAt(i + 1) == '"';
                    if (isEscapedQuote) {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(ch);
                }
                continue;
            }

            if (ch == ',') {
                fields.add(current.toString());
                current.setLength(0);
                continue;
            }
            if (ch == '"') {
                inQuotes = true;
                continue;
            }

            current.append(ch);
        }

        if (inQuotes) {
            throw new IllegalArgumentException("Unclosed quoted field");
        }

        fields.add(current.toString());
        return List.copyOf(fields);
    }
}
