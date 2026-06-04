package com.fraudguard.fraud;

import com.fraudguard.payments.domain.Money;

/**
 * Builds {@link FeatureSnapshot} instances.
 *
 * <p>Its single responsibility is construction: {@code FeatureSnapshot} has nine fields, several
 * of them adjacent ints and booleans that are easy to transpose in a positional constructor.
 * This builder lets callers and tests set only the fields a scenario cares about, by name.
 *
 * <p>Defaults model a clean, established account: no velocity, known device, nothing blocklisted,
 * home country US, and no history yet (set {@code priorTransactionCount} + {@code trailingAverageAmount}
 * to give it history).
 */
public final class FeatureSnapshotBuilder {

    private int txnCountLastMinute = 0;
    private int txnCountLastHour = 0;
    private Money trailingAverageAmount = null;
    private int priorTransactionCount = 0;
    private boolean newDevice = false;
    private String accountHomeCountry = "US";
    private boolean cardOnBlocklist = false;
    private boolean ipOnBlocklist = false;
    private boolean deviceOnBlocklist = false;

    public FeatureSnapshotBuilder txnCountLastMinute(int v) { this.txnCountLastMinute = v; return this; }
    public FeatureSnapshotBuilder txnCountLastHour(int v) { this.txnCountLastHour = v; return this; }
    public FeatureSnapshotBuilder trailingAverageAmount(Money v) { this.trailingAverageAmount = v; return this; }
    public FeatureSnapshotBuilder priorTransactionCount(int v) { this.priorTransactionCount = v; return this; }
    public FeatureSnapshotBuilder newDevice(boolean v) { this.newDevice = v; return this; }
    public FeatureSnapshotBuilder accountHomeCountry(String v) { this.accountHomeCountry = v; return this; }
    public FeatureSnapshotBuilder cardOnBlocklist(boolean v) { this.cardOnBlocklist = v; return this; }
    public FeatureSnapshotBuilder ipOnBlocklist(boolean v) { this.ipOnBlocklist = v; return this; }
    public FeatureSnapshotBuilder deviceOnBlocklist(boolean v) { this.deviceOnBlocklist = v; return this; }

    public FeatureSnapshot build() {
        return new FeatureSnapshot(
                txnCountLastMinute, txnCountLastHour, trailingAverageAmount,
                priorTransactionCount, newDevice, accountHomeCountry,
                cardOnBlocklist, ipOnBlocklist, deviceOnBlocklist);
    }
}
