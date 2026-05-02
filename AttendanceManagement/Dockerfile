FROM eclipse-temurin:17-jdk AS build

WORKDIR /app

COPY lib ./lib
COPY src ./src

RUN mkdir -p out && javac -cp "lib/*" src/App.java src/ValidationUtils.java -d out

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /app/out ./out
COPY lib ./lib
COPY web ./web
COPY database ./database
COPY config.properties ./config.properties

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

CMD ["java", "-cp", "out:lib/*", "App"]
