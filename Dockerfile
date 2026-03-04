FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY MahjongHexWebApp.java ./
RUN javac --add-modules jdk.httpserver MahjongHexWebApp.java

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/*.class ./
COPY index.html ./

EXPOSE 8080
ENV PORT=8080

CMD ["java", "--add-modules", "jdk.httpserver", "MahjongHexWebApp"]
