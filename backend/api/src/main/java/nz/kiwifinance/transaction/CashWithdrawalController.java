package nz.kiwifinance.transaction;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nz.kiwifinance.common.web.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cash-withdrawals")
@RequiredArgsConstructor
class CashWithdrawalController {

    private final CashWithdrawalService cashWithdrawals;

    /**
     * Recent ATM and cash withdrawals the person hasn't told us about yet.
     */
    @GetMapping
    List<CashWithdrawals.View> unsorted(CurrentUser user) {
        return cashWithdrawals.unsorted(user.id());
    }

    /**
     * Records what a cash withdrawal was spent on. Whatever isn't spent stays in the cash wallet.
     */
    @PostMapping("/{transactionId}")
    CashWithdrawals.Result sort(
            CurrentUser user, @PathVariable UUID transactionId, @Valid @RequestBody CashWithdrawals.Spending request) {
        return cashWithdrawals.sort(user.id(), transactionId, request);
    }
}
