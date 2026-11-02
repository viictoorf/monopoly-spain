package monopoly.game;

import java.util.ArrayList;
import java.util.List;

/**
 * The 40 squares, in board order. Square names, prices, rents and the little winks live here:
 * edit this file to change the map.
 */
final class Board {
    private final List<Square> squares;

    private Board(List<Square> squares) {
        this.squares = List.copyOf(squares);
    }

    List<Square> squares() { return squares; }
    Square get(int index) { return squares.get(index); }

    private static Street street(List<Square> s, String name, String shortName, String blurb, ColorGroup g,
                                 int price, int house, int... rents) {
        Street street = new Street(s.size(), name, g, price, house, rents);
        street.info(shortName, blurb);
        return street;
    }

    private static Station station(List<Square> s, String name, String shortName, String blurb) {
        Station station = new Station(s.size(), name);
        station.info(shortName, blurb);
        return station;
    }

    private static Utility utility(List<Square> s, String name, String shortName, String blurb) {
        Utility utility = new Utility(s.size(), name);
        utility.info(shortName, blurb);
        return utility;
    }

    private static Square with(Square square, String shortName, String blurb) {
        return square.info(shortName, blurb);
    }

    static Board spain() {
        List<Square> s = new ArrayList<>();
        s.add(with(new Squares.Go(), "Salida", "Cobra la nómina: 200 € cada vez que pasas."));
        s.add(street(s, "Soria", null, "Machado le dedicó sus Campos de Soria.", ColorGroup.BROWN, 60, 50, 2, 10, 30, 90, 160, 250));
        s.add(with(new Squares.CardSquare(s.size(), false), "Junta", "Reunión de vecinos: nadie sabe cómo acaba."));
        s.add(street(s, "Teruel", null, "¡Teruel existe!", ColorGroup.BROWN, 60, 50, 4, 20, 60, 180, 320, 450));
        s.add(with(new Squares.Tax(s.size(), "Declaración de la Renta", 200), "Renta", "Hacienda somos todos."));
        s.add(station(s, "Estación de Atocha", "Atocha", "El jardín tropical, las tortugas y el AVE."));
        s.add(street(s, "Cuenca", null, "Casas colgadas sobre la hoz del Huécar.", ColorGroup.LIGHT_BLUE, 100, 50, 6, 30, 90, 270, 400, 550));
        s.add(with(new Squares.CardSquare(s.size(), true), "Suerte", "Saca una carta y encomiéndate a la lotería."));
        s.add(street(s, "Zamora", null, "Capital del Románico.", ColorGroup.LIGHT_BLUE, 100, 50, 6, 30, 90, 270, 400, 550));
        s.add(street(s, "Huesca", null, "Puerta del Pirineo aragonés.", ColorGroup.LIGHT_BLUE, 120, 50, 8, 40, 100, 300, 450, 600));
        s.add(with(new Squares.Jail(), "Cárcel", "Si solo pasas por aquí, estás de visita."));
        s.add(street(s, "Lugo", null, "Muralla romana completa, Patrimonio de la Humanidad.", ColorGroup.PINK, 140, 100, 10, 50, 150, 450, 625, 750));
        s.add(utility(s, "Compañía de la Luz", "La Luz", "La factura sube, aunque no la uses."));
        s.add(street(s, "Ourense", null, "Aguas termales en plena ciudad: As Burgas.", ColorGroup.PINK, 140, 100, 10, 50, 150, 450, 625, 750));
        s.add(street(s, "Granada", null, "La Alhambra y la tapa gratis con cada caña.", ColorGroup.PINK, 160, 100, 12, 60, 180, 500, 700, 900));
        s.add(station(s, "Estación de Barcelona-Sants", "Sants", "Rodalies: paciencia incluida en el billete."));
        s.add(street(s, "León", null, "El Barrio Húmedo y su tapeo.", ColorGroup.ORANGE, 180, 100, 14, 70, 200, 550, 750, 950));
        s.add(with(new Squares.CardSquare(s.size(), false), "Junta", "Reunión de vecinos: nadie sabe cómo acaba."));
        s.add(street(s, "Burgos", null, "Catedral gótica y morcilla.", ColorGroup.ORANGE, 180, 100, 14, 70, 200, 550, 750, 950));
        s.add(street(s, "Valladolid", "Pucela", "Pucela: Semana Santa y Seminci.", ColorGroup.ORANGE, 200, 100, 16, 80, 220, 600, 800, 1000));
        s.add(with(new Squares.FreeParking(), "Siesta", "Descansas. Ni cobras ni pagas."));
        s.add(street(s, "Santander", null, "El Sardinero y la bahía más bonita.", ColorGroup.RED, 220, 150, 18, 90, 250, 700, 875, 1050));
        s.add(with(new Squares.CardSquare(s.size(), true), "Suerte", "Saca una carta y encomiéndate a la lotería."));
        s.add(street(s, "Oviedo", null, "Sidra, fabada y Woody Allen en bronce.", ColorGroup.RED, 220, 150, 18, 90, 250, 700, 875, 1050));
        s.add(street(s, "Pamplona", null, "San Fermín: chupinazo y encierro.", ColorGroup.RED, 240, 150, 20, 100, 300, 750, 925, 1100));
        s.add(station(s, "Estación de Sevilla Santa Justa", "Santa Justa", "AVE a Madrid desde 1992."));
        s.add(street(s, "Vigo", null, "Las Cíes y la Navidad con más luces de España.", ColorGroup.YELLOW, 260, 150, 22, 110, 330, 800, 975, 1150));
        s.add(street(s, "A Coruña", null, "La Torre de Hércules, un faro romano aún en uso.", ColorGroup.YELLOW, 260, 150, 22, 110, 330, 800, 975, 1150));
        s.add(utility(s, "Aguas y Canales", "Aguas", "Agua del grifo, con un puntito de cal."));
        s.add(street(s, "Zaragoza", null, "El Pilar y el Tubo de tapas.", ColorGroup.YELLOW, 280, 150, 24, 120, 360, 850, 1025, 1200));
        s.add(with(new Squares.GoToJail(), "A la cárcel", "Directo al calabozo: sin pasar por la Salida."));
        s.add(street(s, "Sevilla", null, "Feria de Abril y Semana Santa.", ColorGroup.GREEN, 300, 200, 26, 130, 390, 900, 1100, 1275));
        s.add(street(s, "Valencia", null, "Fallas y paella (sin chorizo, por favor).", ColorGroup.GREEN, 300, 200, 26, 130, 390, 900, 1100, 1275));
        s.add(with(new Squares.CardSquare(s.size(), false), "Junta", "Reunión de vecinos: nadie sabe cómo acaba."));
        s.add(street(s, "San Sebastián", "Donosti", "Pintxos con vistas a La Concha.", ColorGroup.GREEN, 320, 200, 28, 150, 450, 1000, 1200, 1400));
        s.add(station(s, "Estación de Valencia Joaquín Sorolla", "Sorolla", "AVE con nombre de pintor valenciano."));
        s.add(with(new Squares.CardSquare(s.size(), true), "Suerte", "Saca una carta y encomiéndate a la lotería."));
        s.add(street(s, "Barcelona", null, "La Sagrada Família, en obras desde 1882.", ColorGroup.DARK_BLUE, 350, 200, 35, 175, 500, 1100, 1300, 1500));
        s.add(with(new Squares.Tax(s.size(), "Recibo del IBI", 100), "IBI", "El recibo llega siempre en el peor momento."));
        s.add(street(s, "Madrid", null, "El kilómetro cero está en la Puerta del Sol.", ColorGroup.DARK_BLUE, 400, 200, 50, 200, 600, 1400, 1700, 2000));
        return new Board(s);
    }
}
