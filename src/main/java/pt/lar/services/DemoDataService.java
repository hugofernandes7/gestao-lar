package pt.lar.services;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.lar.config.LarProperties;
import pt.lar.models.Funcionario;
import pt.lar.models.Horario;
import pt.lar.models.QuantidadeRefeicao;
import pt.lar.models.Refeicao;
import pt.lar.models.MedicacaoPlano;
import pt.lar.models.MedicacaoPlanoAuditoria;
import pt.lar.models.Mobilidade;
import pt.lar.models.RiscoQueda;
import pt.lar.models.TexturaAlimentar;
import pt.lar.models.Registo;
import pt.lar.models.RegistoNotaHistorico;
import pt.lar.models.TipoRegisto;
import pt.lar.models.Utente;
import pt.lar.models.UtilizadorGestao;
import pt.lar.models.AcessoSaude;
import pt.lar.models.UtenteCondicao;
import pt.lar.repositories.AcessoSaudeRepository;
import pt.lar.repositories.CondicaoProtocoloRepository;
import pt.lar.repositories.FuncionarioRepository;
import pt.lar.repositories.UtenteCondicaoRepository;
import pt.lar.repositories.MedicacaoPlanoAuditoriaRepository;
import pt.lar.repositories.MedicacaoPlanoRepository;
import pt.lar.repositories.RegistoNotaHistoricoRepository;
import pt.lar.repositories.RegistoRepository;
import pt.lar.repositories.UtenteRepository;
import pt.lar.repositories.UtilizadorGestaoRepository;

/**
 * Gera dados fictícios RELATIVOS a "agora" (para os indicadores aparecerem sempre na demo).
 * Determinístico (Random com semente fixa), exceto pela hora a que corre.
 */
@Service
public class DemoDataService {

    private static final String[] NOMES = {
            "Maria da Conceição Ferreira", "António Manuel Silva", "Rosa Maria Pereira",
            "José Carlos Oliveira", "Ana Luísa Santos", "Manuel Joaquim Costa",
            "Alice Fernanda Rodrigues", "Joaquim Augusto Martins", "Fernanda Isabel Sousa",
            "Francisco António Lopes", "Maria Helena Gomes", "Armando José Ribeiro",
            "Lurdes Maria Carvalho", "Fernando Jorge Almeida", "Olinda Maria Teixeira"};

    private record Modelo(String medicamento, String dose, Horario horario, String obs) {}

    private static final List<Modelo> POOL = List.of(
            new Modelo("Paracetamol", "1 g", Horario.MANHA, null),
            new Modelo("Omeprazol", "20 mg", Horario.MANHA, "Em jejum"),
            new Modelo("Ramipril", "5 mg", Horario.MANHA, null),
            new Modelo("Bisoprolol", "2,5 mg", Horario.MANHA, null),
            new Modelo("Furosemida", "40 mg", Horario.MANHA, "Vigiar tensão arterial"),
            new Modelo("Levotiroxina", "50 mcg", Horario.MANHA, "Em jejum, 30 min antes do pequeno-almoço"),
            new Modelo("Metformina", "850 mg", Horario.ALMOCO, "Tomar com a refeição"),
            new Modelo("Cálcio + Vitamina D", "1 comprimido", Horario.ALMOCO, null),
            new Modelo("Metformina", "850 mg", Horario.JANTAR, "Tomar com a refeição"),
            new Modelo("Amlodipina", "5 mg", Horario.JANTAR, null),
            new Modelo("Varfarina", "5 mg", Horario.JANTAR, "Controlo de INR semanal"),
            new Modelo("Sinvastatina", "20 mg", Horario.DEITAR, null),
            new Modelo("Lorazepam", "1 mg", Horario.DEITAR, "Suspender se sonolência excessiva"),
            new Modelo("Donepezilo", "10 mg", Horario.DEITAR, null),
            new Modelo("Atorvastatina", "20 mg", Horario.DEITAR, null));

