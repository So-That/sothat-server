FROM openjdk:17-jdk-slim
# 3. 작업 디렉토리 설정
WORKDIR /app
# 4. jar 파일을 컨테이너로 복사
COPY build/libs/Kafka_ES-0.0.1-SNAPSHOT.jar app.jar
# 5. 실행 명령어
ENTRYPOINT ["java", "-jar", "app.jar"]
