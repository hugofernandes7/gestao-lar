package pt.lar.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final SessaoInterceptor sessaoInterceptor;

    public WebConfig(SessaoInterceptor sessaoInterceptor) {
        this.sessaoInterceptor = sessaoInterceptor;
    }

    /** O mesmo URL devolve página inteira ou fragmento (htmx): nunca pode ficar em cache. */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new org.springframework.web.servlet.HandlerInterceptor() {
            @Override
            public boolean preHandle(jakarta.servlet.http.HttpServletRequest req,
                                     jakarta.servlet.http.HttpServletResponse res, Object handler) {
                res.setHeader("Cache-Control", "no-store");
                return true;
            }
        }).addPathPatterns("/tablet/**", "/admin/**");
        registry.addInterceptor(sessaoInterceptor).addPathPatterns("/tablet/**");
    }
}
