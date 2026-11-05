(function () {
    'use strict';

    var principal = document.querySelector('.acesso');
    if (!principal) return;

    // Depois do PIN certo: mostra "Olá, Ana" um instante e segue para onde se estava a ir.
    var continuar = document.getElementById('continuar');
    if (continuar) {
        setTimeout(function () { window.location.href = continuar.getAttribute('href'); }, 1400);
        return;
    }

    var formulario = document.getElementById('teclado');
    var visor = document.getElementById('visor');
    var instrucao = document.getElementById('instrucao');
    var botaoOk = document.getElementById('tecla-ok');
    var numero = '';
    var pin = '';
    var fase = 'numero';

    function mostrar() {
        if (fase === 'numero') {
            visor.textContent = numero;
            visor.className = 'visor';
            visor.setAttribute('aria-label', 'Número');
            visor.setAttribute('data-vazio', 'Número');
            instrucao.textContent = 'Escreva o seu número e toque em OK.';
            botaoOk.disabled = numero.length === 0;
        } else {
            var pontos = '';
            for (var i = 0; i < 4; i++) pontos += (i < pin.length ? '●' : '○') + (i < 3 ? ' ' : '');
            visor.textContent = pontos;
            visor.className = 'visor visor-pin';
            visor.setAttribute('aria-label', 'PIN: ' + pin.length + ' de 4 dígitos');
            visor.removeAttribute('data-vazio');
            instrucao.textContent = 'Escreva o seu PIN (4 dígitos).';
            botaoOk.disabled = true;
        }
    }

    function digito(d) {
        if (fase === 'numero') {
            if (numero.length < 4 && !(numero === '' && d === '0')) numero += d;   // 1–9999, sem zero à esquerda
        } else if (pin.length < 4) {
            pin += d;
            if (pin.length === 4) {            // entra sozinho ao 4.º dígito: número + PIN num único pedido
                mostrar();
                document.getElementById('campo-numero').value = numero;
                document.getElementById('campo-pin').value = pin;
                formulario.submit();
                return;
            }
        }
        mostrar();
    }

    function apagar() {
        if (fase === 'pin') {
            if (pin.length > 0) pin = pin.slice(0, -1);
            else fase = 'numero';              // com o PIN vazio, apagar volta ao número
        } else {
            numero = numero.slice(0, -1);
        }
        mostrar();
    }

    function ok() {
        if (fase === 'numero' && numero.length > 0) {
            fase = 'pin';
            mostrar();
        }
    }

    formulario.addEventListener('click', function (e) {
        var tecla = e.target.closest('[data-tecla]');
        if (!tecla || tecla.disabled) return;
        var v = tecla.getAttribute('data-tecla');
        if (v === 'apagar') apagar();
        else if (v === 'ok') ok();
        else digito(v);
    });

    // Teclado físico (PC): dígitos, Backspace e Enter.
    document.addEventListener('keydown', function (e) {
        if (/^[0-9]$/.test(e.key)) digito(e.key);
        else if (e.key === 'Backspace') apagar();
        else if (e.key === 'Enter' && fase === 'numero') { e.preventDefault(); ok(); }
    });

    // Sem toques durante 60 s: volta à grelha (não conta como interação da sessão).
    var inativo;
    function reiniciar() {
        clearTimeout(inativo);
        inativo = setTimeout(function () { window.location.href = '/tablet?auto=1'; }, 60000);
    }
    ['pointerdown', 'touchstart', 'click', 'keydown'].forEach(function (n) {
        document.addEventListener(n, reiniciar, { passive: true });
    });
    reiniciar();

    mostrar();
})();
