import java.util.Collections;
import java.util.LinkedList;

public class CardStack {
    private int decks;
    private int deckCounter = 0;
    private double cut; // gibt an, bei wie vielen verbleibenden Karten ein neuer Stapel erstellt wird.
    private LinkedList<Card> cards;

    // cut ist ein Wert zwischen 0 und 1. Er gibt an, bei welchem Anteil verbleibender Karten ein neuer Stapel gebildet wird.
    public CardStack(int decks, double cut) {
        this.decks = decks;
        this.cut = decks * 52 * cut;
        initCards();
    }

    public CardStack(int decks) {
        this(decks, 0.25);
    }

    private void initCards() {
        cards = new LinkedList<>();
        for (int deck = deckCounter; deck < deckCounter + decks; deck++) {
            for(Card.Color color : Card.Color.values()) {
                for(Card.Value value : Card.Value.values()) {
                    this.cards.add(new Card(color, value, deck));
                }
            }
        }
        deckCounter += decks;
        Collections.shuffle(cards);
    }

    public synchronized Card pop(String owner) {
        Card card = cards.pop();
        card.setOwner("owner");
        if (cards.size() <= cut) initCards();
        return card;
    }
}
