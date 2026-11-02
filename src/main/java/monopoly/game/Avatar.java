package monopoly.game;

/**
 * The token a player moves around the board. Only the logic lives here: the icons are chosen by the web UI.
 * With the optional "house rules" the car and the ball get special movement (see Game.carMove / Game.ballMove).
 */
public enum Avatar {
    COCHE, PELOTA, SOMBRERO, ESFINGE, TORO, PAELLA, FLAMENCA, PULPO
}
