class CardExpiredException extends RuntimeException {}

interface PaymentGateway {
    void charge(String cardToken, int amountCents);
}

class PayPalGateway implements PaymentGateway {
    public void charge(String cardToken, int amountCents) {
        if (amountCents <= 0) throw new CardExpiredException();
    }
}

class StripeGateway implements PaymentGateway {
    public void charge(String cardToken, int amountCents) {
        // never throws CardExpiredException
    }
}

class Checkout {
    void run(PaymentGateway gateway) {
        try {
            gateway.charge("tok", 100);
        } catch (Exception e) {
            // swallowed
        }
    }
}
