FROM clojure:temurin-21-tools-deps

WORKDIR /app
COPY deps.edn bb.edn langgraph.edn ./
RUN clojure -P
COPY src ./src

ENV LANGSERVER_PORT=8000
EXPOSE 8000
CMD ["kbb", "-M", "-m", "nemuri.server"]
