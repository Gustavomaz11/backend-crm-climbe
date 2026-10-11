package com.climb.api.config;

import com.climb.api.service.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiRouteTokenSecurityTest {
    AnnotationConfigApplicationContext context;FilterChainProxy filters;JwtUtil jwt;SessaoAutenticacaoService sessions;
    @BeforeEach void setup() {
        jwt=new JwtUtil();ReflectionTestUtils.setField(jwt,"secretKey","route-test-isolated-key-at-least-256-bits-long");
        ReflectionTestUtils.setField(jwt,"issuer","climb-api");ReflectionTestUtils.setField(jwt,"audience","climb-api");
        ReflectionTestUtils.setField(jwt,"accessTokenExpiration",7200000L);ReflectionTestUtils.setField(jwt,"refreshTokenExpiration",604800000L);
        ReflectionTestUtils.setField(jwt,"pendingTokenExpiration",1800000L);jwt.validarConfiguracao();
        sessions=mock(SessaoAutenticacaoService.class);
        context=new AnnotationConfigApplicationContext();context.register(SecurityConfig.class);
        context.registerBean(JwtAuthenticationFilter.class,()->new JwtAuthenticationFilter(jwt,sessions));context.refresh();
        filters=new FilterChainProxy(context.getBean(SecurityFilterChain.class));
    }
    @AfterEach void close() { context.close();SecurityContextHolder.clearContext(); }

    @Test void todasAsRotasPrivadasDoRepositorioExigemTokenNaCadeiaReal() throws Exception {
        int total=0, privateRoutes=0;
        try(var paths=Files.list(Path.of("src/main/java/com/climb/api/controller"))) {
            for(var file:paths.filter(p->p.toString().endsWith(".java")).toList()) {
                var type=Class.forName("com.climb.api.controller."+file.getFileName().toString().replace(".java",""));
                var base=AnnotatedElementUtils.findMergedAnnotation(type,RequestMapping.class);
                String prefix=base==null||base.path().length==0?"":base.path()[0];
                for(var method:type.getDeclaredMethods()) {
                    var mapping=AnnotatedElementUtils.findMergedAnnotation(method,RequestMapping.class);if(mapping==null)continue;
                    String verb=mapping.method()[0].name();
                    String path=(prefix+(mapping.path().length==0?"":mapping.path()[0])).replaceAll("\\{[^}]+}","1");
                    boolean isPublic=path.startsWith("/auth/")||path.startsWith("/documentos/public/")||path.startsWith("/revisoes/public/")
                            ||path.equals("/hello")||path.equals("/healthz")||(verb.equals("GET")&&path.equals("/cargos"))||(verb.equals("POST")&&path.equals("/usuarios"));
                    var response=new MockHttpServletResponse();var reached=new AtomicBoolean();
                    filters.doFilter(request(verb,path,null),response,(a,b)->reached.set(true));
                    assertThat(reached.get()).as(verb+" "+path).isEqualTo(isPublic);
                    if(!isPublic) { assertThat(response.getStatus()).as(path).isEqualTo(401);privateRoutes++; }
                    total++;
                }
            }
        }
        assertThat(total).isGreaterThan(240);assertThat(privateRoutes).isGreaterThan(220);
        System.out.println("Rotas auditadas: "+total+"; privadas protegidas: "+privateRoutes);
    }

    @Test void refreshCadastroPendenteOuSessaoRevogadaNaoAutorizamRotasPrivadas() throws Exception {
        String sid=UUID.randomUUID().toString();
        for(String token:List.of(jwt.generateRefreshToken(7L,"user@test.local",sid,java.time.Instant.now().plusSeconds(600)),
                jwt.generatePendingRegistrationToken(8L,"pending@test.local"),jwt.generateAccessToken(7L,"user@test.local",sid),"invalido")) {
            var response=new MockHttpServletResponse();var reached=new AtomicBoolean();
            filters.doFilter(request("GET","/usuarios",token),response,(a,b)->reached.set(true));
            assertThat(reached.get()).isFalse();assertThat(response.getStatus()).isIn(401,403);
            SecurityContextHolder.clearContext();
        }
        when(sessions.estaAtiva(sid,7L)).thenReturn(true);
        var reached=new AtomicBoolean();
        filters.doFilter(request("GET","/usuarios",jwt.generateAccessToken(7L,"user@test.local",sid)),new MockHttpServletResponse(),(a,b)->reached.set(true));
        assertThat(reached.get()).isTrue();
    }

    @Test void corsAutorizaSomenteOrigensExplicitas() throws Exception {
        var denied=request("GET","/healthz",null);denied.addHeader("Origin","https://site-nao-autorizado.test");
        var deniedResponse=new MockHttpServletResponse();var reached=new AtomicBoolean();
        filters.doFilter(denied,deniedResponse,(a,b)->reached.set(true));
        assertThat(deniedResponse.getStatus()).isEqualTo(403);assertThat(reached.get()).isFalse();
        var allowed=request("GET","/healthz",null);allowed.addHeader("Origin","http://localhost:5173");
        var allowedResponse=new MockHttpServletResponse();
        filters.doFilter(allowed,allowedResponse,(a,b)->reached.set(true));
        assertThat(reached.get()).isTrue();
        assertThat(allowedResponse.getHeader("Access-Control-Allow-Origin")).isEqualTo("http://localhost:5173");
    }

    private MockHttpServletRequest request(String verb,String path,String token) {
        var request=new MockHttpServletRequest(verb,path);request.setServletPath(path);
        if(token!=null)request.addHeader("Authorization","Bearer "+token);return request;
    }
}
