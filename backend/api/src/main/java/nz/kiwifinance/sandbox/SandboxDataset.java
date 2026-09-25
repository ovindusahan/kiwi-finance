package nz.kiwifinance.sandbox;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import nz.kiwifinance.engine.time.NzTime;

/**
 * A year of believable New Zealand banking activity for one person, generated deterministically
 * so every run of the sandbox returns the same history.
 */
final class SandboxDataset {

    static final String EVERYDAY = "acc_sandbox_everyday";
    static final String SAVINGS = "acc_sandbox_saver";
    static final String CREDIT_CARD = "acc_sandbox_creditcard";
    static final String KIWISAVER = "acc_sandbox_kiwisaver";

    private static final int MONTHS_OF_HISTORY = 13;

    record Account(
            String id,
            String name,
            String type,
            String formatted,
            String bank,
            String balance,
            List<String> attributes) {}

    record Transaction(
            String id,
            String account,
            LocalDate date,
            String description,
            BigDecimal amount,
            String type,
            String merchant,
            String category) {

        Instant instant() {
            return date.atTime(12, 0).atZone(NzTime.ZONE).toInstant();
        }
    }

    private final LocalDate today;
    private final Map<String, List<Transaction>> byAccount = new LinkedHashMap<>();

    SandboxDataset(LocalDate today) {
        this.today = today;
        LocalDate start = today.minusMonths(MONTHS_OF_HISTORY).withDayOfMonth(1);
        byAccount.put(EVERYDAY, everyday(start));
        byAccount.put(SAVINGS, savings(start));
        byAccount.put(CREDIT_CARD, creditCard(start));
        byAccount.put(KIWISAVER, List.of());
    }

    List<Account> accounts() {
        return List.of(
                new Account(
                        EVERYDAY,
                        "Everyday",
                        "CHECKING",
                        "01-0123-0456789-00",
                        "ANZ",
                        "3240.55",
                        List.of("TRANSACTIONS", "PAYMENT_FROM")),
                new Account(
                        SAVINGS,
                        "Online Saver",
                        "SAVINGS",
                        "01-0123-0456789-50",
                        "ANZ",
                        "8460.20",
                        List.of("TRANSACTIONS")),
                new Account(
                        CREDIT_CARD,
                        "Visa Credit Card",
                        "CREDITCARD",
                        "4835-****-****-2211",
                        "ANZ",
                        "-642.18",
                        List.of("TRANSACTIONS")),
                new Account(
                        KIWISAVER, "KiwiSaver Growth Fund", "KIWISAVER", null, "Simplicity", "24850.00", List.of()));
    }

    List<Transaction> transactions(String accountId) {
        return byAccount.getOrDefault(accountId, List.of());
    }

    private List<Transaction> everyday(LocalDate start) {
        Random random = new Random(42);
        List<Transaction> items = new ArrayList<>();
        Builder b = new Builder(EVERYDAY, items);
        for (LocalDate day = start; !day.isAfter(today); day = day.plusDays(1)) {
            int dow = day.getDayOfWeek().getValue();
            boolean payday = day.getDayOfWeek() == DayOfWeek.THURSDAY && day.toEpochDay() % 14 < 7;
            if (payday) {
                b.add(day, "ACME LTD SALARY", "2650.00", "DIRECT CREDIT", null, null);
                b.add(day, "TRANSFER TO ONLINE SAVER", "-300.00", "TRANSFER", null, null);
            }
            if (day.getDayOfWeek() == DayOfWeek.MONDAY) {
                b.add(day, "RENT J SMITH PROPERTY", "-640.00", "AUTOMATIC PAYMENT", null, null);
            }
            if (dow == 6 || (dow == 3 && random.nextBoolean())) {
                String[] stores = {"Countdown", "Pak'nSave", "New World"};
                String store = stores[random.nextInt(stores.length)];
                b.add(
                        day,
                        store.toUpperCase() + " AUCKLAND",
                        money(random, 55, 210),
                        "EFTPOS",
                        store,
                        "Supermarkets and grocery stores");
            }
            if (dow == 5 && random.nextInt(10) < 8) {
                String[] stations = {"Z Energy", "BP", "Mobil"};
                String station = stations[random.nextInt(stations.length)];
                b.add(
                        day,
                        station.toUpperCase() + " GREY LYNN",
                        money(random, 65, 105),
                        "EFTPOS",
                        station,
                        "Fuel retailing");
            }
            if (random.nextInt(100) < 14) {
                String[][] cafes = {
                    {"Mojo", "Cafes and restaurants"},
                    {"Burger Fuel", "Takeaway food services"},
                    {"Hell Pizza", "Takeaway food services"},
                    {"Uber Eats", "Takeaway food services"},
                    {"Ponsonby Social Club", "Cafes and restaurants"}
                };
                String[] pick = cafes[random.nextInt(cafes.length)];
                b.add(day, pick[0].toUpperCase(), money(random, 9, 72), "EFTPOS", pick[0], pick[1]);
            }
            if (random.nextInt(100) < 4) {
                String[][] shops = {
                    {"Kmart", "Department stores"},
                    {"The Warehouse", "Department stores"},
                    {"Mitre 10", "Hardware stores"},
                    {"Unichem Pharmacy", "Pharmacies"},
                    {"Event Cinemas", "Cinemas"}
                };
                String[] pick = shops[random.nextInt(shops.length)];
                b.add(day, pick[0].toUpperCase(), money(random, 12, 140), "EFTPOS", pick[0], pick[1]);
            }
            switch (day.getDayOfMonth()) {
                case 3 ->
                    b.add(
                            day,
                            "MERCURY ENERGY",
                            winter(day) ? money(random, 230, 280) : money(random, 150, 190),
                            "DIRECT DEBIT",
                            "Mercury",
                            "Electricity supply");
                case 6 ->
                    b.add(day, "ONE NZ BROADBAND", "-85.00", "DIRECT DEBIT", "One NZ", "Telecommunications services");
                case 9 ->
                    b.add(day, "2DEGREES MOBILE", "-35.00", "DIRECT DEBIT", "2degrees", "Telecommunications services");
                case 12 -> b.add(day, "NETFLIX.COM", "-22.99", "DIRECT DEBIT", "Netflix", "Digital subscriptions");
                case 14 -> b.add(day, "SPOTIFY NZ", "-16.99", "DIRECT DEBIT", "Spotify", "Digital subscriptions");
                case 17 -> b.add(day, "AA INSURANCE", "-94.50", "DIRECT DEBIT", "AA Insurance", "Insurance");
                case 20 -> b.add(day, "SOUTHERN CROSS HEALTH", "-68.40", "DIRECT DEBIT", "Southern Cross", "Insurance");
                case 22 ->
                    b.add(
                            day,
                            "CITYFITNESS",
                            "-58.00",
                            "DIRECT DEBIT",
                            "CityFitness",
                            "Sports and recreation facilities");
                case 25 -> b.add(day, "PAYMENT TO VISA CREDIT CARD", "-420.00", "TRANSFER", null, null);
                case 28 -> b.add(day, "MONTHLY ACCOUNT FEE", "-5.00", "FEE", null, "Current account fees");
                case 1 -> b.add(day, "ATM WITHDRAWAL PONSONBY RD", "-100.00", "ATM", null, null);
                case 16 -> b.add(day, "ANZ ATM K RD", "-60.00", "ATM", null, null);
                default -> {}
            }
            if (day.getMonth() == Month.MARCH && day.getDayOfMonth() == 15) {
                b.add(day, "NZTA VEHICLE LICENCE", "-118.84", "DEBIT", "NZTA", "Vehicle registration");
            }
            if (day.getMonth() == Month.DECEMBER && day.getDayOfMonth() == 18) {
                b.add(day, "FARMERS CHRISTMAS SHOPPING", "-286.40", "EFTPOS", "Farmers", "Department stores");
            }
        }
        return sorted(items);
    }