    private final UtenteRepository utentes;
    private final UtilizadorGestaoRepository utilizadores;
    private final MedicacaoPlanoRepository planos;
    private final MedicacaoPlanoAuditoriaRepository auditoria;
    private final RegistoRepository registos;
    private final RegistoNotaHistoricoRepository notas;
    private final Clock clock;
    private final LarProperties props;
    private final PerfilService perfis;
    private final FuncionarioRepository funcionarios;
    private final CondicaoProtocoloRepository catalogoCondicoes;
    private final UtenteCondicaoRepository condicoes;
    private final AcessoSaudeRepository acessosSaude;
    private final pt.lar.repositories.PesoRepository pesos;
    private final VigilanciaService vigilancia;
    private final pt.lar.repositories.VigilanciaRepository verificacoes;
    private final pt.lar.repositories.VigilanciaPlanoRepository planosVigilancia;

    public DemoDataService(UtenteRepository utentes, UtilizadorGestaoRepository utilizadores,
                           MedicacaoPlanoRepository planos, MedicacaoPlanoAuditoriaRepository auditoria,
                           RegistoRepository registos, RegistoNotaHistoricoRepository notas, Clock clock, LarProperties props,
                           PerfilService perfis, FuncionarioRepository funcionarios,
                           CondicaoProtocoloRepository catalogoCondicoes, UtenteCondicaoRepository condicoes,
                           AcessoSaudeRepository acessosSaude, pt.lar.repositories.PesoRepository pesos,
                           VigilanciaService vigilancia, pt.lar.repositories.VigilanciaRepository verificacoes,
                           pt.lar.repositories.VigilanciaPlanoRepository planosVigilancia) {
        this.vigilancia = vigilancia;
        this.verificacoes = verificacoes;
        this.planosVigilancia = planosVigilancia;
        this.pesos = pesos;
        this.catalogoCondicoes = catalogoCondicoes;
        this.condicoes = condicoes;
        this.acessosSaude = acessosSaude;
        this.perfis = perfis;
        this.funcionarios = funcionarios;
        this.utentes = utentes;
        this.utilizadores = utilizadores;
        this.planos = planos;
        this.auditoria = auditoria;
        this.registos = registos;
        this.notas = notas;
        this.clock = clock;
        this.props = props;
    }

    /** Apaga os dados clínicos e volta a gerar a demo (os utilizadores de gestão mantêm-se). */
    @Transactional
    public void repor() {
        acessosSaude.deleteAllInBatch();
        condicoes.deleteAllInBatch();
        pesos.deleteAllInBatch();
        verificacoes.deleteAllInBatch();
        planosVigilancia.deleteAllInBatch();
        registos.deleteAllInBatch();
        auditoria.deleteAllInBatch();
        planos.deleteAllInBatch();
        utentes.deleteAllInBatch();
        popular();
    }

