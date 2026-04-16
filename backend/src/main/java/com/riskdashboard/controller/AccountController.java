package com.riskdashboard.controller;

import com.riskdashboard.model.HourlyRiskWindow;
import com.riskdashboard.model.RiskAssessment;
import com.riskdashboard.model.TransactionData;
import com.riskdashboard.service.HourlyWindowAggregatorService;
import com.riskdashboard.service.RiskScoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * REST controller providing all account-oriented endpoints.
 *
 * <p>All endpoints are read-only. Business logic lives in the injected services.
 * This controller is deliberately thin — it validates PathVariables, delegates,
 * and maps 404/400 responses only.</p>
 *
 * <p>CORS is permitted for the Angular dev server (localhost:4200).
 * In production, replace with a proper {@code WebMvcConfigurer} or API gateway policy.</p>
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>{@code GET /api/accounts?query=}          — search accounts by id or name</li>
 *   <li>{@code GET /api/accounts/{id}}              — get account by id</li>
 *   <li>{@code GET /api/accounts/{id}/transactions} — get transactions for account</li>
 *   <li>{@code GET /api/accounts/{id}/risk-assessment} — get risk assessment</li>
 *   <li>{@code GET /api/accounts/{id}/risk-windows}    — get 24h hourly windows</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/accounts")
@CrossOrigin(origins = { "http://localhost:4200", "http://127.0.0.1:4200" })
public class AccountController {

    private static final Logger log = LoggerFactory.getLogger(AccountController.class);

    private static final int DEFAULT_WINDOW_HOURS = 24;
    private static final int MAX_WINDOW_HOURS = 24;

    private final TransactionData.DataFile dataFile;
    private final RiskScoringService riskScoringService;
    private final HourlyWindowAggregatorService hourlyWindowAggregatorService;

    public AccountController(TransactionData.DataFile dataFile,
                              RiskScoringService riskScoringService,
                              HourlyWindowAggregatorService hourlyWindowAggregatorService) {
        this.dataFile = dataFile;
        this.riskScoringService = riskScoringService;
        this.hourlyWindowAggregatorService = hourlyWindowAggregatorService;
    }

    /**
     * Search accounts by account ID or customer name (case-insensitive partial match).
     *
     * @param query the search string; blank returns an empty list
     * @return list of matching accounts (may be empty)
     */
    @GetMapping
    public ResponseEntity<List<TransactionData.Account>> searchAccounts(
            @RequestParam(name = "query", defaultValue = "") String query) {

        String q = query.trim();
        if (q.isBlank()) {
            return ResponseEntity.ok(List.of());
        }

        String lower = q.toLowerCase(Locale.ROOT);
        List<TransactionData.Account> results = dataFile.accounts().stream()
                .filter(a -> a.accountId().toLowerCase(Locale.ROOT).contains(lower)
                        || a.customerName().toLowerCase(Locale.ROOT).contains(lower))
                .toList();

        log.debug("searchAccounts query='{}' → {} result(s)", q, results.size());
        return ResponseEntity.ok(results);
    }

    /**
     * Get a single account by its exact account ID.
     *
     * @param accountId exact account identifier
     * @return 200 with the account, or 404 with an error body
     */
    @GetMapping("/{accountId}")
    public ResponseEntity<?> getAccount(@PathVariable String accountId) {
        Optional<TransactionData.Account> account = dataFile.accounts().stream()
                .filter(a -> a.accountId().equals(accountId))
                .findFirst();

        return account
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404)
                        .body(Map.of("error", "Account not found", "accountId", accountId)));
    }

    /**
     * Get all transactions for a given account.
     *
     * @param accountId exact account identifier
     * @return 200 with transactions list (may be empty), or 404 if account unknown
     */
    @GetMapping("/{accountId}/transactions")
    public ResponseEntity<?> getTransactions(@PathVariable String accountId) {
        if (!accountExists(accountId)) {
            return notFound(accountId);
        }

        List<TransactionData.Transaction> transactions = dataFile.transactions().stream()
                .filter(t -> t.accountId().equals(accountId))
                .toList();

        return ResponseEntity.ok(transactions);
    }

    /**
     * Get the latest risk assessment for a given account.
     *
     * <p>The reference time is always the latest transaction timestamp for the account
     * so that historical mock data always produces a meaningful assessment.</p>
     *
     * @param accountId exact account identifier
     * @return 200 with {@link RiskAssessment}, or 404 if account unknown
     */
    @GetMapping("/{accountId}/risk-assessment")
    public ResponseEntity<?> getRiskAssessment(@PathVariable String accountId) {
        if (!accountExists(accountId)) {
            return notFound(accountId);
        }

        List<TransactionData.Transaction> transactions = dataFile.transactions().stream()
                .filter(t -> t.accountId().equals(accountId))
                .toList();

        Instant referenceTime = deriveReferenceTime(transactions);
        RiskAssessment assessment = riskScoringService.assess(accountId, transactions, referenceTime);
        return ResponseEntity.ok(assessment);
    }

    /**
     * Get hourly risk windows for the rolling 24-hour window.
     *
     * @param accountId exact account identifier
     * @param window    optional query like "24h"; defaults to 24 hours (max 24)
     * @return 200 with list of {@link HourlyRiskWindow}, or 404 if account unknown
     */
    @GetMapping("/{accountId}/risk-windows")
    public ResponseEntity<?> getRiskWindows(
            @PathVariable String accountId,
            @RequestParam(name = "window", defaultValue = "24h") String window) {

        if (!accountExists(accountId)) {
            return notFound(accountId);
        }

        int hours = parseWindowHours(window);
        List<TransactionData.Transaction> transactions = dataFile.transactions().stream()
                .filter(t -> t.accountId().equals(accountId))
                .toList();

        Instant referenceTime = deriveReferenceTime(transactions);
        List<HourlyRiskWindow> windows = hourlyWindowAggregatorService.aggregate(
                transactions, referenceTime, hours);

        return ResponseEntity.ok(windows);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private boolean accountExists(String accountId) {
        return dataFile.accounts().stream().anyMatch(a -> a.accountId().equals(accountId));
    }

    private ResponseEntity<?> notFound(String accountId) {
        return ResponseEntity.status(404)
                .body(Map.of("error", "Account not found", "accountId", accountId));
    }

    /**
     * Anchors to the latest transaction timestamp so historical mock data always
     * produces a meaningful risk assessment regardless of when the server runs.
     */
    private Instant deriveReferenceTime(List<TransactionData.Transaction> transactions) {
        return transactions.stream()
                .map(TransactionData.Transaction::timestamp)
                .map(ts -> {
                    try {
                        return Instant.parse(ts);
                    } catch (Exception e) {
                        return Instant.EPOCH;
                    }
                })
                .max(Instant::compareTo)
                .orElse(Instant.now());
    }

    /**
     * Parses a window string like "24h" into an integer hour count.
     * Unrecognised values fall back to {@value #DEFAULT_WINDOW_HOURS}.
     */
    private int parseWindowHours(String window) {
        try {
            String digits = window.toLowerCase(Locale.ROOT).replace("h", "").trim();
            int hours = Integer.parseInt(digits);
            return Math.min(Math.max(hours, 1), MAX_WINDOW_HOURS);
        } catch (NumberFormatException e) {
            log.warn("Unrecognised window value '{}', defaulting to {}h", window, DEFAULT_WINDOW_HOURS);
            return DEFAULT_WINDOW_HOURS;
        }
    }
}
