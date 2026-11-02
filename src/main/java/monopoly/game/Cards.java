package monopoly.game;

import java.util.List;
import monopoly.game.Card.Effect;

/** The card texts. Edit here to change or add cards. Board indexes used by MOVE_TO: see Board.java. */
final class Cards {
    private Cards() { }

    /** "Suerte": lottery, travel and everyday mishaps. */
    static List<Card> chance() {
        return List.of(
            Card.of("Avanza hasta la Salida y cobra la nómina.", Effect.MOVE_TO, 0),
            Card.of("Pillas el AVE hasta Atocha (con retraso, claro). Si pasas por la Salida, cobras 200 €.", Effect.MOVE_TO, 5),
            Card.of("Te mudas a Madrid, que ahí está el trabajo: avanza hasta Madrid.", Effect.MOVE_TO, 39),
            Card.of("Fin de semana en Barcelona para ver la Sagrada Família: avanza hasta Barcelona.", Effect.MOVE_TO, 37),
            Card.of("Te toca el chupinazo: avanza hasta Pamplona. Si pasas por la Salida, cobras 200 €.", Effect.MOVE_TO, 24),
            Card.of("Te apuntas a las Fallas: avanza hasta Valencia. Si pasas por la Salida, cobras 200 €.", Effect.MOVE_TO, 32),
            Card.of("Retrocede tres casillas: te has equivocado de salida en la autovía.", Effect.MOVE_BACK, 3),
            Card.of("Avanza hasta la estación más cercana. Si tiene dueño, pagas el doble por el billete.", Effect.NEAREST_STATION, 0),
            Card.of("Llega la factura: avanza hasta la compañía más cercana. Si tiene dueño, tira los dados y paga 10 veces el resultado.", Effect.NEAREST_UTILITY, 0),
            Card.of("¡Te toca la pedrea de la lotería de Navidad! Cobra 100 €.", Effect.COLLECT, 100),
            Card.of("Aciertas la quiniela en el bar: cobra 150 €.", Effect.COLLECT, 150),
            Card.of("Multa de la DGT por pasarte 10 km/h en un radar: paga 50 €.", Effect.PAY, 50),
            Card.of("Tu cuñado te arregla el wifi y encima te lo cobra: paga 20 €.", Effect.PAY, 20),
            Card.of("Te toca invitar a la ronda de cañas: paga 25 € a cada jugador.", Effect.PAY_TO_ALL, 25),
            new Card("Derrama para arreglar la fachada: 25 € por casa y 100 € por hotel.", Effect.REPAIRS, 25, 100),
            Card.of("Tienes un enchufe en el juzgado: sal gratis de la cárcel. Guarda esta carta.", Effect.JAIL_FREE, 0),
            Card.of("Hacienda ha revisado tus facturas: ve directamente a la cárcel.", Effect.GO_TO_JAIL, 0));
    }

    /** "Junta de Vecinos": the building's community meeting. */
    static List<Card> chest() {
        return List.of(
            Card.of("Error de la gestoría a tu favor: cobra 200 €.", Effect.COLLECT, 200),
            Card.of("Devolución de Hacienda (sí, existe): cobra 20 €.", Effect.COLLECT, 20),
            Card.of("Herencia de la tía del pueblo: cobra 100 €.", Effect.COLLECT, 100),
            Card.of("Llega la paga extra de verano: cobra 100 €.", Effect.COLLECT, 100),
            Card.of("Te devuelven, por fin, la fianza del piso de alquiler: cobra 50 €.", Effect.COLLECT, 50),
            Card.of("Te toca el Gordo, pero compartido con toda la oficina: cobra 50 €.", Effect.COLLECT, 50),
            Card.of("Derrama del ascensor: paga 100 €.", Effect.PAY, 100),
            Card.of("Hay que pintar el portal: paga 50 €.", Effect.PAY, 50),
            Card.of("La cena de empresa se te va de las manos: paga 40 €.", Effect.PAY, 40),
            Card.of("Gastos de farmacia: paga 30 €.", Effect.PAY, 30),
            Card.of("Vuelves de vacaciones: avanza hasta la Salida y cobra la nómina.", Effect.MOVE_TO, 0),
            Card.of("Es tu cumpleaños: cada jugador te da 10 €.", Effect.COLLECT_FROM_ALL, 10),
            new Card("Derrama por las goteras del tejado: 40 € por casa y 115 € por hotel.", Effect.REPAIRS, 40, 115),
            Card.of("Tu vecino del tercero ha llamado a la Policía por la fiesta: ve directamente a la cárcel.", Effect.GO_TO_JAIL, 0),
            Card.of("El presidente de la comunidad te debe un favor: sal gratis de la cárcel. Guarda esta carta.", Effect.JAIL_FREE, 0));
    }
}
