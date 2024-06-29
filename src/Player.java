import javax.sound.sampled.Port;
import java.util.LinkedList;

public class Player {
    private enum Action {HIT, STAND, SPLIT, DOUBLE, SURRENDER};
    private String ip;
    private int port;
    private CardStack cardStack;
    private boolean split;
    private Hand hand1;
    private Hand hand2;

    private class Hand {
        Player player;
        private LinkedList<Card> cards;
        private Action lastAction;
        private boolean finished;
        private int bet;

        private boolean checkAction(Action action, String card, int deck) {
            Card currentCard = cards.peek();
            if (card.equals(currentCard.toString()) && deck == currentCard.getDeck()) {
                return true;
            } else {
                String message;
                Card lastCard = cards.get(cards.size() - 2);
                if (action == lastAction && card.equals(lastCard.toString()) && deck == lastCard.getDeck()) {
                    message = "action accepted";
                } else {
                    message = "action declined ungueltige Karte";
                }
                Croupier.sendMessage(ip, port, message);
                return false;
            }
        }

        private void split(String card, int deck) {
            if (!player.split) {
                player.hand2
            }
        }
    }

    public synchronized void split(String card, int deck) {
        if (!player.split) {
            player.hand2
        }

    }




}
