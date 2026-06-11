package com.climb.api.model.dto;

public class CompletarCadastroRequestDTO {

    private String cpf;
    private String contato;
    private Long cargoId;

    public String getCpf() { return cpf; }
    public void setCpf(String cpf) { this.cpf = cpf; }

    public String getContato() { return contato; }
    public void setContato(String contato) { this.contato = contato; }

    public Long getCargoId() { return cargoId; }
    public void setCargoId(Long cargoId) { this.cargoId = cargoId; }
}
