# ---------- Estágio 1: build ----------
# Compila o projeto com Maven dentro de um container (você não precisa ter Maven instalado).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
# Baixa as dependências antes de copiar o código: aproveita o cache do Docker
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

# ---------- Estágio 2: execução ----------
# Imagem final menor: só o JRE + o .jar gerado
FROM eclipse-temurin:21-jre
WORKDIR /app
# Usuário sem privilégios (boa prática de segurança)
RUN useradd --system --no-create-home todolist
USER todolist
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
