package nz.kiwifinance.imports;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import nz.kiwifinance.common.error.ApiException;
import nz.kiwifinance.common.error.ErrorCode;
import nz.kiwifinance.engine.money.Money;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * Reads transaction exports from New Zealand banks. Banks put a few lines of account details above
 * the header row and name their columns differently, so the parser finds the header by looking
 * for date and amount columns and maps columns by name.
 */
final class BankCsvParser {

    static final int MAX_ROWS = 20_000;
    private static final int HEADER_SEARCH_LINES = 25;

    private static final List<String> DATE_COLUMNS =
            List.of("date", "transaction date", "posted date", "processed date");
    private static final List<String> AMOUNT_COLUMNS = List.of("amount", "amount (nzd)", "value");
    private static final List<String> CREDIT_COLUMNS =
            List.of("amount (credit)", "credit", "credit amount", "deposits");
    private static final List<String> DEBIT_COLUMNS = List.of("amount (debit)", "debit", "debit amount", "withdrawals");
    private static final List<String> PAYEE_COLUMNS =
            List.of("payee", "other party", "details", "op name", "merchant", "name");
    private static final List<String> DESCRIPTION_COLUMNS =
            List.of("memo/description", "description", "memo", "transaction details", "narrative");
    private static final List<String> EXTRA_COLUMNS = List.of("particulars", "code", "reference");
    private static final List<String> ID_COLUMNS = List.of("unique id", "transaction id", "id");

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("d/M/uu"),
            DateTimeFormatter.ofPattern("uuuu/M/d"),
            DateTimeFormatter.ofPattern("uuuu-M-d"),
            DateTimeFormatter.ofPattern("d-M-uuuu"),
            DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d-MMM-uuuu", Locale.ENGLISH));

    private BankCsvParser() {}

    static ParseResult parse(String content) {
        String text = content.startsWith("﻿") ? content.substring(1) : content;
        List<CSVRecord> records = read(new StringReader(text));
        int headerIndex = findHeader(records);
        if (headerIndex < 0) {
            throw new ApiException(
                    ErrorCode.IMPORT_FORMAT_UNRECOGNISED,
                    "We couldn't find date and amount columns in this file. Export transactions as CSV from your bank and try again.");
        }
        List<String> header = normalise(records.get(headerIndex));
        Columns columns = Columns.from(header);
        CsvFormat format = detect(header);

        List<ParsedRow> rows = new ArrayList<>();
        List<ParseResult.SkippedRow> skipped = new ArrayList<>();
        for (int i = headerIndex + 1; i < records.size(); i++) {
            CSVRecord record = records.get(i);
            int line = (int) record.getRecordNumber();
            if (isBlank(record)) {
                continue;
            }
            if (rows.size() >= MAX_ROWS) {
                throw new ApiException(ErrorCode.IMPORT_TOO_LARGE, "Files can contain up to 20,000 transactions.");
            }
            try {
                rows.add(columns.read(record, line));
            } catch (RowException e) {
                skipped.add(new ParseResult.SkippedRow(line, e.getMessage()));
            }
        }
        return new ParseResult(format, rows, skipped);
    }

    private static List<CSVRecord> read(Reader reader) {
        try (CSVParser parser = CSVFormat.DEFAULT
                .builder()
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .setAllowMissingColumnNames(true)
                .get()
                .parse(reader)) {
            return parser.getRecords();
        } catch (IOException | UncheckedIOException e) {
            throw new ApiException(ErrorCode.IMPORT_FORMAT_UNRECOGNISED, "This file isn't a readable CSV file.");
        }
    }

    private static int findHeader(List<CSVRecord> records) {
        for (int i = 0; i < Math.min(HEADER_SEARCH_LINES, records.size()); i++) {
            List<String> cells = normalise(records.get(i));
            boolean hasDate = cells.stream().anyMatch(DATE_COLUMNS::contains);
            boolean hasAmount = cells.stream().anyMatch(AMOUNT_COLUMNS::contains)
                    || (cells.stream().anyMatch(CREDIT_COLUMNS::contains)
                            && cells.stream().anyMatch(DEBIT_COLUMNS::contains));
            if (hasDate && hasAmount) {
                return i;
            }
        }
        return -1;
    }

    static CsvFormat detect(List<String> header) {
        Set<String> names = new LinkedHashSet<>(header);
        if (names.contains("unique id") && names.contains("tran type")) {
            return CsvFormat.ASB;
        }
        if (names.contains("this party account") || names.contains("other party account")) {
            return CsvFormat.BNZ;
        }
        if (names.contains("op name") || names.contains("tp ref") || names.contains("memo/description")) {
            return CsvFormat.KIWIBANK;
        }
        if (names.contains("other party") && names.contains("analysis code")) {
            return CsvFormat.WESTPAC;
        }
        if (names.contains("details") && names.contains("type")) {
            return CsvFormat.ANZ;
        }
        return CsvFormat.GENERIC;
    }

