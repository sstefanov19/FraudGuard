package com.fraudguard.fraud;

/**
 * The fraud engine's verdict on a transaction.
 *
 * <ul>
 *   <li>{@code APPROVE} — let it through.</li>
 *   <li>{@code REVIEW}  — hold for a human / queue; also the safe degraded default when scoring fails.</li>
 *   <li>{@code BLOCK}   — reject.</li>
 * </ul>
 */
public enum Decision {
    APPROVE,
    REVIEW,
    BLOCK
}
