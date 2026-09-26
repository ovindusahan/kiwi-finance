package nz.kiwifinance.bankfeed.akahu;

import java.util.Locale;
import nz.kiwifinance.account.AccountType;
import nz.kiwifinance.bankfeed.FeedAccount;
import nz.kiwifinance.bankfeed.FeedTransaction;
import nz.kiwifinance.engine.money.Money;
import nz.kiwifinance.engine.time.NzTime;

final class AkahuMapper {

    private static final int VISIBLE_DIGITS = 4;
    private static final int MAX_DESCRIPTION = 500;
    private static final int MAX_MERCHANT = 200;

    private AkahuMapper() {}

    static FeedAccount toFeedAccount(AkahuModels.Account account) {
        var balance = account.balance();
        return new FeedAccount(
                account.id(),
                account.name(),
                account.connection() == null ? null : account.connection().name(),
                accountType(account.type()),
                mask(account.formattedAccount()),
                balance == null || balance.current() == null
                        ? null
                        : Money.ofDollars(balance.current()).cents(),
                account.refreshed() == null ? null : account.refreshed().balance(),
                account.attributes() == null || account.attributes().contains("TRANSACTIONS"),
                !"INACTIVE".equalsIgnoreCase(account.status()));
    }

    static FeedTransaction toFeedTransaction(AkahuModels.Transaction transaction) {
        // Akahu dates are instants; transactions belong to the New Zealand calendar day they
        // happened on, which can differ from the UTC date.
        return new FeedTransaction(
                transaction.id(),
                transaction.date().atZone(NzTime.ZONE).toLocalDate(),
                Money.ofDollars(transaction.amount()).cents(),
                truncate(
                        transaction.description() == null
                                ? ""
                                : transaction.description().trim(),
                        MAX_DESCRIPTION),
                transaction.merchant() == null
                        ? null
                        : truncate(transaction.merchant().name(), MAX_MERCHANT),
                AkahuCategoryMapper.slugFor(transaction),
                "TRANSFER".equalsIgnoreCase(transaction.type()));
    }

    static AccountType accountType(String akahuType) {
        if (akahuType == null) {
            return AccountType.OTHER;
        }
        return switch (akahuType.toUpperCase(Locale.ROOT)) {
            case "CHECKING", "WALLET" -> AccountType.EVERYDAY;
            case "SAVINGS" -> AccountType.SAVINGS;
            case "CREDITCARD" -> AccountType.CREDIT_CARD;
            case "LOAN" -> AccountType.LOAN;
            case "KIWISAVER" -> AccountType.KIWISAVER;
            case "INVESTMENT", "TERMDEPOSIT" -> AccountType.INVESTMENT;
            default -> AccountType.OTHER;
        };
    }

    /**
     * Keeps only the last four digits of an account number, e.g. {@code 12-3456-0123456-00}
     * becomes {@code **-****-*****56-00}.
     */
    static String mask(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            return null;
        }
        long digits = accountNumber.chars().filter(Character::isDigit).count();
        long toHide = Math.max(0, digits - VISIBLE_DIGITS);
        StringBuilder masked = new StringBuilder(accountNumber.length());
        for (char c : accountNumber.toCharArray()) {
            if (Character.isDigit(c) && toHide > 0) {
                masked.append('*');
                toHide--;
            } else {
                masked.append(c);
            }
        }
        return masked.toString();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
