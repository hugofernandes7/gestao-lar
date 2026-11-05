(function () {
    'use strict';

    // Teclado numérico próprio (o do Android nunca aparece): sem campos de texto. Funciona por delegação, por isso
    // continua a funcionar depois de o htmx trocar o conteúdo da ficha.
    // Marcação: <... data-teclado data-decimais="1" data-min="20" data-max="250"> com [data-visor], [data-valor]
    // (campo escondido que segue no pedido) e [data-guardar] (só fica ativo com um valor válido).
    document.addEventListener('click', function (e) {
        var tecla = e.target.closest ? e.target.closest('[data-teclado] .tecla') : null;
        if (!tecla) return;
        var raiz = tecla.closest('[data-teclado]');
        var visor = raiz.querySelector('[data-visor]');
        var valor = raiz.querySelector('[data-valor]');
        var guardar = raiz.querySelector('[data-guardar]');
        var decimais = parseInt(raiz.getAttribute('data-decimais') || '0', 10);
        var min = parseFloat(raiz.getAttribute('data-min'));
        var max = parseFloat(raiz.getAttribute('data-max'));
        var texto = visor.getAttribute('data-texto') || '';
        var t = tecla.getAttribute('data-tecla');

        if (t === 'apagar') {
            texto = texto.slice(0, -1);
        } else if (t === ',') {
            if (decimais > 0 && texto.length > 0 && texto.indexOf(',') < 0) texto += ',';
        } else {
            var partes = texto.split(',');
            if (partes.length === 1) {
                if (partes[0].length < String(Math.floor(max)).length && !(partes[0] === '' && t === '0')) texto += t;
            } else if (partes[1].length < decimais) {
                texto += t;
            }
        }

        visor.setAttribute('data-texto', texto);
        visor.textContent = texto;
        var n = parseFloat(texto.replace(',', '.'));
        var valido = !isNaN(n) && texto.charAt(texto.length - 1) !== ',' && n >= min && n <= max;
        valor.value = valido ? texto.replace(',', '.') : '';
        if (guardar) guardar.disabled = !valido;
    });
})();
