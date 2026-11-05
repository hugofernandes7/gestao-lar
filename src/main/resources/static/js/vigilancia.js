(function () {
    'use strict';

    // Vigilância pós-queda: três perguntas, uma resposta por pergunta. Nada vem escolhido por defeito e «Guardar» só se
    // ativa quando as três estão respondidas. Por delegação, para continuar a funcionar depois de o htmx trocar a ficha.
    document.addEventListener('click', function (e) {
        var botao = e.target.closest ? e.target.closest('[data-vigilancia] .btn-resp') : null;
        if (!botao) return;
        var raiz = botao.closest('[data-vigilancia]');
        var pergunta = botao.closest('[data-pergunta]');
        var nome = pergunta.getAttribute('data-pergunta');

        pergunta.querySelectorAll('.btn-resp').forEach(function (b) {
            var este = b === botao;
            b.setAttribute('aria-pressed', este ? 'true' : 'false');
            b.classList.toggle('resp-ativa', este);
        });
        var campo = raiz.querySelector('input[name="' + nome + '"]');
        if (campo) campo.value = botao.getAttribute('data-valor-resp');

        var todas = true;
        raiz.querySelectorAll('[data-resp-valor]').forEach(function (c) { if (!c.value) todas = false; });
        var guardar = raiz.querySelector('[data-guardar]');
        if (guardar) guardar.disabled = !todas;
    });
})();
