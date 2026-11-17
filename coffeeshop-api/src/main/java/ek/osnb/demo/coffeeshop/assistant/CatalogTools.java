package ek.osnb.demo.coffeeshop.assistant;

import ek.osnb.demo.coffeeshop.catalog.CatalogApi;
import ek.osnb.demo.coffeeshop.catalog.ProductDetailsInfo;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class CatalogTools {

    private final CatalogApi catalog;

    CatalogTools(CatalogApi catalog) {
        this.catalog = catalog;
    }

    @Tool(description = """
            Search the coffee shop's actual product catalog.
            Use this tool when customers ask about available products,
            prices, stock, coffee origins, or equipment.
            An empty search returns all products.
            """)
    List<ProductDetailsInfo> searchProducts(
            @ToolParam(description = "Product name or coffee origin to search for")
            String search) {

        return catalog.searchProducts(null, search);
    }
}