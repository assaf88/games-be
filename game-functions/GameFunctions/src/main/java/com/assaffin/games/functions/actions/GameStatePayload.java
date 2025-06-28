package com.assaffin.games.functions.actions;

public class GameStatePayload {
    private String action;
    private String playerName;

    // No-argument constructor (needed for Jackson)
    public GameStatePayload() {}

    // Optionally, you can add a constructor for convenience
    public GameStatePayload(String action, String playerName) {
        this.action = action;
        this.playerName = playerName;
    }

    // Getter and Setter for action
    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    // Getter and Setter for playerName
    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }
}