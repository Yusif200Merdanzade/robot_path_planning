# 1-ci mərhələ: kodu Maven ilə yığır (testlər yerli kompüterdə işlədilir, burada keçilir)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

# 2-ci mərhələ: yalnız JRE və hazır jar — kiçik və təhlükəsiz image
FROM eclipse-temurin:21-jre
WORKDIR /app
# Proqram root yox, imtiyazsız istifadəçi ilə işləyir: proqramda zəiflik tapılsa belə,
# hücumçu konteynerin içində sistem fayllarını dəyişə, paket quraşdıra bilməz
RUN groupadd --system app && useradd --system --gid app --no-create-home --shell /usr/sbin/nologin app
COPY --from=build --chown=app:app /app/target/*.jar app.jar
USER app
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "app.jar"]
