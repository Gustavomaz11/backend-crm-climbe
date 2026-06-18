package com.climb.api.service;

import com.climb.api.config.GoogleCalendarConfig;
import com.climb.api.mapper.UsuarioMapper;
import com.climb.api.model.Cargo;
import com.climb.api.model.OAuth2ExchangeCode;
import com.climb.api.model.OAuth2PendingRegistration;
import com.climb.api.model.OAuthProvider;
import com.climb.api.model.Usuario;
import com.climb.api.model.UsuarioOAuth;
import com.climb.api.model.dto.AuthResult;
import com.climb.api.model.dto.ExchangeCodeResponseDTO;
import com.climb.api.model.dto.GoogleAuthorizationUrlResponseDTO;
import com.climb.api.model.dto.GoogleOAuthResolveResponseDTO;
import com.climb.api.model.dto.GoogleTokenResponseDTO;
import com.climb.api.model.dto.LoginResponseDTO;
import com.climb.api.model.dto.UsuarioResponseDTO;
import com.climb.api.repository.OAuth2ExchangeCodeRepository;
import com.climb.api.repository.OAuth2PendingRegistrationRepository;
import com.climb.api.repository.UsuarioOAuthRepository;
import com.climb.api.repository.UsuarioRepository;
import com.climb.api.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class GoogleOAuthService {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthService.class);

    public static final String STATUS_LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String STATUS_GOOGLE_NOT_LINKED = "GOOGLE_NOT_LINKED";
    public static final String STATUS_LINK_SUCCESS = "LINK_SUCCESS";
    public static final String STATUS_PENDING_APPROVAL = "PENDING_APPROVAL";
    public static final String STATUS_COMPLETAR_CADASTRO = "COMPLETAR_CADASTRO";

    private static final String GOOGLE_AUTH_URI = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_USERINFO_URI = "https://www.googleapis.com/oauth2/v3/userinfo";
    private static final String GOOGLE_SCOPE = "openid email profile https://www.googleapis.com/auth/calendar";

    private static final int EXCHANGE_CODE_EXPIRATION_SECONDS = 60;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final String MSG_PENDING_APPROVAL = "Sua solicitação está aguardando aprovação do administrador";
    private static final String MSG_DEACTIVATED = "Sua conta foi desativada. Entre em contato com o administrador";
    private static final String MSG_COMPLETAR_CADASTRO = "Cadastro aprovado. Complete seu perfil para concluir o acesso";

    @org.springframework.beans.factory.annotation.Value("${google.calendar.allowed-domain:}")
    private String googleAllowedDomain;

    private final GoogleCalendarConfig googleCalendarConfig;
    private final UsuarioService usuarioService;
    private final UsuarioRepository usuarioRepository;
    private final JwtUtil jwtUtil;
    private final RestClient restClient;
    private final UsuarioOAuthRepository usuarioOAuthRepository;
    private final OAuth2PendingRegistrationRepository pendingRegistrationRepository;
    private final OAuth2ExchangeCodeRepository exchangeCodeRepository;
    private final AuthenticationService authenticationService;
    private final UsuarioMapper usuarioMapper;
    private final OAuth2PendingService pendingService;

    public GoogleOAuthService(
            GoogleCalendarConfig googleCalendarConfig,
            UsuarioService usuarioService,
            UsuarioRepository usuarioRepository,
            JwtUtil jwtUtil,
            UsuarioOAuthRepository usuarioOAuthRepository,
            OAuth2PendingRegistrationRepository pendingRegistrationRepository,
            OAuth2ExchangeCodeRepository exchangeCodeRepository,
            AuthenticationService authenticationService,
            UsuarioMapper usuarioMapper,
            OAuth2PendingService pendingService
    ) {
        this.googleCalendarConfig = googleCalendarConfig;
        this.usuarioService = usuarioService;
        this.usuarioRepository = usuarioRepository;
        this.jwtUtil = jwtUtil;
        this.restClient = RestClient.builder().build();
        this.usuarioOAuthRepository = usuarioOAuthRepository;
        this.pendingRegistrationRepository = pendingRegistrationRepository;
        this.exchangeCodeRepository = exchangeCodeRepository;
        this.authenticationService = authenticationService;
        this.usuarioMapper = usuarioMapper;
        this.pendingService = pendingService;
    }

    // ==================== Google Calendar API Integration ====================

    public GoogleAuthorizationUrlResponseDTO gerarUrlAutorizacao() {
        validarConfiguracao();

        String authorizationUrl = UriComponentsBuilder
                .fromUriString(GOOGLE_AUTH_URI)
                .queryParam("client_id", googleCalendarConfig.getClientId())
                .queryParam("redirect_uri", googleCalendarConfig.getRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", GOOGLE_SCOPE)
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUriString();

        return new GoogleAuthorizationUrlResponseDTO(
                authorizationUrl,
                googleCalendarConfig.getRedirectUri(),
                GOOGLE_SCOPE
        );
    }

    public GoogleTokenResponseDTO trocarCodePorToken(String code) {
        validarConfiguracao();

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("code", code);
        formData.add("client_id", googleCalendarConfig.getClientId());
        formData.add("client_secret", googleCalendarConfig.getClientSecret());
        formData.add("redirect_uri", googleCalendarConfig.getRedirectUri());
        formData.add("grant_type", "authorization_code");

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(GOOGLE_TOKEN_URI)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(Map.class);

            if (response == null || response.get("access_token") == null) {
                throw new RuntimeException("Resposta invalida ao trocar o code por token");
            }

            String googleAccessToken = response.get("access_token").toString();
            UsuarioResponseDTO usuario = autenticarUsuarioGoogle(googleAccessToken);

            return new GoogleTokenResponseDTO(
                    googleAccessToken,
                    response.get("refresh_token") != null ? response.get("refresh_token").toString() : null,
                    response.get("token_type") != null ? response.get("token_type").toString() : null,
                    response.get("expires_in") instanceof Number number ? number.longValue() : null,
                    response.get("scope") != null ? response.get("scope").toString() : null,
                    jwtUtil.generateAccessToken(usuario.getId(), usuario.getEmail()),
                    jwtUtil.generateRefreshToken(usuario.getId(), usuario.getEmail()),
                    jwtUtil.getAccessTokenExpirationTime(),
                    usuario
            );
        } catch (RestClientException e) {
            throw new RuntimeException("Falha ao trocar o code pelo token do Google", e);
        }
    }

    public boolean isConfigured() {
        return googleCalendarConfig.isEnabled()
                && googleCalendarConfig.getClientId() != null
                && !googleCalendarConfig.getClientId().isBlank()
                && googleCalendarConfig.getClientSecret() != null
                && !googleCalendarConfig.getClientSecret().isBlank()
                && googleCalendarConfig.getRedirectUri() != null
                && !googleCalendarConfig.getRedirectUri().isBlank();
    }

    @Transactional
    public URI gerarRedirecionamentoFrontend(GoogleTokenResponseDTO tokenResponse) {
        String code = salvarExchangeCode(
                tokenResponse.appAccessToken(),
                tokenResponse.appRefreshToken(),
                tokenResponse.accessToken(),
                tokenResponse.refreshToken(),
                tokenResponse.appExpiresIn(),
                tokenResponse.usuario());

        return UriComponentsBuilder.fromUriString(googleCalendarConfig.getFrontendUrl())
                .queryParam("google_oauth", "success")
                .queryParam("code", code)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUri();
    }

    @Transactional
    public ExchangeCodeResponseDTO exchangeCode(String code) {
        log.info("GoogleOAuthService.exchangeCode — código: {}", LogSanitizer.oauthCodeForLog(code));
        limparExchangeCodesExpirados();

        OAuth2ExchangeCode exchangeCode = exchangeCodeRepository
                .findByCodeAndConsumidoFalse(code)
                .orElseThrow(() -> new RuntimeException("Codigo invalido ou expirado"));

        if (exchangeCode.getExpiraEm().isBefore(LocalDateTime.now())) {
            log.warn("GoogleOAuthService.exchangeCode — código expirado (expiraEm={})", exchangeCode.getExpiraEm());
            throw new RuntimeException("Codigo expirado");
        }

        exchangeCode.setConsumido(true);
        exchangeCodeRepository.save(exchangeCode);

        UsuarioResponseDTO usuario = null;
        if (exchangeCode.getUserId() != null) {
            usuario = new UsuarioResponseDTO();
            usuario.setId(exchangeCode.getUserId());
            usuario.setEmail(exchangeCode.getUserEmail());
            usuario.setNomeCompleto(exchangeCode.getUserName());
            usuario.setSituacao(exchangeCode.getUserStatus());
            usuario.setCargoNome(exchangeCode.getUserRole());
            Usuario usuarioEntity = usuarioService.buscarPorId(exchangeCode.getUserId());
            usuario.setFotoPerfil(usuarioService.buscarFotoPerfil(usuarioEntity));
        }

        log.info("GoogleOAuthService.exchangeCode — sucesso: userId={}, googleAccessToken={}, appAccessToken length={}",
                exchangeCode.getUserId(),
                LogSanitizer.googleAccessTokenForLog(exchangeCode.getGoogleAccessToken()),
                exchangeCode.getAccessToken() != null ? exchangeCode.getAccessToken().length() : 0);

        return new ExchangeCodeResponseDTO(
                exchangeCode.getAccessToken(),
                exchangeCode.getRefreshToken(),
                exchangeCode.getExpiresIn(),
                exchangeCode.getGoogleAccessToken(),
                exchangeCode.getGoogleRefreshToken(),
                usuario
        );
    }

    private String gerarCodigoSeguro() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void limparExchangeCodesExpirados() {
        exchangeCodeRepository.deleteByExpiraEmBefore(LocalDateTime.now());
    }

    private String salvarExchangeCode(String appAccessToken,
                                      String appRefreshToken,
                                      String googleAccessToken,
                                      String googleRefreshToken,
                                      Long appExpiresIn,
                                      UsuarioResponseDTO usuario) {
        limparExchangeCodesExpirados();

        String code = gerarCodigoSeguro();
        OAuth2ExchangeCode exchangeCode = new OAuth2ExchangeCode();
        exchangeCode.setCode(code);
        exchangeCode.setAccessToken(appAccessToken);
        exchangeCode.setRefreshToken(appRefreshToken);
        exchangeCode.setGoogleAccessToken(googleAccessToken);
        exchangeCode.setGoogleRefreshToken(googleRefreshToken);
        exchangeCode.setExpiresIn(appExpiresIn);

        if (usuario != null) {
            exchangeCode.setUserId(usuario.getId());
            exchangeCode.setUserEmail(usuario.getEmail());
            exchangeCode.setUserName(usuario.getNomeCompleto());
            exchangeCode.setUserStatus(usuario.getSituacao());
            exchangeCode.setUserRole(usuario.getCargoNome());
        }

        exchangeCode.setExpiraEm(LocalDateTime.now().plusSeconds(EXCHANGE_CODE_EXPIRATION_SECONDS));
        exchangeCode.setConsumido(false);
        exchangeCode.setCriadoEm(LocalDateTime.now());

        exchangeCodeRepository.save(exchangeCode);
        return code;
    }

    public URI gerarRedirecionamentoErro(String errorMessage) {
        return UriComponentsBuilder.fromUriString(googleCalendarConfig.getFrontendUrl())
                .queryParam("google_oauth", "error")
                .queryParam("google_oauth_error", errorMessage)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUri();
    }

    // ==================== OAuth2 Login/Registration Flow ====================

    @Transactional
    public GoogleOAuthResolveResponseDTO resolverLoginGoogle(String providerUserId,
                                                             String email,
                                                             String nome,
                                                             String avatarUrl) {
        validarDadosGoogle(providerUserId, email);
        limparPendenciasExpiradas();

        UsuarioOAuth vinculo = usuarioOAuthRepository
                .findByProviderAndProviderUserId(OAuthProvider.GOOGLE, providerUserId)
                .orElse(null);

        if (vinculo != null) {
            atualizarDadosVinculoGoogle(vinculo, email, nome, avatarUrl);
            return resolverUsuarioVinculado(vinculo.getUsuario(), email);
        }

        OAuth2PendingRegistration pending = pendingService
                .findAtivoPorProvider(OAuthProvider.GOOGLE, providerUserId)
                .orElse(null);

        if (pending != null) {
            if (pending.getExpiraEm().isBefore(LocalDateTime.now())) {
                pending = pendingService.criarPendingGoogle(providerUserId, email, nome, avatarUrl);
            }

            if (Boolean.TRUE.equals(pending.getAprovado())) {
                String pendingToken = jwtUtil.generatePendingRegistrationToken(pending.getId(), pending.getEmail());
                GoogleOAuthResolveResponseDTO response = new GoogleOAuthResolveResponseDTO();
                response.setStatus(STATUS_COMPLETAR_CADASTRO);
                response.setPendingToken(pendingToken);
                response.setEmail(pending.getEmail());
                response.setNome(pending.getNome());
                response.setAvatarUrl(pending.getAvatarUrl());
                response.setMessage(MSG_COMPLETAR_CADASTRO);
                return response;
            }

            return montarResolveResponse(STATUS_PENDING_APPROVAL, pending.getEmail(),
                    pending.getNome(), pending.getAvatarUrl(), MSG_PENDING_APPROVAL);
        }

        Usuario usuarioExistente = usuarioService.buscarPorEmail(email);
        if (usuarioExistente != null) {
            AuthResult<Void> situacao = authenticationService.validarUsuarioAtivo(
                    usuarioExistente,
                    "Usuario nao encontrado");

            if (!situacao.isSuccess()) {
                return resolveResponseComEmail(STATUS_PENDING_APPROVAL, email, situacao.message());
            }

            vincularConta(usuarioExistente.getId(), providerUserId, email, nome, avatarUrl);
            return resolverUsuarioVinculado(usuarioExistente, email);
        }

        OAuth2PendingRegistration novoPending = pendingService
                .criarPendingGoogle(providerUserId, email, nome, avatarUrl);
        return montarResolveResponse(STATUS_PENDING_APPROVAL, novoPending.getEmail(),
                novoPending.getNome(), novoPending.getAvatarUrl(), MSG_PENDING_APPROVAL);
    }

    private GoogleOAuthResolveResponseDTO resolverUsuarioVinculado(Usuario usuario, String email) {
        AuthResult<Void> situacao = authenticationService.validarUsuarioAtivo(usuario, "Usuario nao encontrado");

        return switch (situacao.status()) {
            case SUCCESS -> {
                AuthResult<LoginResponseDTO> loginResult = authenticationService.gerarRespostaLogin(usuario);
                if (!loginResult.isSuccess()) {
                    yield resolveResponseComEmail(STATUS_PENDING_APPROVAL, email, loginResult.message());
                }
                GoogleOAuthResolveResponseDTO response = new GoogleOAuthResolveResponseDTO();
                response.setStatus(STATUS_LOGIN_SUCCESS);
                response.setLogin(loginResult.data());
                response.setMessage("Login Google realizado com sucesso");
                yield response;
            }
            case INATIVO -> resolveResponseComEmail(STATUS_PENDING_APPROVAL, email, MSG_DEACTIVATED);
            default -> resolveResponseComEmail(STATUS_PENDING_APPROVAL, email, MSG_PENDING_APPROVAL);
        };
    }

    private GoogleOAuthResolveResponseDTO resolveResponseComEmail(String status, String email, String message) {
        GoogleOAuthResolveResponseDTO response = new GoogleOAuthResolveResponseDTO();
        response.setStatus(status);
        response.setEmail(email);
        response.setMessage(message);
        return response;
    }

    private GoogleOAuthResolveResponseDTO montarResolveResponse(String status, String email, String nome,
                                                                String avatarUrl, String message) {
        GoogleOAuthResolveResponseDTO response = new GoogleOAuthResolveResponseDTO();
        response.setStatus(status);
        response.setEmail(email);
        response.setNome(nome);
        response.setAvatarUrl(avatarUrl);
        response.setMessage(message);
        return response;
    }

    @Transactional
    public GoogleOAuthResolveResponseDTO vincularConta(Long usuarioId,
                                                       String providerUserId,
                                                       String email,
                                                       String nome,
                                                       String avatarUrl) {
        validarDadosGoogle(providerUserId, email);

        Usuario usuario = usuarioService.buscarPorId(usuarioId);
        AuthResult<Void> situacao = authenticationService.validarUsuarioAtivo(usuario, "Usuario nao encontrado");
        if (!situacao.isSuccess()) {
            throw new RuntimeException(situacao.message());
        }

        UsuarioOAuth vinculoExistente = usuarioOAuthRepository
                .findByProviderAndProviderUserId(OAuthProvider.GOOGLE, providerUserId)
                .orElse(null);

        if (vinculoExistente != null && !vinculoExistente.getUsuario().getId().equals(usuarioId)) {
            throw new RuntimeException("Esta conta Google ja esta vinculada a outro usuario");
        }

        UsuarioOAuth vinculo = usuarioOAuthRepository
                .findByUsuarioIdAndProvider(usuarioId, OAuthProvider.GOOGLE)
                .orElseGet(UsuarioOAuth::new);

        if (vinculo.getId() != null && !vinculo.getProviderUserId().equals(providerUserId)) {
            throw new RuntimeException("Este usuario ja possui outra conta Google vinculada");
        }

        vinculo.setUsuario(usuario);
        vinculo.setProvider(OAuthProvider.GOOGLE);
        vinculo.setProviderUserId(providerUserId);
        vinculo.setEmailProvider(email);
        vinculo.setNomeProvider(nome);
        vinculo.setAvatarUrl(avatarUrl);
        if (vinculo.getVinculadoEm() == null) {
            vinculo.setVinculadoEm(LocalDateTime.now());
        }
        usuarioOAuthRepository.save(vinculo);

        return montarResolveResponse(STATUS_LINK_SUCCESS, email, nome, avatarUrl, "Conta Google vinculada com sucesso");
    }

    // ==================== OAuth2 Callback Handler ====================

    @Transactional
    public URI resolverCallbackGoogle(String authorizationCode) {
        validarConfiguracao();

        Map<String, Object> googleResp = trocarCodePorGoogleTokenRaw(authorizationCode);
        String googleAccessToken = googleResp.get("access_token").toString();
        String googleRefreshToken = googleResp.get("refresh_token") != null
                ? googleResp.get("refresh_token").toString() : null;

        Map<String, Object> userInfo = obterInfoUsuarioGoogle(googleAccessToken);
        String providerUserId = userInfo.get("sub") != null ? userInfo.get("sub").toString() : null;
        String email = userInfo.get("email") != null ? userInfo.get("email").toString() : null;
        String nome = userInfo.get("name") != null ? userInfo.get("name").toString() : null;
        String avatarUrl = userInfo.get("picture") != null ? userInfo.get("picture").toString() : null;

        GoogleOAuthResolveResponseDTO resolution = resolverLoginGoogle(providerUserId, email, nome, avatarUrl);

        return switch (resolution.getStatus()) {
            case STATUS_LOGIN_SUCCESS -> redirectAposLogin(resolution.getLogin(),
                    googleAccessToken, googleRefreshToken);
            case STATUS_COMPLETAR_CADASTRO -> redirectCompletarCadastro(resolution.getPendingToken(),
                    googleAccessToken, googleRefreshToken);
            case STATUS_GOOGLE_NOT_LINKED -> UriComponentsBuilder
                    .fromUriString(googleCalendarConfig.getFrontendUrl())
                    .queryParam("google_oauth", "not_linked")
                    .queryParam("email", email != null ? email : "")
                    .build().encode(StandardCharsets.UTF_8).toUri();
            case STATUS_PENDING_APPROVAL -> UriComponentsBuilder
                    .fromUriString(googleCalendarConfig.getFrontendUrl())
                    .queryParam("google_oauth", "pending_approval")
                    .queryParam("email", email != null ? email : "")
                    .build().encode(StandardCharsets.UTF_8).toUri();
            default -> gerarRedirecionamentoErro("Unknown status: " + resolution.getStatus());
        };
    }

    private URI redirectAposLogin(LoginResponseDTO login,
                                  String googleAccessToken, String googleRefreshToken) {
        String code = salvarExchangeCode(
                login.getAccessToken(),
                login.getRefreshToken(),
                googleAccessToken,
                googleRefreshToken,
                login.getExpiresIn(),
                login.getUsuario());

        return UriComponentsBuilder.fromUriString(googleCalendarConfig.getFrontendUrl())
                .queryParam("google_oauth", "success")
                .queryParam("code", code)
                .build().encode(StandardCharsets.UTF_8).toUri();
    }

    private URI redirectCompletarCadastro(String pendingToken,
                                          String googleAccessToken, String googleRefreshToken) {
        String code = salvarExchangeCode(
                pendingToken,
                null,
                googleAccessToken,
                googleRefreshToken,
                jwtUtil.getPendingTokenExpirationTime(),
                null);

        return UriComponentsBuilder.fromUriString(googleCalendarConfig.getFrontendUrl())
                .queryParam("google_oauth", "completar_cadastro")
                .queryParam("code", code)
                .build().encode(StandardCharsets.UTF_8).toUri();
    }

    // ==================== Private Helper Methods ====================

    private void validarConfiguracao() {
        if (!isConfigured()) {
            throw new RuntimeException("Google Calendar OAuth nao configurado. Defina GOOGLE_CALENDAR_CLIENT_ID, GOOGLE_CALENDAR_CLIENT_SECRET e GOOGLE_CALENDAR_REDIRECT_URI.");
        }
    }

    private Map<String, Object> trocarCodePorGoogleTokenRaw(String code) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("code", code);
        formData.add("client_id", googleCalendarConfig.getClientId());
        formData.add("client_secret", googleCalendarConfig.getClientSecret());
        formData.add("redirect_uri", googleCalendarConfig.getRedirectUri());
        formData.add("grant_type", "authorization_code");

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(GOOGLE_TOKEN_URI)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(Map.class);

            if (response == null || response.get("access_token") == null) {
                throw new RuntimeException("Resposta invalida ao trocar o code por token");
            }
            return response;
        } catch (RestClientException e) {
            throw new RuntimeException("Falha ao trocar o code pelo token do Google", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> obterInfoUsuarioGoogle(String googleAccessToken) {
        Map<String, Object> userInfo = restClient.get()
                .uri(GOOGLE_USERINFO_URI)
                .header("Authorization", "Bearer " + googleAccessToken)
                .retrieve()
                .body(Map.class);

        if (userInfo == null || userInfo.get("sub") == null) {
            throw new RuntimeException("O Google nao retornou informacoes de usuario validas");
        }
        return userInfo;
    }

    private void validarDadosGoogle(String providerUserId, String email) {
        if (providerUserId == null || providerUserId.isBlank()) {
            throw new RuntimeException("O Google nao retornou um identificador valido");
        }

        if (email == null || email.isBlank()) {
            throw new RuntimeException("O Google nao retornou um e-mail valido");
        }
    }

    private void limparPendenciasExpiradas() {
        pendingRegistrationRepository.deleteByExpiraEmBefore(LocalDateTime.now());
    }

    private UsuarioResponseDTO autenticarUsuarioGoogle(String googleAccessToken) {
        @SuppressWarnings("unchecked")
        Map<String, Object> userInfo = restClient.get()
                .uri(GOOGLE_USERINFO_URI)
                .header("Authorization", "Bearer " + googleAccessToken)
                .retrieve()
                .body(Map.class);

        String email = userInfo != null && userInfo.get("email") != null ? userInfo.get("email").toString() : null;

        if (email == null || email.isBlank()) {
            throw new RuntimeException("Google nao retornou e-mail para autenticar no sistema.");
        }

        Usuario usuario = usuarioService.buscarPorEmail(email);

        if (usuario == null) {
            try {
                usuario = criarUsuarioGoogle(email, userInfo);
            } catch (Exception e) {
                throw new RuntimeException("Erro ao cadastrar novo usuario. Verifique as permissoes do banco de dados.");
            }
        }

        if (!"ATIVO".equals(usuario.getSituacao())) {
            throw new RuntimeException("Usuario do sistema esta inativo.");
        }

        sincronizarVinculoGoogle(usuario, userInfo, email);
        return usuarioMapper.toResponse(usuario);
    }

    private String sincronizarVinculoGoogle(Usuario usuario, Map<String, Object> userInfo, String email) {
        if (usuario == null || userInfo == null || userInfo.get("sub") == null) {
            return null;
        }

        String providerUserId = userInfo.get("sub").toString();
        String avatarUrl = userInfo.get("picture") != null ? userInfo.get("picture").toString() : null;
        String nome = userInfo.get("name") != null ? userInfo.get("name").toString() : usuario.getNomeCompleto();

        UsuarioOAuth vinculo = usuarioOAuthRepository
                .findByProviderAndProviderUserId(OAuthProvider.GOOGLE, providerUserId)
                .orElseGet(() -> usuarioOAuthRepository
                        .findByUsuarioIdAndProvider(usuario.getId(), OAuthProvider.GOOGLE)
                        .orElseGet(UsuarioOAuth::new));

        if (vinculo.getId() != null && !vinculo.getUsuario().getId().equals(usuario.getId())) {
            throw new RuntimeException("Esta conta Google ja esta vinculada a outro usuario");
        }

        vinculo.setUsuario(usuario);
        vinculo.setProvider(OAuthProvider.GOOGLE);
        vinculo.setProviderUserId(providerUserId);
        atualizarDadosVinculoGoogle(vinculo, email, nome, avatarUrl);
        return avatarUrl;
    }

    private void atualizarDadosVinculoGoogle(UsuarioOAuth vinculo, String email, String nome, String avatarUrl) {
        vinculo.setEmailProvider(email);
        vinculo.setNomeProvider(nome);
        vinculo.setAvatarUrl(avatarUrl);
        if (vinculo.getVinculadoEm() == null) {
            vinculo.setVinculadoEm(LocalDateTime.now());
        }
        usuarioOAuthRepository.save(vinculo);
    }

    private Usuario criarUsuarioGoogle(String email, Map<String, Object> userInfo) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        if (googleAllowedDomain != null && !googleAllowedDomain.isBlank() && !normalizedEmail.endsWith(googleAllowedDomain)) {
            throw new RuntimeException("E-mail Google " + email + " nao pertence ao dominio permitido (" + googleAllowedDomain + ").");
        }

        Cargo cargo = usuarioService.buscarCargoOuFalhar(1L);

        Usuario usuario = new Usuario();
        usuario.setNomeCompleto(obterNomeGoogle(normalizedEmail, userInfo));
        usuario.setCpf(gerarCpfGoogle(normalizedEmail));
        usuario.setEmail(normalizedEmail);
        usuario.setContato("");
        usuario.setSenhaHash("GOOGLE_OAUTH_" + UUID.randomUUID());
        usuario.setSituacao("ESPERANDO_APROVACAO");
        usuario.setCargo(cargo);

        return usuarioRepository.save(usuario);
    }

    private String obterNomeGoogle(String email, Map<String, Object> userInfo) {
        if (userInfo != null && userInfo.get("name") != null && !userInfo.get("name").toString().isBlank()) {
            return userInfo.get("name").toString();
        }

        return email.substring(0, email.indexOf('@'));
    }

    private String gerarCpfGoogle(String email) {
        long base = Integer.toUnsignedLong(email.hashCode());

        for (int offset = 0; offset < 100; offset++) {
            String cpf = String.format("%011d", (base + offset) % 100_000_000_000L);

            if (usuarioRepository.findByCpf(cpf).isEmpty()) {
                return cpf;
            }
        }

        throw new RuntimeException("Nao foi possivel gerar CPF tecnico para usuario Google.");
    }
}
