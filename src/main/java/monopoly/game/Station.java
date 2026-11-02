package monopoly.game;

/** Railway station: rent doubles with each station owned (25, 50, 100, 200); doubled again after a "nearest station" card. */
public final class Station extends Property {
    Station(int index, String name) {
        super(index, name, 200);
    }

    @Override public String getKind() { return "station"; }

    @Override
    int rent(Game game) {
        int rent = 25 << (getOwner().count(Station.class) - 1);
        return game.rentMode() == Game.RentMode.DOUBLE ? rent * 2 : rent;
    }
}