    @Transactional
    public void popular() {
        if (utentes.count() > 0) return;

        Instant agora = clock.instant();
        ZoneId zona = clock.getZone();
        LocalDate hoje = LocalDate.now(clock);
        Random rnd = new Random(42);
        UtilizadorGestao autor = utilizadores.findByUsername("enfermeira").orElse(null);
        Instant criacaoPlanos = agora.minus(Duration.ofDays(30));

        // 1. Utentes (quartos 1..15)
        List<Utente> lista = new ArrayList<>();
        for (int i = 0; i < NOMES.length; i++) {
            lista.add(utentes.save(new Utente(NOMES[i], String.valueOf(i + 1))));
        }

        // 2. Planos de medicação (1 a 5 linhas por utente) + auditoria inicial
        List<MedicacaoPlano> todosPlanos = new ArrayList<>();
        for (int i = 0; i < lista.size(); i++) {
            Utente u = lista.get(i);
            List<Modelo> escolhidos = new ArrayList<>(POOL);
            java.util.Collections.shuffle(escolhidos, rnd);
            int n = 1 + (i % 5);
            for (Modelo m : escolhidos.subList(0, n)) {
                String obs = m.obs();
                if (i == 1 && m == escolhidos.get(0)) obs = "ALERGIA: penicilina";
                MedicacaoPlano p = planos.save(new MedicacaoPlano(
                        u, m.medicamento(), m.dose(), m.horario(), obs, criacaoPlanos, autor));
                auditoria.save(new MedicacaoPlanoAuditoria(p, snapshot(p), criacaoPlanos, autor));
                todosPlanos.add(p);
            }
        }

        // 3. Banhos dos últimos 7 dias + hoje
        for (int i = 0; i < lista.size(); i++) {
            for (int d = 7; d >= 0; d--) {
                if (d == 0 && i % 4 == 0) continue;          // alguns ainda sem banho hoje
                if (d > 0 && rnd.nextInt(100) < 6) continue; // falhas ocasionais no histórico
                LocalDateTime ldt = hoje.minusDays(d).atTime(10, 0).plusMinutes(rnd.nextInt(90));
                Instant inst = ldt.atZone(zona).toInstant();
                if (inst.isAfter(agora)) continue;
                registos.save(new Registo(lista.get(i), TipoRegisto.BANHO, null, "Banho", null,
                        inst, ldt.toLocalDate()));
            }
        }

        // 4. Tomas: a maioria dadas, algumas em falta; 2 utentes com atraso hoje (se já houver toma > 1 h atrás)
        Set<Long> comAtraso = new HashSet<>();
        int atraso = props.atrasoMinutos();
        for (MedicacaoPlano p : todosPlanos) {
            int idx = p.getUtente().getId().intValue() - lista.get(0).getId().intValue();
            for (int d = 7; d >= 0; d--) {
                LocalDate dia = hoje.minusDays(d);
                LocalDateTime ldt = dia.atTime(props.horaDe(p.getHorario())).plusMinutes(rnd.nextInt(20));
                Instant inst = ldt.atZone(zona).toInstant();
                if (inst.isAfter(agora)) continue;
                boolean emAtraso = ldt.plusMinutes(atraso).atZone(zona).toInstant().isBefore(agora);
                boolean dado;
                if (d == 0) {
                    if (emAtraso && (idx == 2 || idx == 7) && comAtraso.add(p.getUtente().getId())) {
                        dado = false; // toma em atraso para a demo
                    } else {
                        dado = emAtraso || rnd.nextInt(100) < 70;
                    }
                } else {
                    dado = rnd.nextInt(100) < 93;
                }
                if (dado) {
                    registos.save(new Registo(p.getUtente(), TipoRegisto.TOMA, null, p.descricao(), p,
                            inst, ldt.toLocalDate()));
                }
            }
        }

        // 5. Ocorrências
        Registo quedaRecente = ocorrencia(lista.get(3), TipoRegisto.QUEDA, "COM_LESAO", "Escorregou ao levantar-se da cama",
                agora.minus(Duration.ofHours(5)), zona);
        vigilanciaDeDemonstracao(quedaRecente, 2, 2);                          // 1.ª e 2.ª feitas (a 2.ª com alerta); a 3.ª daqui a ~3 h                       // recente: <48 h
        ocorrencia(lista.get(6), TipoRegisto.DOR, "PEITO", null,
                agora.minus(Duration.ofHours(30)), zona);                      // recente: <48 h
        Registo quedaAntiga = ocorrencia(lista.get(10), TipoRegisto.QUEDA, "SEM_LESAO", null,
                agora.minus(Duration.ofHours(60)), zona);
        vigilanciaDeDemonstracao(quedaAntiga, 4, 0);                           // vigilância completa, sem alertas                      // fora das 48 h
        ocorrencia(lista.get(8), TipoRegisto.QUEDA, "SEM_LESAO", "Encontrada sentada no chão",
                agora.minus(Duration.ofDays(4)), zona);
        ocorrencia(lista.get(0), TipoRegisto.DOR, "CABECA", null,
                agora.minus(Duration.ofDays(5)), zona);
        ocorrencia(lista.get(12), TipoRegisto.DOR, "COSTAS", "Queixa-se ao levantar",
                agora.minus(Duration.ofDays(6)), zona);

        // 6. Perfis de atenção e "Sobre mim": variados, revistos em datas diferentes
        for (int i = 0; i < lista.size(); i++) {
            Long id = lista.get(i).getId();
            PerfilService.Dados d = PERFIS.get(i).dados();
            if (d.riscoQueda() == RiscoQueda.ALTO) {
                // o risco foi subindo: guarda primeiro a versão anterior, para o histórico ter mais de uma versão
                perfis.guardar(id, PERFIS.get(i).risco2(RiscoQueda.MEDIO).dados(), autor, agora.minus(Duration.ofDays(60)));
            }
            perfis.guardar(id, d, autor, agora.minus(Duration.ofDays(2L + (i * 3L) % 40)));
        }

        // 6b. Condições de saúde (o nome só se vê na aba Saúde, com PIN; no tablet sem PIN só os avisos de atuação)
        validarProtocolosDeDemonstracao(autor, agora);
        condicoesDeDemonstracao(lista, hoje, autor, agora);

        // 6c. Refeições dos dias anteriores (um utente que come pouco) e pesagens (um utente com perda de peso)
        refeicoesEPesosDeDemonstracao(lista, hoje, zona);

        // 7. Quem registou: as funcionárias de demonstração (se existirem) alternam; um em cada cinco fica sem sessão (nulo)
        List<Funcionario> equipa = funcionarios.findAllByOrderByNumeroAsc();
        if (!equipa.isEmpty()) {
            for (Registo r : registos.findAll()) {
                if (r.getId() % 5 != 0) r.setRealizadoPor(equipa.get((int) (r.getId() % equipa.size())));
            }
        }
    }

