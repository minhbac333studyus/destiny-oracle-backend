package com.destinyoracle.entity;

/** Matches the card_stage PostgreSQL enum. Order defines progression. */
public enum CardStage {
    storm,
    fog,
    clearing,
    aura,
    radiance,
    legend;

    public CardStage next() {
        CardStage[] values = values();
        int idx = this.ordinal();
        return idx < values.length - 1 ? values[idx + 1] : null;
    }

    public boolean isAfter(CardStage other) {
        return this.ordinal() > other.ordinal();
    }
}
