import java.util.LinkedList;

public class Player {
    private enum Action {HIT, STAND, SPLIT, DOUBLE, SURRENDER};
    private String name;
    public String ip;
    public int port;
    private CardStack cardStack;
    private boolean split;
    private Hand hand1;
    private Hand hand2;
    private boolean terminated;
    private int bet;

    private class Hand {
        Player player;
        private LinkedList<Card> cards;
        private Action lastAction;
        private boolean finished;
        private int bet;
        private boolean containsAce;
        private boolean terminated;
        private boolean doubled;

        private Hand (Player player, int bet) {
            this.player = player;
            this.bet = bet;
            this.cards = new LinkedList<>();
        }

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

        private void hit(String card, int deck) {
            if (terminated) {
                player.decline();
                return;
            }
            Card newCard = cardStack.pop(player.name);
            cards.add(newCard);
            if (newCard.getValue() == Card.Value.ASS) containsAce = true;
            player.accept();
            new Thread(() -> Croupier.sendCard(newCard)).start();
            if (getValue() >= 21) {
                terminated = true;
                Croupier.checkTerminated();
            }
        }

        private void doubleDown(String card, int deck) {
            if (terminated || doubled) {
                player.decline();
                return;
            }
            bet = 2 * bet;
            doubled = true;
            player.accept();
        }

        private void stand(String card, int deck) {
            terminated = true;
            Croupier.checkTerminated();
            player.accept();
        }

        private int getValue() {
            int value = 1;
            for (Card c : cards) {
                value += c.getValueNumber();
            }
            if (value <= 11) value += 10;
            return value;
        }
    }

    public Player(String ip, int port, String name, CardStack cardStack) {
        this.ip = ip;
        this.port = port;
        this.cardStack = cardStack;
        this.name = name;
    }

    public void init() {
        hand1 = new Hand(this, bet);
        for (int i = 0; i< 2; i++) {
            Card newCard = cardStack.pop(name);
            hand1.cards.add(newCard);
            if (newCard.getValue() == Card.Value.ASS) hand1.containsAce = true;
            new Thread(() -> Croupier.sendCard(newCard)).start();
        }

    }

    public synchronized void split(String card, int deck) {
        String message;
        if (!split && hand1.cards.size() == 2) {
            hand2.cards.add(hand1.cards.pop());
            message = "action accepted";
        } else {
            message = "action declined Split nicht moeglich";
        }
        Croupier.sendMessage(ip, port, message);
    }

    public void hit(String card, int deck) {
        Card top1 = hand1.cards.peek();
        if (top1.toString().equals(card) && deck == top1.getDeck()) {
            hand1.hit(card, deck);
        } else if (split) {
            Card top2 = hand2.cards.peek();
            if (top2.toString().equals(card) && deck == top2.getDeck()) {
                hand1.hit(card, deck);
            }
        }
    }

    public void stand(String card, int deck) {
        Card top1 = hand1.cards.peek();
        if (top1.toString().equals(card) && deck == top1.getDeck()) {
            hand1.stand(card, deck);
        } else if (split) {
            Card top2 = hand2.cards.peek();
            if (top2.toString().equals(card) && deck == top2.getDeck()) {
                hand1.stand(card, deck);
            }
        }
    }

    public void doubleDown(String card, int deck) {
        Card top1 = hand1.cards.peek();
        if (top1.toString().equals(card) && deck == top1.getDeck()) {
            hand1.doubleDown(card, deck);
        } else if (split) {
            Card top2 = hand2.cards.peek();
            if (top2.toString().equals(card) && deck == top2.getDeck()) {
                hand1.doubleDown(card, deck);
            }
        }
    }

    public void surrender (String card, int deck) {
        if (!split && hand1.cards.size() == 2) {
            hand1.terminated = true;
            Croupier.checkTerminated();
            accept();
        } else {
            decline();
        }
    }

    public void giveWin(int croupierValue) {
        int win = 0;
        if (hand1.getValue() < croupierValue || hand1.getValue() > 21) {
            win = - hand1.bet;
        } else {
            win = hand1.bet;
        }
        if (split) {
            if (hand2.getValue() < croupierValue || hand2.getValue() > 21) {
                win -= hand2.bet;
            } else {
                win += hand2.bet;
            }
        }
        Croupier.sendMessage(ip, port, "prize " + win);
        //ToDo repeatition
    }

    private void decline() {
        Croupier.sendMessage(ip, port, "action declined");
    }

    private void accept() {
        Croupier.sendMessage(ip, port, "action accepted");
    }

    public boolean getTerminated() {
        if (!split) {
            return  hand1.terminated;
        } else {
            return hand1.terminated && hand2.terminated;
        }

    }

    public void bet(int bet) {
        this.bet = bet;
    }

    public int getPort() {
        return port;
    }

    public String getIp() {
        return ip;
    }

    public String getName() {
        return this.name;
    }

}
