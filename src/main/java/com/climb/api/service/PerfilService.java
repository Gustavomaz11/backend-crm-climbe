package com.climb.api.service;

import com.climb.api.model.Usuario;
import com.climb.api.model.dto.ArquivoUploadResponseDTO;
import com.climb.api.model.dto.AtualizarMeuPerfilRequestDTO;
import com.climb.api.model.dto.UsuarioResponseDTO;
import com.climb.api.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Service
public class PerfilService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;
    private final CloudflareR2ArquivoStorageService storageService;

    public PerfilService(UsuarioRepository usuarioRepository,
                         UsuarioService usuarioService,
                         CloudflareR2ArquivoStorageService storageService) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioService = usuarioService;
        this.storageService = storageService;
    }

    public UsuarioResponseDTO buscar(Long usuarioId) {
        return usuarioService.buscarPorIdDTO(usuarioId);
    }

    @Transactional
    public UsuarioResponseDTO atualizar(Long usuarioId, AtualizarMeuPerfilRequestDTO dto) {
        Usuario usuario = usuarioService.buscarPorId(usuarioId);
        String email = dto.email().trim().toLowerCase(Locale.ROOT);

        usuarioRepository.findByEmail(email)
                .filter(outro -> !outro.getId().equals(usuarioId))
                .ifPresent(outro -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Email ja esta em uso");
                });
        usuarioRepository.findByCpf(dto.cpf().trim())
                .filter(outro -> !outro.getId().equals(usuarioId))
                .ifPresent(outro -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "CPF ja esta em uso");
                });

        usuario.setNomeCompleto(dto.nomeCompleto().trim());
        usuario.setEmail(email);
        usuario.setCpf(dto.cpf().trim());
        usuario.setContato(dto.contato().trim());
        return usuarioService.toResponse(usuarioRepository.save(usuario));
    }

    @Transactional
    public UsuarioResponseDTO atualizarFoto(Long usuarioId, MultipartFile foto) {
        if (foto == null || foto.isEmpty() || foto.getContentType() == null
                || !foto.getContentType().toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione uma imagem valida");
        }

        Usuario usuario = usuarioService.buscarPorId(usuarioId);
        ArquivoUploadResponseDTO upload = storageService.salvar(foto, "usuarios/" + usuarioId + "/perfil");
        usuario.setFotoPerfilUrl(upload.url());
        return usuarioService.toResponse(usuarioRepository.save(usuario));
    }
}
