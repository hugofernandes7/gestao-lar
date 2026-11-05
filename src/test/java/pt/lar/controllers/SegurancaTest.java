package pt.lar.controllers;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import pt.lar.BaseIntegracaoTest;
import pt.lar.models.Utente;

/** /tablet é aberto (sem login nem CSRF); /admin exige login e CSRF. */
@AutoConfigureMockMvc
class SegurancaTest extends BaseIntegracaoTest {

    @Autowired MockMvc mvc;

    @Test
    void tabletNaoPedeLogin() throws Exception {
        mvc.perform(get("/tablet")).andExpect(status().isOk());
    }

    @Test
    void tabletRegistaSemTokenCsrf() throws Exception {
        Utente u = utente("Maria");
        mvc.perform(post("/tablet/utentes/" + u.getId() + "/banho"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Banho registado às")));
        mvc.perform(post("/tablet/utentes/" + u.getId() + "/banho"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("já estava registado")));
    }

    @Test
    void adminSemLoginVaiParaOLogin() throws Exception {
        mvc.perform(get("/admin/utentes")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/admin/login"));
        mvc.perform(get("/admin/historico")).andExpect(status().is3xxRedirection());
    }

    @Test
    void paginaDeLoginEAberta() throws Exception {
        mvc.perform(get("/admin/login")).andExpect(status().isOk());
    }

    @Test
    void adminComLoginEntra() throws Exception {
        mvc.perform(get("/admin/utentes").with(user("enfermeira").roles("ENFERMEIRO"))).andExpect(status().isOk());
        mvc.perform(get("/admin/utentes").with(user("gerontologo").roles("GERONTOLOGO"))).andExpect(status().isOk());
    }

    @Test
    void adminRecusaPostSemCsrf() throws Exception {
        mvc.perform(post("/admin/registos/1/anular").with(user("enfermeira").roles("ENFERMEIRO")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminAceitaPostComCsrf() throws Exception {
        Utente u = utente("Maria");
        mvc.perform(post("/admin/utentes/" + u.getId() + "/ativo").param("ativo", "false")
                        .with(user("enfermeira").roles("ENFERMEIRO")).with(csrf()))
                .andExpect(status().is3xxRedirection());
    }
}
