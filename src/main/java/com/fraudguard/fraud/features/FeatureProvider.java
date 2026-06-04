package com.fraudguard.fraud.features;

import com.fraudguard.fraud.FeatureSnapshot;
import com.fraudguard.payments.domain.Transaction;

public interface FeatureProvider {

    FeatureSnapshot load(Transaction transaction);
}
