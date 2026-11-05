package pt.lar.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pt.lar.BaseIntegracaoTest;
import pt.lar.ImagensDeTeste;
import pt.lar.models.Utente;
import pt.lar.models.UtenteFoto;
import pt.lar.repositories.UtenteFotoRepository;

/** Fotos na base de dados (bytea), servidas com ETag. */
@AutoConfigureMockMvc
class FotoPersistenciaTest extends BaseIntegracaoTest {

    @Autowired UtenteAdminService admin;
    @Autowired UtenteFotoRepository fotos;
    @Autowired MockMvc mvc;

    private MockMultipartFile ficheiro(byte[] bytes) {
        return new MockMultipartFile("foto", "foto.jpg", "image/jpeg", bytes);
    }

    private byte[] iphone6() {
        return ImagensDeTeste.jpegComOrientacao(ImagensDeTeste.meiaVermelhaMeiaAzul(1200, 600), 6);
    }

    @Test
    void fotoFicaGuardadaNaBaseDeDadosJaProcessada() throws Exception {
        Utente u = admin.guardar(null, "Maria", "1", ficheiro(iphone6()), false);

        UtenteFoto f = fotos.findById(u.getId()).orElseThrow();
        assertThat(f.getContentType()).isEqualTo("image/jpeg");
        assertThat(f.getEtag()).hasSize(32);
        var img = ImagensDeTeste.ler(f.getConteudo());
        assertThat(img.getWidth()).isEqualTo(400);
        assertThat(img.getHeight()).isEqualTo(400);
        // orientação 6 já aplicada: vermelho em cima, azul em baixo
        assertThat(ImagensDeTeste.ehVermelho(img.getRGB(200, 100))).isTrue();
        assertThat(ImagensDeTeste.ehAzul(img.getRGB(200, 300))).isTrue();
        assertThat(admin.temFoto(u.getId())).isTrue();
    }

    @Test
    void fotoInvalidaNaoCriaOUtenteNemFicaAMeio() {
        long antes = utentes.count();

        assertThatThrownBy(() -> admin.guardar(null, "Maria", "1", ficheiro("lixo".getBytes()), false))
                .isInstanceOf(FotoService.FotoInvalidaException.class);

        assertThat(utentes.count()).isEqualTo(antes);
        assertThat(fotos.count()).isZero();
    }

    @Test
    void servidaComEtagE304QuandoNaoMudou() throws Exception {
        Utente u = admin.guardar(null, "Maria", "1", ficheiro(iphone6()), false);

        MvcResult primeira = mvc.perform(get("/fotos/" + u.getId()))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("Cache-Control", "no-cache"))
                .andReturn();
        String etag = primeira.getResponse().getHeader("ETag");
        assertThat(etag).isEqualTo("\"" + fotos.findById(u.getId()).orElseThrow().getEtag() + "\"");
        assertThat(primeira.getResponse().getContentAsByteArray()).isNotEmpty();

        MvcResult segunda = mvc.perform(get("/fotos/" + u.getId()).header("If-None-Match", etag))
                .andExpect(status().isNotModified())
                .andReturn();
        assertThat(segunda.getResponse().getContentAsByteArray()).as("304 não leva corpo").isEmpty();
    }

    @Test
    void trocarAFotoMudaOEtagEOBrowserVoltaADescarregar() throws Exception {
        Utente u = admin.guardar(null, "Maria", "1", ficheiro(iphone6()), false);
        String etagAntigo = mvc.perform(get("/fotos/" + u.getId())).andReturn().getResponse().getHeader("ETag");

        admin.guardar(u.getId(), "Maria", "1",
                ficheiro(ImagensDeTeste.jpeg(ImagensDeTeste.meiaVermelhaMeiaAzul(300, 300))), false);

        MvcResult depois = mvc.perform(get("/fotos/" + u.getId()).header("If-None-Match", etagAntigo))
                .andExpect(status().isOk()).andReturn();
        assertThat(depois.getResponse().getHeader("ETag")).isNotEqualTo(etagAntigo);
    }

    @Test
    void removerAFotoVoltaAoAvatarComIniciais() throws Exception {
        Utente u = admin.guardar(null, "Maria da Conceição Ferreira", "1", ficheiro(iphone6()), false);

        admin.guardar(u.getId(), "Maria da Conceição Ferreira", "1", null, true);

        assertThat(fotos.findById(u.getId())).isEmpty();
        MvcResult r = mvc.perform(get("/fotos/" + u.getId())).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/svg+xml")).andExpect(header().exists("ETag")).andReturn();
        assertThat(r.getResponse().getContentAsString()).contains(">MF<");
        mvc.perform(get("/fotos/" + u.getId()).header("If-None-Match", r.getResponse().getHeader("ETag")))
                .andExpect(status().isNotModified());
    }

    @Test
    void semFotoNaoSeMexeNaFotoExistente() throws Exception {
        Utente u = admin.guardar(null, "Maria", "1", ficheiro(iphone6()), false);
        String etag = fotos.findById(u.getId()).orElseThrow().getEtag();

        admin.guardar(u.getId(), "Maria Silva", "2", null, false);     // só edita o nome

        assertThat(fotos.findById(u.getId()).orElseThrow().getEtag()).isEqualTo(etag);
    }

    @Test
    void apagarOUtenteApagaAFoto() throws Exception {
        Utente u = admin.guardar(null, "Maria", "1", ficheiro(iphone6()), false);
        utentes.deleteAllInBatch();                                     // ON DELETE CASCADE
        assertThat(fotos.findById(u.getId())).isEmpty();
    }

    @Test
    void envioPeloFormularioDoAdminGravaOUtenteEAFotoRodada() throws Exception {
        mvc.perform(multipart("/admin/utentes")
                        .file(ficheiro(iphone6()))
                        .param("nome", "Teresa Duarte").param("quarto", "16")
                        .with(user("enfermeira").roles("ENFERMEIRO")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        Utente u = utentes.findAll().get(0);
        assertThat(u.getNome()).isEqualTo("Teresa Duarte");
        var img = ImagensDeTeste.ler(fotos.findById(u.getId()).orElseThrow().getConteudo());
        assertThat(ImagensDeTeste.ehVermelho(img.getRGB(200, 100))).isTrue();
    }

    @Test
    void formularioRecusaFicheiroQueNaoEImagemEMostraAMensagem() throws IOException, Exception {
        mvc.perform(multipart("/admin/utentes")
                        .file(ficheiro("lixo".getBytes()))
                        .param("nome", "Teresa Duarte").param("quarto", "16")
                        .with(user("enfermeira").roles("ENFERMEIRO")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("Formato de imagem não suportado")));
        assertThat(utentes.count()).isZero();
    }
}
