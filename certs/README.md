# Certificados locais para o container

Este diretório pode receber certificados raiz públicos exigidos pela rede local para inspeção HTTPS.
Arquivos com extensão `.cer`, `.crt` ou `.pem` são importados pelo `Dockerfile` nos truststores Java das imagens de build e runtime.

Os certificados locais são ignorados pelo Git. Para exportar uma CA instalada no Windows:

```powershell
.\scripts\export-windows-root-ca.ps1 -SubjectContains "Sophos SSL CA" 
```

Depois, reconstrua a imagem sem cache:

```powershell
docker compose build --no-cache climb-api
```

Não use este mecanismo para chaves privadas ou certificados que contenham material secreto.