    private static final int IDX_COME_POUCO = 7;     // demência avançada: as últimas 3 refeições foram «Pouco» ou «Recusou»
    private static final int IDX_PERDA_DE_PESO = 11;  // perdeu mais de 5 % em 30 dias

    private void refeicoesEPesosDeDemonstracao(List<Utente> lista, LocalDate hoje, ZoneId zona) {
        Random rnd = new Random(7);
        Refeicao[] feitas = {Refeicao.PEQUENO_ALMOCO, Refeicao.ALMOCO, Refeicao.LANCHE, Refeicao.JANTAR};
        int[][] horas = {{8, 20}, {12, 50}, {16, 5}, {19, 40}};
        for (int i = 0; i < lista.size(); i++) {
            for (int d = 7; d >= 1; d--) {                              // só dias anteriores: a secção «Hoje» fica como estava
                for (int k = 0; k < feitas.length; k++) {
                    QuantidadeRefeicao q;
                    boolean ultimas3 = d == 1 && k >= 1;                  // almoço, lanche e jantar de ontem
                    if (i == IDX_COME_POUCO) {
                        q = ultimas3 ? (k == 2 ? QuantidadeRefeicao.RECUSOU : QuantidadeRefeicao.POUCO)
                                : (rnd.nextInt(100) < 50 ? QuantidadeRefeicao.POUCO : QuantidadeRefeicao.METADE);
                    } else {
                        int p = rnd.nextInt(100);
                        q = p < 58 ? QuantidadeRefeicao.TUDO : p < 88 ? QuantidadeRefeicao.METADE : p < 97 ? QuantidadeRefeicao.POUCO : QuantidadeRefeicao.RECUSOU;
                        if (ultimas3 && k == 3) q = QuantidadeRefeicao.TUDO;   // o jantar de ontem fecha o alerta
                    }
                    LocalDate dia = hoje.minusDays(d);
                    Instant quando = dia.atTime(horas[k][0], horas[k][1]).plusMinutes(rnd.nextInt(25)).atZone(zona).toInstant();
                    Registo r = new Registo(lista.get(i), TipoRegisto.REFEICAO, q.name(), feitas[k].getEtiqueta(), null, quando, dia);
                    r.setRefeicao(feitas[k]);
                    registos.save(r);
                }
            }
            // pesagens: há 3 e 2 meses (e há 4 semanas e 5 dias para o utente com perda de peso)
            double base = 52 + (i * 37 % 40) + rnd.nextInt(10) / 10.0;
            int[] dias = i == IDX_PERDA_DE_PESO ? new int[] {90, 60, 27, 5} : new int[] {90, 60, 28};
            for (int j = 0; j < dias.length; j++) {
                double kg = i == IDX_PERDA_DE_PESO ? base - new double[] {0, 0.5, 0.5, 4.5}[j]
                        : base + (rnd.nextInt(9) - 4) / 10.0;
                LocalDate dia = hoje.minusDays(dias[j]);
                Instant quando = dia.atTime(9, 30).atZone(zona).toInstant();
                Registo r = registos.save(new Registo(lista.get(i), TipoRegisto.PESO, null, "Peso", null, quando, dia));
                pesos.save(new pt.lar.models.Peso(r, lista.get(i), java.math.BigDecimal.valueOf(kg).setScale(1, java.math.RoundingMode.HALF_UP)));
            }
        }
    }

