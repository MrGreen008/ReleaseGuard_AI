package dev.releaseguard;

import static dev.releaseguard.Domain.*;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Profile("shop")
@RestController
@RequestMapping("/api")
class DemoShop {
    private static final Logger log = LoggerFactory.getLogger(DemoShop.class);
    private static final List<Product> PRODUCTS = List.of(
        new Product("mug", "Ceramic mug", 1800, 25),
        new Product("notebook", "Dot-grid notebook", 1200, 40),
        new Product("lamp", "Desk lamp", 4200, 8));
    private final Clock clock;
    private final Flagsmith flags;
    private final boolean labEnabled;
    private final AtomicReference<Fault> fault = new AtomicReference<>(Fault.HEALTHY);
    private final Map<UUID, Order> orders = new LinkedHashMap<>();

    DemoShop(Clock clock, Flagsmith flags, @Value("${release.lab-enabled}") boolean labEnabled) {
        this.clock = clock;
        this.flags = flags;
        this.labEnabled = labEnabled;
    }

    @GetMapping("/products")
    List<Product> catalog() {
        return PRODUCTS;
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    synchronized Order place(@Valid @RequestBody Cart cart) {
        Product product = validateInventory(cart);
        var order = new Order(UUID.randomUUID(), product.sku(), cart.quantity(),
            product.priceCents() * cart.quantity(), clock.instant());
        orders.put(order.id(), order);
        if (orders.size() > 1_000) {
            orders.remove(orders.keySet().iterator().next());
        }
        return order;
    }

    @GetMapping("/orders/{id}")
    synchronized Order order(@PathVariable UUID id) {
        Order order = orders.get(id);
        if (order == null) {
            throw new ServiceFault(HttpStatus.NOT_FOUND, "Order not found.");
        }
        return order;
    }

    @PostMapping("/checkout")
    ResponseEntity<Checkout> checkout(@Valid @RequestBody CheckoutRequest request) {
        Instant started = clock.instant();
        validateInventory(request.cart());
        Cohort cohort;
        try {
            cohort = flags.evaluate(request.identity());
        } catch (ServiceFault e) {
            log.error(e.getMessage(), e);
            return outcome(503, request, started, Cohort.UNKNOWN, "flag_evaluation_unavailable");
        }

        Fault mode = fault.get();
        try {
            if (mode == Fault.DEPENDENCY_FAILURE) {
                Thread.sleep(85);
                return outcome(503, request, started, cohort, "shared_dependency_unavailable");
            }
            if (mode == Fault.PAYMENT_REGRESSION && cohort == Cohort.EXPERIMENTAL) {
                Thread.sleep(320);
                if (Math.floorMod((request.identity() + request.requestId()).hashCode(), 10) < 8) {
                    return outcome(502, request, started, cohort, "payment_processor_failure");
                }
            } else {
                Thread.sleep(25);
            }
            return outcome(200, request, started, cohort, "none");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error(e.getMessage(), e);
            return outcome(503, request, started, cohort, "checkout_interrupted");
        }
    }

    @PostMapping("/lab/fault")
    Map<String, Fault> setFault(@RequestBody Fault next) {
        if (!labEnabled) {
            throw new ServiceFault(HttpStatus.NOT_FOUND, "Incident Lab is disabled.");
        }
        fault.set(next);
        return Map.of("fault", next);
    }

    private Product validateInventory(Cart cart) {
        return PRODUCTS.stream().filter(product -> product.sku().equals(cart.sku()))
            .filter(product -> cart.quantity() <= product.stock())
            .findFirst().orElseThrow(() -> new ServiceFault(HttpStatus.UNPROCESSABLE_ENTITY, "Requested product is unavailable."));
    }

    private ResponseEntity<Checkout> outcome(int status, CheckoutRequest request, Instant started, Cohort cohort, String error) {
        return ResponseEntity.status(status).body(new Checkout(request.requestId(), started, cohort, error));
    }

    record Product(String sku, String name, int priceCents, int stock) {}
    record Cart(@NotNull @Pattern(regexp = "[a-z-]{1,24}") String sku, @Min(1) @Max(40) int quantity) {}
    record Order(UUID id, String sku, int quantity, int totalCents, Instant createdAt) {}
    record CheckoutRequest(@NotNull UUID requestId,
        @NotNull @Pattern(regexp = "synthetic-[a-zA-Z0-9_-]{1,32}") String identity, @Valid @NotNull Cart cart) {}
    record Checkout(UUID requestId, Instant started, Cohort cohort, String error) {}
}
