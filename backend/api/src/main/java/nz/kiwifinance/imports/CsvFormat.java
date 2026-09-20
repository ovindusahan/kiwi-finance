package nz.kiwifinance.imports;

/**
 * Bank export layouts the importer recognises. Detection is by header names, so small changes to
 * a bank's export still import correctly; anything with date, amount and description columns is
 * accepted as {@link #GENERIC}.
 */
public enum CsvFormat {
    ANZ,
    ASB,
    BNZ,
    KIWIBANK,
    WESTPAC,
    GENERIC
}