    /** Textos [DEMO]: a maioria fica por validar; 3 (usados na demonstração) validados pela enfermeira de demo. */
    private void validarProtocolosDeDemonstracao(UtilizadorGestao autor, Instant agora) {
        for (var c : catalogoCondicoes.findAll()) {
            if (java.util.Set.of("ANTICOAGULADO", "DIABETES_INSULINA", "DEMENCIA").contains(c.getCodigo())) c.validar(agora, autor);
            else c.limparValidacao();
        }
    }

    private void condicao(Utente u, String codigo, String detalhe, LocalDate desde, String notas, UtilizadorGestao autor,
                          Instant quando, boolean ativa) {
        var tipo = catalogoCondicoes.findById(codigo).orElseThrow();
        UtenteCondicao c = condicoes.save(new UtenteCondicao(u, tipo, detalhe, desde, notas, quando, autor));
        if (!ativa) c.definirAtiva(false, quando, autor);
    }

    private void condicoesDeDemonstracao(List<Utente> l, LocalDate hoje, UtilizadorGestao autor, Instant agora) {
        Instant antes = agora.minus(Duration.ofDays(20));
        condicao(l.get(0), "ENFARTE", "Há alguns anos; sem queixas desde então", hoje.minusYears(8), null, autor, antes, true);
        condicao(l.get(1), "DIABETES_ORAL", "Tipo 2", hoje.minusYears(12), "Vigiar a dieta nas festas e nos lanches.", autor, antes, true);
        condicao(l.get(2), "DPOC_OXIGENIO", "Oxigénio à noite e quando tem falta de ar", hoje.minusYears(4), "Tubo de oxigénio verificado no turno da manhã.", autor, antes, true);
        condicao(l.get(3), "ANTICOAGULADO", "Controlo semanal", hoje.minusYears(3), "Qualquer queda: avisar a enfermagem de imediato.", autor, antes, true);
        condicao(l.get(3), "OUTRA", "Hipertensão arterial", hoje.minusYears(15), null, autor, antes, true);
        condicao(l.get(4), "ANTICOAGULADO", "Terminou o tratamento", hoje.minusYears(2), null, autor, antes, false);
        condicao(l.get(5), "AVC_SEQUELAS", "Dificuldade em engolir líquidos", hoje.minusYears(5), "Refeições sentado e direito.", autor, antes, true);
        condicao(l.get(6), "INSUF_CARDIACA", null, hoje.minusYears(6), null, autor, antes, true);
        condicao(l.get(6), "PACEMAKER", "Colocado há 2 anos", hoje.minusYears(2), null, autor, antes, true);
        condicao(l.get(7), "DEMENCIA", "Fase avançada", hoje.minusYears(5), "Fica mais calmo com fotografias antigas.", autor, antes, true);
        condicao(l.get(8), "DEMENCIA", "Fase inicial", hoje.minusYears(2), "Pode tentar sair do quarto à noite.", autor, antes, true);
        condicao(l.get(9), "DIABETES_ORAL", "Tipo 2", hoje.minusYears(9), null, autor, antes, true);
        condicao(l.get(10), "EPILEPSIA", "Crises raras, controladas", hoje.minusYears(15), "Última crise há mais de 2 anos.", autor, antes, true);
        condicao(l.get(11), "DPOC_OXIGENIO", null, hoje.minusYears(3), null, autor, antes, true);
        condicao(l.get(12), "DIABETES_INSULINA", "Tipo 2, insulina ao pequeno-almoço e ao jantar", hoje.minusYears(10),
                "Não saltar refeições. Já teve descidas de açúcar de manhã.", autor, antes, true);
        condicao(l.get(13), "DEMENCIA", "Fase moderada", hoje.minusYears(4), null, autor, antes, true);
        condicao(l.get(13), "ENFARTE", null, hoje.minusYears(7), null, autor, antes, true);
        condicao(l.get(14), "OUTRA", "Osteoporose", hoje.minusYears(6), "Tem cuidado extra ao levantar e a deitar: ossos frágeis.", autor, antes, true);

        // Alguns acessos recentes ao separador Saúde (se existirem funcionárias de demonstração)
        List<Funcionario> equipa = funcionarios.findAllByOrderByNumeroAsc();
        if (!equipa.isEmpty()) {
            acessosSaude.save(new AcessoSaude(equipa.get(0), l.get(12), agora.minus(Duration.ofHours(3))));
            acessosSaude.save(new AcessoSaude(equipa.get(equipa.size() > 1 ? 1 : 0), l.get(3), agora.minus(Duration.ofHours(2))));
            acessosSaude.save(new AcessoSaude(equipa.get(0), l.get(7), agora.minus(Duration.ofMinutes(50))));
        }
    }

