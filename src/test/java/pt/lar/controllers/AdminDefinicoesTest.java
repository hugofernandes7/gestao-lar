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

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import pt.lar.BaseIntegracaoTest;
import pt.lar.models.Perfil;
import pt.lar.models.UtilizadorGestao;

/** Página "Definições" do /admin. */
@AutoConfigureMockMvc
class AdminDefinicoesTest extends BaseIntegracaoTest {

    @Autowired MockMvc mvc;

    private static RequestPostProcessor admin() {
        return user("enfermeira").roles("ENFERMEIRO");
    }

    @Test
    void exigeLogin() throws Exception {
        mvc.perform(get("/admin/definicoes")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/admin/login"));
        mvc.perform(post("/admin/definicoes").with(csrf()).param("minutos", "9")).andExpect(status().is3xxRedirection());
        assertThat(definicoesService.atuais().minutos()).isEqualTo(2);
    }

    @Test
    void mostraOsValoresAtuaisEExplicaQueAMedicacaoExigeSempre() throws Exception {
        mvc.perform(get("/admin/definicoes").with(admin()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("A medicação exige sempre sessão")))
                .andExpect(content().string(Matchers.containsString("continua a pedir PIN")))
                .andExpect(content().string(Matchers.containsString("value=\"2\"")));
    }

    @Test
    void guardaEAuditaComQuemAlterou() throws Exception {
        utilizadores.save(new UtilizadorGestao("Enfermeira Ana", "enfermeira", "x", Perfil.ENFERMEIRO));

        mvc.perform(post("/admin/definicoes").with(admin()).with(csrf())
                        .param("ocorrencia", "false").param("toma", "true").param("minutos", "4"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/definicoes"));

        var a = definicoesService.atuais();
        assertThat(a.ocorrencia()).isFalse();
        assertThat(a.minutos()).isEqualTo(4);
        String pagina = mvc.perform(get("/admin/definicoes").with(admin())).andReturn().getResponse().getContentAsString();
        assertThat(pagina).contains("Exige sessão — Ocorrências (queda e dor): sim → não").contains("Duração da sessão: 2 min → 4 min")
                .contains("Enfermeira Ana");
    }

    @Test
    void caixasNaoMarcadasSignificamNao() throws Exception {
        mvc.perform(post("/admin/definicoes").with(admin()).with(csrf()).param("minutos", "2"))
                .andExpect(status().is3xxRedirection());

        var a = definicoesService.atuais();
        assertThat(a.ocorrencia()).isFalse();
        assertThat(a.toma()).isFalse();
        assertThat(definicoesService.exige(pt.lar.services.DefinicoesService.Exigencia.TOMA)).isTrue();
    }

    @Test
    void tomaDesligadaMostraQueNaoTemEfeito() throws Exception {
        mvc.perform(post("/admin/definicoes").with(admin()).with(csrf()).param("ocorrencia", "true").param("minutos", "2"));

        mvc.perform(get("/admin/definicoes").with(admin()))
                .andExpect(content().string(Matchers.containsString("neste momento está desligada, sem efeito")));
    }

    @Test
    void duracaoInvalidaVoltaAoFormularioComErroENaoGuardaNada() throws Exception {
        for (String m : new String[] {"0", "61", "abc", ""}) {
            mvc.perform(post("/admin/definicoes").with(admin()).with(csrf())
                            .param("ocorrencia", "true").param("toma", "true").param("minutos", m))
                    .andExpect(status().isOk())
                    .andExpect(content().string(Matchers.containsString("duração da sessão")));
        }
        assertThat(definicoesService.atuais().ocorrencia()).isTrue();
        assertThat(definicoesRepo.count()).isZero();
    }

    @Test
    void aPaginaNaoOferecoOBanhoEUmPedidoComBanhoNaoTemEfeito() throws Exception {
        mvc.perform(get("/admin/definicoes").with(admin()))
                .andExpect(content().string(Matchers.not(Matchers.containsString("name=\"banho\""))))
                .andExpect(content().string(Matchers.containsString("O banho nunca exige sessão")));

        mvc.perform(post("/admin/definicoes").with(admin()).with(csrf()).param("banho", "true")
                .param("ocorrencia", "true").param("toma", "true").param("peso", "true").param("minutos", "2"));

        assertThat(definicoesService.exige(pt.lar.models.TipoRegisto.BANHO)).isFalse();
        assertThat(definicoesRepo.count()).isZero();
    }

    @Test
    void recusaPostSemCsrf() throws Exception {
        mvc.perform(post("/admin/definicoes").with(admin()).param("minutos", "9")).andExpect(status().isForbidden());
    }
}
