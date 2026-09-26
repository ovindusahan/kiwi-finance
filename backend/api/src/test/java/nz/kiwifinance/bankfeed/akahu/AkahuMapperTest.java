package nz.kiwifinance.bankfeed.akahu;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import nz.kiwifinance.account.AccountType;
import org.junit.jupiter.api.Test;

class AkahuMapperTest {

    @Test
    void masksAllButTheLastFourDigits() {
        assertThat(AkahuMapper.mask("12-3456-0123456-00")).isEqualTo("**-****-*****56-00");
        assertThat(AkahuMapper.mask("4111")).isEqualTo("4111");
        assertThat(AkahuMapper.mask(null)).isNull();
    }

    @Test
    void mapsAccountTypes() {
        assertThat(AkahuMapper.accountType("CHECKING")).isEqualTo(AccountType.EVERYDAY);
        assertThat(AkahuMapper.accountType("CREDITCARD")).isEqualTo(AccountType.CREDIT_CARD);
        assertThat(AkahuMapper.accountType("TERMDEPOSIT")).isEqualTo(AccountType.INVESTMENT);
        assertThat(AkahuMapper.accountType("KIWISAVER")).isEqualTo(AccountType.KIWISAVER);
        assertThat(AkahuMapper.accountType("SOMETHING_NEW")).isEqualTo(AccountType.OTHER);
    }

    @Test
    void mapsAccountsWithExactCents() {
        var account = new AkahuModels.Account(
                "acc_1",
                "Credit card",
                "ACTIVE",
                "CREDITCARD",
                "4111-****-****-1234",
                new AkahuModels.Connection("c", "Kiwibank", null),
                new AkahuModels.Balance(new BigDecimal("-1234.10"), null, "NZD", false),
                List.of("TRANSACTIONS"),
                null);

        var mapped = AkahuMapper.toFeedAccount(account);

        assertThat(mapped.balanceCents()).isEqualTo(-123_410L);
        assertThat(mapped.institution()).isEqualTo("Kiwibank");
        assertThat(mapped.supportsTransactions()).isTrue();
        assertThat(mapped.active()).isTrue();
    }

    @Test
    void usesTheNewZealandCalendarDay() {
        // 11:30 UTC on 30 September is 00:30 on 1 October in New Zealand daylight time.
        var transaction = new AkahuModels.Transaction(
                "t1",
                "acc_1",
                Instant.parse("2026-09-30T11:30:00Z"),
                "  NETFLIX.COM  ",
                new BigDecimal("-22.99"),
                "CREDIT CARD",
                null,
                null);

        var mapped = AkahuMapper.toFeedTransaction(transaction);

        assertThat(mapped.postedOn()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(mapped.amountCents()).isEqualTo(-2_299L);
        assertThat(mapped.description()).isEqualTo("NETFLIX.COM");
    }

    @Test
    void mapsEnrichedCategoriesToSystemCategories() {
        assertThat(slug("Supermarkets and grocery stores", "EFTPOS", "-50")).isEqualTo("groceries");
        assertThat(slug("Cafes and restaurants", "EFTPOS", "-12")).isEqualTo("eating-out");
        assertThat(slug("Takeaway food services", "EFTPOS", "-30")).isEqualTo("takeaways");
        assertThat(slug("Fuel retailing", "EFTPOS", "-90")).isEqualTo("fuel");
        assertThat(slug("Electricity supply", "DIRECT DEBIT", "-180")).isEqualTo("power-gas");
        assertThat(slug("Telecommunications services", "DIRECT DEBIT", "-85")).isEqualTo("internet-phone");
        assertThat(slug("Current account fees", "FEE", "-5")).isEqualTo("bank-fees");
        assertThat(slug(null, "TRANSFER", "-500")).isEqualTo("transfers");
        assertThat(slug(null, "INTEREST", "3.21")).isEqualTo("interest");
        assertThat(slug("Something unusual", "EFTPOS", "-5")).isNull();
    }

    private static String slug(String categoryName, String type, String amount) {
        var category = categoryName == null ? null : new AkahuModels.Category("c", categoryName, null);
        return AkahuCategoryMapper.slugFor(new AkahuModels.Transaction(
                "t", "a", Instant.now(), "x", new BigDecimal(amount), type, null, category));
    }
}