    /** Montagem curta dos perfis de demonstração (mesma ordem de {@link #NOMES}). */
    private static final class P {
        private String alergias, dieta, avisos, trato, gostos;
        private Mobilidade mob = Mobilidade.AUTONOMO;
        private Integer transf;
        private RiscoQueda risco = RiscoQueda.BAIXO;
        private TexturaAlimentar textura = TexturaAlimentar.NORMAL;
        private boolean ajuda;
        private Set<String> com = Set.of();

        P alergias(String s) { this.alergias = s; return this; }
        P mob(Mobilidade m, Integer transferencia) { this.mob = m; this.transf = transferencia; return this; }
        P mob(Mobilidade m) { return mob(m, null); }
        P risco(RiscoQueda r) { this.risco = r; return this; }
        P comida(TexturaAlimentar t, String dieta, boolean ajuda) { this.textura = t; this.dieta = dieta; this.ajuda = ajuda; return this; }
        P com(String... flags) { this.com = Set.of(flags); return this; }
        P aviso(String s) { this.avisos = s; return this; }
        P trato(String s) { this.trato = s; return this; }
        P gostos(String s) { this.gostos = s; return this; }

        P copia() {
            P p = new P();
            p.alergias = alergias; p.dieta = dieta; p.avisos = avisos; p.trato = trato; p.gostos = gostos;
            p.mob = mob; p.transf = transf; p.risco = risco; p.textura = textura; p.ajuda = ajuda; p.com = com;
            return p;
        }

