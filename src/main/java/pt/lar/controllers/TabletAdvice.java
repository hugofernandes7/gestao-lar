package pt.lar.controllers;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import pt.lar.models.Funcionario;
import pt.lar.services.SessaoService;

/** Disponibiliza às páginas do tablet a funcionária com sessão de PIN ({@code sessao}), para a barra superior. */
@ControllerAdvice(assignableTypes = {TabletController.class, AcessoController.class})
public class TabletAdvice {

    @ModelAttribute("sessao")
    Funcionario sessao(HttpServletRequest pedido) {
        return (Funcionario) pedido.getAttribute(SessaoService.ATRIBUTO_PEDIDO);
    }
}
