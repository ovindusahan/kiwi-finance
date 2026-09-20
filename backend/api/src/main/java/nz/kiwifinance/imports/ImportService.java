package nz.kiwifinance.imports;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nz.kiwifinance.account.AccountService;
import nz.kiwifinance.transaction.ExternalTransaction;
import nz.kiwifinance.transaction.ImportResult;
import nz.kiwifinance.transaction.TransactionService;
import nz.kiwifinance.transaction.TransactionSource;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class ImportService {

    private static final int MAX_SKIPPED_REPORTED = 20;

    private final ImportBatchRepository batches;
    private final AccountService accounts;
    private final TransactionService transactions;

    @Transactional
    ImportResponse importCsv(UUID userId, UUID accountId, String fileName, String content) {
        accounts.getOpen(userId, accountId);
        ParseResult parsed = BankCsvParser.parse(content);
        List<ExternalTransaction> incoming = toExternal(parsed.rows());
        ImportResult result =
                transactions.importTransactions(userId, accountId, TransactionSource.CSV_IMPORT, incoming);
        ImportBatch batch = batches.save(new ImportBatch(
                userId,
                accountId,
                parsed.format(),
                fileName,
                parsed.rows().size(),
                result.created(),
                result.updated() + result.unchanged()));
        return ImportResponse.from(
                batch, parsed.skipped().stream().limit(MAX_SKIPPED_REPORTED).toList());
    }

    @Transactional(readOnly = true)
    List<ImportResponse> recent(UUID userId) {
        return batches.findByUserIdOrderByCreatedAtDesc(userId, Limit.of(20)).stream()
                .map(batch -> ImportResponse.from(batch, List.of()))
                .toList();
    }

    /**
     * Gives each row a stable ID so importing an overlapping export doesn't duplicate anything.
     * Banks rarely include an ID, so it is derived from the row's contents, with a counter to keep
     * genuinely identical transactions on the same day apart.
     */
    static List<ExternalTransaction> toExternal(List<ParsedRow> rows) {
        Map<String, Integer> occurrences = new HashMap<>();
        List<ExternalTransaction> result = new ArrayList<>(rows.size());
        for (ParsedRow row : rows) {
            String externalId;
            if (row.uniqueId() != null) {
                externalId = "csv:id:" + row.uniqueId();
            } else {
                String key = row.date() + "|" + row.amountCents() + "|"
                        + row.description().toLowerCase().replaceAll("\\s+", " ");
                int occurrence = occurrences.merge(key, 1, Integer::sum);
                externalId = "csv:" + sha256(key + "|" + occurrence).substring(0, 40);
            }
            result.add(new ExternalTransaction(
                    truncate(externalId, 100),
                    row.date(),
                    row.amountCents(),
                    row.description(),
                    row.merchant(),
                    null,
                    false));
        }
        return result;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
