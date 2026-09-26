package nz.kiwifinance.engine.time;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

public final class NzTime {

    public static final ZoneId ZONE = ZoneId.of("Pacific/Auckland");

    private NzTime() {}

    public static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(ZONE));
    }

    public static YearMonth currentMonth(Clock clock) {
        return YearMonth.now(clock.withZone(ZONE));
    }
}
