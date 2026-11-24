# Gestão do Lar — protótipo da Fase 0

Aplicação web para registos de cuidados num lar pequeno (~15 utentes), pensada para funcionárias de ação
direta com pouca experiência em tecnologia: **cada registo em, no máximo, 2 toques**.

Esta é a **Fase 0**: um protótipo para testar usabilidade com **dados fictícios**, a correr num PC Windows,
com um tablet Android (10–11", horizontal) ligado ao hotspot do PC, **sem internet**.

> **Não usar com dados reais de utentes.** Não há HTTPS (o PIN e a sessão viajam em HTTP simples) nem cópias de
> segurança. As passwords de demonstração são públicas (estão neste ficheiro).

| Vista | Endereço | Acesso |
|---|---|---|
| **Tablet** (grelha de utentes e ficha) | `/tablet` | aberta (rede local); a medicação pede número + PIN |
| **Gestão** (utentes, plano de medicação, histórico, funcionárias, acessos) | `/admin` | login (enfermeiro ou gerontólogo) |

Stack: Java 21, Spring Boot 3, Maven, PostgreSQL 16 + Flyway, Thymeleaf + htmx (servido localmente, sem CDN),
CSS próprio, Spring Security (só no `/admin`), Docker Compose.

---

## 1. Como arrancar

### Requisitos
- **Windows 10/11 com Docker Desktop** instalado e **aberto** (o ícone da baleia tem de estar estável).
- Na **primeira vez** o PC precisa de internet para descarregar as imagens Docker e as dependências Maven.
  Depois disso, tudo corre **sem internet**. Faz o primeiro arranque em casa, antes do teste.
- O JDK 21 só é necessário para correr os testes (secção 6).

### Arrancar
Na pasta do projeto (PowerShell):

```powershell
docker compose up -d --build
```

Espera uns 20–30 segundos e abre:

- Tablet: <http://localhost:8080/tablet>
- Gestão: <http://localhost:8080/admin>
- Estado da aplicação: <http://localhost:8080/actuator/health> (deve responder `{"status":"UP"}`)

Para ver o que a aplicação está a fazer: `docker compose logs -f app`.

### Arrancar com um duplo clique (computador do lar)
`Iniciar-AMS.bat` faz tudo sozinho: abre o Docker Desktop se estiver fechado, espera que fique pronto, arranca a
aplicação e abre o AMS no browser (`http://localhost:8080/tablet`). Para as funcionárias:
1. Instalação (uma vez, com internet): instalar o Docker Desktop, abrir a pasta do projeto e fazer `docker compose up -d --build` (ver acima).
2. Botão direito em `Iniciar-AMS.bat` → *Enviar para* → *Ambiente de trabalho (criar atalho)*. É esse atalho que se usa.
3. Opcional: no Docker Desktop → Definições → *Start Docker Desktop when you sign in*; e pôr o atalho na pasta de arranque do Windows (`Win+R` → `shell:startup`) para o AMS abrir quando o computador liga.
Os dados ficam guardados entre utilizações. Depois de alterar o código, é preciso `docker compose up -d --build` para a imagem apanhar as alterações.

### Parar e recomeçar
```powershell
docker compose down          # pára (os dados ficam guardados)
docker compose down -v       # pára E apaga a base de dados, incluindo as fotos (recomeça do zero)
```

### Ver a base de dados (opcional)
```powershell
docker compose --profile tools up -d adminer     # abre http://localhost:8081
# Sistema: PostgreSQL | Servidor: db | Utilizador: lar | Palavra-passe: lar | Base de dados: lar
```

---

## 2. Credenciais de demonstração

Área de gestão (`/admin`):

| Utilizador | Perfil | Palavra-passe |
|---|---|---|
| `enfermeira` | ENFERMEIRO | `demo1234` |
| `gerontologo` | GERONTOLOGO | `demo1234` |

Os dois perfis têm as mesmas permissões nesta fase. A palavra-passe de demonstração pode ser mudada com a
variável `LAR_DEMO_PASSWORD` (a acrescentar ao `docker-compose.yml`; só tem efeito na primeira criação dos utilizadores).

O tablet (`/tablet`) não tem login de gestão. As **funcionárias** entram nele com um **número + PIN de 4 dígitos**
(ver «Acesso das funcionárias»). Na demo existem 6, criadas no arranque (e **não** alteradas por «Repor dados de
demonstração»):

| Número | Nome | PIN |
|---|---|---|
| 1 | Ana Ferreira | `1111` |
| 2 | Beatriz Costa | `2222` |
| 3 | Carla Mendes | `3333` |
| 4 | Dulce Sousa | `4444` |
| 5 | Elisa Rocha | `5555` |
| 6 | Filipa Nunes | `6666` |

São PINs fáceis de propósito (só dados fictícios). Mudam-se em `/admin/funcionarios` → «Repor PIN».

---

## 3. Dados de demonstração

No perfil `demo` (o que o `docker-compose.yml` usa), no primeiro arranque a base de dados fica com:

- **15 utentes** fictícios (quartos 1 a 15), com avatares gerados com as iniciais (podes carregar fotos no `/admin`);
- **planos de medicação** variados (1 a 5 linhas por utente);
- **histórico dos últimos 7 dias**: banhos, tomas (a maioria dadas, algumas em falta) e quedas/dores;
- pelo menos **uma queda nas últimas 48 h** (quarto 4, com lesão) e **uma dor** (quarto 7), para a faixa
  vermelha aparecer;
- **tomas em atraso hoje** (por exemplo, quartos 3 e 8), para o selo laranja aparecer;
- **perfil de atenção e "Sobre mim" dos 15 utentes**, variados (mobilidades, riscos, texturas, alergias, avisos),
  revistos em datas diferentes. Os utentes com risco de queda alto têm duas versões no histórico do perfil.

Os dados são gerados **em relação à hora a que arrancas**. Por isso:

- **Medicação em atraso só aparece depois das 10:00** (09:00 + 1 h). Se fizeres o teste de manhã cedo,
  antecipa o horário da manhã no `docker-compose.yml` (descomenta a linha) e reinicia:
  ```yaml
  LAR_HORARIOS_MANHA: "06:00"
  ```
- Para voltar a gerar a demo com a hora atual, entra no `/admin` → **Utentes** → **Repor dados de
  demonstração** (apaga tudo o que foi registado nos testes). Não aparece fora do perfil `demo`.

Os horários das tomas estão em configuração (`lar.horarios.*` no `application.yml`):
Manhã 09:00, Almoço 13:00, Jantar 19:00, Deitar 22:00. Podem ser mudados por variáveis de ambiente
(`LAR_HORARIOS_ALMOCO=13:30`, etc.).

---

## 4. Ligar o tablet ao Hotspot Móvel do Windows

O PC cria uma rede Wi-Fi própria e o tablet liga-se a ela. **Não é preciso internet.**
O Windows dá ao PC o endereço fixo **192.168.137.1**, por isso o tablet abre:

> **http://192.168.137.1:8080/tablet**

### Passo a passo

**A. Ativar o Hotspot Móvel no PC**
1. **Definições → Rede e Internet → Hotspot móvel**.
2. Em *Partilhar a minha ligação à Internet a partir de*, escolhe a ligação disponível (Ethernet ou Wi-Fi).
3. Em *Editar*, define o nome da rede e uma palavra-passe (8+ caracteres) e escolhe a banda **2,4 GHz**
   (tablets antigos ligam-se melhor).
4. Liga o interruptor **Hotspot móvel**.

> **Atenção — causa mais comum de falhas:** o Windows costuma **recusar ligar o hotspot se o PC não tiver
> nenhuma ligação** (Ethernet, Wi-Fi ou dados móveis) para partilhar. Se aparecer um erro do género
> "Não é possível configurar o hotspot móvel", liga o PC a **qualquer** rede (cabo ou Wi-Fi) antes de
> ativar o hotspot. **Testa isto antes do dia.** Alternativa: liga o tablet e o PC ao hotspot de um
> telemóvel; nesse caso o IP do PC é outro (ver "Descobrir o IP do PC").

**B. Abrir a porta 8080 na firewall do Windows**

Num **PowerShell como Administrador** (botão direito no menu Iniciar → *Terminal (Administrador)*):

```powershell
New-NetFirewallRule -DisplayName "Gestao Lar 8080" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow -Profile Any
```

(`-Profile Any` é importante: a rede do hotspot costuma ser classificada como *Pública*.)
Para remover a regra depois do teste:

```powershell
Remove-NetFirewallRule -DisplayName "Gestao Lar 8080"
```

**C. Ligar o tablet**
1. No tablet: **Definições → Wi-Fi**, liga à rede do hotspot do PC.
2. **Desliga os dados móveis** do tablet. Como esta rede não tem internet, o Android pode tentar mudar
   para os dados móveis e "perder" a ligação ao PC.
3. Se o Android disser "Sem internet, ligação instável", escolhe **Manter ligação** / **Continuar ligado**.
4. Abre o Chrome e vai a `http://192.168.137.1:8080/tablet` (escreve o `http://` completo).

**D. Ecrã inteiro (opcional, recomendado)**

O Chrome não instala PWAs em HTTP; "Adicionar ao ecrã inicial" cria só um atalho, com barra de endereço.
Para esconder a barra:
1. No Chrome do tablet abre `chrome://flags`.
2. Procura `unsafely-treat-insecure-origin-as-secure`, ativa, escreve `http://192.168.137.1:8080`
   e carrega em **Relançar**.
3. Abre a página e usa **⋮ → Adicionar ao ecrã inicial → Instalar**. Passa a abrir em ecrã inteiro,
   horizontal.

### Descobrir o IP do PC
```powershell
ipconfig
```
Procura o adaptador *Wi-Fi Local* / *Ligação de Área Local\* …* do hotspot: o endereço normal é `192.168.137.1`.
Se usares outra rede, é o IPv4 do adaptador ligado a essa rede.

### Se o tablet não abre a página
1. No **PC**, `http://localhost:8080/tablet` abre? Se não, a aplicação não está a correr (`docker compose ps`).
2. No tablet, o Wi-Fi é o do hotspot (e não outra rede)? Os dados móveis estão desligados?
3. A regra de firewall existe e tem `-Profile Any`? (`Get-NetFirewallRule -DisplayName "Gestao Lar 8080"`)
4. O Docker Desktop está aberto? Sem ele, a porta 8080 não responde.
5. Usaste `http://` (e não `https://`)?

---

## 5. Como se usa

### Tablet
- **Grelha:** os 15 utentes. Uma **faixa vermelha "Ocorrência"** significa queda ou dor nas últimas 48 h;
  um **selo laranja "Medicação em atraso"** significa que há uma toma de hoje por dar mais de 1 h depois do
  horário. A grelha atualiza-se sozinha de 60 em 60 s.
- **Ficha** (toca no utente), em **duas colunas** (o ecrã é horizontal; falta altura, não largura):
  - **esquerda (~40 %)**: foto, nome, «como gosta de ser tratado», aviso de ocorrência e bloco «Atenção». Se o
    conteúdo for mais alto que o ecrã, **rola só esta coluna**;
  - **direita (~60 %)**: separadores, **os botões de ação numa grelha 3×2 (sempre visíveis, sem scroll, em qualquer ficha)**,
    confirmação, painéis e «Hoje». Os painéis longos (ex.: medicação com 5 linhas) rolam dentro da coluna.
  - **Banho** (1 toque), **Queda** → *Sem lesão / Com lesão* (2 toques), **Dor / mal-estar** → local (2 toques),
    **Medicação** → **Dado** (2 toques, com sessão iniciada).
  - **Refeição** (Etapa 8, sem PIN): 1.º toque abre o painel já na refeição certa pela hora (a última janela que já começou: pequeno-almoço 07:00, almoço 12:00, lanche 15:30, jantar 18:30, ceia 21:00; antes das 07:00, pequeno-almoço; configurável em `lar.refeicoes.inicio`); 2.º toque = *Tudo / Metade / Pouco / Recusou*. Para registar outra refeição há um toque extra nos chips. Uma refeição por tipo, utente e dia: se já existe, o botão mostra «Almoço: Metade» e um novo registo pede confirmação («Substituir o registo?») e anula o anterior. «Desfazer» como nos outros registos; se o registo substituiu outro, desfazer repõe o anterior (V10, mesma transação). No admin, anular esse registo pergunta se o anterior deve ser reativado.
  - **Peso** (Saúde, com PIN): teclado numérico próprio (1 decimal, 20–250 kg), mostra o último peso e a variação; Guardar só fica ativo com um valor válido.
  - **Alertas** no bloco Atenção (sem selos novos na grelha): «Come pouco há 3 refeições» (últimas 3 refeições registadas *Pouco*/*Recusou*) e «Perda de peso — avisar enfermagem» (> 5 % em 30 dias). Sem diagnósticos nem nomes de medicamentos.
  - **Hidratação** (sem PIN por defeito, como a refeição; segue a definição «Refeições e hidratação»): ocupa o 3.º lugar da 1.ª linha, que estava reservado ao «Humor» (Etapa 10; quando chegar precisará de outro sítio). 3 momentos por dia: **manhã** (desde as 07:00), **tarde** (12:00) e **noite** (18:00), configuráveis em `lar.hidratacao.inicio`. 1.º toque abre o painel já no momento certo pela hora; 2.º toque = nível de **0 a 3**: **0 Nada, 1 Pouco, 2 Razoável, 3 Bem** (quanto bebeu nesse momento). Um registo por momento, utente e dia: se já existe, o botão mostra «Hidratação: 2 (Razoável)» e um novo registo pede confirmação («Substituir o registo?»). «Desfazer» como nas refeições (repõe o anterior se foi uma substituição). Aparece em «Hoje» e no histórico do admin (filtro «Hidratação»). Não gera alertas.
  - **Ficha de emergência (PDF, para o INEM)**: no separador **Saúde** (com número + PIN) há o botão «Ficha de emergência (PDF)»; o mesmo botão existe no `/admin` → Utentes. Descarrega um PDF A4 com: identificação (nome, **idade** e data de nascimento, sexo, n.º de utente SNS, quarto), **contacto de emergência**, médico assistente, **alergias** (em destaque), condições de saúde ativas (com detalhe, data e notas), **medicação habitual** com a **última toma registada**, estado funcional (mobilidade, risco de queda, orientação, textura/dieta, próteses, avisos), último **peso**, **quedas e dores dos últimos 7 dias** (com notas) e um texto livre «Outras informações importantes». Fica de fora o dia a dia (banhos, refeições, hidratação, gostos, como gosta de ser tratado) e os protocolos internos do lar. O que não estiver preenchido aparece como «Não registado» (um perfil de atenção por preencher aparece como «ALERGIAS: NÃO REGISTADO», nunca como «sem alergias»). A idade, o SNS, o contacto, o médico e o texto livre preenchem-se em `/admin` → Utentes → Editar. Cada emissão fica registada (quem, de quem, quando) em `/admin/acessos`.
  - **Vigilância pós-queda** (Etapa 9): cada queda cria 4 verificações (por defeito 1 h, 4 h, 8 h e 24 h depois; `lar.vigilancia.intervalos-horas`). A ficha mostra, por baixo do aviso da queda, «Vigilância 1 de 4 — às 15:40» com o botão **Fazer** (não é um 7.º botão da grelha). Fazer pede sempre número + PIN e tem **3 perguntas** (consciente e orientado? dor? move-se como habitual?): as respostas **nunca vêm preenchidas** e **Guardar só se ativa com as 3**. É a **exceção aceite ao limite de toques**: Fazer + 3 respostas + Guardar = 5 toques (o PIN não conta). Uma resposta fora do normal (não consciente, dor moderada/forte, move-se com dificuldade/não se move) grava um alerta e mostra o bloco «Avisar a enfermagem agora». Passados 30 min da hora prevista (`lar.vigilancia.tolerancia-minutos`) sem a fazer, a grelha mostra um **selo laranja com um olho** («Vigilância em atraso»). Anular a queda (tablet ou admin) cancela as verificações por fazer. No admin, `/admin/historico` (filtro «Vigilância») mostra quem fez, as respostas, o atraso e o alerta.
  - **Separador Saúde** (3.º separador, **só com sessão de PIN**): mostra as condições ativas do utente e, para cada
    uma, «O que fazer» passo a passo. Cada abertura fica registada (quem, qual utente, quando) e aparece em
    `/admin/acessos`. É só leitura. Sem PIN, o tablet **nunca** mostra nomes de condições nem de medicamentos:
    o bloco «Atenção» só tem a célula «O que fazer se…» com avisos de atuação (ex.: «Se estiver suado, trémulo ou
    confuso, medir o açúcar…»), escritos sem o nome da doença.
- Depois de cada registo aparece a confirmação e o botão **Desfazer** durante 10 s (sem perguntas "tem a
  certeza?"). Desfazer marca o registo como anulado; deixa de contar para os indicadores.
- **Notas:** em quedas e dores, o botão **Nota** na secção **Hoje** permite acrescentar ou editar a nota a
  qualquer momento (também aparece logo a seguir ao registo).
- A ficha volta sozinha à grelha após 60 s sem toques.
- **Bloco "Atenção"** (no topo da ficha, sempre visível, sem PIN): alergias, mobilidade (e transferência com
  1 ou 2 pessoas), risco de queda (Baixo / Médio / Alto), alimentação (textura, dieta, ajuda a comer),
  comunicação (ouve mal esq./dir., óculos, prótese auditiva, prótese dentária, desorientado) e avisos
  especiais, com ícones e etiquetas curtas. No fundo mostra "Revisto em … por …".
  - **Alergias e avisos** usam um chip branco com **contorno escuro** e um ícone de losango. O **vermelho**
    fica reservado às ocorrências, e o laranja ao atraso.
  - O risco de queda gradua-se por intensidade e por texto, nunca só por cor.
  - Se o utente ainda não tem perfil, o bloco diz "Perfil de atenção ainda por preencher" (não mostra
    valores por defeito, que dariam uma falsa segurança).
- **Separadores** por baixo do bloco: **Registos** (o ecrã de sempre, aberto por defeito, por isso nenhum
  registo ganha toques) e **Sobre mim** (só leitura: "Como gosta de ser tratado" e "Gostos e temas de
  conversa"). O "como gosta de ser tratado" aparece também por baixo do nome.
- **Acesso das funcionárias (número + PIN):**
  - **Entrar** (barra superior da grelha) ou, ao tocar em **Medicação** sem sessão, abre-se o ecrã de acesso: um
    **teclado numérico próprio** (o teclado do Android nunca aparece): escreve-se o número, **OK**, e o PIN de 4
    dígitos; **ao 4.º dígito entra sozinho**. Mostra «Olá, Ana» (o nome só aparece depois do PIN certo) e segue
    para onde se ia: se vinha da medicação, volta ao **painel de medicação do mesmo utente**.
  - Qualquer erro dá sempre a mesma mensagem, **«Número ou PIN incorreto»**, exista o número ou não.
  - **Sessão:** expira **N minutos depois da última interação** (por defeito 2; `lar.sessao.minutos`, editável em `/admin` → Definições) (o refrescar da grelha, de 60 s, e o regresso
    automático à grelha não contam). **Voltar à grelha não termina a sessão**; o botão **Sair** (barra superior da
    grelha e topo da ficha) sim. Uma só sessão serve para vários utentes seguidos.
  - **Medicação exige sessão**: sem ela não se abre o painel, não se dá «Dado» (não se regista nada) e nenhum nome
    de medicamento aparece no tablet (a secção «Hoje» diz só «Medicação dada»). O selo laranja de atraso na
    grelha mantém-se sem PIN.
  - **Que registos exigem sessão** (por tipo; `lar.registo.exige-sessao.*` no `application.yml`, editável em `/admin` →
    Definições, com auditoria de quem alterou): **Banho = nunca (regra fixa, não configurável)**, **Ocorrências (queda e dor) = sim**, **Medicação = sim**
    (valores por defeito). O **banho regista sempre sem login** (guarda quem foi só se já houver sessão). Quando as
    ocorrências exigem sessão e não há sessão, tocar em Queda ou Dor/mal-estar leva ao ecrã de acesso e, depois do PIN,
    **volta ao passo seguinte do mesmo utente** (Com lesão / Sem lesão, ou o local da dor). **Nunca se regista nada automaticamente.** A medicação exige sempre sessão:
    se «toma» for desligada nas Definições, o painel continua a pedir PIN (a página di-lo).
    **A regra é aplicada no `RegistoService`** (banho, queda, dor e toma): sem sessão o serviço recusa com
    `SessaoNecessariaException` antes de tocar em dados; o `TabletController` só traduz a recusa num redirect para
    `/tablet/acesso` (HX-Redirect em htmx), por isso nenhum outro chamador consegue contornar a regra.
  - Quando há sessão, guarda-se quem fez o registo (`registo.realizado_por`), exigida ou não; sem sessão fica em branco.
  - **Desfazer** (60 s): se o tipo exige sessão, **só a mesma funcionária** (com sessão) o pode fazer; se não exige,
    qualquer pessoa. Um registo sem autor, num tipo que passou a exigir sessão, só se anula no `/admin`.
  - **Bloqueios:** 5 PINs errados bloqueiam esse número durante 5 min; 20 falhas seguidas no tablet (qualquer
    número) bloqueiam o acesso por PIN durante 15 min. A mensagem de bloqueio é igual para números que existem e
    que não existem. Tudo fica na base de dados (sobrevive a reinícios).

### Gestão (`/admin`)
- **Utentes:** criar, editar (inclui os dados da ficha de emergência: data de nascimento, sexo, n.º SNS, contacto, médico, informação livre), botão «Ficha de emergência (PDF)», desativar/reativar (os desativados aparecem a cinzento, com etiqueta
  "Desativado"); foto por upload (JPG/PNG).
  - A foto é **guardada na base de dados** (tabela `utente_foto`, `bytea`), por isso entra nas cópias da BD e
    não há volume de fotos. Antes de gravar, é **rodada segundo a orientação EXIF** (fotos de iPhone/telemóvel
    tiradas na vertical ficam direitas), recortada em quadrado e reduzida a 400 px (JPEG).
  - É servida com `ETag` (o tablet só volta a descarregar se a foto mudar). Sem foto, mostra-se um avatar com
    as iniciais.
- **Perfil** (botão na lista de utentes): formulário do bloco "Atenção" e do "Sobre mim" (campos
  estruturados, com listas fechadas). Cada gravação marca o perfil como **revisto em / por** (é o que o tablet
  mostra) e guarda uma **versão completa** na auditoria (`utente_perfil_auditoria`); a página mostra o histórico
  com o que mudou em cada versão (ex.: "Risco de queda: Médio → Alto").
- **Funcionárias** (`/admin/funcionarios`): criar, editar o nome, desativar/reativar e **repor o PIN**. O
  **número** (1–9999) é atribuído sem zeros à esquerda, é proposto o seguinte ao maior já usado (incluindo
  desativadas; pode ser alterado) e **nunca é reutilizado** nem editável depois. O PIN é guardado só como hash
  BCrypt; ao criar ou repor com o campo em branco gera-se um PIN que é **mostrado uma só vez**; também se pode
  escrever à mão. Não há fotos.
- **Definições** (`/admin/definicoes`): que registos exigem sessão (Ocorrências, Medicação) e a duração da sessão
  (1–60 min). O valor guardado prevalece sobre o `application.yml`; cada alteração guarda quem, quando e o valor anterior.
- **Acessos** (`/admin/acessos`): tentativas de PIN falhadas (data, número tentado, motivo), bloqueios (em vigor
  / terminados) e **acessos ao separador Saúde** (quem, utente, quando).
- **Protocolos** (`/admin/protocolos`): as 11 condições (diabetes com insulina / oral, anticoagulação, AVC, enfarte,
  insuficiência cardíaca, epilepsia, demência, DPOC com oxigénio, pacemaker, outra). Para cada uma edita-se o
  nome, o protocolo «O que fazer» (um passo por linha) e o **aviso de atuação** (não pode levar o nome da condição).
  O catálogo é **editável**: a enfermagem pode **criar**, renomear e **desativar** condições (uma desativada deixa de
  se poder atribuir, mas o que já está atribuído mantém-se) e **apagar** só as que nenhum utente tem.
  - **Validação:** cada protocolo tem *validado por / em* (`validado_por`, `validado_em`). Só o perfil **ENFERMEIRO**
    valida. **Qualquer edição (ou uma condição nova) volta a pô-lo «por validar».** No tablet, um protocolo por
    validar mostra «Protocolo por validar pela enfermagem» em vez do texto e o seu aviso de atuação **não aparece** no
    bloco Atenção.
  - Os textos de partida estão marcados **[DEMO]** (genéricos, não são clínicos) e a V8 deixa-os todos por validar; a demo
    traz 3 validados pela enfermeira de demonstração (anticoagulado, diabetes com insulina, demência).
  - **Antes de qualquer uso real, a enfermagem tem de escrever e validar os protocolos.**
- **Condições do utente** (botão «Condições» na lista de utentes): adicionar, editar (detalhe, desde, notas) e
  desativar/reativar; guarda quem e quando. Só uma condição ativa de cada tipo (exceto «Outra», que exige detalhe).
- **Plano de medicação:** por utente (medicamento, dose, horário, observações/alergias). Cada alteração
  guarda **quem e quando** e mantém as versões anteriores (tabela `medicacao_plano_auditoria`), visíveis na
  página do plano.
- **Histórico:** filtra por utente e dia; mostra banhos, ocorrências e tomas, com a coluna **Quem** (nome e número
  da funcionária que tinha sessão ao registar; «—» se não havia). Registos anulados aparecem
  **riscados**, com quem anulou e quando ("Tablet (sem identificação)" quando foi o "Desfazer" do tablet).
  Aqui é possível **anular** qualquer registo, sem limite de tempo.
  - **Histórico de notas:** cada vez que a nota de uma queda/dor é acrescentada, editada ou removida, guarda-se
    uma versão com a hora (`registo_nota_historico`). No histórico, "Versões da nota (n)" mostra todas, da mais
    antiga à mais recente, incluindo "(nota removida)" e também nas ocorrências anuladas.

### Regras de negócio
| Regra | Detalhe |
|---|---|
| Banho | no máximo 1 registo ativo por utente por dia |
| Toma | no máximo 1 registo ativo por linha de plano (medicamento + horário) por dia |
| Atraso | agora **>** horário + 60 min e sem toma ativa hoje; uma linha criada (ou com horário mudado) depois do horário de hoje não conta como atraso |
| Ocorrência recente | queda ou dor com menos de 48 h (limite inclusivo) |
| Anulados | não contam para nenhum indicador |
| Desfazer no tablet | só nos primeiros 60 s (o botão fica visível 10 s) |
| Refeição | 1 registo ativo por utente, dia e tipo de refeição (índice único parcial); substituir anula o anterior |
| Hidratação | 1 registo ativo por utente, dia e momento (manhã, tarde, noite; índice único parcial); nível 0–3 (0 Nada, 1 Pouco, 2 Razoável, 3 Bem); substituir anula o anterior |
| Peso | 20–250 kg, 1 casa decimal; alerta se a perda > 5 % nos últimos 30 dias (`lar.peso`) |
| Exige sessão | `lar.registo.exige-sessao` (ou Definições): ocorrência, toma, **refeição (por defeito não)** e **peso (por defeito sim)**; aplicado em `RegistoService` |
| Vigilância | 4 verificações por queda (+1/4/8/24 h); atraso = hora prevista + 30 min (estrito); exige sempre sessão de PIN; respostas obrigatórias |
| Dia | dia civil em `Europe/Lisbon` |

As duas regras de unicidade estão garantidas **na própria base de dados** (índices únicos parciais), por isso
aguentam toques duplos e dois tablets ao mesmo tempo.

---

### Aspeto visual
Direção "Acolhedora": fundo creme, azul-marinho como cor principal, tipo **Atkinson Hyperlegible** (ficheiros
`woff2` locais em `static/fonts/`, licença OFL incluída; sem pedidos externos), cartões arredondados com sombra
suave, ícones SVG com o mesmo traço. As cores têm significado fixo e não se usam para decorar:
**vermelho** = ocorrência, **laranja** = atraso, **verde** = feito. Estados da medicação: *por dar*, *em atraso*,
*dada*; utentes e linhas *desativados* aparecem a cinzento com etiqueta escrita (nunca só por cor).
O nome do lar na barra superior configura-se com `LAR_NOME` (por defeito "AMS").
Os nomes na grelha podem ocupar 2 linhas, mas nunca são cortados nem ficam abaixo de 21 px.
Capturas: `screenshots/final/` (aspeto geral) e `screenshots/etapa5/` (regenerar com `.\mvnw.cmd test "-Dtest=CapturasEsteticasTest" "-Dcapturas=true"`).
screenshots/etapa5b/ capturas da ficha em duas colunas (incluindo um pior caso artificial com a coluna esquerda a rolar)
screenshots/etapa6/  capturas do acesso por número + PIN, da sessão e do admin de funcionárias e acessos
screenshots/etapa7/  capturas do separador Saúde (pede PIN, protocolos), do admin de protocolos, condições e acessos; `09`–`15` mostram protocolo validado vs por validar
screenshots/etapa8/  capturas da Etapa 8: grelha 3×2, refeição (quantidade, trocar de refeição, confirmar substituição), alertas, peso com teclado, gráfico no admin, filtro por tipo e Definições
screenshots/etapa9/  capturas da Etapa 9: aviso e botão Fazer, as 3 perguntas (Guardar desativado → ativo), «Avisar a enfermagem agora», selo laranja na grelha, histórico no admin

---

## 6. Testes

```powershell
.\mvnw.cmd test
```

Requisitos: **JDK 21** (Temurin) e **Docker Desktop aberto** (os testes de integração usam um PostgreSQL
real num contentor, via Testcontainers).

Na **primeira execução** o Playwright descarrega o Chromium (cerca de 150 MB), por isso precisa de internet
nessa altura; depois funciona offline. Para correr só os testes sem interface: `.\mvnw.cmd test "-Dtest=!*UiTest"`.

> Se tiveres a variável `JAVA_HOME` a apontar para outro JDK (por exemplo o do Android Studio), define-a para
> o JDK 21 antes: `$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.12"` (ajusta o caminho).

| Ficheiro | O que cobre |
|---|---|
| `RegrasTest` | atraso (limite estrito de 60 min, tomas dadas, linhas retiradas, plano novo, verão/fuso) e ocorrência recente (47h59 / 48h / 48h01, janela configurável) — sem base de dados |
| `RegistoServiceTest` | unicidade de banho e toma (inclui toques simultâneos), repor depois de desfazer, desfazer (60 s, duplo, sem autor), anulação no admin (quem/quando), notas |
| `IndicadoresTest` | grelha: ocorrência e atraso a partir de dados reais; registos anulados não contam; toma desfeita volta a "em atraso" |
| `SegurancaTest` | `/tablet` aberto e sem CSRF; `/admin` exige login e CSRF |
| `FichaUiTest` | **interface num Chromium real (Playwright, 1280x800)**: grelha → ficha → banho → desfazer; queda → adicionar nota → guardar (caixa grande, com o cursor lá dentro); a nota não desaparece aos 10 s; nota pela secção "Hoje" (adicionar e editar); medicação → dado → desfazer (volta a "em atraso") |
| `GrelhaUiTest` | 15 utentes em 5×3 sem scroll; faixa de ocorrência; a grelha atualiza-se sozinha ao fim de 60 s (espera o ciclo real) |
| `FotoServiceTest` | processamento de imagem: orientação EXIF 1–8 (incl. a 6 dos iPhone), recorte quadrado, PNG transparente, ficheiro inválido, ETag |
| `FotoPersistenciaTest` | foto em `bytea`, `ETag` + `304`, trocar/remover foto, apagar utente apaga a foto, upload pelo formulário do admin |
| `NotaHistoricoTest` | versões das notas (acrescentar, editar, remover, texto igual não duplica) e a sua apresentação no histórico do admin |
| `PerfilServiceTest` | perfil de atenção: gravar, revisto em/por, versões e diferenças, textos vazios, restrições da BD, cascata ao apagar o utente, perfis da demo |
| `FichaAtencaoTest` | o que a ficha mostra em «Atenção» e «Sobre mim» (sem perfil, completo, transferência 1/2 pessoas) |
| `AdminPerfilTest` | formulário do admin: exige login, valida, grava, mostra o histórico, e o tablet reflete o que foi gravado |
| `AtencaoUiTest` | **«Atenção» e os 4 botões de ação visíveis sem scroll a 1280×800 nas 15 fichas**; alergias com contorno escuro (sem vermelho/laranja); «Sobre mim» só leitura; registar continua a custar os mesmos toques |
| `AcessibilidadeUiTest` | **axe-core** (WCAG 2.0/2.1 A e AA, incluindo contraste) em todos os ecrãs do tablet e do admin, incluindo «Atenção», «Sobre mim», o acesso (número, PIN, erro, «Olá»), a barra com sessão e as páginas de funcionárias e acessos |
| `AspetoUiTest` | grelha 5×3 sem scroll, nomes longos em 2 linhas sem corte e ≥ 21 px, foto do cartão grande, botões ≥ 2 cm |
| `FuncionarioServiceTest` | números (1–9999, único, nunca reutilizado, próximo = máximo + 1 incluindo desativadas), PIN de 4 dígitos em BCrypt, gerar / repor / desativar |
| `AcessoServiceTest` | PIN certo e errado, bloqueio por número (5 falhas, 5 min), bloqueio global (20 falhas, 15 min), contagem desde o último sucesso / fim do bloqueio, números inexistentes tratados como os que existem |
| `AcessoMvcTest` | mensagem idêntica para número inexistente e PIN errado (e para bloqueios), expiração aos 2 min com o relógio simulado, o refresco da grelha não renova, medicação só com sessão (HX-Redirect), `realizado_por`, nenhum nome de medicamento no tablet sem sessão |
| `AdminFuncionarioTest` | gestão das funcionárias, PIN mostrado uma só vez, `/admin/acessos`, coluna «Quem» do histórico |
| `DefinicoesServiceTest` | valores do yml por defeito, o guardado prevalece, «toma» desligada sem efeito, auditoria (quem, anterior → novo), validação 1–60 min |
| `ExigenciaSessaoMvcTest` | **todas as combinações tipo × exige × sessão** (banho, queda, dor, toma), passo seguinte após o PIN, «Desfazer» só da mesma funcionária, duração da sessão configurável |
| `AdminDefinicoesTest` | página Definições: login, guardar, auditoria, caixas por marcar = não, aviso da medicação, validação |
| `AcessoUiTest` | fluxo completo pelo teclado do ecrã (número → OK → PIN → entra ao 4.º dígito), sem campos de texto, teclas ≥ 2 cm, medicação → acesso → volta ao mesmo painel, vários utentes com um login, expiração, Sair |

| `SaudeServiceTest` | condições e protocolos: validações (duplicadas, «Outra» com detalhe, data futura), avisos de atuação sem nomes de doenças, passos, acessos |
| `SaudeMvcTest` | Saúde só com PIN (redirect/HX-Redirect e regresso ao separador), cada abertura registada, **nenhum nome de condição, protocolo, nota ou medicamento no tablet sem sessão** |
| `AdminSaudeTest` | `/admin/protocolos`, condições do utente, acessos à Saúde; login, CSRF, 404 entre utentes |
| `SaudeUiTest` | fluxo completo no Chromium: ficha → Saúde → PIN → protocolos → Registos → Saúde → sessão expirada → PIN |
| `ValidacaoProtocolosTest` | catálogo editável (criar, renomear, desativar, apagar só sem utentes) e validação (só enfermagem, editar tira a validação, tablet esconde texto e aviso, admin, seed de demo) |
| `RefeicaoServiceTest` | janela da refeição (06:00, 11:00, 15:00, 23:45, fronteiras), uma por tipo e dia, substituir, «come pouco há 3 refeições» |
| `PesoServiceTest` | validação, sessão, variação, alerta (> 5 % em 30 dias, fronteiras), gráfico SVG válido e sem recursos externos |
| `RefeicaoPesoMvcTest` | rotas, confirmação de substituição, exigência de sessão por tipo, Hoje e filtro por tipo no admin |
| `RefeicaoPesoUiTest` | **grelha 3×2 visível sem scroll nas 15 fichas, botões ≥ 2 cm, contagem de toques por fluxo (refeição 2, trocar 3, substituir 3, peso 2)**, teclado do peso, alertas, desfazer |
| `VigilanciaServiceTest` | os 4 pontos nascem com a queda, intervalos da configuração, atraso aos 30 min (29:59 / 30:00 / 30:01), anular a queda cancela as pendentes, respostas obrigatórias, alerta, PIN obrigatório, descrição do histórico |
| `VigilanciaMvcTest` | aviso sem PIN, «Fazer» pede PIN e volta ao painel, perguntas sem nada preenchido, resposta incompleta não grava, bloco de alerta, selo na grelha, histórico e filtro no admin |
| `VigilanciaUiTest` | **Fazer → 3 respostas → Guardar = 5 toques**, Guardar só com as 3, botões ≥ 2 cm, alerta, selo laranja com ícone, queda anulada tira o aviso |

Total: **478 testes** (mais 7, `CapturasEsteticasTest`, que só correm com `-Dcapturas=true`). Os de interface demoram
cerca de 3 minutos porque esperam temporizadores reais de 10 s e 60 s.
O `axe.min.js` está em `src/test/resources/axe/` e só é usado nos testes (a aplicação não tem dependências externas).

---

## 7. Estrutura do projeto

```
src/main/java/pt/lar/
  config/        Segurança, relógio (Europe/Lisbon), propriedades, arranque da demo
  controllers/   TabletController, FotoController, Admin*Controller
  models/        Entidades JPA e enumerações
  repositories/  Spring Data JPA
  services/      Regras, RegistoService, GrelhaService, FichaService, PlanoService, …
src/main/resources/
  db/migration/  V1__schema.sql, V2__fotos_e_historico_de_notas.sql, V3__perfil_de_atencao.sql, V4__funcionarios_e_acesso_por_pin.sql, V5__definicoes.sql, V6__banho_nunca_exige_sessao.sql, V7__condicoes_e_saude.sql, V8__catalogo_editavel_e_validacao_de_protocolos.sql, V9__refeicoes_e_peso.sql, V10__substituicao_de_refeicao.sql, V11__vigilancia_pos_queda.sql (Flyway)
  templates/     tablet/*, admin/*  (Thymeleaf)
  static/        css/, fonts/ (Atkinson Hyperlegible), js/htmx.min.js (local), manifest.json, icons/
screenshots/final/   capturas 1280×800 do aspeto final (grelha, ficha, admin)
screenshots/etapa5/  capturas da Etapa 5: «Atenção», alergias, «Sobre mim» e formulário do perfil
```
(Etapa 7: `SaudeService`, `AdminSaudeController`, entidades `CondicaoProtocolo`, `UtenteCondicao`, `AcessoSaude`.)
(Etapa 8: `RefeicaoService`, `PesoService`, `PesoGraficoSvg`, `AdminPesoController`, entidade `Peso`, `static/js/teclado-num.js`.)
(Etapa 9: `VigilanciaService`, entidades `VigilanciaPlano` e `VigilanciaVerificacao`, `static/js/vigilancia.js`.)

## 8. Fora de âmbito (de propósito)

Refeições, hidratação, eliminação, posicionamentos, escalas, portal de
familiares, notificações, funcionamento offline / service worker, relatórios PDF.

## 9. Limitações conhecidas

- HTTP simples (sem HTTPS): adequado só para a rede local do teste, com dados fictícios.
- Não há cópias de segurança; `docker compose down -v` apaga tudo.
- Fotos: apenas JPG/PNG (HEIC/WebP não são aceites). A orientação EXIF é aplicada no upload.
- Atualizar de uma versão anterior: a migração V2 passa as fotos para a base de dados, mas **não importa** as
  fotos que estavam no antigo volume `dados-fotos` (só havia dados fictícios): volta a carregá-las no `/admin`.
- O bloco «Atenção» e o «Sobre mim» são visíveis a qualquer pessoa com acesso ao tablet (sem PIN, por decisão
  de desenho): usar só dados fictícios.
- Atkinson Hyperlegible desenha o zero com uma barra ("0̸"), de propósito, para não se confundir com a letra O.
- Fora do perfil `demo` não é criado nenhum utilizador de gestão; numa instalação real seria preciso criar o
  primeiro (a gestão de utilizadores de gestão não existe nesta fase).
- A grelha está pensada para **15 utentes** (5×3 sem scroll); com mais, a página passa a ter scroll.
- **Acesso por PIN (limitações do protótipo, aceites):** um PIN de 4 dígitos é fraco; o bloqueio global (20 falhas
  seguidas) permite que alguém bloqueie o acesso por PIN a todas durante 15 min (o tablet continua a servir banho,
  queda e dor); a sessão viaja em HTTP simples, na rede local do hotspot. Sem HTTPS, a sessão e o PIN podem ser
  vistos por quem estiver na mesma rede.
- O número de uma funcionária nunca é reutilizado nem alterado; para «corrigir» um número, desativa-se e cria-se outra.
- Se a sessão expira com o painel de medicação aberto, o primeiro toque seguinte leva ao ecrã de acesso (e depois
  volta ao painel); nada é registado até haver sessão.
- Numa ficha com perfil muito carregado, a coluna esquerda rola; o essencial (alergias, mobilidade, risco) fica no
  topo e os botões de ação nunca saem do ecrã.