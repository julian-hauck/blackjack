import java.util.LinkedList;

public class Player implements Client {
    public enum Action {HIT, STAND, SPLIT, DOUBLE, SURRENDER};
    private Action lastAction;
    private String lastCard;
    private int lastDeck;
    private String name;
    private String ip;
    private int port;
    private CardStack cardStack;
    private boolean split;
    private Hand hand1;
    private Hand hand2;
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
        private int value = 0;

        private Hand (Player player, int bet) {
            this.player = player;
            this.bet = bet;
            this.cards = new LinkedList<>();
        }

        private void addCard() {
            Card newCard = cardStack.pop(player.name);
            cards.add(newCard);
            if (newCard.getValue() == Card.Value.ASS) containsAce = true;
            value += newCard.getValueNumber();
            if (getValue() >= 21) {
                terminated = true;
                Croupier.checkTerminated();
            }
            new Thread(() -> Croupier.sendCard(newCard)).start();
        }

        private void hit(String card, int deck) {
            if (terminated) {
                player.decline("keine Aktion mehr moeglich");
                return;
            } else if (doubled && cards.size() > 2) {
                player.decline("wegen verdoppelten Einsatzes nicht moeglich");
            }
            addCard();
            player.accept(Action.HIT, card, deck);
        }

        private void doubleDown(String card, int deck) {
            if (terminated || doubled || cards.size() > 2) {
                player.decline("Aktion nicht mehr moeglich");
                return;
            } else {
                bet = 2 * bet;
                doubled = true;
                player.accept(Action.DOUBLE, card, deck);
            }

        }

        private void stand(String card, int deck) {
            terminated = true;
            Croupier.checkTerminated();
            player.accept(Action.STAND, card, deck);
        }

        private int getValue() {
            if (containsAce && value <= 11) {
                return value + 10;
            } else {
                return value;
            }
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
        hand1.addCard();
        hand1.addCard();
    }

    public synchronized void split(String card, int deck) {
        String message;
        if (!split && hand1.cards.size() == 2) {
            hand2.cards.add(hand1.cards.pop());
            hand1.addCard();
            hand2.addCard();
            accept(Action.SPLIT, card, deck);
        } else {
            decline("Split nicht moeglich");
        }
    }

    public void surrender (String card, int deck) {
        if (!split && hand1.cards.size() == 2) {
            hand1.terminated = true;
            Croupier.checkTerminated();
            accept(Action.SURRENDER, card, deck);
        } else {
            decline("Surrender nur im ersten Zug moeglich");
        }
    }

    public void giveWin(int croupierValue) {
        int win = 0;
        if (hand1.getValue() < croupierValue || hand1.getValue() > 21) {
            win = - hand1.bet;
        } else if (hand1.getValue() > croupierValue){
            if (hand1.getValue() == 21) {
                win = (int) (hand1.bet * 1.5);
            }
            win = hand1.bet;
        }
        if (split) {
            if (hand2.getValue() < croupierValue || hand2.getValue() > 21) {
                win -= hand1.bet;
            } else if (hand2.getValue() > croupierValue){
                if (hand2.getValue() == 21) {
                    win += (int) (hand2.bet * 1.5);
                }
                win += hand2.bet;
            }
        }
        Croupier.sendMessage(ip, port, "prize " + win);
        //ToDo repeatition
    }

    public void action(Action action, String card, int deck) {
        if (action == lastAction && card.equals(lastCard) && deck == lastDeck) {
            accept(action, card, deck);
        } else if (getTerminated()) {
            decline("keine Spielzuege mehr moeglich");
        } else if (action == Action.SPLIT) {
            split(card, deck);
        } else if (action == Action.SURRENDER) {
            surrender(card, deck);
        } else {
            Hand hand = null;
            Card top1 = hand1.cards.peekLast();
            if (top1.toString().equals(card) && deck == top1.getDeck()) {
                hand = hand1;
            } else if (split) {
                Card top2 = hand2.cards.peekLast();
                if (top2.toString().equals(card) && deck == top2.getDeck()) {
                    hand = hand2;
                }
            }
            if (hand == null) {
                decline("ungueltige Karte");
            } else {
                switch (action) {
                    case HIT :
                        hand.hit(card, deck);
                    case STAND:
                        hand.stand(card, deck);
                    case DOUBLE:
                        hand.stand(card, deck);
                }
            }
        }
    }

    private void decline() {
        Croupier.sendMessage(ip, port, "action declined");
    }

    private void decline(String message) {
        Croupier.sendMessage(ip, port, "action declined " + message);
    }

    private void accept(Action action, String card, int deck) {
        lastAction = action;
        lastCard = card;
        lastDeck = deck;
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
        Croupier.sendMessage(ip, port, "bet accepted");
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
