package com.fraudguard.fraud;

import com.fraudguard.payments.domain.Money;

/**
 * A point-in-time snapshot of the signals the rules need, computed once per transaction
 * and passed in. Keeping this separate from the rules makes every rule a pure function of
 * {@code (Transaction, FeatureSnapshot)} — trivially unit-testable with no database.
 *
 * <p>In build-order step 2 these fields are filled from bounded Postgres queries (and later,
 * Redis). For now they are supplied directly by callers and tests (see {@link FeatureSnapshotBuilder}).
 *
 * <ul>
 *   <li>{@code txnCountLastMinute} / {@code txnCountLastHour} — velocity over rolling windows
 *       for this account/card.</li>
 *   <li>{@code trailingAverageAmount} — mean amount of the account's recent transactions;
 *       null when there is no history.</li>
 *   <li>{@code priorTransactionCount} — how many prior transactions exist (0 = first ever).</li>
 *   <li>{@code newDevice} — first time this account is seen on this device.</li>
 *   <li>{@code accountHomeCountry} — the account's registered country, for geo comparison.</li>
 *   <li>{@code cardOnBlocklist} / {@code ipOnBlocklist} / {@code deviceOnBlocklist} —
 *       known-bad identifiers.</li>
 * </ul>
 */
public record FeatureSnapshot(
        int txnCountLastMinute,
        int txnCountLastHour,
        Money trailingAverageAmount,
        int priorTransactionCount,
        boolean newDevice,
        String accountHomeCountry,
        boolean cardOnBlocklist,
        boolean ipOnBlocklist,
        boolean deviceOnBlocklist) {

    public boolean hasHistory() {
        return priorTransactionCount > 0 && trailingAverageAmount != null;
    }

    public boolean anyBlocklistHit() {
        return cardOnBlocklist || ipOnBlocklist || deviceOnBlocklist;
    }
}
