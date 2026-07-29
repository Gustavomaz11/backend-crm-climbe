FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

COPY certs/ /tmp/climb-certificates/
RUN set -eu; \
    for certificate in /tmp/climb-certificates/*.cer /tmp/climb-certificates/*.crt /tmp/climb-certificates/*.pem; do \
        [ -f "$certificate" ] || continue; \
        alias="climb-$(basename "$certificate" | tr '. ' '--')"; \
        keytool -importcert -noprompt -trustcacerts \
            -alias "$alias" \
            -file "$certificate" \
            -cacerts \
            -storepass changeit; \
    done

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:resolve -B
COPY src/ src/
RUN ./mvnw package -DskipTests -B

FROM eclipse-temurin:25-jre
WORKDIR /app

COPY certs/ /tmp/climb-certificates/
RUN set -eu; \
    for certificate in /tmp/climb-certificates/*.cer /tmp/climb-certificates/*.crt /tmp/climb-certificates/*.pem; do \
        [ -f "$certificate" ] || continue; \
        alias="climb-$(basename "$certificate" | tr '. ' '--')"; \
        keytool -importcert -noprompt -trustcacerts \
            -alias "$alias" \
            -file "$certificate" \
            -cacerts \
            -storepass changeit; \
    done

COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
