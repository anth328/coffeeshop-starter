package ek.osnb.demo.coffeeshop.assistant;

import ek.osnb.demo.coffeeshop.order.OrderApi;
import ek.osnb.demo.coffeeshop.order.OrderInfo;
import ek.osnb.demo.coffeeshop.order.OrderSummaryInfo;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class OrderTools {

    private final OrderApi orders;

    OrderTools(OrderApi orders) {
        this.orders = orders;
    }

    @Tool(description = """
            List the authenticated customer's orders.
            Use when the customer asks about their order history,
            recent orders, or order statuses.
            """)
    List<OrderSummaryInfo> listMyOrders(ToolContext context) {
        Long customerId = (Long) context.getContext().get("customerId");

        return orders.findOrders(customerId);
    }

    @Tool(description = """
            Retrieve details of one of the authenticated
            customer's orders, including items and delivery address.
            """)
    OrderInfo getMyOrder(Long orderId, ToolContext context) {
        Long customerId = (Long) context.getContext().get("customerId");

        return orders.getOrder(customerId, orderId);
    }
}