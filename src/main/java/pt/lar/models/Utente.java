package pt.lar.models;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "utente")
public class Utente {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String nome;
    @Column(nullable = false)
    private String quarto;
    @Column(nullable = false)
    private boolean ativo = true;

    // Identificação para a ficha de emergência (INEM). Todos opcionais.
    @Column(name = "data_nascimento")
    private LocalDate dataNascimento;
    @Enumerated(EnumType.STRING)
    private Sexo sexo;
    @Column(name = "numero_utente_sns")
    private String numeroUtenteSns;
    @Column(name = "contacto_nome")
    private String contactoNome;
    @Column(name = "contacto_parentesco")
    private String contactoParentesco;
    @Column(name = "contacto_telefone")
    private String contactoTelefone;
    @Column(name = "medico_assistente")
    private String medicoAssistente;
    @Column(name = "info_emergencia")
    private String infoEmergencia;

    protected Utente() {}

    public Utente(String nome, String quarto) {
        this.nome = nome;
        this.quarto = quarto;
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getQuarto() { return quarto; }
    public void setQuarto(String quarto) { this.quarto = quarto; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    public LocalDate getDataNascimento() { return dataNascimento; }
    public void setDataNascimento(LocalDate dataNascimento) { this.dataNascimento = dataNascimento; }
    public Sexo getSexo() { return sexo; }
    public void setSexo(Sexo sexo) { this.sexo = sexo; }
    public String getNumeroUtenteSns() { return numeroUtenteSns; }
    public void setNumeroUtenteSns(String numeroUtenteSns) { this.numeroUtenteSns = numeroUtenteSns; }
    public String getContactoNome() { return contactoNome; }
    public void setContactoNome(String contactoNome) { this.contactoNome = contactoNome; }
    public String getContactoParentesco() { return contactoParentesco; }
    public void setContactoParentesco(String contactoParentesco) { this.contactoParentesco = contactoParentesco; }
    public String getContactoTelefone() { return contactoTelefone; }
    public void setContactoTelefone(String contactoTelefone) { this.contactoTelefone = contactoTelefone; }
    public String getMedicoAssistente() { return medicoAssistente; }
    public void setMedicoAssistente(String medicoAssistente) { this.medicoAssistente = medicoAssistente; }
    public String getInfoEmergencia() { return infoEmergencia; }
    public void setInfoEmergencia(String infoEmergencia) { this.infoEmergencia = infoEmergencia; }
}
