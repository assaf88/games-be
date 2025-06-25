package com.assaffin.games.functions.actions;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class GameStatePayload {
    public String action;
    public String playerName;
}
