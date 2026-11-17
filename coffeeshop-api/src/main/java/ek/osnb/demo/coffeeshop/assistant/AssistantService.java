package ek.osnb.demo.coffeeshop.assistant;

import ek.osnb.demo.coffeeshop.customer.CustomerApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
class AssistantService {

    private final ChatClient chatClient;
    private final CustomerApi customers;
    private final StaffTools staffTools;
    private final OrderTools orderTools;

    AssistantService(
            ChatClient.Builder builder,
            ChatMemory memory,
            CatalogTools catalogTools,
            OrderTools orderTools,
            CustomerApi customers,
            StaffTools staffTools) {

        this.customers = customers;
        this.staffTools = staffTools;
        this.orderTools = orderTools;

        this.chatClient = builder
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(memory).build()
                )
                .defaultTools(catalogTools, orderTools)
                .build();
    }

    String chat(Authentication authentication, String message) {

        String username = authentication.getName();

        boolean isStaff = authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_STAFF"));

        var prompt = chatClient.prompt()
                .system("""
                    You are a helpful pirate assistant for a coffee shop.
                    All prices are in DKK.
                    Use tools for actual product and order information.
                    Never invent products, prices, stock levels or orders.
                    """)
                .user(message)
                .advisors(advisor -> advisor
                        .param(ChatMemory.CONVERSATION_ID, username));

        if (isStaff) {
            prompt = prompt.tools(staffTools);
        } else {
            var customer = customers.findCustomerByEmail(username);

            if (customer.isPresent()) {
                prompt = prompt
                        .tools(orderTools)
                        .toolContext(Map.of(
                                "customerId", customer.get().id()
                        ));
            }
        }

        return prompt.call().content();
    }
}