        /** Cópia com outro risco de queda (versão anterior do perfil). */
        P risco2(RiscoQueda r) { P p = copia(); p.risco = r; return p; }

        PerfilService.Dados dados() {
            return new PerfilService.Dados(alergias, mob, transf, risco, textura, dieta, ajuda,
                    com.contains("esq"), com.contains("dir"), com.contains("oculos"), com.contains("auditiva"),
                    com.contains("dentaria"), com.contains("desor"), avisos, trato, gostos);
        }
    }

    private static final List<P> PERFIS = List.of(
            new P().mob(Mobilidade.BENGALA).risco(RiscoQueda.MEDIO).comida(TexturaAlimentar.NORMAL, "Sem sal", false)
                    .com("oculos").trato("D. Maria")
                    .gostos("Gosta de ouvir fado e de falar dos netos. Foi costureira. Fica mais calma com a rádio baixinho."),
            new P().alergias("Penicilina").comida(TexturaAlimentar.NORMAL, "Sem açúcar", false).com("esq", "auditiva")
                    .trato("Sr. António; falar de frente e devagar")
                    .gostos("Futebol (é do Benfica), dominó e jornais. Foi motorista de camionagem."),
            new P().alergias("Marisco").mob(Mobilidade.ANDARILHO, 1).risco(RiscoQueda.MEDIO)
                    .comida(TexturaAlimentar.MOLE, "Sem sal", false).com("oculos", "dentaria").trato("D. Rosa")
                    .gostos("Adora jardinagem e telenovelas. Gosta de chá de camomila à tarde."),
            new P().mob(Mobilidade.ANDARILHO, 1).risco(RiscoQueda.ALTO).com("oculos")
                    .aviso("Pede ajuda para se levantar: não o deixar levantar sozinho")
                    .trato("Sr. José; chamá-lo pelo nome antes de lhe tocar")
                    .gostos("Foi pedreiro. Gosta de falar de obras e da aldeia onde nasceu."),
            new P().alergias("Iodo").trato("Ana Luísa")
                    .gostos("Muito conversadora. Gosta de bordados e de jogar às cartas."),
            new P().mob(Mobilidade.CADEIRA_RODAS, 2).risco(RiscoQueda.MEDIO)
                    .comida(TexturaAlimentar.PASTOSA, "Sem sal", true).com("esq", "dentaria")
                    .aviso("Risco de engasgamento: refeições sentado e direito").trato("Sr. Manuel")
                    .gostos("Gosta de música popular e de ver o telejornal."),
            new P().alergias("Anti-inflamatórios (AINEs)").mob(Mobilidade.BENGALA).risco(RiscoQueda.ALTO)
                    .comida(TexturaAlimentar.MOLE, "Hipossalina", false).com("oculos", "auditiva", "dir").trato("D. Alice")
                    .gostos("Foi professora primária. Gosta de ler e de palavras cruzadas."),
            new P().mob(Mobilidade.ACAMADO, 2).risco(RiscoQueda.MEDIO)
                    .comida(TexturaAlimentar.LIQUIDOS_ESPESSADOS, "Triturada", true).com("dentaria", "desor")
                    .aviso("Grades da cama levantadas").trato("Sr. Joaquim; falar devagar e com voz calma")
                    .gostos("Gostava de pescar. Reage bem a fotografias antigas."),
            new P().alergias("Sulfamidas").mob(Mobilidade.ANDARILHO, 1).risco(RiscoQueda.ALTO).com("oculos", "desor")
                    .aviso("Vigiar saídas do quarto à noite").trato("D. Fernanda")
                    .gostos("Gosta de dançar e de cantar canções antigas."),
            new P().comida(TexturaAlimentar.NORMAL, "Sem açúcar", false).com("esq", "dir", "auditiva")
                    .trato("Sr. Francisco; falar de frente")
                    .gostos("Jogava bilhar. Gosta de futebol e de passear no jardim."),
            new P().alergias("Frutos secos").mob(Mobilidade.BENGALA).risco(RiscoQueda.MEDIO).com("oculos")
                    .trato("Helena").gostos("Gosta de pintura e de passeios ao sol. Adora chocolate negro."),
            new P().mob(Mobilidade.ANDARILHO, 1).risco(RiscoQueda.MEDIO).comida(TexturaAlimentar.MOLE, null, false)
                    .com("esq", "dentaria").trato("Sr. Armando")
                    .gostos("Foi marinheiro. Gosta de contar histórias do mar."),
            new P().alergias("Penicilina").comida(TexturaAlimentar.NORMAL, "Sem açúcar", false).com("oculos")
                    .trato("D. Lurdes").gostos("Gosta de culinária e de receitas antigas. Costuma ajudar a pôr a mesa."),
            new P().mob(Mobilidade.CADEIRA_RODAS, 1).risco(RiscoQueda.MEDIO)
                    .comida(TexturaAlimentar.NORMAL, "Sem sal", true).com("auditiva", "dir", "desor")
                    .aviso("Confirmar sempre os travões da cadeira").trato("Sr. Fernando")
                    .gostos("Gosta de música clássica e de dominó."),
            new P().trato("D. Olinda").gostos("Muito sociável. Gosta de jogos de grupo e de rezar o terço."));

