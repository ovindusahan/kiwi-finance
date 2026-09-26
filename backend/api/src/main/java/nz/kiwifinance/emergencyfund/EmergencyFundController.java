package nz.kiwifinance.emergencyfund;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nz.kiwifinance.common.web.CurrentUser;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class EmergencyFundController {

    private final EmergencyFundService emergencyFundService;

    @GetMapping("/api/v1/emergency-fund")
    EmergencyFundResponse get(CurrentUser user) {
        return emergencyFundService.forUser(user.id());
    }

    /**
     * Chooses the one account that holds the emergency fund. Send a null account to clear it.
     */
    @PutMapping("/api/v1/emergency-fund/account")
    EmergencyFundResponse chooseAccount(CurrentUser user, @RequestBody ChooseAccountRequest request) {
        return emergencyFundService.chooseAccount(user.id(), request.accountId());
    }

    @Schema(name = "EmergencyFundAccountRequest")
    record ChooseAccountRequest(@Nullable UUID accountId) {}
}
