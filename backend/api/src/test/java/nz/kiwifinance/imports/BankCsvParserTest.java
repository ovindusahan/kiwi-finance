package nz.kiwifinance.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import nz.kiwifinance.common.error.ApiException;
import org.junit.jupiter.api.Test;

class BankCsvParserTest {

    @Test
    void readsAsbExportsWithAccountDetailsAboveTheHeader() {
        String csv = """
                Created date / time : 1 October 2026 / 09:12:44
                Bank 12; Branch 3456; Account 0123456-00 (Streamline)
                From date 20260901
                To date 20260930
                Avail Bal : 1520.75 as of 20260930

                Date,Unique Id,Tran Type,Cheque Number,Payee,Memo,Amount
                2026/09/29,2026092901,D/C,,"COUNTDOWN PONSONBY","4835-****-****-1234 Df",-84.50
                2026/09/25,2026092501,D/C,,"ACME LTD","SALARY",2650.00
                """;

        ParseResult result = BankCsvParser.parse(csv);

        assertThat(result.format()).isEqualTo(CsvFormat.ASB);
        assertThat(result.rows()).hasSize(2);
        ParsedRow first = result.rows().getFirst();
        assertThat(first.date()).isEqualTo(LocalDate.of(2026, 9, 29));
        assertThat(first.amountCents()).isEqualTo(-8_450);
        assertThat(first.merchant()).isEqualTo("COUNTDOWN PONSONBY");
        assertThat(first.description()).isEqualTo("COUNTDOWN PONSONBY 4835-****-****-1234 Df");
        assertThat(first.uniqueId()).isEqualTo("2026092901");
    }

    @Test
    void readsAnzExports() {
        String csv = """
                Type,Details,Particulars,Code,Reference,Amount,Date,ForeignCurrencyAmount,ConversionCharge
                Eft-Pos,Z Energy Kingsland,4835****,1234,,-91.20,14/09/2026,,
                Direct Credit,Acme Ltd,Salary,,Sept,"2,650.00",12/09/2026,,
                """;

        ParseResult result = BankCsvParser.parse(csv);

        assertThat(result.format()).isEqualTo(CsvFormat.ANZ);
        assertThat(result.rows()).extracting(ParsedRow::amountCents).containsExactly(-9_120L, 265_000L);
        assertThat(result.rows().get(1).description()).isEqualTo("Acme Ltd Salary Sept");
    }

    @Test
    void readsKiwibankExportsWithSeparateCreditAndDebitColumns() {
        String csv = """
                Account number,Date,Memo/Description,Source Code (payment type),TP ref,TP part,TP code,OP ref,OP part,OP code,OP name,OP Bank Account Number,Amount (credit),Amount (debit),Amount,Balance
                38-9000-0123456-00,14-09-2026,MERCURY ENERGY ;,DD,,,,,,,Mercury,,,182.40,,1000.00
                38-9000-0123456-00,15-09-2026,INTEREST,IN,,,,,,,,,3.21,,,1003.21
                """;

        ParseResult result = BankCsvParser.parse(csv);

        assertThat(result.format()).isEqualTo(CsvFormat.KIWIBANK);
        assertThat(result.rows()).extracting(ParsedRow::amountCents).containsExactly(-18_240L, 321L);
        assertThat(result.rows().getFirst().merchant()).isEqualTo("Mercury");
    }

    @Test
    void readsWestpacAndBnzExports() {
        String westpac = """
                Date,Amount,Other Party,Description,Reference,Particulars,Analysis Code
                03/09/2026,-15.99,Spotify,DEBIT CARD PURCHASE,,,
                """;
        String bnz = """
                Date,Amount,Payee,Particulars,Code,Reference,Tran Type,This Party Account,Other Party Account,Serial,Transaction Code,Batch Number,Originating Bank/Branch,Processed Date
                03/09/26,-22.99,Netflix,,,,DD,02-0100-0123456-00,,,,,,04/09/26
                """;

        assertThat(BankCsvParser.parse(westpac).format()).isEqualTo(CsvFormat.WESTPAC);
        assertThat(BankCsvParser.parse(westpac).rows().getFirst().amountCents()).isEqualTo(-1_599L);
        ParseResult bnzResult = BankCsvParser.parse(bnz);
        assertThat(bnzResult.format()).isEqualTo(CsvFormat.BNZ);
        assertThat(bnzResult.rows().getFirst().date()).isEqualTo(LocalDate.of(2026, 9, 3));
    }

    @Test
    void skipsUnreadableRowsAndReportsThem() {
        String csv = """
                Date,Description,Amount
                01/09/2026,Coffee,-5.50
                not a date,Broken,-1.00
                02/09/2026,Nothing,
                03/09/2026,Refund,(12.00)
                """;

        ParseResult result = BankCsvParser.parse(csv);

        assertThat(result.format()).isEqualTo(CsvFormat.GENERIC);
        assertThat(result.rows()).extracting(ParsedRow::amountCents).containsExactly(-550L, -1_200L);
        assertThat(result.skipped())
                .extracting(ParseResult.SkippedRow::reason)
                .containsExactly("has a date we couldn't read: not a date", "has no amount");
    }

    @Test
    void rejectsFilesWithoutTransactionColumns() {
        assertThatThrownBy(() -> BankCsvParser.parse("name,email\nAroha,aroha@example.nz\n"))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        e -> assertThat(e.errorCode().code()).isEqualTo("import_format_unrecognised"));
    }

    @Test
    void handlesByteOrderMarksAndCurrencySymbols() {
        ParseResult result = BankCsvParser.parse("﻿Date,Description,Amount\n01/09/2026,Pay,\"$1,234.56\"\n");

        assertThat(result.rows().getFirst().amountCents()).isEqualTo(123_456L);
    }
}
