package pt.lar.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import pt.lar.services.SessaoService;

/**
 * Em cada pedido ao tablet: confirma se a sessão de PIN ainda é válida e põe a funcionária no pedido.
 * Só renova os 2 minutos nos pedidos feitos por uma pessoa: o refresco da grelha (60 s) e o regresso
 * automático à grelha ({@code ?auto=1}) não contam.
 */
@Component
public class SessaoInterceptor implements HandlerInterceptor {

    private final SessaoService sessao;

    public SessaoInterceptor(SessaoService sessao) {
        this.sessao = sessao;
    }

    @Override
    public boolean preHandle(HttpServletRequest pedido, HttpServletResponse resposta, Object handler) {
        boolean automatico = "/tablet/grelha".equals(pedido.getRequestURI()) || pedido.getParameter("auto") != null;
        sessao.resolver(pedido, !automatico)
                .ifPresent(f -> pedido.setAttribute(SessaoService.ATRIBUTO_PEDIDO, f));
        return true;
    }
}
