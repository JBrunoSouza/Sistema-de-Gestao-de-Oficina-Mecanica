FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app

# Copia os arquivos de dependência primeiro para aproveitar o cache do Docker
COPY pom.xml .
RUN mvn dependency:go-offline

# Copia o código-fonte e compila
COPY src ./src
RUN mvn clean package