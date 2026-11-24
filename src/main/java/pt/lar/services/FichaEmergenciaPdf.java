package pt.lar.services;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.HeaderFooter;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.springframework.stereotype.Component;
import pt.lar.services.FichaEmergenciaService.FichaEmergencia;
import pt.lar.services.FichaEmergenciaService.LinhaCondicao;
import pt.lar.services.FichaEmergenciaService.LinhaMedicacao;
import pt.lar.services.FichaEmergenciaService.LinhaOcorrencia;
import pt.lar.services.FichaEmergenciaService.Par;

/**
 * Desenha a ficha de emergência em A4 (PDF). Usa as fontes base do PDF (Helvetica), que cobrem o português, por isso
 * não precisa de ficheiros de fontes e funciona sem internet. Tudo o que falta aparece como «Não registado».
 */
@Component
public class FichaEmergenciaPdf {

    private static final Color AZUL = new Color(0x0E, 0x2B, 0x4A);
    private static final Color AZUL_CLARO = new Color(0xE3, 0xEC, 0xF6);
    private static final Color VERMELHO = new Color(0xB0, 0x1E, 0x1E);
    private static final Color CINZA = new Color(0x55, 0x5B, 0x63);
    private static final Color LINHA = new Color(0xC9, 0xCF, 0xD6);

    private static final Font TITULO = FontFactory.getFont(FontFactory.HELVETICA, 20, Font.BOLD, AZUL);
    private static final Font SUBTITULO = FontFactory.getFont(FontFactory.HELVETICA, 9.5f, Font.NORMAL, CINZA);
    private static final Font SECAO = FontFactory.getFont(FontFactory.HELVETICA, 11, Font.BOLD, AZUL);
    private static final Font ROTULO = FontFactory.getFont(FontFactory.HELVETICA, 9.5f, Font.BOLD, CINZA);
    private static final Font TEXTO = FontFactory.getFont(FontFactory.HELVETICA, 10.5f, Font.NORMAL, Color.BLACK);
    private static final Font NEGRITO = FontFactory.getFont(FontFactory.HELVETICA, 10.5f, Font.BOLD, Color.BLACK);
    private static final Font NOME = FontFactory.getFont(FontFactory.HELVETICA, 15, Font.BOLD, Color.BLACK);
    private static final Font PEQUENO = FontFactory.getFont(FontFactory.HELVETICA, 9, Font.NORMAL, CINZA);
    private static final Font FALTA = FontFactory.getFont(FontFactory.HELVETICA, 10.5f, Font.ITALIC, CINZA);
    private static final Font ALERTA = FontFactory.getFont(FontFactory.HELVETICA, 12, Font.BOLD, VERMELHO);

