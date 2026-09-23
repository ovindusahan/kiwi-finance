package nz.kiwifinance.preferences;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

final class PreferencesRequests {

    private PreferencesRequests() {}

    /**
     * @param widgets the widgets to show, in order; send {@code null} to go back to the default layout
     */
    @Schema(name = "HomeLayoutRequest")
    record HomeLayout(@Size(max = 30) List<@NotNull @Valid HomeWidget> widgets) {}

    @Schema(name = "PreferencesRequest")
    record Update(Boolean emergencyFundReminders) {}
}
