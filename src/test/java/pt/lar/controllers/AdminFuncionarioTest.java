package pt.lar.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.servlet.FlashMap;
import pt.lar.BaseIntegracaoTest;
import pt.lar.models.Funcionario;
import pt.lar.models.Utente;
import pt.lar.services.AcessoService;
import pt.lar.services.RegistoService;

/** /admin/funcionarios, /admin/acessos e a coluna "Quem" do histórico. */
@AutoConfigureMockMvc
class AdminFuncionarioTest extends BaseIntegracaoTest {

    @Autowired MockMvc mvc;
    @Autowired AcessoService acesso;
    @Autowired RegistoService registoService;

    private static RequestPostProcessor admin() {
        return user("enfermeira").roles("ENFERMEIRO");
    }

    @Test
    void tudoExigeLogin() throws Exception {
        mvc.perform(get("/admin/funcionarios")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/admin/login"));
        mvc.perform(get("/admin/funcionarios/nova")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/admin/funcionarios").with(csrf()).param("numero", "1").param("nome", "Ana"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/acessos")).andExpect(status().is3xxRedirection());
        assertThat(funcionarios.count()).isZero();
    }

    @Test
    void formularioNovoPropoeOProximoNumero() throws Exception {
        funcionaria(1, "Ana", "1111");
        funcionaria(4, "Bia", "2222");

        mvc.perform(get("/admin/funcionarios/nova").with(admin()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("value=\"5\"")));
    }

    @Test
    void criarSemPinGeraUmEMostraOUmaSoVez() throws Exception {
        var resultado = mvc.perform(post("/admin/funcionarios").with(admin()).with(csrf())
                        .param("numero", "1").param("nome", "Ana Ferreira").param("pin", ""))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/funcionarios"))
                .andReturn();
        FlashMap flash = resultado.getFlashMap();

        Funcionario ana = funcionarios.findByNumero(1).orElseThrow();
        assertThat(ana.getNome()).isEqualTo("Ana Ferreira");
        assertThat(ana.isAtiva()).isTrue();

        String primeira = mvc.perform(get("/admin/funcionarios").with(admin()).flashAttrs(flash))
                .andReturn().getResponse().getContentAsString();
        assertThat(primeira).contains("pin-valor").contains("não volta a ser mostrado");
        String pin = primeira.replaceAll("(?s).*class=\"pin-valor\">(\\d{4})<.*", "$1");
        assertThat(acesso.tentar("1", pin).estado()).isEqualTo(AcessoService.Estado.OK);

        String segunda = mvc.perform(get("/admin/funcionarios").with(admin())).andReturn().getResponse().getContentAsString();
        assertThat(segunda).doesNotContain("pin-valor").doesNotContain(ana.getPinHash());
    }

    @Test
    void criarComPinEscritoNaoOMostra() throws Exception {
        var resultado = mvc.perform(post("/admin/funcionarios").with(admin()).with(csrf())
                        .param("numero", "2").param("nome", "Bia").param("pin", "4321"))
                .andExpect(status().is3xxRedirection()).andReturn();

        String pagina = mvc.perform(get("/admin/funcionarios").with(admin()).flashAttrs(resultado.getFlashMap()))
                .andReturn().getResponse().getContentAsString();
        assertThat(pagina).doesNotContain("pin-valor").doesNotContain("4321");
        assertThat(acesso.tentar("2", "4321").estado()).isEqualTo(AcessoService.Estado.OK);
    }

    @Test
    void numeroJaUsadoOuPinMauVoltamAoFormularioComErro() throws Exception {
        funcionaria(1, "Ana", "1111");

        mvc.perform(post("/admin/funcionarios").with(admin()).with(csrf())
                        .param("numero", "1").param("nome", "Outra").param("pin", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("já foi atribuído")));
        mvc.perform(post("/admin/funcionarios").with(admin()).with(csrf())
                        .param("numero", "2").param("nome", "Bia").param("pin", "12"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("4 dígitos")));
        mvc.perform(post("/admin/funcionarios").with(admin()).with(csrf())
                        .param("numero", "abc").param("nome", "Bia").param("pin", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("valor inteiro")));
        assertThat(funcionarios.count()).isEqualTo(1);
    }

    @Test
    void listaMostraNumeroNomeEEstadoMasNuncaOPin() throws Exception {
        Funcionario ana = funcionaria(1, "Ana Ferreira", "1111");
        Funcionario bia = funcionaria(2, "Beatriz Costa", "2222");
        funcionarioService.definirAtiva(bia.getId(), false);

        String pagina = mvc.perform(get("/admin/funcionarios").with(admin())).andReturn().getResponse().getContentAsString();

        assertThat(pagina).contains("Ana Ferreira").contains("Beatriz Costa").contains("Desativada").contains("Ativa");
        assertThat(pagina).doesNotContain(ana.getPinHash()).doesNotContain("1111").doesNotContain("2222");
    }

