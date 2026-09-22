package shopify.api.core;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import shopify.api.core.config.DotenvInitializer;

@SpringBootApplication
public class ShopifyApplication {

	public static void main(String[] args) {
		Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
		dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));

		new SpringApplicationBuilder(ShopifyApplication.class)
				.initializers(new DotenvInitializer())
				.run(args);

		SpringApplication.run(ShopifyApplication.class, args);
	}

}
