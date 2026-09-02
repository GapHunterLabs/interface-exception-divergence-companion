package dev.gaphunter.interfaceexceptiondivergencecompanion.inspection

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Every test method uses its own uniquely-suffixed class/interface
 * names ([gatewayHierarchy]) -- reusing the SAME class names across
 * different `configureByText` files within one test run produces
 * duplicate/ambiguous class declarations across the shared light
 * project (confirmed the hard way: an earlier version of this suite
 * reused "PaymentGateway"/"PayPalGateway" etc. verbatim in every test
 * method, and `ClassInheritorsSearch` picked up cross-test leftovers).
 *
 * Assertions match on this plugin's OWN distinctive message text
 * ("Liskov violation"), never on the exception class's bare name --
 * confirmed the hard way too: the light test fixture's mock JDK can
 * leave `extends RuntimeException` unresolved in some run orders,
 * producing an UNRELATED "Incompatible types... required:
 * java.lang.Throwable" compiler diagnostic that happens to also
 * mention the exception's name, which a looser assertion (matching
 * just the class name) would misread as this plugin's own warning.
 */
class InterfaceExceptionDivergenceInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(InterfaceExceptionDivergenceInspection::class.java)
    }

    private fun gatewayHierarchy(suffix: String) = """
        class CardExpiredException$suffix extends RuntimeException {}

        interface PaymentGateway$suffix {
            void charge(String cardToken, int amountCents);
        }

        class PayPalGateway$suffix implements PaymentGateway$suffix {
            public void charge(String cardToken, int amountCents) {
                if (amountCents <= 0) throw new CardExpiredException$suffix();
            }
        }

        class StripeGateway$suffix implements PaymentGateway$suffix {
            public void charge(String cardToken, int amountCents) {
                // never throws CardExpiredException$suffix
            }
        }
    """.trimIndent()

    fun `test a call caught broadly and silently is flagged when implementations diverge`() {
        myFixture.configureByText(
            "Checkout1.java",
            gatewayHierarchy("A") + "\n\n" + """
            class Checkout1 {
                void run(PaymentGatewayA gateway) {
                    try {
                        gateway.charge("tok", 100);
                    } catch (Exception e) {
                        // swallowed
                    }
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.any { it.description?.contains("Liskov violation") == true && it.description?.contains("CardExpiredExceptionA") == true })
    }

    fun `test a call whose catch logs the exception is not flagged`() {
        myFixture.configureByText(
            "Checkout2.java",
            gatewayHierarchy("B") + "\n\n" + """
            class Checkout2 {
                void run(PaymentGatewayB gateway) {
                    try {
                        gateway.charge("tok", 100);
                    } catch (Exception e) {
                        System.out.println("failed: " + e);
                    }
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.none { it.description?.contains("Liskov violation") == true })
    }

    fun `test a call not wrapped in any try-catch is not flagged`() {
        myFixture.configureByText(
            "Checkout3.java",
            gatewayHierarchy("C") + "\n\n" + """
            class Checkout3 {
                void run(PaymentGatewayC gateway) {
                    gateway.charge("tok", 100);
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.none { it.description?.contains("Liskov violation") == true })
    }

    fun `test a call through the CONCRETE type, not the interface, is not flagged`() {
        myFixture.configureByText(
            "Checkout4.java",
            gatewayHierarchy("D") + "\n\n" + """
            class Checkout4 {
                void run(PayPalGatewayD gateway) {
                    try {
                        gateway.charge("tok", 100);
                    } catch (Exception e) {
                        // swallowed, but the static type already tells the reader this can throw
                    }
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.none { it.description?.contains("Liskov violation") == true })
    }

    fun `test an interface with only one real implementation is not flagged`() {
        myFixture.configureByText(
            "Single.java",
            """
            class OnlyExceptionE extends RuntimeException {}

            interface OnlyGatewayE {
                void charge();
            }

            class OnlyImplE implements OnlyGatewayE {
                public void charge() {
                    throw new OnlyExceptionE();
                }
            }

            class CallerE {
                void run(OnlyGatewayE gateway) {
                    try {
                        gateway.charge();
                    } catch (Exception e) {
                        // only one implementation exists -- nothing to diverge from
                    }
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.none { it.description?.contains("Liskov violation") == true })
    }
}
