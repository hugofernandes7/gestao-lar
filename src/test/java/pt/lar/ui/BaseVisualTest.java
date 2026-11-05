package pt.lar.ui;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import pt.lar.models.Perfil;
import pt.lar.models.Registo;
import pt.lar.models.TipoRegisto;
import pt.lar.models.Utente;
import pt.lar.models.UtilizadorGestao;
import pt.lar.services.DemoDataService;
import pt.lar.services.RegistoService;
import pt.lar.services.UtenteAdminService;

/**
 * Cenário comum para os testes de aspeto (capturas e acessibilidade): terça-feira às 15:30, dados de demonstração
 * (ocorrências recentes nos quartos 4 e 7, medicação em atraso nos quartos 3 e 8), um utente desativado, um registo
 * anulado e um utilizador de gestão.
 */
abstract class BaseVisualTest extends BaseUiTest {

    @Autowired DemoDataService demo;
    @Autowired UtenteAdminService utenteAdmin;
    @Autowired RegistoService registoService;
    @Autowired PasswordEncoder encoder;

    protected Utente quarto1;   // sem banho hoje
    protected Utente quarto3;   // medicação em atraso
    protected Utente quarto4;   // queda com lesão há 5 h
    protected Registo queda4;
    protected UtilizadorGestao enfermeira;

    @BeforeEach
    void montarCenario() throws Exception {
        relogio.definir("2026-11-03T15:30:00");
        funcionaria(1, "Ana Ferreira", "1111");
        funcionaria(2, "Beatriz Costa", "2222");
        demo.popular();
        List<Utente> lista = utentes.findAllByOrderByIdAsc();
        quarto1 = lista.get(0);
        quarto3 = lista.get(2);
        quarto4 = lista.get(3);
        queda4 = registos.findAll().stream()
                .filter(r -> r.getUtente().getId().equals(quarto4.getId()) && r.getTipo() == TipoRegisto.QUEDA)
                .findFirst().orElseThrow();

        Utente desativado = utenteAdmin.guardar(null, "Teresa Maria Duarte", "16", null, false);
        utenteAdmin.definirAtivo(desativado.getId(), false);

        enfermeira = utilizadores.save(new UtilizadorGestao("Enfermeira Ana", "enfermeira",
                encoder.encode("demo1234"), Perfil.ENFERMEIRO));
        // um registo anulado no dia, para ver o aspeto "anulado" no histórico
        Registo banho = registoService.banho(lista.get(5).getId()).registo();
        registoService.anularNoAdmin(banho.getId(), enfermeira);
    }

    protected void entrarNoAdmin() {
        pagina.navigate(url("/admin/login"));
        pagina.fill("input[name=username]", "enfermeira");
        pagina.fill("input[name=password]", "demo1234");
        pagina.click("button[type=submit]");
        pagina.waitForURL("**/admin/utentes**");
    }
}