    @Test
    void editarDesativarEReporPin() throws Exception {
        Funcionario ana = funcionaria(1, "Ana", "1111");

        mvc.perform(post("/admin/funcionarios/" + ana.getId()).with(admin()).with(csrf()).param("nome", "Ana Maria"))
                .andExpect(status().is3xxRedirection());
        assertThat(funcionarios.findById(ana.getId()).orElseThrow().getNome()).isEqualTo("Ana Maria");

        mvc.perform(post("/admin/funcionarios/" + ana.getId() + "/ativa").with(admin()).with(csrf()).param("ativa", "false"))
                .andExpect(status().is3xxRedirection());
        assertThat(acesso.tentar("1", "1111").estado()).isEqualTo(AcessoService.Estado.INCORRETO);
        mvc.perform(post("/admin/funcionarios/" + ana.getId() + "/ativa").with(admin()).with(csrf()).param("ativa", "true"));

        mvc.perform(post("/admin/funcionarios/" + ana.getId() + "/pin").with(admin()).with(csrf()).param("pin", "9876"))
                .andExpect(status().is3xxRedirection());
        assertThat(acesso.tentar("1", "1111").estado()).isEqualTo(AcessoService.Estado.INCORRETO);
        assertThat(acesso.tentar("1", "9876").estado()).isEqualTo(AcessoService.Estado.OK);
        assertThat(funcionarios.findById(ana.getId()).orElseThrow().getNumero()).isEqualTo(1);
    }

    @Test
    void adminRecusaPostsSemCsrf() throws Exception {
        mvc.perform(post("/admin/funcionarios").with(admin()).param("numero", "1").param("nome", "Ana"))
                .andExpect(status().isForbidden());
        assertThat(funcionarios.count()).isZero();
    }

    // ---- /admin/acessos --------------------------------------------------------------------

    @Test
    void acessosListaFalhasEBloqueios() throws Exception {
        funcionaria(1, "Ana", "1111");
        for (int i = 0; i < 5; i++) acesso.tentar("1", "0000");
        acesso.tentar("1", "1111");             // recusada: bloqueado
        acesso.tentar("1234", "0000");          // número que não existe

        String pagina = mvc.perform(get("/admin/acessos").with(admin())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(pagina).contains("PIN incorreto").contains("Recusada: acesso bloqueado").contains("1234")
                .contains("Número 1 (5 PINs errados)").contains("Em vigor");

        relogio.avancar(Duration.ofMinutes(6));
        assertThat(mvc.perform(get("/admin/acessos").with(admin())).andReturn().getResponse().getContentAsString())
                .contains("Terminado");
    }

    @Test
    void acessosSemNadaDizSoQueNaoHa() throws Exception {
        mvc.perform(get("/admin/acessos").with(admin())).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Nenhuma tentativa falhada")));
    }

    // ---- Histórico: coluna "Quem" ------------------------------------------------------------

    @Test
    void historicoMostraQuemFezEmTodosOsTipos() throws Exception {
        Utente maria = utente("Maria");
        var plano = plano(maria, "Paracetamol", pt.lar.models.Horario.MANHA);
        Funcionario ana = funcionaria(1, "Ana Ferreira", "1111");
        registoService.banho(maria.getId(), ana);
        registoService.queda(maria.getId(), true, ana);
        registoService.dor(maria.getId(), "PEITO", ana);
        registoService.toma(maria.getId(), plano.getId(), ana);

        String pagina = mvc.perform(get("/admin/historico").with(admin()).param("dia", "2026-11-03"))
                .andReturn().getResponse().getContentAsString();

        assertThat(pagina).contains("<th>Quem</th>");
        assertThat(pagina.split("Ana Ferreira \\(nº 1\\)", -1).length - 1).as("uma por registo").isEqualTo(4);
    }

    @Test
    void historicoSemSessaoMostraTracoEmVezDeNome() throws Exception {
        Utente maria = utente("Maria");
        registoService.banho(maria.getId());

        String pagina = mvc.perform(get("/admin/historico").with(admin()).param("dia", "2026-11-03"))
                .andReturn().getResponse().getContentAsString();

        assertThat(pagina).contains("<th>Quem</th>").doesNotContain("(nº");
    }
}