    public byte[] gerar(FichaEmergencia f) {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 34, 44);
        try {
            PdfWriter.getInstance(doc, saida);
            doc.addTitle("Ficha de emergência — " + f.nome());
            doc.addCreator("Gestão do Lar");
            // em todas as páginas: o nome do utente e o número da página (se as folhas se separarem, não se perdem)
            HeaderFooter rodape = new HeaderFooter(new Phrase("Ficha de emergência — " + f.nome() + " — página ", PEQUENO), true);
            rodape.setAlignment(Element.ALIGN_CENTER);
            rodape.setBorder(Rectangle.TOP);
            doc.setFooter(rodape);
            doc.open();

            cabecalho(doc, f);
            identificacao(doc, f);
            alergias(doc, f);
            condicoes(doc, f.condicoes());
            medicacao(doc, f.medicacao());
            estadoFuncional(doc, f);
            peso(doc, f);
            ocorrencias(doc, f.ocorrencias());
            if (naoVazio(f.infoEmergencia())) {
                secao(doc, "OUTRAS INFORMAÇÕES IMPORTANTES");
                doc.add(new Paragraph(f.infoEmergencia(), TEXTO));
            }
            Paragraph aviso = new Paragraph("Documento gerado pelo sistema de gestão do lar a partir dos registos da equipa, "
                    + "à data e hora indicadas. Confirmar com o utente, a família e o registo clínico. "
                    + "Não substitui um relatório médico.", PEQUENO);
            aviso.setSpacingBefore(14);
            doc.add(aviso);
        } catch (DocumentException e) {
            throw new IllegalStateException("Não foi possível gerar a ficha de emergência", e);
        } finally {
            if (doc.isOpen()) doc.close();
        }
        return saida.toByteArray();
    }

    // ---- Blocos ---------------------------------------------------------------------------------------------

    private void cabecalho(Document doc, FichaEmergencia f) throws DocumentException {
        doc.add(new Paragraph("FICHA DE EMERGÊNCIA", TITULO));
        Paragraph sub = new Paragraph(f.lar() + "  ·  Emitida em " + f.emitidaEm() + " por " + f.emitidaPor(), SUBTITULO);
        sub.setSpacingAfter(8);
        doc.add(sub);
    }

    private void identificacao(Document doc, FichaEmergencia f) throws DocumentException {
        PdfPTable t = tabela(new float[] {1.5f, 3f, 1.5f, 2.2f});
        t.addCell(rotulo("Nome"));
        PdfPCell nome = celula(new Phrase(f.nome(), NOME));
        nome.setColspan(3);
        t.addCell(nome);

        t.addCell(rotulo("Idade"));
        t.addCell(celula(f.idade() == null ? falta() : new Phrase(f.idade()
                + (f.dataNascimento() == null ? "" : "  (nasc. " + f.dataNascimento() + ")"), NEGRITO)));
        t.addCell(rotulo("Sexo"));
        t.addCell(valor(f.sexo()));

        t.addCell(rotulo("N.º utente SNS"));
        t.addCell(valor(f.numeroUtenteSns()));
        t.addCell(rotulo("Quarto (lar)"));
        t.addCell(valor(f.quarto()));

        t.addCell(rotulo("Contacto de emergência"));
        PdfPCell contacto = celula(contactoTexto(f));
        contacto.setColspan(3);
        t.addCell(contacto);

        t.addCell(rotulo("Médico assistente"));
        PdfPCell medico = valor(f.medicoAssistente());
        medico.setColspan(3);
        t.addCell(medico);
        doc.add(t);
    }

    private Phrase contactoTexto(FichaEmergencia f) {
        if (!naoVazio(f.contactoNome()) && !naoVazio(f.contactoTelefone())) return falta();
        StringBuilder s = new StringBuilder();
        if (naoVazio(f.contactoNome())) s.append(f.contactoNome());
        if (naoVazio(f.contactoParentesco())) s.append(" (").append(f.contactoParentesco()).append(")");
        if (naoVazio(f.contactoTelefone())) s.append(s.length() > 0 ? " — " : "").append("tel. ").append(f.contactoTelefone());
        return new Phrase(s.toString(), NEGRITO);
    }

    /** O bloco mais importante: moldura vermelha. Perfil por preencher é dito às claras, não é «sem alergias». */
    private void alergias(Document doc, FichaEmergencia f) throws DocumentException {
        String texto;
        if (!f.perfilPreenchido()) texto = "NÃO REGISTADO — o perfil de atenção deste utente ainda não foi preenchido.";
        else if (naoVazio(f.alergias())) texto = f.alergias();
        else texto = "Nenhuma alergia registada no lar.";
        PdfPTable t = tabela(new float[] {1});
        t.setSpacingBefore(8);
        PdfPCell c = new PdfPCell(new Phrase("ALERGIAS:  " + texto, ALERTA));
        c.setBorderColor(VERMELHO);
        c.setBorderWidth(2f);
        c.setPadding(7);
        t.addCell(c);
        doc.add(t);
    }

    private void condicoes(Document doc, List<LinhaCondicao> condicoes) throws DocumentException {
        secao(doc, "CONDIÇÕES DE SAÚDE");
        if (condicoes.isEmpty()) {
            doc.add(new Paragraph("Nenhuma condição de saúde registada.", FALTA));
            return;
        }
        for (LinhaCondicao c : condicoes) {
            Paragraph p = new Paragraph();
            p.add(new com.lowagie.text.Chunk(c.nome(), NEGRITO));
            StringBuilder extra = new StringBuilder();
            if (naoVazio(c.detalhe())) extra.append(" — ").append(c.detalhe());
            if (naoVazio(c.desde())) extra.append("  (desde ").append(c.desde()).append(")");
            p.add(new com.lowagie.text.Chunk(extra.toString(), TEXTO));
            p.setSpacingAfter(naoVazio(c.notas()) ? 0 : 3);
            doc.add(p);
            if (naoVazio(c.notas())) {
                Paragraph n = new Paragraph(c.notas(), PEQUENO);
                n.setIndentationLeft(12);
                n.setSpacingAfter(3);
                doc.add(n);
            }
        }
    }

    private void medicacao(Document doc, List<LinhaMedicacao> medicacao) throws DocumentException {
        secao(doc, "MEDICAÇÃO HABITUAL");
        if (medicacao.isEmpty()) {
            doc.add(new Paragraph("Sem medicação prescrita registada.", FALTA));
            return;
        }
        PdfPTable t = tabela(new float[] {3.2f, 1.6f, 2f, 2.6f});
        for (String h : new String[] {"Medicamento", "Dose", "Horário", "Última toma registada"}) {
            PdfPCell c = celula(new Phrase(h, ROTULO));
            c.setBackgroundColor(AZUL_CLARO);
            t.addCell(c);
        }
        for (LinhaMedicacao m : medicacao) {
            t.addCell(celula(new Phrase(m.medicamento(), NEGRITO)));
            t.addCell(celula(new Phrase(m.dose(), TEXTO)));
            t.addCell(celula(new Phrase(m.horario(), TEXTO)));
            t.addCell(celula(m.ultimaToma() == null ? new Phrase("nenhuma registada", FALTA) : new Phrase(m.ultimaToma(), TEXTO)));
            if (naoVazio(m.observacoes())) {
                PdfPCell o = celula(new Phrase("Obs.: " + m.observacoes(), PEQUENO));
                o.setColspan(4);
                t.addCell(o);
            }
        }
        doc.add(t);
    }

    private void estadoFuncional(Document doc, FichaEmergencia f) throws DocumentException {
        secao(doc, "ESTADO FUNCIONAL E COMUNICAÇÃO");
        if (f.estadoFuncional().isEmpty()) {
            doc.add(new Paragraph("Perfil de atenção por preencher: mobilidade, orientação e comunicação não registadas.", FALTA));
            return;
        }
        PdfPTable t = tabela(new float[] {2f, 6f});
        for (Par p : f.estadoFuncional()) {
            t.addCell(rotulo(p.rotulo()));
            t.addCell(valor(p.valor()));
        }
        doc.add(t);
    }

    private void peso(Document doc, FichaEmergencia f) throws DocumentException {
        secao(doc, "PESO");
        doc.add(new Paragraph(naoVazio(f.peso()) ? f.peso() : FichaEmergenciaService.NAO_REGISTADO,
                naoVazio(f.peso()) ? TEXTO : FALTA));
    }

    private void ocorrencias(Document doc, List<LinhaOcorrencia> ocorrencias) throws DocumentException {
        secao(doc, "QUEDAS E DORES NOS ÚLTIMOS " + FichaEmergenciaService.DIAS_OCORRENCIAS + " DIAS");
        if (ocorrencias.isEmpty()) {
            doc.add(new Paragraph("Nenhuma queda ou dor registada.", FALTA));
            return;
        }
        for (LinhaOcorrencia o : ocorrencias) {
            Paragraph p = new Paragraph();
            p.add(new com.lowagie.text.Chunk(o.quando() + "  ", PEQUENO));
            p.add(new com.lowagie.text.Chunk(o.texto(), NEGRITO));
            if (naoVazio(o.nota())) p.add(new com.lowagie.text.Chunk(" — " + o.nota(), TEXTO));
            p.setSpacingAfter(3);
            doc.add(p);
        }
    }

    // ---- Auxiliares -----------------------------------------------------------------------------------------

    private void secao(Document doc, String titulo) throws DocumentException {
        PdfPTable t = tabela(new float[] {1});
        t.setSpacingBefore(10);
        t.setSpacingAfter(3);
        PdfPCell c = new PdfPCell(new Phrase(titulo, SECAO));
        c.setBackgroundColor(AZUL_CLARO);
        c.setBorder(Rectangle.NO_BORDER);
        c.setPadding(4);
        t.addCell(c);
        t.setKeepTogether(true);
        doc.add(t);
    }

    private static PdfPTable tabela(float[] larguras) {
        PdfPTable t = new PdfPTable(larguras);
        t.setWidthPercentage(100);
        return t;
    }

    private static PdfPCell celula(Phrase p) {
        PdfPCell c = new PdfPCell(p);
        c.setBorderColor(LINHA);
        c.setBorderWidth(.6f);
        c.setPadding(4);
        return c;
    }

    private static PdfPCell rotulo(String texto) {
        return celula(new Phrase(texto, ROTULO));
    }

    /** O valor, ou «Não registado» em cinzento itálico. */
    private static PdfPCell valor(String texto) {
        return celula(naoVazio(texto) ? new Phrase(texto, NEGRITO) : falta());
    }

    private static Phrase falta() {
        return new Phrase(FichaEmergenciaService.NAO_REGISTADO, FALTA);
    }

    private static boolean naoVazio(String s) {
        return s != null && !s.isBlank();
    }
}
