package ek.osnb.demo.coffeeshop;

import com.jayway.jsonpath.JsonPath;
import ek.osnb.demo.coffeeshop.order.OrderPlacedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the whole HTTP API, across features, with the full application and
 * an in-memory H2 database (see src/test/resources/application.properties).
 * No MySQL or Docker needed. Run with: ./mvnw test
 *
 * The catalog is filled by CatalogInitData when the application starts.
 * The tests share one database, so each test creates its own customers and
 * carts, uses its own product, and compares stock before and after instead of
 * assuming fixed numbers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class CheckoutFlowTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private ApplicationEvents events;

    // ---------- Catalog ----------

    @Test
    void catalogIsFilledWithCategoriesAndProducts() {
        assertThat(mvc.get().uri("/api/categories").exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[*].name")
                .asArray()
                .contains("Coffee beans", "Ground coffee", "Equipment");

        assertThat(mvc.get().uri("/api/products").exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[*].name")
                .asArray()
                .contains("Ethiopia Yirgacheffe", "Hand Grinder");
    }

    @Test
    void productsCanBeFilteredByCategory() {
        MvcTestResult categories = mvc.get().uri("/api/categories").exchange();
        List<Integer> equipmentIds = JsonPath.read(body(categories), "$[?(@.name == 'Equipment')].id");

        MvcTestResult result = mvc.get().uri("/api/products?categoryId={id}", equipmentIds.getFirst()).exchange();

        assertThat(result).hasStatusOk()
                .bodyJson()
                .extractingPath("$[*].categoryName")
                .asArray()
                .isNotEmpty()
                .containsOnly("Equipment");
    }

    @Test
    void productsCanBeSearchedByNameOrOrigin() {
        assertThat(mvc.get().uri("/api/products?q=kenya").exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[*].name")
                .asArray()
                .containsExactly("Kenya Nyeri AA");
    }

    @Test
    void unknownProductReturns404AsProblemDetail() {
        MvcTestResult result = mvc.get().uri("/api/products/{id}", Long.MAX_VALUE).exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(404);
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Product not found: " + Long.MAX_VALUE);
    }

    // ---------- Customers ----------

    @Test
    void invalidCustomerReturns400WithAnErrorPerField() {

        MvcTestResult result = mvc.put()
                .uri("/api/customers/me")
                .with(user("anna@example.com").roles("CUSTOMER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                {
                    "name": "",
                    "street": "",
                    "postalCode": "1165",
                    "city": "København K"
                }
                """)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson()
                .extractingPath("$.errors.name").isNotNull();
        assertThat(result).bodyJson()
                .extractingPath("$.errors.street").isNotNull();
    }

    @Test
    void sameEmailCannotBeRegisteredTwice() {

        String email = uniqueEmail();

        String json = """
            {
                "email": "%s",
                "password": "SecurePassword123!"
            }
            """.formatted(email);

        // First registration succeeds
        MvcTestResult first = post("/api/register", json);

        assertThat(first).hasStatus(HttpStatus.CREATED);

        // Registering the same email again fails
        MvcTestResult second = post("/api/register", json);

        assertThat(second).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void customerCanOnlyAccessOwnProfile() {

        String email = uniqueEmail();

        // Create a profile as the authenticated customer
        MvcTestResult created = mvc.put()
                .uri("/api/customers/me")
                .with(user(email).roles("CUSTOMER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                {
                    "name": "Anna Jensen",
                    "street": "Nørregade 1",
                    "postalCode": "1165",
                    "city": "København K"
                }
                """)
                .exchange();

        assertThat(created).hasStatusOk();

        // Same customer can retrieve their profile
        MvcTestResult ownProfile = mvc.get()
                .uri("/api/customers/me")
                .with(user(email).roles("CUSTOMER"))
                .exchange();

        assertThat(ownProfile).hasStatusOk();
        assertThat(ownProfile).bodyJson()
                .extractingPath("$.email").isEqualTo(email);

        // Another customer cannot retrieve Anna's profile
        MvcTestResult otherProfile = mvc.get()
                .uri("/api/customers/me")
                .with(user(uniqueEmail()).roles("CUSTOMER"))
                .exchange();

        assertThat(otherProfile).hasStatus(HttpStatus.NOT_FOUND);
    }

    // ---------- Checkout ----------

    @Test
    void fullCheckoutFlow() {
        long productId = findProductId("Paper Filters (100 pcs)");
        int stockBefore = stockOf(productId);
        String email = uniqueEmail();
        createCustomer(email);
        long cartId = createCart();

        // Add to cart
        MvcTestResult cart = post("/api/carts/" + cartId + "/items", """
                {"productId": %d, "quantity": 3}
                """.formatted(productId));
        assertThat(cart).hasStatusOk();
        assertThat(cart).bodyJson().extractingPath("$.totalQuantity").isEqualTo(3);

        // Place the order
        MvcTestResult order = placeOrder(email, cartId);
        assertThat(order).hasStatus(HttpStatus.CREATED);
        assertThat(order).bodyJson().extractingPath("$.status").isEqualTo("PLACED");
        assertThat(order).bodyJson().extractingPath("$.lines[0].productName").isEqualTo("Paper Filters (100 pcs)");
        assertThat(decimal(order, "$.total")).isEqualByComparingTo("147.00");
        long orderId = number(order, "$.id");

        // The order feature told the rest of the app
        assertThat(events.stream(OrderPlacedEvent.class))
                .singleElement()
                .satisfies(event -> assertThat(event.orderId()).isEqualTo(orderId));

        // The cart is empty and the stock went down
        assertThat(mvc.get().uri("/api/carts/{id}", cartId).exchange())
                .bodyJson().extractingPath("$.lines").asArray().isEmpty();
        assertThat(stockOf(productId)).isEqualTo(stockBefore - 3);

        // The order shows up in the customer's orders
        assertThat(mvc.get()
                .uri("/api/orders")
                .with(user(email).roles("CUSTOMER"))
                .exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].id")
                .isEqualTo((int) orderId);

        // Cancelling puts the items back in stock
        MvcTestResult cancelled = mvc.post()
                .uri("/api/orders/{id}/cancel", orderId)
                .with(user(email).roles("CUSTOMER"))
                .with(csrf())
                .exchange();
        assertThat(cancelled).hasStatusOk();
        assertThat(cancelled).bodyJson().extractingPath("$.status").isEqualTo("CANCELLED");
        assertThat(stockOf(productId)).isEqualTo(stockBefore);

        // ...but only once
        assertThat(mvc.post()
                .uri("/api/orders/{id}/cancel", orderId)
                .with(user(email).roles("CUSTOMER"))
                .with(csrf())
                .exchange())
                .hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void addingMoreToTheCartThanInStockReturns409() {
        long productId = findProductId("French Roast");
        int stock = stockOf(productId);
        long cartId = createCart();

        MvcTestResult result = post("/api/carts/" + cartId + "/items", """
                {"productId": %d, "quantity": %d}
                """.formatted(productId, stock + 1));

        assertThat(result).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").isEqualTo("Only " + stock + " left of French Roast");
    }

    @Test
    void whenStockRunsOutBeforeCheckoutNothingIsChanged() {
        long productId = findProductId("Hand Grinder");
        int stock = stockOf(productId);

        // Two customers put every grinder in their cart
        String firstEmail = uniqueEmail();
        createCustomer(firstEmail);
        long firstCart = createCartWith(productId, stock);

        String secondEmail = uniqueEmail();
        createCustomer(secondEmail);
        long secondCart = createCartWith(productId, stock);

        // The first one checks out and gets them all
        assertThat(placeOrder(firstEmail, firstCart))
                .hasStatus(HttpStatus.CREATED);

        // The second one is too late
        MvcTestResult tooLate = placeOrder(secondEmail, secondCart);
        assertThat(tooLate).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").isEqualTo("Hand Grinder is out of stock");

        // The failed order changed nothing: the cart still has its items
        assertThat(mvc.get().uri("/api/carts/{id}", secondCart).exchange())
                .bodyJson().extractingPath("$.totalQuantity").isEqualTo(stock);
        assertThat(mvc.get()
                .uri("/api/orders")
                .with(user(secondEmail).roles("CUSTOMER"))
                .exchange())
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$")
                .asArray()
                .isEmpty();
        assertThat(stockOf(productId)).isZero();
    }

    @Test
    void emptyCartCannotBeOrdered() {
        String email = uniqueEmail();
        createCustomer(email);
        long cartId = createCart();

        assertThat(placeOrder(email, cartId))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("The cart is empty");
    }

    @Test
    void shippedOrderCannotBeCancelled() {
        long productId = findProductId("Colombia Huila");

        String email = uniqueEmail();
        createCustomer(email);

        long cartId = createCartWith(productId, 1);
        long orderId = number(placeOrder(email, cartId), "$.id");
    }

    // ---------- Helpers ----------

    private long findProductId(String name) {
        MvcTestResult result = mvc.get().uri("/api/products?q={q}", name).exchange();
        List<Integer> ids = JsonPath.read(body(result), "$[?(@.name == '" + name + "')].id");

        assertThat(ids).as("product %s", name).hasSize(1);

        return ids.getFirst();
    }

    private int stockOf(long productId) {
        return (int) number(mvc.get().uri("/api/products/{id}", productId).exchange(), "$.stock");
    }

    private long createCustomer(String email) {
        MvcTestResult result = mvc.put()
                .uri("/api/customers/me")
                .with(user(email).roles("CUSTOMER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(customerJson())
                .exchange();

        assertThat(result).hasStatusOk();

        return number(result, "$.id");
    }

    private long createCart() {
        MvcTestResult result = mvc.post()
                .uri("/api/carts")
                .with(csrf())
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);

        return number(result, "$.id");
    }

    private long createCartWith(long productId, int quantity) {
        long cartId = createCart();

        assertThat(post("/api/carts/" + cartId + "/items", """
                {"productId": %d, "quantity": %d}
                """.formatted(productId, quantity))).hasStatusOk();

        return cartId;
    }

    private MvcTestResult placeOrder(
            String email, long cartId) {

        return mvc.post()
                .uri("/api/orders")
                .with(user(email).roles("CUSTOMER"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"cartId": %d}
                    """.formatted(cartId))
                .exchange();
    }

    private static String customerJson() {
        return """
            {
                "name": "Anna Jensen",
                "street": "Nørregade 1",
                "postalCode": "1165",
                "city": "København K"
            }
            """;
    }

    private static String uniqueEmail() {
        return "test-" + UUID.randomUUID() + "@example.com";
    }

    private MvcTestResult post(String uri, String json) {
        return mvc.post().uri(uri)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }

    private static String body(MvcTestResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    private static long number(MvcTestResult result, String path) {
        Number value = JsonPath.read(body(result), path);
        return value.longValue();
    }

    private static BigDecimal decimal(MvcTestResult result, String path) {
        Number value = JsonPath.read(body(result), path);
        return new BigDecimal(value.toString());
    }
}
