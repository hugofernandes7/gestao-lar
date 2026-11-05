(function () {
    'use strict';

    // Ficha: volta sozinha à grelha após 60 s sem interação.
    var destino = document.body.getAttribute('data-voltar');
    if (destino) {
        var temporizador;
        var reiniciar = function () {
            clearTimeout(temporizador);
            temporizador = setTimeout(function () { window.location.href = destino + '?auto=1'; }, 60000);
        };
        ['pointerdown', 'touchstart', 'click', 'keydown', 'input'].forEach(function (nome) {
            document.addEventListener(nome, reiniciar, { passive: true });
        });
        document.body.addEventListener('htmx:afterRequest', reiniciar);
        reiniciar();
    }

    // Painel de nota: põe o cursor (no fim do texto) na caixa, para o teclado do tablet aparecer.
    function focarCaixaDeTexto() {
        var caixa = document.querySelector('[data-foco]');
        if (!caixa) return;
        caixa.focus();
        var fim = caixa.value.length;
        try { caixa.setSelectionRange(fim, fim); } catch (e) { /* ignorar */ }
    }
    document.body.addEventListener('htmx:afterSettle', focarCaixaDeTexto);
    focarCaixaDeTexto();

    // Se um pedido falhar, avisa em vez de ficar calado.
    function mostrarErro() {
        var caixa = document.getElementById('erro-ligacao');
        if (!caixa) {
            caixa = document.createElement('div');
            caixa.id = 'erro-ligacao';
            caixa.setAttribute('role', 'alert');
            caixa.textContent = 'Não foi possível guardar. Tente outra vez.';
            document.body.appendChild(caixa);
        }
        caixa.style.display = 'block';
        setTimeout(function () { caixa.style.display = 'none'; }, 6000);
    }
    document.body.addEventListener('htmx:responseError', mostrarErro);
    document.body.addEventListener('htmx:sendError', mostrarErro);
})();