    private List<Transaction> savings(LocalDate start) {
        List<Transaction> items = new ArrayList<>();
        Builder b = new Builder(SAVINGS, items);
        for (LocalDate day = start; !day.isAfter(today); day = day.plusDays(1)) {
            if (day.getDayOfWeek() == DayOfWeek.THURSDAY && day.toEpochDay() % 14 < 7) {
                b.add(day, "TRANSFER FROM EVERYDAY", "300.00", "TRANSFER", null, null);
            }
            if (day.getDayOfMonth() == 1) {
                b.add(day, "CREDIT INTEREST PAID", "14.85", "INTEREST", null, null);
            }
        }
        return sorted(items);
    }

    private List<Transaction> creditCard(LocalDate start) {
        Random random = new Random(7);
        List<Transaction> items = new ArrayList<>();
        Builder b = new Builder(CREDIT_CARD, items);
        for (LocalDate day = start; !day.isAfter(today); day = day.plusDays(1)) {
            if (day.getDayOfMonth() == 25) {
                b.add(day, "PAYMENT RECEIVED THANK YOU", "420.00", "TRANSFER", null, null);
            }
            if (random.nextInt(100) < 6) {
                String[][] online = {
                    {"Amazon AU", "Online retail"},
                    {"Air New Zealand", "Airlines"},
                    {"Booking.com", "Accommodation"},
                    {"Rebel Sport", "Sports stores"},
                    {"JB Hi-Fi", "Electronics stores"}
                };
                String[] pick = online[random.nextInt(online.length)];
                b.add(day, pick[0].toUpperCase(), money(random, 25, 260), "CREDIT CARD", pick[0], pick[1]);
            }
        }
        return sorted(items);
    }

    private static boolean winter(LocalDate day) {
        return day.getMonth() == Month.JUNE || day.getMonth() == Month.JULY || day.getMonth() == Month.AUGUST;
    }

    private static String money(Random random, int min, int max) {
        BigDecimal amount =
                BigDecimal.valueOf(min + random.nextDouble() * (max - min)).setScale(2, RoundingMode.HALF_UP);
        return amount.negate().toPlainString();
    }

    private static List<Transaction> sorted(List<Transaction> items) {
        return items.stream()
                .sorted(Comparator.comparing(Transaction::date).reversed())
                .toList();
    }

    private static final class Builder {
        private final String account;
        private final List<Transaction> items;
        private int sequence;

        Builder(String account, List<Transaction> items) {
            this.account = account;
            this.items = items;
        }

        void add(LocalDate date, String description, String amount, String type, String merchant, String category) {
            String id = "txn_%s_%s_%d"
                    .formatted(account.substring(12), date.toString().replace("-", ""), sequence++);
            items.add(
                    new Transaction(id, account, date, description, new BigDecimal(amount), type, merchant, category));
        }
    }
}
