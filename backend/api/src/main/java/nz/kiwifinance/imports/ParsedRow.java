package nz.kiwifinance.imports;

import java.time.LocalDate;

/**
 * @param uniqueId an identifier supplied by the bank, if the export includes one
 */
record ParsedRow(
        int lineNumber, LocalDate date, long amountCents, String description, String merchant, String uniqueId) {}
