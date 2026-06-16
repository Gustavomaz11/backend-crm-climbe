package com.climb.api.service;

import com.climb.api.model.Notificacao;
import com.climb.api.model.Usuario;
import com.climb.api.model.dto.NotificacaoRequestDTO;
import com.climb.api.model.dto.NotificacaoResponseDTO;
import com.climb.api.repository.NotificacaoRepository;
import com.climb.api.repository.UsuarioRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class NotificacaoService {

    private final NotificacaoRepository repository;
    private final UsuarioRepository usuarioRepository;

    public NotificacaoService(NotificacaoRepository repository, UsuarioRepository usuarioRepository) {
        this.repository = repository;
        this.usuarioRepository = usuarioRepository;
    }

    public List<Notificacao> listar() {
        return repository.findAll();
    }

    public List<NotificacaoResponseDTO> listarDTO() {
        return repository.findAll().stream()
                .map(NotificacaoResponseDTO::from)
                .collect(Collectors.toList());
    }

    public Notificacao buscarPorId(Long id) {
        return repository.findById(id).orElseThrow();
    }

    public NotificacaoResponseDTO buscarPorIdDTO(Long id) {
        return NotificacaoResponseDTO.from(buscarPorId(id));
    }

    public List<NotificacaoResponseDTO> listarPorUsuario(Long usuarioId) {
        return repository.findByUsuario_IdOrderByDataCriacaoDescIdNotificacaoDesc(usuarioId).stream()
                .map(NotificacaoResponseDTO::from)
                .collect(Collectors.toList());
    }

    public List<NotificacaoResponseDTO> listarNaoLidasPorUsuario(Long usuarioId) {
        return repository.findByUsuario_IdAndLidaFalseOrderByDataCriacaoDescIdNotificacaoDesc(usuarioId).stream()
                .map(NotificacaoResponseDTO::from)
                .collect(Collectors.toList());
    }

    public long contarNaoLidasPorUsuario(Long usuarioId) {
        return repository.countByUsuario_IdAndLidaFalse(usuarioId);
    }

    public Notificacao criar(Notificacao notificacao) {
        validarNotificacao(notificacao);
        return repository.save(notificacao);
    }

    public NotificacaoResponseDTO criar(NotificacaoRequestDTO dto) {
        return NotificacaoResponseDTO.from(criarEntidade(dto));
    }

    public NotificacaoResponseDTO criarParaUsuario(Long usuarioId, NotificacaoRequestDTO dto) {
        dto.setUsuarioId(usuarioId);
        return NotificacaoResponseDTO.from(criarEntidade(dto));
    }

    public Notificacao atualizar(Long id, Notificacao atualizada) {
        Notificacao notificacao = buscarPorId(id);
        notificacao.setUsuario(atualizada.getUsuario());
        notificacao.setMensagem(atualizada.getMensagem());
        notificacao.setDataEnvio(atualizada.getDataEnvio());
        notificacao.setTipo(atualizada.getTipo());
        notificacao.setLida(Boolean.TRUE.equals(atualizada.getLida()));
        notificacao.setDataLeitura(atualizada.getDataLeitura());
        validarNotificacao(notificacao);
        return repository.save(notificacao);
    }

    public NotificacaoResponseDTO atualizar(Long id, NotificacaoRequestDTO dto) {
        Notificacao notificacao = buscarPorId(id);
        aplicarDTO(notificacao, dto);
        validarNotificacao(notificacao);
        return NotificacaoResponseDTO.from(repository.save(notificacao));
    }

    public NotificacaoResponseDTO marcarComoLida(Long id) {
        Notificacao notificacao = buscarPorId(id);
        marcarLida(notificacao);
        return NotificacaoResponseDTO.from(repository.save(notificacao));
    }

    public NotificacaoResponseDTO marcarComoLidaDoUsuario(Long id, Long usuarioId) {
        Notificacao notificacao = repository.findByIdNotificacaoAndUsuario_Id(id, usuarioId)
                .orElseThrow(() -> new RuntimeException("Notificacao nao encontrada para o usuario informado"));
        marcarLida(notificacao);
        return NotificacaoResponseDTO.from(repository.save(notificacao));
    }

    public void marcarTodasComoLidasDoUsuario(Long usuarioId) {
        List<Notificacao> notificacoes = repository.findByUsuario_IdAndLidaFalseOrderByDataCriacaoDescIdNotificacaoDesc(usuarioId);
        notificacoes.forEach(this::marcarLida);
        repository.saveAll(notificacoes);
    }

    public void deletar(Long id) {
        repository.deleteById(id);
    }

    private Notificacao criarEntidade(NotificacaoRequestDTO dto) {
        Notificacao notificacao = new Notificacao();
        aplicarDTO(notificacao, dto);
        validarNotificacao(notificacao);
        return repository.save(notificacao);
    }

    private void aplicarDTO(Notificacao notificacao, NotificacaoRequestDTO dto) {
        if (dto.getUsuarioId() == null) {
            throw new RuntimeException("Usuario e obrigatorio");
        }

        Usuario usuario = usuarioRepository.findById(dto.getUsuarioId())
                .orElseThrow(() -> new RuntimeException("Usuario nao encontrado"));

        notificacao.setUsuario(usuario);
        notificacao.setMensagem(dto.getMensagem());
        notificacao.setTipo(dto.getTipo());
        notificacao.setDataEnvio(dto.getDataEnvio() != null ? dto.getDataEnvio() : LocalDate.now());
    }

    private void validarNotificacao(Notificacao notificacao) {
        if (notificacao.getUsuario() == null || notificacao.getUsuario().getId() == null) {
            throw new RuntimeException("Usuario e obrigatorio");
        }
        if (notificacao.getMensagem() == null || notificacao.getMensagem().isBlank()) {
            throw new RuntimeException("Mensagem e obrigatoria");
        }
        if (notificacao.getTipo() == null || notificacao.getTipo().isBlank()) {
            notificacao.setTipo("GERAL");
        }
        if (notificacao.getDataEnvio() == null) {
            notificacao.setDataEnvio(LocalDate.now());
        }
        if (notificacao.getLida() == null) {
            notificacao.setLida(false);
        }
    }

    private void marcarLida(Notificacao notificacao) {
        if (!Boolean.TRUE.equals(notificacao.getLida())) {
            notificacao.setLida(true);
            notificacao.setDataLeitura(LocalDateTime.now());
        }
    }
}