    private static List<String> normalise(CSVRecord record) {
        List<String> cells = new ArrayList<>();
        record.forEach(cell -> cells.add(cell.trim().toLowerCase(Locale.ROOT)));
        return cells;
    }

    private static boolean isBlank(CSVRecord record) {
        for (String cell : record) {
            if (!cell.isBlank()) {
                return false;
            }
        }
        return true;
    }

    static LocalDate parseDate(String value) {
        String trimmed = value.trim();
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(trimmed, format);
            } catch (DateTimeParseException ignored) {
                // Try the next format.
            }
        }
        throw new RowException("has a date we couldn't read: " + trimmed);
    }

    /**
     * Parses amounts such as {@code -1,234.50}, {@code $45.00} or {@code (12.00)}.
     */
    static long parseAmount(String value) {
        String cleaned = value.trim()
                .replace("$", "")
                .replace(",", "")
                .replace("NZD", "")
                .trim();
        boolean negative = cleaned.startsWith("(") && cleaned.endsWith(")");
        if (negative) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        if (cleaned.isEmpty()) {
            return 0;
        }
        try {
            long cents = Money.ofDollars(new BigDecimal(cleaned)).cents();
            return negative ? -cents : cents;
        } catch (NumberFormatException | ArithmeticException e) {
            throw new RowException("has an amount we couldn't read: " + value.trim());
        }
    }

    private record Columns(
            int date,
            int amount,
            int credit,
            int debit,
            List<Integer> payee,
            List<Integer> description,
            List<Integer> extras,
            int uniqueId) {

        static Columns from(List<String> header) {
            return new Columns(
                    first(header, DATE_COLUMNS),
                    first(header, AMOUNT_COLUMNS),
                    first(header, CREDIT_COLUMNS),
                    first(header, DEBIT_COLUMNS),
                    all(header, PAYEE_COLUMNS),
                    all(header, DESCRIPTION_COLUMNS),
                    all(header, EXTRA_COLUMNS),
                    first(header, ID_COLUMNS));
        }

        ParsedRow read(CSVRecord record, int line) {
            LocalDate postedOn = parseDate(cell(record, date).orElseThrow(() -> new RowException("has no date")));
            long cents;
            if (amount >= 0 && cell(record, amount).isPresent()) {
                cents = parseAmount(cell(record, amount).get());
            } else {
                long credit = cell(record, this.credit)
                        .map(BankCsvParser::parseAmount)
                        .orElse(0L);
                long debit =
                        cell(record, this.debit).map(BankCsvParser::parseAmount).orElse(0L);
                cents = Math.abs(credit) - Math.abs(debit);
            }
            if (cents == 0) {
                throw new RowException("has no amount");
            }
            String merchant = payee.stream()
                    .map(i -> cell(record, i))
                    .flatMap(Optional::stream)
                    .findFirst()
                    .orElse(null);
            String extra = extras.stream()
                    .map(i -> cell(record, i))
                    .flatMap(Optional::stream)
                    .collect(Collectors.joining(" "));
            String main = description.stream()
                    .map(i -> cell(record, i))
                    .flatMap(Optional::stream)
                    .findFirst()
                    .orElse(null);
            String text = String.join(" ", nonBlank(merchant, main, extra)).trim();
            if (text.isEmpty()) {
                text = cents > 0 ? "Deposit" : "Payment";
            }
            return new ParsedRow(
                    line,
                    postedOn,
                    cents,
                    truncate(text, 500),
                    merchant == null ? null : truncate(merchant, 200),
                    cell(record, uniqueId).orElse(null));
        }

        private static List<String> nonBlank(String... values) {
            List<String> parts = new ArrayList<>();
            for (String value : values) {
                if (value != null && !value.isBlank() && !parts.contains(value)) {
                    parts.add(value);
                }
            }
            return parts;
        }

        private static Optional<String> cell(CSVRecord record, int index) {
            if (index < 0 || index >= record.size()) {
                return Optional.empty();
            }
            String value = record.get(index).trim();
            return value.isEmpty() ? Optional.empty() : Optional.of(value);
        }

        private static int first(List<String> header, List<String> candidates) {
            for (String candidate : candidates) {
                int index = header.indexOf(candidate);
                if (index >= 0) {
                    return index;
                }
            }
            return -1;
        }

        private static List<Integer> all(List<String> header, List<String> candidates) {
            return candidates.stream().map(header::indexOf).filter(i -> i >= 0).toList();
        }

        private static String truncate(String value, int max) {
            return value.length() <= max ? value : value.substring(0, max);
        }
    }

    static final class RowException extends RuntimeException {
        RowException(String message) {
            super(message);
        }
    }
}
