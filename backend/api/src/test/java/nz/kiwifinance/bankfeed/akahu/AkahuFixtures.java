package nz.kiwifinance.bankfeed.akahu;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

final class AkahuFixtures {

    static final String APP_TOKEN = "app_token_personal123";
    static final String USER_TOKEN = "user_token_personal456";

    private AkahuFixtures() {}

    static AkahuModels.User user(String id) {
        return new AkahuModels.User(id, "aroha@example.nz");
    }

    static List<AkahuModels.Account> accounts(String everydayBalance) {
        return List.of(
                new AkahuModels.Account(
                        "acc_everyday",
                        "Everyday",
                        "ACTIVE",
                        "CHECKING",
                        "12-3456-0123456-00",
                        new AkahuModels.Connection("conn_asb", "ASB", null),
                        new AkahuModels.Balance(new BigDecimal(everydayBalance), null, "NZD", false),
                        List.of("TRANSACTIONS"),
                        new AkahuModels.Refreshed(Instant.parse("2026-09-30T18:00:00Z"), null, null)),
                new AkahuModels.Account(
                        "acc_kiwisaver",
                        "KiwiSaver Growth",
                        "ACTIVE",
                        "KIWISAVER",
                        null,
                        new AkahuModels.Connection("conn_simplicity", "Simplicity", null),
                        new AkahuModels.Balance(new BigDecimal("24500.00"), null, "NZD", false),
                        List.of(),
                        null));
    }

    static List<AkahuModels.Transaction> transactions() {
        return List.of(
                transaction(
                        "txn_1",
                        "2026-09-29T11:00:00Z",
                        "-84.50",
                        "COUNTDOWN PONSONBY",
                        "Countdown",
                        "Supermarkets and grocery stores",
                        "EFTPOS"),
                transaction(
                        "txn_2",
                        "2026-09-27T11:00:00Z",
                        "-6.20",
                        "MOJO COFFEE",
                        "Mojo",
                        "Cafes and restaurants",
                        "EFTPOS"),
                transaction("txn_3", "2026-09-25T11:00:00Z", "2650.00", "SALARY ACME LTD", null, null, "DIRECT CREDIT"),
                transaction("txn_4", "2026-09-24T11:00:00Z", "-500.00", "TRANSFER TO SAVINGS", null, null, "TRANSFER"));
    }

    static AkahuModels.Transaction transaction(
            String id, String date, String amount, String description, String merchant, String category, String type) {
        return new AkahuModels.Transaction(
                id,
                "acc_everyday",
                Instant.parse(date),
                description,
                new BigDecimal(amount),
                type,
                merchant == null ? null : new AkahuModels.Merchant("m_" + id, merchant),
                category == null ? null : new AkahuModels.Category("c_" + id, category, null));
    }
}