    private Registo ocorrencia(Utente u, TipoRegisto tipo, String detalhe, String nota, Instant quando, ZoneId zona) {
        Registo r = new Registo(u, tipo, detalhe, tipo == TipoRegisto.QUEDA ? "Queda" : "Dor / mal-estar",
                null, quando, quando.atZone(zona).toLocalDate());
        r.setNota(nota);
        r = registos.save(r);
        if (nota != null) {                                   // primeira versão da nota, como se fosse escrita na hora
            notas.save(new RegistoNotaHistorico(r, nota, quando.plusSeconds(90)));
        }
        return r;
    }

    /**
     * Plano de vigilância de uma queda da demo: as primeiras {@code feitas} verificações já feitas (10 min depois da hora
     * prevista), a de ordem {@code comAlerta} (1-based; 0 = nenhuma) com uma resposta fora do normal.
     */
    private void vigilanciaDeDemonstracao(Registo queda, int feitas, int comAlerta) {
        vigilancia.criarPlano(queda);
        var autora = funcionarios.findByNumero(1).orElse(null);
        int total = props.vigilancia().intervalosHoras().size();
        for (var v : verificacoes.daQueda(queda.getId())) {
            if (v.getOrdem() > feitas) break;
            Instant feita = v.getPrevistaEm().plus(Duration.ofMinutes(10));
            boolean alerta = v.getOrdem() == comAlerta;
            Registo reg = new Registo(queda.getUtente(), TipoRegisto.VIGILANCIA, v.getOrdem() + "/" + total,
                    "Vigilância " + v.getOrdem() + " de " + total, null, feita, feita.atZone(ZoneId.of(props.fuso())).toLocalDate());
            reg.setRealizadoPor(autora);
            reg = registos.save(reg);
            v.registar(feita, reg, true, alerta ? VigilanciaService.DOR_MODERADA : VigilanciaService.DOR_SEM,
                    VigilanciaService.MOVE_SIM, alerta);
            verificacoes.save(v);
        }
    }

    private static String snapshot(MedicacaoPlano p) {
        return "{\"medicamento\":\"%s\",\"dose\":\"%s\",\"horario\":\"%s\",\"observacoes\":%s,\"ativo\":%s}".formatted(
                esc(p.getMedicamento()), esc(p.getDose()), p.getHorario(),
                p.getObservacoes() == null ? "null" : "\"" + esc(p.getObservacoes()) + "\"", p.isAtivo());
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
