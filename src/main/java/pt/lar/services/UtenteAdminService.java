package pt.lar.services;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import pt.lar.models.Sexo;
import pt.lar.models.Utente;
import pt.lar.repositories.UtenteRepository;

@Service
public class UtenteAdminService {

    private final UtenteRepository utentes;
    private final FotoService fotos;

    public UtenteAdminService(UtenteRepository utentes, FotoService fotos) {
        this.utentes = utentes;
        this.fotos = fotos;
    }

    @Transactional(readOnly = true)
    public List<Utente> listar(boolean incluirInativos) {
        List<Utente> todos = incluirInativos ? utentes.findAllByOrderByIdAsc() : utentes.findByAtivoTrueOrderByIdAsc();
        return todos.stream()
                .sorted(java.util.Comparator.comparing(Utente::isAtivo).reversed()
                        .thenComparingInt(u -> numero(u.getQuarto())).thenComparing(Utente::getNome))
                .toList();
    }

    @Transactional(readOnly = true)
    public Utente obter(Long id) {
        return utentes.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public boolean temFoto(Long id) {
        return fotos.existe(id);
    }

    /**
     * Grava o utente e, se vier uma foto, processa-a (EXIF, recorte, redução) antes de gravar nada: uma foto
     * inválida não deixa o utente a meio.
     */
    @Transactional(rollbackFor = Exception.class)
    public Utente guardar(Long id, String nome, String quarto, MultipartFile foto, boolean removerFoto)
            throws FotoService.FotoInvalidaException, IOException {
        return guardar(id, nome, quarto, foto, removerFoto, null);
    }

    /** Dados de identificação para a ficha de emergência (INEM). Os textos vazios ficam a nulo. */
    public record DadosEmergencia(LocalDate dataNascimento, Sexo sexo, String numeroUtenteSns, String contactoNome,
                                  String contactoParentesco, String contactoTelefone, String medicoAssistente,
                                  String infoEmergencia) {}

    /** Como acima; {@code emergencia == null} não mexe nos dados de emergência que o utente já tem. */
    @Transactional(rollbackFor = Exception.class)
    public Utente guardar(Long id, String nome, String quarto, MultipartFile foto, boolean removerFoto,
                          DadosEmergencia emergencia) throws FotoService.FotoInvalidaException, IOException {
        boolean temNovaFoto = foto != null && !foto.isEmpty();
        if (temNovaFoto) fotos.processar(foto.getBytes());        // valida já (lança FotoInvalidaException)

        Utente u = id == null ? new Utente(nome.strip(), quarto.strip()) : obter(id);
        u.setNome(nome.strip());
        u.setQuarto(quarto.strip());
        if (emergencia != null) {
            u.setDataNascimento(emergencia.dataNascimento());
            u.setSexo(emergencia.sexo());
            u.setNumeroUtenteSns(vazioParaNulo(emergencia.numeroUtenteSns()));
            u.setContactoNome(vazioParaNulo(emergencia.contactoNome()));
            u.setContactoParentesco(vazioParaNulo(emergencia.contactoParentesco()));
            u.setContactoTelefone(vazioParaNulo(emergencia.contactoTelefone()));
            u.setMedicoAssistente(vazioParaNulo(emergencia.medicoAssistente()));
            u.setInfoEmergencia(vazioParaNulo(emergencia.infoEmergencia()));
        }
        u = utentes.save(u);

        if (temNovaFoto) {
            fotos.guardar(u.getId(), foto);
        } else if (removerFoto) {
            fotos.remover(u.getId());
        }
        return u;
    }

    @Transactional
    public void definirAtivo(Long id, boolean ativo) {
        obter(id).setAtivo(ativo);
    }

    private static String vazioParaNulo(String s) {
        if (s == null) return null;
        String t = s.strip();
        return t.isEmpty() ? null : t;
    }

    private static int numero(String quarto) {
        try {
            return Integer.parseInt(quarto.trim());
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }
}
