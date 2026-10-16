package com.climb.api.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.br.CNPJ;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.Map;

@Entity
@Table(name = "empresas")
public class Empresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_empresa")
    private Long idEmpresa;

    @Column(name = "razao_social", nullable = false)
    private String razaoSocial;

    @Column(name = "nome_fantasia", nullable = false)
    private String nomeFantasia;

    @CNPJ
    @NotBlank
    @Column(name = "cnpj", length = 18, nullable = false, unique = true)
    private String cnpj;

    @Column(name = "logradouro")
    private String logradouro;

    @Column(name = "numero")
    private String numero;

    @Column(name = "bairro")
    private String bairro;

    @Column(name = "cidade")
    private String cidade;

    @Column(name = "uf")
    private String uf;

    @Column(name = "cep")
    private String cep;

    @Column(name = "telefone", length = 50)
    private String telefone;

    @Column(name = "email")
    private String email;

    @Column(name = "representante_nome")
    private String representanteNome;

    @Column(name = "representante_cpf", length = 14)
    private String representanteCpf;

    @Column(name = "representante_contato", length = 50)
    private String representanteContato;

    @Column(name = "tipo_empresa", length = 40)
    private String tipoEmpresa;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dados_cadastrais", columnDefinition = "json")
    private Map<String, Object> dadosCadastrais;

    public String getTipoEmpresa() { return tipoEmpresa; }
    public void setTipoEmpresa(String tipoEmpresa) { this.tipoEmpresa = tipoEmpresa; }
    public Map<String, Object> getDadosCadastrais() { return dadosCadastrais; }
    public void setDadosCadastrais(Map<String, Object> dados) { this.dadosCadastrais = dados; }

    public Long getIdEmpresa() { return idEmpresa; }
    public void setIdEmpresa(Long idEmpresa) { this.idEmpresa = idEmpresa; }

    public String getRazaoSocial() { return razaoSocial; }
    public void setRazaoSocial(String razaoSocial) { this.razaoSocial = razaoSocial; }

    public String getNomeFantasia() { return nomeFantasia; }
    public void setNomeFantasia(String nomeFantasia) { this.nomeFantasia = nomeFantasia; }

    public String getCnpj() { return cnpj; }
    public void setCnpj(String cnpj) { this.cnpj = cnpj; }

    public String getLogradouro() { return logradouro; }
    public void setLogradouro(String logradouro) { this.logradouro = logradouro; }

    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }

    public String getBairro() { return bairro; }
    public void setBairro(String bairro) { this.bairro = bairro; }

    public String getCidade() { return cidade; }
    public void setCidade(String cidade) { this.cidade = cidade; }

    public String getUf() { return uf; }
    public void setUf(String uf) { this.uf = uf; }

    public String getCep() { return cep; }
    public void setCep(String cep) { this.cep = cep; }

    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRepresentanteNome() { return representanteNome; }
    public void setRepresentanteNome(String representanteNome) { this.representanteNome = representanteNome; }

    public String getRepresentanteCpf() { return representanteCpf; }
    public void setRepresentanteCpf(String representanteCpf) { this.representanteCpf = representanteCpf; }

    public String getRepresentanteContato() { return representanteContato; }
    public void setRepresentanteContato(String representanteContato) { this.representanteContato = representanteContato; }
}
