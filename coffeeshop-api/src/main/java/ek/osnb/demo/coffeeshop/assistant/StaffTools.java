package ek.osnb.demo.coffeeshop.assistant;

import ek.osnb.demo.coffeeshop.catalog.CatalogApi;
import ek.osnb.demo.coffeeshop.catalog.ProductDetailsInfo;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class StaffTools {

    private final CatalogApi catalog;

    StaffTools(CatalogApi catalog) {
        this.catalog = catalog;
    }

    @Tool(description = """
            Find products with low stock.
            Only available to authorized staff.
            The threshold is the maximum stock quantity to include.
            """)
    List<ProductDetailsInfo> findLowStock(int threshold) {

        return catalog.searchProducts(null, null)
                .stream()
                .filter(product -> product.stock() <= threshold)
                .toList();
    }
}