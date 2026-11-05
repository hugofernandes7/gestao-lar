package pt.lar.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import pt.lar.services.DemoDataService;

/** Perfil "demo": popula a base de dados no arranque se estiver vazia. */
@Component
@Profile("demo")
@Order(2)
public class DemoRunner implements ApplicationRunner {

    private final DemoDataService demo;

    public DemoRunner(DemoDataService demo) {
        this.demo = demo;
    }

    @Override
    public void run(ApplicationArguments args) {
        demo.popular();
    }
}
