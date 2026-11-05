# Spring AI RAG (Spring Boot + Ollama + pgvector)

Ask questions about your own documents (PDF, Word, Markdown, text, Java code). Answers come
from a local LLM running in Ollama and cite the document chunks they used. Nothing leaves
your machine.

## How it works

```
Upload / docs folder
   │  1. READ    Apache Tika extracts text (PDF, DOCX, MD, TXT, Java ...)
   │  2. SPLIT   TokenTextSplitter -> ~800-token chunks
   │  3. EMBED   Ollama nomic-embed-text -> 768-number vector per chunk
   ▼  4. STORE   PostgreSQL + pgvector (HNSW index, cosine distance)

Question
   │  5. RETRIEVE embed the question, top-k most similar chunks from pgvector
   ▼  6. GENERATE  Ollama llama3.2 answers using ONLY those chunks, with [1] [2] citations
```

| Layer | Class |
|---|---|
| REST API | `DocumentController` (`/api/documents`), `ChatController` (`/api/chat`) |
| Steps 1–4 | `IngestionService` |
| Steps 5–6 | `RagService` |
| Settings | `application.yml`, `RagProperties` |
| Web page | `src/main/resources/static/index.html` |

## Tech stack

Java 17+, Spring Boot 3.5, Spring AI 1.1, Ollama (llama3.2 + nomic-embed-text),
PostgreSQL 16 + pgvector, Apache Tika, Docker Compose.

## Prerequisites

- **JDK 17 or newer**
- **Maven**, or IntelliJ IDEA (which bundles Maven)
- **Docker Desktop** (runs PostgreSQL + pgvector)
- **Ollama**: https://ollama.com/download (uses your NVIDIA GPU automatically)

## Run it

```bash
# 1. Download the models (about 2.3 GB total, one time)
ollama pull llama3.2
ollama pull nomic-embed-text

# 2. Start the vector database (container "pgvector", port 5432, db ragdb, postgres/postgres)
docker compose up -d

# 3. Start the app (Windows: the wrapper downloads Maven into .mvn\ on first run)
.\mvnw.cmd spring-boot:run
#    or, with Maven installed:  mvn spring-boot:run
```

Stop the app with Ctrl+C and the database with `docker compose down`.
The app uses port 8080, so stop any other app on that port first.

Then open http://localhost:8080, click **Ingest docs/ folder** (a sample Spring Boot notes
file is included), and ask: *"How should I configure JWT in Spring Security?"*

## API

```bash
# Upload a document (PowerShell: use curl.exe instead of curl)
curl -F "file=@C:/path/to/notes.pdf" http://localhost:8080/api/documents

# Ingest everything in the docs/ folder
curl -X POST http://localhost:8080/api/documents/ingest-folder

# Ask a question
curl -X POST http://localhost:8080/api/chat -H "Content-Type: application/json" -d "{\"question\":\"What is Actuator used for?\"}"

# Health check
curl http://localhost:8080/actuator/health
```

Example response:

```json
{
  "answer": "Actuator adds production endpoints such as /actuator/health ... [1]",
  "sources": [
    { "index": 1, "source": "spring-boot-notes.md", "score": 0.71, "excerpt": "..." }
  ]
}
```

## Configuration

All in `src/main/resources/application.yml`:

| Property | Default | Meaning |
|---|---|---|
| `spring.ai.ollama.chat.options.model` | `llama3.2` | Answering model. `qwen2.5:7b` or `llama3.1:8b` give better answers on a GPU with 8 GB+ |
| `spring.ai.ollama.embedding.options.model` | `nomic-embed-text` | Embedding model. If you change it, update `dimensions` and reset the database |
| `rag.top-k` | `4` | Chunks given to the LLM |
| `rag.similarity-threshold` | `0.4` | Minimum similarity for a chunk to be used |

## Notes

- Uploading the same file twice stores it twice. To start fresh: `docker compose down -v`, then `docker compose up -d`.
- `RagService` does retrieval step by step so each stage is visible. Spring AI can also do it in one line with `QuestionAnswerAdvisor` (see the comment in `RagService`).

## Ideas to extend

- Streaming answers (`chatClient.prompt()...stream().content()` with Server-Sent Events)
- Chat memory for follow-up questions (`MessageChatMemoryAdvisor`)
- JWT authentication with Spring Security
- Metadata filters (search only one document) with `SearchRequest.filterExpression`
- Dockerfile for the app, and deploy to AWS (EC2/ECS + RDS PostgreSQL with pgvector)
- Upgrade to Spring AI 2.0 / Spring Boot 4
