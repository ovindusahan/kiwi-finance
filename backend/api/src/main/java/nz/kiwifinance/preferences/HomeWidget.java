package nz.kiwifinance.preferences;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * One widget on the home screen. The list order is the order on screen.
 *
 * @param id which widget it is, such as {@code accounts} or {@code goals}
 * @param size how much of the row it takes on a wide screen
 */
@Schema(name = "HomeWidget")
public record HomeWidget(
        @NotNull @Pattern(regexp = "[a-zA-Z]{1,40}") String id,
        @NotNull Size size) {

    public enum Size {
        SMALL,
        MEDIUM,
        LARGE,
        FULL
    }
}
