package com.climb.api.service;

import com.climb.api.model.Contrato;
import com.climb.api.model.Empresa;
import com.climb.api.model.Notificacao;
import com.climb.api.model.Proposta;
import com.climb.api.model.Usuario;
import com.climb.api.repository.NotificacaoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

@Service
public class ContratoNotificacaoService {

    private static final String TIPO_CONTRATO = "CONTRATO";
    private static final String TIPO_CONTRATO_VENCIMENTO = "CONTRATO_VENCIMENTO";

    private final NotificacaoRepository notificacaoRepository;
    private final EmailService emailService;
    private final int diasAvisoVencimento;

    public ContratoNotificacaoService(NotificacaoRepository notificacaoRepository,
                                      EmailService emailService,
                                      @Value("${app.contract-notifications.expiration-warning-days:30}") int diasAvisoVencimento) {
        this.notificacaoRepository = notificacaoRepository;
        this.emailService = emailService;
        this.diasAvisoVencimento = diasAvisoVencimento;
    }

    public void notificarContratoCriado(Contrato contrato) {
        String assunto = "Contrato criado";
        String mensagem = "Contrato #" + contrato.getIdContrato() + " criado com status " + contrato.getStatus() + ".";
        notificarInteressados(contrato, assunto, mensagem, TIPO_CONTRATO);
    }

    public void notificarContratoAtualizado(Contrato anterior, Contrato atualizado) {
        StringBuilder detalhes = new StringBuilder();

        if (!Objects.equals(anterior.getStatus(), atualizado.getStatus())) {
            detalhes.append("Status alterado de ")
                    .append(valorOuNaoInformado(anterior.getStatus()))
                    .append(" para ")
                    .append(valorOuNaoInformado(atualizado.getStatus()))
                    .append(".\n");
        }

        if (!Objects.equals(anterior.getDataInicio(), atualizado.getDataInicio())) {
            detalhes.append("Data de inicio alterada de ")
                    .append(valorOuNaoInformado(anterior.getDataInicio()))
                    .append(" para ")
                    .append(valorOuNaoInformado(atualizado.getDataInicio()))
                    .append(".\n");
        }

        if (!Objects.equals(anterior.getDataFim(), atualizado.getDataFim())) {
            detalhes.append("Data de fim alterada de ")
                    .append(valorOuNaoInformado(anterior.getDataFim()))
                    .append(" para ")
                    .append(valorOuNaoInformado(atualizado.getDataFim()))
                    .append(".\n");
        }

        if (detalhes.isEmpty()) {
            detalhes.append("O contrato foi atualizado.");
        }

        String assunto = "Contrato atualizado";
        String mensagem = "Contrato #" + atualizado.getIdContrato() + " atualizado.\n\n" + detalhes;
        notificarInteressados(atualizado, assunto, mensagem, TIPO_CONTRATO);
    }

    public void notificarContratoRemovido(Contrato contrato) {
        String assunto = "Contrato removido";
        String mensagem = "Contrato #" + contrato.getIdContrato() + " foi removido do sistema.";
        notificarInteressados(contrato, assunto, mensagem, TIPO_CONTRATO);
    }

    public void notificarVencimentoProximo(Contrato contrato, LocalDate hoje) {
        if (contrato.getDataFim() == null || contrato.getDataFim().isBefore(hoje)) {
            return;
        }

        long diasRestantes = ChronoUnit.DAYS.between(hoje, contrato.getDataFim());
        if (diasRestantes > diasAvisoVencimento) {
            return;
        }

        String assunto = "Contrato com vencimento proximo";
        String mensagem = "Contrato #" + contrato.getIdContrato()
                + " vence em " + diasRestantes
                + " dia(s), na data " + contrato.getDataFim() + ".";

        notificarInteressados(contrato, assunto, mensagem, TIPO_CONTRATO_VENCIMENTO);
    }

    private void notificarInteressados(Contrato contrato, String assunto, String mensagem, String tipo) {
        Usuario responsavel = obterResponsavel(contrato);
        Empresa empresa = obterEmpresa(contrato);

        if (responsavel != null) {
            salvarNotificacao(responsavel, mensagem, tipo);
            emailService.enviarEmail(responsavel.getEmail(), assunto, montarCorpo(responsavel.getNomeCompleto(), mensagem));
        }

        if (empresa != null && empresa.getEmail() != null && !empresa.getEmail().isBlank()) {
            emailService.enviarEmail(empresa.getEmail(), assunto, montarCorpo(empresa.getNomeFantasia(), mensagem));
        }
    }

    private void salvarNotificacao(Usuario usuario, String mensagem, String tipo) {
        LocalDate hoje = LocalDate.now();
        boolean jaRegistradaHoje = notificacaoRepository
                .existsByUsuario_IdAndMensagemAndTipoAndDataEnvio(usuario.getId(), mensagem, tipo, hoje);

        if (jaRegistradaHoje) {
            return;
        }

        Notificacao notificacao = new Notificacao();
        notificacao.setUsuario(usuario);
        notificacao.setMensagem(mensagem);
        notificacao.setTipo(tipo);
        notificacao.setDataEnvio(hoje);
        notificacaoRepository.save(notificacao);
    }

    private Usuario obterResponsavel(Contrato contrato) {
        if (contrato.getUsuario() != null) {
            return contrato.getUsuario();
        }

        Proposta proposta = contrato.getProposta();
        return proposta != null ? proposta.getUsuario() : null;
    }

    private Empresa obterEmpresa(Contrato contrato) {
        if (contrato.getEmpresa() != null) {
            return contrato.getEmpresa();
        }

        Proposta proposta = contrato.getProposta();
        return proposta != null ? proposta.getEmpresa() : null;
    }

    private String montarCorpo(String nomeDestino, String mensagem) {
        String saudacao = nomeDestino == null || nomeDestino.isBlank() ? "Ola" : "Ola, " + nomeDestino;
        return saudacao + "!\n\n" + mensagem + "\n\nAtenciosamente,\nEquipe Climb";
    }

    private String valorOuNaoInformado(Object valor) {
        return valor != null ? valor.toString() : "nao informado";
    }
}
