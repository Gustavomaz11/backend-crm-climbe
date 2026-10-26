package com.climb.api.service;

import com.climb.api.model.Notificacao;
import com.climb.api.model.SolicitacaoAcesso;
import com.climb.api.model.SolicitacaoAcessoOrigem;
import com.climb.api.repository.NotificacaoRepository;
import com.climb.api.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AcessoNotificacaoService {
    private final UsuarioRepository usuarios;
    private final NotificacaoRepository notificacoes;
    private final EmailNotificacaoFilaService emails;

    public AcessoNotificacaoService(UsuarioRepository usuarios, NotificacaoRepository notificacoes,
                                   EmailNotificacaoFilaService emails) {
        this.usuarios = usuarios;
        this.notificacoes = notificacoes;
        this.emails = emails;
    }

    public void novaSolicitacao(SolicitacaoAcesso solicitacao) {
        String mensagem = nome(solicitacao) + " (" + solicitacao.getEmail() + ") solicitou acesso ao CRM da Climbe. "
                + "Analise o cadastro na tela de Solicitações para aprovar ou recusar o acesso.";
        for (var administrador : usuarios.buscarAdministradoresAtivos()) {
            Notificacao aviso = new Notificacao();
            aviso.setUsuario(administrador);
            aviso.setTipo("SOLICITACAO_ACESSO");
            aviso.setMensagem(mensagem);
            aviso.setDestino("/aprovar-acesso");
            notificacoes.save(aviso);
        }
    }

    public void acessoAprovado(SolicitacaoAcesso solicitacao) {
        boolean google = solicitacao.getOrigem() == SolicitacaoAcessoOrigem.GOOGLE;
        String mensagem = "Olá, " + nome(solicitacao) + "!\n\n"
                + "Seu acesso ao CRM da Climbe foi aprovado e você já pode iniciar suas atividades.\n\n"
                + (google ? "Clique no botão abaixo e entre com sua conta Google. Se este for seu primeiro acesso, "
                        + "conclua seu cadastro para começar."
                        : "Clique no botão abaixo e entre com o e-mail e a senha cadastrados para começar.")
                + "\n\nSeja bem-vindo à equipe!\nEquipe Climbe";
        emails.enfileirar(new NotificacaoEntregaService.EmailNotificacao(solicitacao.getEmail(),
                "Seu acesso ao CRM da Climbe foi aprovado!", mensagem, google ? "/convite" : "/"));
    }

    private String nome(SolicitacaoAcesso solicitacao) {
        return solicitacao.getNomeCompleto() == null || solicitacao.getNomeCompleto().isBlank()
                ? solicitacao.getEmail() : solicitacao.getNomeCompleto();
    }
}
